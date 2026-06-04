#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# download_fonts.sh
# Run once after cloning the repo to fetch Sora font files
# from Google Fonts and place them in the correct resource dir.
# ─────────────────────────────────────────────────────────────

set -e

FONT_DIR="app/src/main/res/font"
mkdir -p "$FONT_DIR"

BASE="https://github.com/sursly/sora/raw/master/fonts/ttf"

declare -A FONTS=(
  ["sora_regular"]="Sora-Regular.ttf"
  ["sora_medium"]="Sora-Medium.ttf"
  ["sora_semibold"]="Sora-SemiBold.ttf"
  ["sora_bold"]="Sora-Bold.ttf"
)

for KEY in "${!FONTS[@]}"; do
  FILE="${FONTS[$KEY]}"
  OUT="$FONT_DIR/$KEY.ttf"
  if [ ! -f "$OUT" ]; then
    echo "Downloading $FILE → $OUT"
    curl -fsSL "$BASE/$FILE" -o "$OUT" || {
      echo "⚠ Could not download $FILE — using fallback URL"
      curl -fsSL "https://fonts.gstatic.com/s/sora/v12/${FILE}" -o "$OUT" || true
    }
  else
    echo "✓ $OUT already exists"
  fi
done

echo "✅ All Sora fonts ready in $FONT_DIR"
