#!/usr/bin/env python3
"""Upload an .aab to Google Play and submit it for review, without a browser.

Why this exists
---------------
The browser automation used for releases before this script could not upload the
bundle: the file-upload bridge only accepts user-shared paths and caps at 10 MB,
while our bundle is ~37 MB. Computer-use cannot substitute because browsers are
granted read-only. So every release stalled on a human drag-and-drop. This talks
to the Android Publisher API directly and removes that step.

Setup (once)
------------
  1. A Google Cloud service account with a JSON key.
  2. Play Console -> Users and permissions -> Invite the service account's email,
     granting at least "Release to production, exclude devices, and use Play App
     Signing" on this app.
  3. Point PLAY_SERVICE_ACCOUNT_JSON at the key file (see --help).

⚠️ The key file is a credential. Keep it out of the repo; .gitignore covers
tools/*.json and the default path lives under KEYS/.

Usage
-----
  ./tools/play_publish.py --aab path/to/app-prod-release.aab --track internal
  ./tools/play_publish.py --aab ... --track production --notes-from-track internal
  ./tools/play_publish.py --aab ... --track production --dry-run

Managed publishing note
-----------------------
If managed publishing is ON (it is, for com.ga.airdrop.app), committing an edit
sends the release to REVIEW. It does NOT go live: after review it parks in
"Changes ready to publish" until a human presses Publish. This script never
publishes; it only submits.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
import zipfile

PACKAGE_NAME = "com.ga.airdrop.app"
DEFAULT_KEY_PATH = "/Volumes/MAC PRO /DEV PROJECTS/KEYS/KOTLIN 2026/play-service-account.json"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"

# Play rejects a versionCode at or below one already live. Mirrors
# `knownPlayProductionVersionCodeFloor` in app/build.gradle.kts — keep in step.
# ⚠️ These two DRIFTED: both sat at 21 while 25 was live, so 22-25 passed both
# local guards and would have failed only at Google. Bump BOTH every release.
VERSION_CODE_FLOOR = 25


def die(msg: str) -> "None":
    print(f"error: {msg}", file=sys.stderr)
    raise SystemExit(1)


def read_bundle_identity(aab_path: str) -> "tuple[int, str]":
    """Pull versionCode/versionName out of the bundle's proto manifest.

    Verifying what is actually IN the artifact — rather than trusting the
    filename or the gradle property that was passed — is the point. A bundle
    built with the wrong -PplayVersionCode looks identical from the outside.
    """
    if not os.path.isfile(aab_path):
        die(f"no such bundle: {aab_path}")
    try:
        with zipfile.ZipFile(aab_path) as z:
            blob = z.read("base/manifest/AndroidManifest.xml")
    except (KeyError, zipfile.BadZipFile) as exc:
        die(f"{aab_path} is not a readable .aab ({exc})")

    text = blob.decode("latin-1")
    # aapt2 proto stores the attribute name, then a length-prefixed decimal
    # string of the value. Anchor on the attribute name so we cannot pick up a
    # stray integer from elsewhere in the manifest.
    def attr_after(name: str) -> "str | None":
        idx = text.find(name)
        if idx < 0:
            return None
        window = text[idx + len(name): idx + len(name) + 40]
        m = re.search(r"[\x01-\x20]([0-9][0-9.]*)", window)
        return m.group(1) if m else None

    code_s = attr_after("versionCode")
    name_s = attr_after("versionName")
    if not code_s:
        die("could not read versionCode from the bundle manifest")
    return int(code_s), (name_s or "?")


def load_notes(service, edit_id: str, from_track: str) -> "list | None":
    """Reuse the release notes already approved on another track."""
    tracks = service.edits().tracks().list(
        packageName=PACKAGE_NAME, editId=edit_id).execute()
    for track in tracks.get("tracks", []):
        if track.get("track") != from_track:
            continue
        releases = track.get("releases") or []
        if not releases:
            continue
        # Newest release on that track wins.
        newest = max(releases, key=lambda r: max(
            (int(c) for c in r.get("versionCodes", [0])), default=0))
        notes = newest.get("releaseNotes")
        if notes:
            return notes
    return None


def main() -> int:
    p = argparse.ArgumentParser(
        description="Upload an .aab to Play and submit it for review.")
    p.add_argument("--aab", required=True, help="path to the .aab")
    p.add_argument("--track", default="internal",
                   help="internal | alpha | beta | production (default: internal)")
    p.add_argument("--name", help="release name shown in the console")
    p.add_argument("--notes-from-track",
                   help="copy release notes from this track's newest release")
    p.add_argument("--rollout", type=float,
                   help="staged rollout fraction 0<f<1; omit for full rollout")
    p.add_argument("--key", default=os.environ.get(
        "PLAY_SERVICE_ACCOUNT_JSON", DEFAULT_KEY_PATH),
        help="service-account JSON (or set PLAY_SERVICE_ACCOUNT_JSON)")
    p.add_argument("--dry-run", action="store_true",
                   help="do everything except commit; the edit is discarded")
    args = p.parse_args()

    version_code, version_name = read_bundle_identity(args.aab)
    size_mb = os.path.getsize(args.aab) / 1048576
    print(f"bundle      : {args.aab}")
    print(f"             versionCode={version_code} versionName={version_name} "
          f"({size_mb:.1f} MB)")

    if version_code <= VERSION_CODE_FLOOR:
        die(f"versionCode {version_code} is at or below the known-live floor "
            f"{VERSION_CODE_FLOOR}; Play would reject it")

    if not os.path.isfile(args.key):
        die(f"no service-account key at {args.key}\n"
            "  Create one in Google Cloud, then invite its email in Play Console\n"
            "  under Users and permissions with release access to this app.")

    from google.oauth2 import service_account          # noqa: E402
    from googleapiclient.discovery import build        # noqa: E402
    from googleapiclient.errors import HttpError       # noqa: E402
    from googleapiclient.http import MediaFileUpload   # noqa: E402

    creds = service_account.Credentials.from_service_account_file(
        args.key, scopes=[SCOPE])
    service = build("androidpublisher", "v3",
                    credentials=creds, cache_discovery=False)

    try:
        edit = service.edits().insert(body={}, packageName=PACKAGE_NAME).execute()
    except HttpError as exc:
        die("could not open an edit — the service account most likely has no "
            f"access to {PACKAGE_NAME} in Play Console.\n  {exc}")
    edit_id = edit["id"]
    print(f"edit        : {edit_id}")

    committed = False
    try:
        print("uploading   : ...", flush=True)
        media = MediaFileUpload(
            args.aab, mimetype="application/octet-stream", resumable=True)
        uploaded = service.edits().bundles().upload(
            packageName=PACKAGE_NAME, editId=edit_id,
            media_body=media).execute()
        uploaded_code = int(uploaded["versionCode"])
        print(f"uploaded    : versionCode={uploaded_code}")
        if uploaded_code != version_code:
            die(f"Play reports versionCode {uploaded_code} but the file says "
                f"{version_code} — refusing to continue")

        release = {
            "name": args.name or f"{version_code} ({version_name})",
            "versionCodes": [str(version_code)],
            "status": "completed",
        }
        if args.rollout is not None:
            if not 0 < args.rollout < 1:
                die("--rollout must be strictly between 0 and 1")
            release["status"] = "inProgress"
            release["userFraction"] = args.rollout

        if args.notes_from_track:
            notes = load_notes(service, edit_id, args.notes_from_track)
            if notes:
                release["releaseNotes"] = notes
                print(f"notes       : copied from '{args.notes_from_track}'")
            else:
                # Loud, because shipping a release with no "what's new" is a
                # silent downgrade of the listing, not a neutral default.
                print(f"notes       : WARNING none found on "
                      f"'{args.notes_from_track}' — release will have none")

        service.edits().tracks().update(
            packageName=PACKAGE_NAME, editId=edit_id, track=args.track,
            body={"track": args.track, "releases": [release]}).execute()
        print(f"track       : {args.track} "
              f"({'staged ' + str(args.rollout) if args.rollout else 'full rollout'})")

        if args.dry_run:
            print("dry-run     : discarding the edit, nothing was submitted")
            service.edits().delete(
                packageName=PACKAGE_NAME, editId=edit_id).execute()
            return 0

        service.edits().commit(
            packageName=PACKAGE_NAME, editId=edit_id).execute()
        committed = True
        print("committed   : SUBMITTED FOR REVIEW")
        print()
        print("Managed publishing is ON, so this does NOT go live. After Google's")
        print("review it waits in 'Changes ready to publish' for a human.")
        return 0
    except HttpError as exc:
        die(f"Play API rejected the request:\n  {exc}")
    finally:
        if not committed and not args.dry_run:
            # Leaving an edit open blocks later edits with a confusing error.
            try:
                service.edits().delete(
                    packageName=PACKAGE_NAME, editId=edit_id).execute()
            except Exception:
                pass


if __name__ == "__main__":
    raise SystemExit(main())
