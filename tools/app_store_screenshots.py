#!/usr/bin/env python3
"""Upload localized App Store screenshots to an App Store version.

Expects a folder laid out as  <lang>/app_store_iphone_2556x1179/1.png ... and
<lang>/app_store_ipad_2752x2064/1.png ..., where <lang> is the game's language code (en, fr,
zh-TW, ...). Each language maps to one or more App Store Connect locales (es fills es-ES and
es-MX). For every locale and device, the version's screenshot set is replaced by the folder's
images in file-name order, unless it already holds exactly those images (compared by MD5).

    python3 tools/app_store_screenshots.py --dir shots --version 1.1.4            # dry run
    python3 tools/app_store_screenshots.py --dir shots --version 1.1.4 --apply    # upload

Credentials: the same ASC_KEY_ID / ASC_ISSUER_ID / ASC_KEY_PATH as app_store_metadata.py.
"""

import argparse
import hashlib
import os
import struct
import sys
import time
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from app_store_metadata import BUNDLE_ID, EDITABLE_STATES, Client, state_of  # noqa: E402

# Folder name -> (App Store Connect display type, accepted (width, height)).
DEVICES = {
    "app_store_iphone_2556x1179": ("APP_IPHONE_61", {(2556, 1179), (1179, 2556)}),
    "app_store_ipad_2752x2064": ("APP_IPAD_PRO_3GEN_129", {(2752, 2064), (2064, 2752)}),
}
# Game language folder -> App Store Connect locales.
LOCALES = {
    "en": ["en-US"], "zh-TW": ["zh-Hant"], "ja": ["ja"], "ko": ["ko"], "fr": ["fr-FR"],
    "it": ["it"], "es": ["es-ES", "es-MX"], "de": ["de-DE"], "pt-BR": ["pt-BR"], "id": ["id"],
    "tr": ["tr"], "vi": ["vi"], "ru": ["ru"], "hi": ["hi"], "ar": ["ar-SA"], "th": ["th"],
}
MAX_PER_SET = 10


