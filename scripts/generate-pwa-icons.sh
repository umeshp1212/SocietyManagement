#!/usr/bin/env bash
# =============================================================
# Generate the 8 PWA icon PNGs for the member portal from a single
# source image (SVG or a large square PNG).
#
# Usage:
#   scripts/generate-pwa-icons.sh [SOURCE]
#
#   SOURCE defaults to:
#     frontend-new/projects/member-portal/src/assets/icons/icon-source.svg
#   You can pass your own logo (ideally a 512x512+ square PNG or an SVG):
#     scripts/generate-pwa-icons.sh /path/to/my-logo.png
#
# Requires ONE of: ImageMagick (`convert`/`magick`), rsvg-convert, or inkscape.
#   Ubuntu/Debian:  sudo apt install -y imagemagick
#   (for crisp SVG rendering, also:  sudo apt install -y librsvg2-bin)
# =============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ICON_DIR="${SCRIPT_DIR}/../frontend-new/projects/member-portal/src/assets/icons"
SOURCE="${1:-${ICON_DIR}/icon-source.svg}"

SIZES=(72 96 128 144 152 192 384 512)

if [ ! -f "$SOURCE" ]; then
  echo "ERROR: source image not found: $SOURCE" >&2
  exit 1
fi

mkdir -p "$ICON_DIR"
echo "Source : $SOURCE"
echo "Output : $ICON_DIR"

# Pick an available renderer.
render() {
  local size="$1" out="$2"
  if command -v rsvg-convert >/dev/null 2>&1 && [[ "$SOURCE" == *.svg ]]; then
    rsvg-convert -w "$size" -h "$size" "$SOURCE" -o "$out"
  elif command -v magick >/dev/null 2>&1; then
    magick -background none "$SOURCE" -resize "${size}x${size}" "$out"
  elif command -v convert >/dev/null 2>&1; then
    convert -background none "$SOURCE" -resize "${size}x${size}" "$out"
  elif command -v inkscape >/dev/null 2>&1; then
    inkscape "$SOURCE" --export-type=png --export-filename="$out" \
      --export-width="$size" --export-height="$size" >/dev/null 2>&1
  else
    echo "ERROR: no image tool found. Install imagemagick (and optionally librsvg2-bin)." >&2
    exit 1
  fi
}

for s in "${SIZES[@]}"; do
  out="${ICON_DIR}/icon-${s}x${s}.png"
  render "$s" "$out"
  echo "  created icon-${s}x${s}.png"
done

echo "Done. All 8 icons generated."
echo "Next: rebuild the frontend so the icons ship in the member PWA:"
echo "  docker compose build frontend && docker compose up -d frontend"
