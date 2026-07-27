#!/usr/bin/env bash
# Create the Google Cloud service account that lets tools/play_publish.py talk
# to the Play Developer API, and drop its key where that script expects it.
#
# Run this ONCE. It is idempotent: re-running reuses whatever already exists and
# only creates what is missing.
#
# ⚠️ PREREQUISITE — this script cannot do it for you:
#   `gcloud auth login` must have completed for an account that can administer
#   the Google Cloud project. Claude cannot type the password, and Google
#   step-ups to a password challenge on Cloud Console for org accounts.
#
# AFTER this script there is exactly ONE console step left, which it prints:
# inviting the service account in Play Console. Google deliberately does not
# expose that over an API — a service account cannot grant itself Play access,
# which is the whole point of the separation.
set -euo pipefail

PROJECT_ID="${PLAY_GCP_PROJECT:-}"
SA_NAME="play-publisher"
SA_DISPLAY="Play release publisher (Claude automation)"
KEY_DIR="/Volumes/MAC PRO /DEV PROJECTS/KEYS/KOTLIN 2026"
KEY_PATH="$KEY_DIR/play-service-account.json"
PACKAGE_NAME="com.ga.airdrop.app"

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }
die() { printf '\nerror: %s\n' "$*" >&2; exit 1; }

command -v gcloud >/dev/null || die "gcloud is not installed"

say "1. Checking gcloud authentication"
if ! gcloud projects list --format='value(projectId)' >/dev/null 2>&1; then
  die "gcloud cannot reach the API — the login has expired or never completed.

  Run this yourself in a terminal (it opens a browser and needs your password,
  which is why Claude cannot run it for you):

      gcloud auth login

  Then re-run this script."
fi
ACCOUNT="$(gcloud config get-value account 2>/dev/null)"
printf '   authenticated as: %s\n' "$ACCOUNT"

say "2. Selecting a project"
if [[ -z "$PROJECT_ID" ]]; then
  printf '   available projects:\n'
  gcloud projects list --format='table(projectId,name)' | sed 's/^/     /'
  die "set PLAY_GCP_PROJECT to the project id you want to use, then re-run.
  (If the Play Console already has a linked project, use THAT one — a second
   project will not have access to the app.)"
fi
gcloud config set project "$PROJECT_ID" >/dev/null
printf '   project: %s\n' "$PROJECT_ID"

say "3. Enabling the Android Publisher API"
if gcloud services list --enabled --filter='config.name:androidpublisher.googleapis.com' \
     --format='value(config.name)' | grep -q androidpublisher; then
  printf '   already enabled\n'
else
  gcloud services enable androidpublisher.googleapis.com
  printf '   enabled\n'
fi

SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"

say "4. Creating the service account"
if gcloud iam service-accounts describe "$SA_EMAIL" >/dev/null 2>&1; then
  printf '   already exists: %s\n' "$SA_EMAIL"
else
  gcloud iam service-accounts create "$SA_NAME" --display-name="$SA_DISPLAY"
  printf '   created: %s\n' "$SA_EMAIL"
fi

say "5. Creating a key"
if [[ -f "$KEY_PATH" ]]; then
  printf '   a key already exists at:\n     %s\n' "$KEY_PATH"
  printf '   NOT creating another. Delete that file first if you want a fresh key.\n'
else
  mkdir -p "$KEY_DIR"
  gcloud iam service-accounts keys create "$KEY_PATH" --iam-account="$SA_EMAIL"
  chmod 600 "$KEY_PATH"
  printf '   written (owner-only): %s\n' "$KEY_PATH"
fi

say "DONE — one manual step remains"
cat <<EOF

  Google does not allow a service account to grant itself Play access, so this
  last step is console-only and intentional:

    1. Open Play Console -> Users and permissions -> Invite new users
    2. Email:  $SA_EMAIL
    3. Under "App permissions", add: $PACKAGE_NAME
    4. Grant at least:
         - Release to testing tracks
         - Release to production, exclude devices, and use Play App Signing
    5. Invite.

  Then verify end-to-end WITHOUT submitting anything:

    ./tools/play_publish.py \\
      --aab app/build/outputs/bundle/prodRelease/app-prod-release.aab \\
      --track internal --dry-run

  A clean dry-run means uploads and submissions are fully automated from then
  on. Managed publishing still gates go-live, so nothing reaches users without
  a human pressing Publish.

EOF
