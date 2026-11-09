#!/usr/bin/env python3
"""Upload the App Store listing text in store-metadata/app-store.json to App Store Connect.

Sets, for every locale in the file, the version's description / keywords / promotional text /
What's New, and the app's name / subtitle, creating any locale App Store Connect doesn't have yet.
New locales copy the support, marketing and privacy policy URLs from the primary (en-US) locale.
Only fields present in the file are written; anything missing keeps its current value.

    python3 tools/app_store_metadata.py --check                    # offline: limits only
    python3 tools/app_store_metadata.py --version 1.1.3            # dry run: show what would change
    python3 tools/app_store_metadata.py --version 1.1.3 --apply    # write it

Credentials (an App Store Connect API key with the App Manager or Admin role - a Developer key
can upload builds but is refused here) come from ASC_KEY_ID, ASC_ISSUER_ID and ASC_KEY_PATH (the
.p8 file). Needs PyJWT with its crypto extra: pip install "pyjwt[crypto]".
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

API = "https://api.appstoreconnect.apple.com/v1"
BUNDLE_ID = "com.infiltrate.shadowheist"
PRIMARY_LOCALE = "en-US"
METADATA = Path(__file__).resolve().parent.parent / "store-metadata" / "app-store.json"

LIMITS = {"name": 30, "subtitle": 30, "keywords": 100, "promotionalText": 170,
          "description": 4000, "whatsNew": 4000}
VERSION_FIELDS = ("description", "keywords", "promotionalText", "whatsNew")
INFO_FIELDS = ("name", "subtitle")
# Version (and app info) states in which App Store Connect accepts listing edits.
EDITABLE_STATES = {"PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED",
                   "METADATA_REJECTED", "INVALID_BINARY"}


def load_metadata():
    raw = json.loads(METADATA.read_text(encoding="utf-8"))
    shared = raw.get("shared", {})
    locales = {}
    for locale, fields in raw["locales"].items():
        out = dict(fields)
        desc = out.get("description")
        if isinstance(desc, str) and desc.startswith("@"):
            desc = shared[desc[1:]]
        if isinstance(desc, list):
            out["description"] = "\n\n".join(desc)
        if "name" not in out and raw.get("name"):
            out["name"] = raw["name"]
        locales[locale] = out
    return locales


def check(locales):
    problems = []
    for locale, fields in locales.items():
        for field, limit in LIMITS.items():
            value = fields.get(field)
            if value is None:
                continue
            if len(value) > limit:
                problems.append(f"{locale}.{field}: {len(value)} characters, limit {limit}")
        keywords = fields.get("keywords")
        if keywords is not None:
            words = keywords.split(",")
            if any(w != w.strip() or not w for w in words):
                problems.append(f"{locale}.keywords: spaces around commas or empty entries waste characters")
            if len(set(w.lower() for w in words)) != len(words):
                problems.append(f"{locale}.keywords: repeated keyword")
    return problems


class Client:
    def __init__(self, key_id, issuer_id, key_path):
        import jwt  # PyJWT, only needed when talking to the API

        self._jwt = jwt
        self._key_id = key_id
        self._issuer_id = issuer_id
        self._key = Path(key_path).read_text()
        self._token = None
        self._token_expiry = 0

    def _auth(self):
        now = int(time.time())
        if not self._token or now > self._token_expiry - 60:
            self._token_expiry = now + 15 * 60
            self._token = self._jwt.encode(
                {"iss": self._issuer_id, "iat": now, "exp": self._token_expiry, "aud": "appstoreconnect-v1"},
                self._key, algorithm="ES256", headers={"kid": self._key_id, "typ": "JWT"})
        return self._token

    def request(self, method, path, body=None, params=None):
        url = path if path.startswith("http") else API + path
        if params:
            url += "?" + urllib.parse.urlencode(params)
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(url, data=data, method=method, headers={
            "Authorization": f"Bearer {self._auth()}", "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req) as resp:
                text = resp.read().decode()
                return json.loads(text) if text else {}
        except urllib.error.HTTPError as e:
            detail = e.read().decode(errors="replace")
            try:
                errors = json.loads(detail).get("errors", [])
                detail = "; ".join(f"{x.get('title')}: {x.get('detail')}" for x in errors) or detail
            except ValueError:
                pass
            raise SystemExit(f"{method} {path} failed ({e.code}): {detail}")

    def get_all(self, path, params=None):
        params = dict(params or {}, limit=200)
        result = self.request("GET", path, params=params)
        items = list(result.get("data", []))
        while result.get("links", {}).get("next"):
            result = self.request("GET", result["links"]["next"])
            items.extend(result.get("data", []))
        return items


def state_of(attrs):
    return attrs.get("appVersionState") or attrs.get("appStoreState") or attrs.get("state")


def diff(current, wanted, fields):
    return {f: wanted[f] for f in fields if f in wanted and (current or {}).get(f) != wanted[f]}


def short(value):
    value = value.replace("\n", " / ")
    return value if len(value) <= 70 else value[:67] + "..."


def sync(client, locales, version_string, apply):
    apps = client.get_all("/apps", {"filter[bundleId]": BUNDLE_ID})
    if not apps:
        raise SystemExit(f"No app with bundle ID {BUNDLE_ID}")
    app_id = apps[0]["id"]

    versions = client.get_all(f"/apps/{app_id}/appStoreVersions",
                              {"filter[versionString]": version_string, "filter[platform]": "IOS"})
    if not versions:
        raise SystemExit(f"Version {version_string} doesn't exist yet - add it in App Store Connect "
                         "(iOS App → + Version) first.")
    version = versions[0]
    vstate = state_of(version["attributes"])
    print(f"Version {version_string}: {vstate}")
    if vstate not in EDITABLE_STATES:
        print(f"  warning: App Store Connect may refuse edits in state {vstate}; "
              "only promotional text can change after submission.")

    infos = client.get_all(f"/apps/{app_id}/appInfos")
    editable_infos = [i for i in infos if state_of(i["attributes"]) in EDITABLE_STATES]
    app_info = editable_infos[0] if editable_infos else None
    if app_info is None:
        print("  warning: no editable app info record, so name and subtitle are skipped "
              "(they only change together with a version being prepared).")

    version_locs = {l["attributes"]["locale"]: l for l in
                    client.get_all(f"/appStoreVersions/{version['id']}/appStoreVersionLocalizations")}
    info_locs = {}
    if app_info:
        info_locs = {l["attributes"]["locale"]: l for l in
                     client.get_all(f"/appInfos/{app_info['id']}/appInfoLocalizations")}

    primary_version = version_locs.get(PRIMARY_LOCALE, {}).get("attributes", {})
    primary_info = info_locs.get(PRIMARY_LOCALE, {}).get("attributes", {})
    changes = 0

    for locale, wanted in locales.items():
        # App info first: App Store Connect wants a name in a locale before its version text.
        if app_info:
            existing = info_locs.get(locale)
            if existing:
                attrs = diff(existing["attributes"], wanted, INFO_FIELDS)
                if attrs:
                    changes += 1
                    print(f"[{locale}] update name/subtitle: " + ", ".join(f"{k}={short(v)!r}" for k, v in attrs.items()))
                    if apply:
                        client.request("PATCH", f"/appInfoLocalizations/{existing['id']}", {"data": {
                            "type": "appInfoLocalizations", "id": existing["id"], "attributes": attrs}})
            else:
                attrs = {f: wanted[f] for f in INFO_FIELDS if f in wanted}
                attrs["locale"] = locale
                if primary_info.get("privacyPolicyUrl"):
                    attrs["privacyPolicyUrl"] = primary_info["privacyPolicyUrl"]
                changes += 1
                print(f"[{locale}] add locale to app info: name={attrs.get('name')!r} subtitle={attrs.get('subtitle')!r}")
                if apply:
                    client.request("POST", "/appInfoLocalizations", {"data": {
                        "type": "appInfoLocalizations", "attributes": attrs,
                        "relationships": {"appInfo": {"data": {"type": "appInfos", "id": app_info["id"]}}}}})

        existing = version_locs.get(locale)
        if existing:
            attrs = diff(existing["attributes"], wanted, VERSION_FIELDS)
            if attrs:
                changes += 1
                print(f"[{locale}] update " + ", ".join(sorted(attrs)))
                if apply:
                    client.request("PATCH", f"/appStoreVersionLocalizations/{existing['id']}", {"data": {
                        "type": "appStoreVersionLocalizations", "id": existing["id"], "attributes": attrs}})
        else:
            attrs = {f: wanted[f] for f in VERSION_FIELDS if f in wanted}
            attrs["locale"] = locale
            for url_field in ("supportUrl", "marketingUrl"):
                if primary_version.get(url_field):
                    attrs[url_field] = primary_version[url_field]
            changes += 1
            print(f"[{locale}] add locale to version {version_string}: " + ", ".join(sorted(k for k in attrs if k != "locale")))
            if apply:
                client.request("POST", "/appStoreVersionLocalizations", {"data": {
                    "type": "appStoreVersionLocalizations", "attributes": attrs,
                    "relationships": {"appStoreVersion": {"data": {"type": "appStoreVersions", "id": version["id"]}}}}})

    if not changes:
        print("Everything already matches.")
    elif apply:
        print(f"Done: {changes} change(s) written.")
    else:
        print(f"Dry run: {changes} change(s) would be written. Re-run with --apply to write them.")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="only validate the file, offline")
    parser.add_argument("--version", help="App Store version string, e.g. 1.1.3")
    parser.add_argument("--apply", action="store_true", help="write the changes (default is a dry run)")
    args = parser.parse_args()
    # The log prints every locale's text; a Windows console's default code page can't encode it.
    sys.stdout.reconfigure(encoding="utf-8")

    locales = load_metadata()
    problems = check(locales)
    for p in problems:
        print("problem:", p)
    if problems:
        sys.exit(1)
    print(f"{METADATA.name}: {len(locales)} locales within App Store limits.")
    if args.check:
        return
    if not args.version:
        parser.error("--version is required unless --check")

    missing = [v for v in ("ASC_KEY_ID", "ASC_ISSUER_ID", "ASC_KEY_PATH") if not os.environ.get(v)]
    if missing:
        raise SystemExit("Missing environment variables: " + ", ".join(missing))
    client = Client(os.environ["ASC_KEY_ID"], os.environ["ASC_ISSUER_ID"], os.environ["ASC_KEY_PATH"])
    sync(client, locales, args.version, args.apply)


if __name__ == "__main__":
    main()
