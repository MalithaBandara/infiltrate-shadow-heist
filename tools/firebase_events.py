#!/usr/bin/env python3
"""Print Firebase (Google Analytics 4) event counts for Infiltrate, per platform, read-only.

    pip install "pyjwt[crypto]"
    python tools/firebase_events.py path/to/service-account.json [days]   # default 28 days

The service account needs no Google Cloud role, only read access to the Analytics property:
Google Analytics > Admin > Property access management > + > the service account's email, role
Viewer. The Google Analytics Data API must be enabled in the shadow-heist-game Cloud project.
"""

import json
import sys
import time
import urllib.parse
import urllib.request

# The GA4 property linked to Firebase project shadow-heist-game (needs the Google Analytics Data
# API enabled in that Cloud project).
PROPERTY = "properties/563203740"
SCOPE ="https://www.googleapis.com/auth/analytics.readonly"
# levels_cleared_N: the Nth distinct level a player cleared (every N from the release after build
# 33; only 3 and 5
# before that).
CLEARED = [f"levels_cleared_{n}" for n in range(1, 13)]
EVENTS = ["first_open", "session_start", "level_start", "level_complete", "level_fail",
          "tutorial_complete", *CLEARED, "ad_impression"]


def token(key_path):
    import jwt

    key = json.load(open(key_path, encoding="utf-8"))
    now = int(time.time())
    assertion = jwt.encode({"iss": key["client_email"], "scope": SCOPE, "aud": key["token_uri"],
                            "iat": now, "exp": now + 3600}, key["private_key"], algorithm="RS256")
    body = urllib.parse.urlencode({"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                                   "assertion": assertion}).encode()
    with urllib.request.urlopen(key["token_uri"], body) as resp:
        return json.load(resp)["access_token"]


def call(url, tok, body=None):
    req = urllib.request.Request(url, json.dumps(body).encode() if body else None,
                                 {"Authorization": f"Bearer {tok}", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as resp:
            return json.load(resp)
    except urllib.error.HTTPError as e:
        raise SystemExit(f"{url} failed ({e.code}): {e.read().decode(errors='replace')}")


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    days = int(sys.argv[2]) if len(sys.argv) > 2 else 28
    tok = token(sys.argv[1])

    for prop in [{"displayName": "Shadow Heist", "property": PROPERTY}]:
        print(f"\n== {prop['displayName']} ({prop['property']}), last {days} days ==")
        report = call(f"https://analyticsdata.googleapis.com/v1beta/{prop['property']}:runReport", tok, {
            "dateRanges": [{"startDate": f"{days}daysAgo", "endDate": "today"}],
            "dimensions": [{"name": "eventName"}, {"name": "platform"}],
            "metrics": [{"name": "eventCount"}, {"name": "totalUsers"}],
            "dimensionFilter": {"filter": {"fieldName": "eventName",
                                           "inListFilter": {"values": EVENTS}}},
        })
        rows = sorted(((r["dimensionValues"][0]["value"], r["dimensionValues"][1]["value"],
                        int(r["metricValues"][0]["value"]), int(r["metricValues"][1]["value"]))
                       for r in report.get("rows", [])), key=lambda r: (EVENTS.index(r[0]), r[1]))
        if not rows:
            print("  no events yet")
        print(f"  {'event':<20}{'platform':<10}{'events':>8}{'users':>8}")
        for name, platform, count, users in rows:
            print(f"  {name:<20}{platform:<10}{count:>8}{users:>8}")

        # How far players got: users who cleared at least N distinct levels, and how many of them
        # stopped there. Builds up to 33 only send 3 and 5.
        users = {}
        for name, _platform, _count, n_users in rows:
            if name in CLEARED:
                users[name] = users.get(name, 0) + n_users
        if users:
            print(f"\n  {'levels cleared':<16}{'users':>8}{'stopped':>9}")
            for i, name in enumerate(CLEARED):
                if name not in users:
                    continue
                nxt = users.get(CLEARED[i + 1]) if i + 1 < len(CLEARED) else 0
                stopped = "" if nxt is None else f"{users[name] - nxt:>9}"
                print(f"  {name.removeprefix('levels_cleared_'):<16}{users[name]:>8}{stopped}")

        daily = call(f"https://analyticsdata.googleapis.com/v1beta/{prop['property']}:runReport", tok, {
            "dateRanges": [{"startDate": f"{days}daysAgo", "endDate": "today"}],
            "dimensions": [{"name": "date"}, {"name": "eventName"}],
            "metrics": [{"name": "eventCount"}],
            "dimensionFilter": {"filter": {"fieldName": "eventName", "inListFilter": {
                "values": ["first_open", "tutorial_complete", "levels_cleared_3"]}}},
        })
        per_day = {}
        for r in daily.get("rows", []):
            d, e = r["dimensionValues"][0]["value"], r["dimensionValues"][1]["value"]
            per_day.setdefault(d, {})[e] = int(r["metricValues"][0]["value"])
        if per_day:
            print(f"\n  {'date':<10}{'first_open':>12}{'tutorial':>10}{'cleared_3':>11}")
            for d in sorted(per_day):
                v = per_day[d]
                print(f"  {d:<10}{v.get('first_open', 0):>12}{v.get('tutorial_complete', 0):>10}"
                      f"{v.get('levels_cleared_3', 0):>11}")


if __name__ == "__main__":
    main()
