#!/usr/bin/env python3
"""Read and write the in-app product titles and descriptions on App Store Connect and Google Play.

    python tools/store_products.py pull --out live.json            # read-only: what the stores have now
    python tools/store_products.py check                           # offline: store-metadata/products.json limits
    python tools/store_products.py push --store apple              # dry run: show what would change
    python tools/store_products.py push --store play --apply       # write it

`pull` lists every in-app product each store has (product ID, type, state, every localization)
and the languages the app itself is listed in, so products.json is written against what is
really there. `push` writes products.json to the stores: each product's text goes to every
language the app is listed in, matched to a game language (fr-FR and fr-CA take "fr", es-ES and
es-MX take "es", zh-Hant/zh-TW take "zh-TW", pt-PT takes "pt-BR"); store languages with no match
are left as they are.

Limits (App Store Connect Help; Play Developer API OneTimeProduct): App Store display name 2-30,
description 45; Play title 55, description 200.

Credentials
  App Store: ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_PATH (.p8), a key with the App Manager or Admin role.
  Google Play: PLAY_KEY_PATH or --play-key, a service account JSON with access to the app.
Needs PyJWT with its crypto extra: pip install "pyjwt[crypto]".
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from app_store_metadata import BUNDLE_ID, Client as AscClient  # noqa: E402

PACKAGE = "com.infiltrate.shadowheist"
ASC_V2 = "https://api.appstoreconnect.apple.com/v2"
PLAY_API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" + PACKAGE
PRODUCTS = Path(__file__).resolve().parent.parent / "store-metadata" / "products.json"

GAME_LANGUAGES = ("en", "fr", "it", "es", "pt-BR", "de", "id", "tr", "vi", "ru", "ja", "ko",
                  "zh-TW", "hi", "ar", "th")
LIMITS = {"appStoreName": 30, "appStoreDescription": 45, "playTitle": 55, "playDescription": 200}
# Play only accepts "latency tolerant" updates for large batches, and listing text isn't urgent.
# In-app purchase localization states App Store Connect's API won't PATCH (409 "Cannot edit ...").
ASC_LOCKED_STATES = {"APPROVED", "ACTIVE", "WAITING_FOR_REVIEW", "IN_REVIEW"}
PLAY_LATENCY ="PRODUCT_UPDATE_LATENCY_TOLERANCE_LATENCY_TOLERANT"


def game_language(store_locale):
    """Store locale (en-US, zh-Hant, es-419, pt-PT, ...) -> game language code, or None."""
    loc = store_locale.replace("_", "-")
    if loc in GAME_LANGUAGES:
        return loc
    low = loc.lower()
    if low in ("zh-hant", "zh-tw", "zh-hk"):
        return "zh-TW"
    base = low.split("-")[0]
    if base == "zh":
        return None  # simplified Chinese: the game has no translation
    if base == "pt":
        return "pt-BR"
    return base if base in GAME_LANGUAGES else None


def length(s):
    return len(s)  # Python counts code points, which is what both stores count


# ---------------------------------------------------------------- products.json

def load_products():
    raw = json.loads(PRODUCTS.read_text(encoding="utf-8"))
    return raw["products"]


def check(products):
    problems = []
    for key, p in products.items():
        text = p.get("text", {})
        missing = [l for l in GAME_LANGUAGES if l not in text]
        if missing:
            problems.append(f"{key}: no text for {', '.join(missing)}")
        for lang, fields in text.items():
            if lang not in GAME_LANGUAGES:
                problems.append(f"{key}.{lang}: not a game language")
            for field, limit in LIMITS.items():
                value = fields.get(field)
                if value is None:
                    problems.append(f"{key}.{lang}.{field}: missing")
                elif length(value) > limit:
                    problems.append(f"{key}.{lang}.{field}: {length(value)} characters, limit {limit}: {value!r}")
                elif field == "appStoreName" and length(value) < 2:
                    problems.append(f"{key}.{lang}.{field}: under 2 characters")
    return problems


# ---------------------------------------------------------------- App Store Connect

def asc_client():
    missing = [v for v in ("ASC_KEY_ID", "ASC_ISSUER_ID", "ASC_KEY_PATH") if not os.environ.get(v)]
    if missing:
        raise SystemExit("Missing environment variables for App Store Connect: " + ", ".join(missing))
    return AscClient(os.environ["ASC_KEY_ID"], os.environ["ASC_ISSUER_ID"], os.environ["ASC_KEY_PATH"])


def asc_state(client):
    apps = client.get_all("/apps", {"filter[bundleId]": BUNDLE_ID})
    if not apps:
        raise SystemExit(f"No app with bundle ID {BUNDLE_ID}")
    app_id = apps[0]["id"]
    # The languages the app is listed in = its app info localizations (the newest record).
    infos = client.get_all(f"/apps/{app_id}/appInfos")
    locales = set()
    for info in infos:
        for loc in client.get_all(f"/appInfos/{info['id']}/appInfoLocalizations"):
            locales.add(loc["attributes"]["locale"])
    products = []
    for iap in client.get_all(f"/apps/{app_id}/inAppPurchasesV2"):
        a = iap["attributes"]
        locs = client.get_all(f"{ASC_V2}/inAppPurchases/{iap['id']}/inAppPurchaseLocalizations")
        products.append({
            "id": iap["id"], "productId": a.get("productId"), "referenceName": a.get("name"),
            "type": a.get("inAppPurchaseType"), "state": a.get("state"),
            "localizations": {l["attributes"]["locale"]: {
                "id": l["id"], "name": l["attributes"].get("name"),
                "description": l["attributes"].get("description"), "state": l["attributes"].get("state")}
                for l in locs},
        })
    return {"appId": app_id, "listingLocales": sorted(locales), "products": products}


def asc_push(client, products, apply):
    live = asc_state(client)
    by_product_id = {p["productId"]: p for p in live["products"]}
    changes = 0
    skipped = []
    for key, p in products.items():
        pid = p.get("appStore")
        if not pid:
            continue
        iap = by_product_id.get(pid)
        if not iap:
            raise SystemExit(f"{key}: App Store Connect has no in-app purchase {pid!r} "
                             f"(it has: {', '.join(sorted(by_product_id))})")
        for locale in live["listingLocales"]:
            lang = game_language(locale)
            if not lang:
                print(f"[{pid}] {locale}: no game language, left as it is")
                continue
            t = p["text"][lang]
            want = {"name": t["appStoreName"], "description": t["appStoreDescription"]}
            have = iap["localizations"].get(locale)
            if have:
                attrs = {k: v for k, v in want.items() if have.get(k) != v}
                if not attrs:
                    continue
                if have.get("state") in ASC_LOCKED_STATES:
                    # The API refuses (409) to edit a live or in-review localization; App Store
                    # Connect's web page has to be used for those.
                    skipped.append(f"{pid} {locale} ({have.get('state')})")
                    continue
                changes += 1
                print(f"[{pid}] {locale}: {have.get('name')!r} / {have.get('description')!r}\n"
                      f"{'':>{len(pid) + 3}}{len(locale) * ' '}  -> {want['name']!r} / {want['description']!r}")
                if apply:
                    client.request("PATCH", f"/inAppPurchaseLocalizations/{have['id']}", {"data": {
                        "type": "inAppPurchaseLocalizations", "id": have["id"], "attributes": attrs}})
            else:
                changes += 1
                print(f"[{pid}] {locale}: add {want['name']!r} / {want['description']!r}")
                if apply:
                    client.request("POST", "/inAppPurchaseLocalizations", {"data": {
                        "type": "inAppPurchaseLocalizations", "attributes": dict(want, locale=locale),
                        "relationships": {"inAppPurchaseV2": {"data": {"type": "inAppPurchases", "id": iap["id"]}}}}})
    if skipped:
        print("Not editable through the API (change these in App Store Connect): " + ", ".join(skipped))
    summary(changes, apply)


# ---------------------------------------------------------------- Google Play

class PlayClient:
    def __init__(self, key_path):
        import jwt

        self._key = json.loads(Path(key_path).read_text(encoding="utf-8"))
        now = int(time.time())
        assertion = jwt.encode({
            "iss": self._key["client_email"], "scope": "https://www.googleapis.com/auth/androidpublisher",
            "aud": "https://oauth2.googleapis.com/token", "iat": now, "exp": now + 3600,
        }, self._key["private_key"], algorithm="RS256")
        body = urllib.parse.urlencode({"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                                       "assertion": assertion}).encode()
        with urllib.request.urlopen(urllib.request.Request("https://oauth2.googleapis.com/token", body)) as r:
            self._token = json.loads(r.read())["access_token"]
        self.email = self._key["client_email"]

    def request(self, method, path, body=None, params=None):
        url = PLAY_API + path
        if params:
            url += "?" + urllib.parse.urlencode(params)
        data = json.dumps(body).encode() if body is not None else (b"{}" if method == "POST" else None)
        req = urllib.request.Request(url, data=data, method=method, headers={
            "Authorization": f"Bearer {self._token}", "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req) as resp:
                text = resp.read().decode()
                return json.loads(text) if text else {}
        except urllib.error.HTTPError as e:
            raise SystemExit(f"{method} {path} failed ({e.code}): {e.read().decode(errors='replace')}")


def play_state(client):
    # Store listing languages (and the default) come from an edit, which is then thrown away.
    edit = client.request("POST", "/edits")
    try:
        listings = client.request("GET", f"/edits/{edit['id']}/listings").get("listings", [])
        details = client.request("GET", f"/edits/{edit['id']}/details")
    finally:
        client.request("DELETE", f"/edits/{edit['id']}")
    products, token = [], None
    while True:
        params = {"pageSize": 1000}
        if token:
            params["pageToken"] = token
        page = client.request("GET", "/oneTimeProducts", params=params)
        products.extend(page.get("oneTimeProducts", []))
        token = page.get("nextPageToken")
        if not token:
            break
    return {
        "defaultLanguage": details.get("defaultLanguage"),
        "listingLanguages": sorted(l["language"] for l in listings),
        "products": [{
            "productId": p["productId"],
            "purchaseOptions": [{"id": o.get("purchaseOptionId"), "state": o.get("state"),
                                 "type": "rent" if "rentOption" in o else "buy"}
                                for o in p.get("purchaseOptions", [])],
            "regionsVersion": (p.get("regionsVersion") or {}).get("version"),
            "listings": {l["languageCode"]: {"title": l.get("title"), "description": l.get("description")}
                         for l in p.get("listings", [])},
        } for p in products],
    }


def play_push(client, products, apply):
    live = play_state(client)
    raw = {}
    token = None
    while True:  # the full product bodies, so the PATCH sends back everything but listings unchanged
        params = {"pageSize": 1000}
        if token:
            params["pageToken"] = token
        page = client.request("GET", "/oneTimeProducts", params=params)
        raw.update({p["productId"]: p for p in page.get("oneTimeProducts", [])})
        token = page.get("nextPageToken")
        if not token:
            break
    changes = 0
    for key, p in products.items():
        pid = p.get("play")
        if not pid:
            continue
        product = raw.get(pid)
        if not product:
            raise SystemExit(f"{key}: Play has no one-time product {pid!r} (it has: {', '.join(sorted(raw))})")
        listings = {l["languageCode"]: dict(l) for l in product.get("listings", [])}
        changed = []
        for language in live["listingLanguages"]:
            lang = game_language(language)
            if not lang:
                print(f"[{pid}] {language}: no game language, left as it is")
                continue
            t = p["text"][lang]
            want = {"languageCode": language, "title": t["playTitle"], "description": t["playDescription"]}
            have = listings.get(language)
            if have and have.get("title") == want["title"] and have.get("description") == want["description"]:
                continue
            print(f"[{pid}] {language}: " + (f"{have.get('title')!r} / {have.get('description')!r}\n"
                                              f"{'':>{len(pid) + len(language) + 5}}-> " if have else "add ")
                  + f"{want['title']!r} / {want['description']!r}")
            listings[language] = want
            changed.append(language)
        if not changed:
            continue
        changes += len(changed)
        if apply:
            body = dict(product, listings=list(listings.values()))
            body.pop("regionsVersion", None)
            # Lower case here: the list endpoint is /oneTimeProducts, but PATCH 404s unless it's this.
            client.request("PATCH", f"/onetimeproducts/{pid}", body, params={
                "updateMask": "listings",
                "regionsVersion.version": (product.get("regionsVersion") or {}).get("version") or "2025/03",
                "latencyTolerance": PLAY_LATENCY})
    summary(changes, apply)


# ---------------------------------------------------------------- main

def summary(changes, apply):
    if not changes:
        print("Everything already matches.")
    elif apply:
        print(f"Done: {changes} listing(s) written.")
    else:
        print(f"Dry run: {changes} listing(s) would change. Re-run with --apply to write them.")


def play_key(args):
    key = args.play_key or os.environ.get("PLAY_KEY_PATH")
    if not key:
        raise SystemExit("Pass --play-key <service-account.json> or set PLAY_KEY_PATH")
    return key


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=("pull", "check", "push"))
    parser.add_argument("--store", choices=("apple", "play", "both"), default="both")
    parser.add_argument("--play-key", help="Google Play service account JSON (or PLAY_KEY_PATH)")
    parser.add_argument("--out", help="pull: write the JSON here instead of printing it")
    parser.add_argument("--apply", action="store_true", help="push: write the changes (default is a dry run)")
    args = parser.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")
    stores = ("apple", "play") if args.store == "both" else (args.store,)

    if args.command == "pull":
        out = {}
        if "apple" in stores:
            out["appStore"] = asc_state(asc_client())
        if "play" in stores:
            client = PlayClient(play_key(args))
            print(f"Google Play: signed in as {client.email}", file=sys.stderr)
            out["play"] = play_state(client)
        text = json.dumps(out, ensure_ascii=False, indent=2)
        if args.out:
            Path(args.out).write_text(text + "\n", encoding="utf-8")
            print(f"Wrote {args.out}", file=sys.stderr)
        else:
            print(text)
        return

    products = load_products()
    problems = check(products)
    for p in problems:
        print("problem:", p)
    if problems:
        sys.exit(1)
    print(f"{PRODUCTS.name}: {len(products)} products, {len(GAME_LANGUAGES)} languages, within store limits.")
    if args.command == "check":
        return
    if "apple" in stores:
        print("\n== App Store Connect ==")
        asc_push(asc_client(), products, args.apply)
    if "play" in stores:
        print("\n== Google Play ==")
        play_push(PlayClient(play_key(args)), products, args.apply)


if __name__ == "__main__":
    main()
