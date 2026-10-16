#!/usr/bin/env python3
"""Upload, promote, or inspect AIRDROP releases using the Google Play API.

Reuse PLAY_SERVICE_ACCOUNT_JSON or --key; never put credentials in the repo.
--status reads track configuration. --dry-run plans without uploading a bundle,
changing a track, or committing an edit. Both use a temporary API edit for reads.

Examples:
  ./tools/play_publish.py --status
  ./tools/play_publish.py --aab app-prod-release.aab --track internal
  ./tools/play_publish.py --promote-code 37 --from-track internal --track production --dry-run
  ./tools/play_publish.py --promote-code 37 --from-track internal --track production

A committed edit is not proof of public availability. Google review and the
current managed-publishing setting control when a production release is public.
API track status "completed" describes full rollout configuration, not approval.
"""

from __future__ import annotations

import argparse
import copy
import json
import os
import re
import sys
import zipfile

PACKAGE_NAME = "com.ga.airdrop.app"
DEFAULT_KEY_PATH = "/Volumes/MAC PRO /DEV PROJECTS/KEYS/AIRDROP KOTLIN 2026/play-service-account.json"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"


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


def release_codes(release):
    return [int(code) for code in release.get("versionCodes", [])]


def plan_promotion(tracks, source, target, version_code, rollout=None):
    """Select the exact reviewed artifact and retain its existing release metadata."""
    if source == target:
        raise ValueError("source and target tracks must differ")
    matches = [r for t in tracks if t.get("track") == source
               for r in t.get("releases", []) if version_code in release_codes(r)]
    if len(matches) != 1 or release_codes(matches[0]) != [version_code]:
        raise ValueError("source must contain exactly one release with only the requested code")
    if matches[0].get("status") != "completed":
        raise ValueError("source release must be completed before promotion")
    target_releases = [r for t in tracks if t.get("track") == target
                       for r in t.get("releases", [])]
    if any(c > version_code for r in target_releases for c in release_codes(r)):
        raise ValueError("target contains a newer version; refusing to replace it")
    release = copy.deepcopy(matches[0])
    release["status"] = "completed" if rollout is None else "inProgress"
    release.pop("userFraction", None)
    if rollout is not None:
        release["userFraction"] = rollout
    # Re-running an already submitted promotion must not reset review or metadata.
    if any(release_codes(r) == [version_code] and
           r.get("status") == release["status"] and
           r.get("userFraction") == release.get("userFraction")
           for r in target_releases):
        return None
    return release


def run_edit(service, args, bundle_identity=None, media_factory=None):
    edit_id = service.edits().insert(body={}, packageName=PACKAGE_NAME).execute()["id"]
    committed = False
    try:
        scope = {"packageName": PACKAGE_NAME, "editId": edit_id}
        tracks = service.edits().tracks().list(**scope).execute().get("tracks", [])
        if args.status:
            print(json.dumps({"package": PACKAGE_NAME, "tracks": tracks,
                              "public_availability": "Not established by track configuration"}, indent=2))
            return 0
        if args.promote_code is not None:
            release = plan_promotion(tracks, args.from_track, args.track,
                                     args.promote_code, args.rollout)
            if release is None:
                print("unchanged   : requested rollout already configured; no edit committed")
                print("Public availability still requires separate verification.")
                return 0
        else:
            version_code, version_name = bundle_identity
            bundles = service.edits().bundles().list(**scope).execute().get("bundles", [])
            apks = service.edits().apks().list(**scope).execute().get("apks", [])
            used = [int(x["versionCode"]) for x in bundles + apks]
            used += [c for t in tracks for r in t.get("releases", []) for c in release_codes(r)]
            if used and version_code <= max(used):
                raise ValueError(f"versionCode {version_code} is not above live API artifact maximum {max(used)}")
            release = {"name": args.name or f"{version_code} ({version_name})",
                       "versionCodes": [str(version_code)], "status": "completed"}
            if args.rollout is not None:
                release.update(status="inProgress", userFraction=args.rollout)
            if args.notes_from_track:
                notes = load_notes(service, edit_id, args.notes_from_track)
                if not notes:
                    raise ValueError("requested source track has no release notes")
                release["releaseNotes"] = notes
        print(json.dumps({"target_track": args.track, "release": release, "dry_run": args.dry_run}, indent=2))
        if args.dry_run:
            print("dry-run     : plan only; no upload, track update, or commit")
            return 0
        if args.aab:
            media = media_factory(args.aab, mimetype="application/octet-stream", resumable=True)
            uploaded = service.edits().bundles().upload(**scope, media_body=media).execute()
            if int(uploaded["versionCode"]) != bundle_identity[0]:
                raise ValueError("uploaded version code differs from bundle identity")
        service.edits().tracks().update(**scope, track=args.track,
            body={"track": args.track, "releases": [release]}).execute()
        service.edits().validate(**scope).execute()
        service.edits().commit(**scope, changesNotSentForReview=False).execute()
        committed = True
        print("committed   : release edit accepted; public availability is NOT yet verified")
        print("Google review and the current managed-publishing setting determine publication.")
        return 0
    finally:
        if not committed:
            # Only discard the temporary edit created by this invocation.
            try:
                service.edits().delete(packageName=PACKAGE_NAME, editId=edit_id).execute()
            except Exception:
                print("warning: temporary edit cleanup failed; it will expire", file=sys.stderr)


