#!/usr/bin/env python3
"""Fail when F-Droid has not published our latest stable release.

F-Droid builds every new tag on its own (`AutoUpdateMode: Version`), and a build that fails
there publishes nothing and tells nobody: no email, no issue, no red check on our side. The
previous version simply stays listed. Because the recipe uses `Binaries:`, every failure
mode -- a reproducibility mismatch, a toolchain F-Droid's image lacks, a scanner rejection --
looks the same from outside: F-Droid is behind. This script is that view from outside.

A release fails the check when either

  * F-Droid's most recent build cycle lists its versionCode under `failedBuilds`, or
  * it was published more than --grace-days ago and F-Droid still suggests an older version.

The grace period counts from the GitHub release's `published_at`, not the tag: until the
draft is published its APK 404s and F-Droid cannot fetch the reference binary. One cycle is
roughly a day of building plus up to a day before signing and deploy, and a tag that lands
just after a cycle starts waits for the next one, so less than four days false-alarms.

Release candidates are ignored, as F-Droid ignores them (`UpdateCheckMode: Tags` only
matches vYYYY.MM.N). Version codes are compared as integers, never names.

    scripts/check-fdroid-published.py
    scripts/check-fdroid-published.py --grace-days 5

Set GITHUB_TOKEN to avoid the anonymous API rate limit.
"""

import argparse
import datetime
import json
import os
import re
import sys
import urllib.error
import urllib.request

REPO = "garfiec/Librechat-Mobile"
APP_ID = "com.garfiec.librechat"
TAG_RE = re.compile(r"^v(\d{4})\.(\d{2})\.(\d+)$")

LATEST_RELEASE = "https://api.github.com/repos/%s/releases/latest" % REPO
FDROID_PACKAGE = "https://f-droid.org/api/v1/packages/%s" % APP_ID
FDROID_BUILD_STATUS = "https://f-droid.org/repo/status/build.json"
BUILD_LOG = "https://monitor.f-droid.org/builds/log/%s/%%d" % APP_ID
LISTING = "https://f-droid.org/packages/%s/" % APP_ID


def fetch_json(url, token=None):
    request = urllib.request.Request(url, headers={"User-Agent": "check-fdroid-published"})
    if token:
        request.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def version_code(tag):
    """version.properties packs YYYY.MM.N as YEAR*10000 + MONTH*100 + N."""
    match = TAG_RE.match(tag)
    if not match:
        return None
    year, month, patch = (int(part) for part in match.groups())
    return year * 10000 + month * 100 + patch


def error(title, message):
    # The ::error:: annotation surfaces on the run page; the plain line is for local runs.
    if os.environ.get("GITHUB_ACTIONS"):
        print("::error title=%s::%s" % (title, message))
    else:
        print("%s: %s" % (title, message))


def main():
    parser = argparse.ArgumentParser(
        description=__doc__.splitlines()[0],
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--grace-days", type=float, default=4,
        help="days after publishing before a missing F-Droid version fails (default 4)",
    )
    args = parser.parse_args()

    release = fetch_json(LATEST_RELEASE, os.environ.get("GITHUB_TOKEN"))
    tag = release["tag_name"]
    code = version_code(tag)
    if code is None:
        error("Unexpected tag", "latest release %s does not match vYYYY.MM.N" % tag)
        return 1
    published = datetime.datetime.fromisoformat(release["published_at"].replace("Z", "+00:00"))
    age = datetime.datetime.now(datetime.timezone.utc) - published
    age_days = age.total_seconds() / 86400
    print("Latest release: %s (versionCode %d), published %.1f days ago" % (tag, code, age_days))

    try:
        listed = fetch_json(FDROID_PACKAGE)["suggestedVersionCode"]
    except urllib.error.HTTPError as e:
        if e.code != 404:
            raise
        listed = None
    print("F-Droid suggests: %s" % (listed if listed is not None else "nothing (not listed)"))

    if listed is not None and listed >= code:
        print("F-Droid is current.")
        return 0

    failed = fetch_json(FDROID_BUILD_STATUS).get("failedBuilds", [])
    if [APP_ID, code] in failed:
        error(
            "F-Droid build failed",
            "%s failed in F-Droid's latest build cycle. Log: %s" % (tag, BUILD_LOG % code),
        )
        return 1

    if age_days > args.grace_days:
        error(
            "F-Droid is behind",
            "%s was published %.1f days ago and F-Droid still suggests %s. "
            "Build log (if it was attempted): %s . Listing: %s"
            % (tag, age_days, listed if listed is not None else "nothing", BUILD_LOG % code, LISTING),
        )
        return 1

    print("Within the %g-day grace period; not failing yet." % args.grace_days)
    return 0


if __name__ == "__main__":
    sys.exit(main())
