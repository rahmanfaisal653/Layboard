#!/usr/bin/env bash
# One-time setup: creates the release keystore OUTSIDE the repo and uploads it, with its
# passwords, as GitHub Actions secrets. Run it yourself in a terminal; passwords are typed
# at hidden prompts and never printed or stored anywhere else.
set -euo pipefail

REPO="${1:-rahmanfaisal653/Layboard}"
KEYSTORE_DIR="$HOME/.layboard"
KEYSTORE="$KEYSTORE_DIR/layboard.jks"
ALIAS="layboard"

if [ -e "$KEYSTORE" ]; then
  echo "Keystore already exists at $KEYSTORE. Refusing to overwrite it." >&2
  exit 1
fi

read -r -s -p "Choose a keystore password (min 6 chars): " PASSWORD
echo
read -r -s -p "Repeat it: " PASSWORD_AGAIN
echo
if [ "$PASSWORD" != "$PASSWORD_AGAIN" ] || [ "${#PASSWORD}" -lt 6 ]; then
  echo "Passwords differ or are too short." >&2
  exit 1
fi

mkdir -p "$KEYSTORE_DIR"
chmod 700 "$KEYSTORE_DIR"

export LAYBOARD_PASSWORD="$PASSWORD"
keytool -genkeypair -v \
  -keystore "$KEYSTORE" -storetype PKCS12 \
  -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Layboard, O=rahmanfaisal653" \
  -storepass:env LAYBOARD_PASSWORD -keypass:env LAYBOARD_PASSWORD
chmod 600 "$KEYSTORE"

base64 -w0 "$KEYSTORE" | gh secret set LAYBOARD_KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$PASSWORD" | gh secret set LAYBOARD_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS" | gh secret set LAYBOARD_KEY_ALIAS --repo "$REPO"
printf '%s' "$PASSWORD" | gh secret set LAYBOARD_KEY_PASSWORD --repo "$REPO"

echo
echo "Done. Secrets uploaded to $REPO."
echo "BACK UP $KEYSTORE and remember the password: without them you cannot ship updates that install over this version."
