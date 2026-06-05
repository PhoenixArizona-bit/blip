#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# generate_keystore.sh
# Run ONCE locally to create your release signing keystore.
# Then follow the instructions to add secrets to GitHub.
# ─────────────────────────────────────────────────────────────

set -e

KEYSTORE_FILE="blip-release.jks"
KEY_ALIAS="blip"

echo "════════════════════════════════════════"
echo "  Blip Release Keystore Generator"
echo "════════════════════════════════════════"
echo ""
echo "⚠  Keep the generated .jks file SAFE."
echo "   If you lose it you cannot update the app."
echo ""

read -p "Keystore password: " -s KS_PASS; echo
read -p "Key password (can be same): " -s KEY_PASS; echo
read -p "Your name (CN): " CN
read -p "Organisation (O) [Blip]: " ORG
ORG=${ORG:-Blip}
read -p "Country code (C) [PK]: " CC
CC=${CC:-PK}

keytool -genkeypair \
  -keystore "$KEYSTORE_FILE" \
  -alias "$KEY_ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass "$KS_PASS" \
  -keypass "$KEY_PASS" \
  -dname "CN=$CN, O=$ORG, C=$CC"

echo ""
echo "✅  Keystore created: $KEYSTORE_FILE"
echo ""
echo "════════════════════════════════════════"
echo "  Next: add these 4 GitHub Secrets"
echo "  (Settings → Secrets → Actions)"
echo "════════════════════════════════════════"
echo ""
echo "Secret name          Value"
echo "───────────────────  ──────────────────────────────────"
echo "KEYSTORE_BASE64      $(base64 -w 0 $KEYSTORE_FILE)"
echo "KEYSTORE_PASSWORD    $KS_PASS"
echo "KEY_ALIAS            $KEY_ALIAS"
echo "KEY_PASSWORD         $KEY_PASS"
echo ""
echo "⚠  The KEYSTORE_BASE64 value above is your entire keystore."
echo "   Copy it carefully into the GitHub secret."