def png_info(path):
    with open(path, "rb") as f:
        head = f.read(26)
    if head[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit(f"{path}: not a PNG")
    width, height = struct.unpack(">II", head[16:24])
    has_alpha = head[25] in (4, 6)
    return width, height, has_alpha


def collect(root):
    """{locale: {display_type: [Path, ...]}} plus a list of problems."""
    plan, problems = {}, []
    for lang, locales in LOCALES.items():
        for folder, (display, sizes) in DEVICES.items():
            d = root / lang / folder
            files = sorted(d.glob("*.png"), key=lambda p: (len(p.stem), p.stem)) if d.is_dir() else []
            if not files:
                problems.append(f"{lang}/{folder}: no PNG files")
                continue
            if len(files) > MAX_PER_SET:
                problems.append(f"{lang}/{folder}: {len(files)} files, App Store allows {MAX_PER_SET}")
            for f in files:
                w, h, alpha = png_info(f)
                if (w, h) not in sizes:
                    problems.append(f"{f}: {w}x{h} isn't accepted for {display}")
                if alpha:
                    problems.append(f"{f}: has an alpha channel, which App Store Connect rejects")
            for loc in locales:
                plan.setdefault(loc, {})[display] = files
    return plan, problems


def md5(path):
    return hashlib.md5(path.read_bytes()).hexdigest()


def upload(client, set_id, path):
    data = path.read_bytes()
    shot = client.request("POST", "/appScreenshots", {"data": {
        "type": "appScreenshots",
        "attributes": {"fileName": path.name, "fileSize": len(data)},
        "relationships": {"appScreenshotSet": {"data": {"type": "appScreenshotSets", "id": set_id}}}}})["data"]
    for op in shot["attributes"]["uploadOperations"]:
        chunk = data[op["offset"]:op["offset"] + op["length"]]
        headers = {h["name"]: h["value"] for h in op.get("requestHeaders", [])}
        req = urllib.request.Request(op["url"], data=chunk, method=op["method"], headers=headers)
        with urllib.request.urlopen(req) as resp:
            resp.read()
    client.request("PATCH", f"/appScreenshots/{shot['id']}", {"data": {
        "type": "appScreenshots", "id": shot["id"],
        "attributes": {"uploaded": True, "sourceFileChecksum": hashlib.md5(data).hexdigest()}}})
    return shot["id"]


def wait_processed(client, ids, label):
    pending = set(ids)
    deadline = time.time() + 600
    while pending and time.time() < deadline:
        for sid in list(pending):
            state = client.request("GET", f"/appScreenshots/{sid}")["data"]["attributes"].get(
                "assetDeliveryState") or {}
            if state.get("state") == "COMPLETE":
                pending.discard(sid)
            elif state.get("state") == "FAILED":
                errors = "; ".join(e.get("description", "") for e in state.get("errors", []))
                raise SystemExit(f"{label}: App Store Connect rejected a screenshot: {errors}")
        if pending:
            time.sleep(3)
    if pending:
        raise SystemExit(f"{label}: {len(pending)} screenshot(s) still processing after 10 minutes")


def sync(client, plan, version_string, apply):
    app_id = client.get_all("/apps", {"filter[bundleId]": BUNDLE_ID})[0]["id"]
    versions = client.get_all(f"/apps/{app_id}/appStoreVersions",
                              {"filter[versionString]": version_string, "filter[platform]": "IOS"})
    if not versions:
        raise SystemExit(f"Version {version_string} doesn't exist in App Store Connect.")
    version = versions[0]
    vstate = state_of(version["attributes"])
    print(f"Version {version_string}: {vstate}")
    if vstate not in EDITABLE_STATES:
        raise SystemExit("Screenshots can only change while the version is being prepared.")

    version_locs = {l["attributes"]["locale"]: l["id"] for l in
                    client.get_all(f"/appStoreVersions/{version['id']}/appStoreVersionLocalizations")}
    changes = 0
    for locale, devices in plan.items():
        loc_id = version_locs.get(locale)
        if not loc_id:
            print(f"[{locale}] skipped: the version has no {locale} localization yet "
                  "(run app_store_metadata.py first)")
            continue
        sets = {s["attributes"]["screenshotDisplayType"]: s["id"] for s in
                client.get_all(f"/appStoreVersionLocalizations/{loc_id}/appScreenshotSets")}
        others = sorted(set(sets) - set(devices))
        for display, files in devices.items():
            label = f"[{locale}] {display}"
            set_id = sets.get(display)
            current = client.get_all(f"/appScreenshotSets/{set_id}/appScreenshots") if set_id else []
            current_sums = [(s["attributes"].get("sourceFileChecksum") or "") for s in current]
            wanted_sums = [md5(f) for f in files]
            if current_sums == wanted_sums:
                print(f"{label}: already up to date ({len(files)})")
                continue
            changes += 1
            print(f"{label}: replace {len(current)} screenshot(s) with {len(files)}")
            if not apply:
                continue
            if not set_id:
                set_id = client.request("POST", "/appScreenshotSets", {"data": {
                    "type": "appScreenshotSets", "attributes": {"screenshotDisplayType": display},
                    "relationships": {"appStoreVersionLocalization": {
                        "data": {"type": "appStoreVersionLocalizations", "id": loc_id}}}}})["data"]["id"]
            for s in current:
                client.request("DELETE", f"/appScreenshots/{s['id']}")
            new_ids = [upload(client, set_id, f) for f in files]
            wait_processed(client, new_ids, label)
            client.request("PATCH", f"/appScreenshotSets/{set_id}/relationships/appScreenshots", {
                "data": [{"type": "appScreenshots", "id": i} for i in new_ids]})
        if others:
            print(f"[{locale}] note: also has screenshot sets this folder doesn't cover: {', '.join(others)}")

    if not changes:
        print("Everything already matches.")
    elif apply:
        print(f"Done: {changes} screenshot set(s) replaced.")
    else:
        print(f"Dry run: {changes} screenshot set(s) would be replaced. Re-run with --apply to upload.")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dir", required=True, help="folder with <lang>/<device>/N.png")
    parser.add_argument("--version", help="App Store version string, e.g. 1.1.4")
    parser.add_argument("--check", action="store_true", help="only validate the folder, offline")
    parser.add_argument("--apply", action="store_true", help="upload (default is a dry run)")
    args = parser.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")

    plan, problems = collect(Path(args.dir))
    for p in problems:
        print("problem:", p)
    if problems:
        sys.exit(1)
    print(f"{len(plan)} locales x {len(DEVICES)} devices of screenshots look valid.")
    if args.check:
        return
    if not args.version:
        parser.error("--version is required unless --check")
    missing = [v for v in ("ASC_KEY_ID", "ASC_ISSUER_ID", "ASC_KEY_PATH") if not os.environ.get(v)]
    if missing:
        raise SystemExit("Missing environment variables: " + ", ".join(missing))
    client = Client(os.environ["ASC_KEY_ID"], os.environ["ASC_ISSUER_ID"], os.environ["ASC_KEY_PATH"])
    sync(client, plan, args.version, args.apply)


if __name__ == "__main__":
    main()