def parse_args(argv=None):
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    mode = p.add_mutually_exclusive_group(required=True)
    mode.add_argument("--aab", help="new bundle to upload")
    mode.add_argument("--promote-code", type=int, help="existing version code to promote without uploading again")
    mode.add_argument("--status", action="store_true", help="read track configuration without changing releases")
    p.add_argument("--track", help="explicit destination track (including existing custom tracks)")
    p.add_argument("--from-track", default="internal", help="source track for promotion")
    p.add_argument("--name", help="name for a new uploaded release")
    p.add_argument("--notes-from-track", help="copy notes for a new uploaded release")
    p.add_argument("--rollout", type=float, help="fraction strictly between 0 and 1; omit for full rollout")
    p.add_argument("--key", default=os.environ.get("PLAY_SERVICE_ACCOUNT_JSON", DEFAULT_KEY_PATH))
    p.add_argument("--dry-run", action="store_true", help="plan only; no upload, track update, or commit")
    args = p.parse_args(argv)
    if not args.status and not args.track:
        p.error("--track is required for upload or promotion")
    if args.rollout is not None and not 0 < args.rollout < 1:
        p.error("--rollout must be strictly between 0 and 1")
    if args.rollout is not None and args.track == "internal":
        p.error("internal track does not support staged rollouts")
    if args.promote_code is not None and not 0 < args.promote_code <= 2_100_000_000:
        p.error("--promote-code must be a valid positive Play version code")
    if args.promote_code is not None and (args.name or args.notes_from_track):
        p.error("promotion preserves the selected source release name and notes")
    if args.status and (args.track or args.rollout is not None or args.name or args.notes_from_track):
        p.error("--status cannot be combined with release mutation options")
    return args


def main():
    args = parse_args()
    identity = read_bundle_identity(args.aab) if args.aab else None
    if identity and not 0 < identity[0] <= 2_100_000_000:
        die("bundle versionCode is outside Play's supported range")
    if not os.path.isfile(args.key):
        die("existing Play service-account credential unavailable; use --key or PLAY_SERVICE_ACCOUNT_JSON")
    from google.oauth2 import service_account
    from googleapiclient.discovery import build
    from googleapiclient.http import MediaFileUpload
    from googleapiclient.errors import HttpError
    creds = service_account.Credentials.from_service_account_file(args.key, scopes=[SCOPE])
    service = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)
    try:
        return run_edit(service, args, identity, MediaFileUpload)
    except ValueError as exc:
        die(str(exc))
    except HttpError as exc:
        # Avoid dumping response bodies containing operational or account details.
        die(f"Play API returned HTTP {exc.resp.status}; no public availability claim can be made")


if __name__ == "__main__":
    raise SystemExit(main())
