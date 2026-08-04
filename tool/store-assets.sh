#!/usr/bin/env bash
# Regenerate the store listing images that F-Droid and Play both read from
# fastlane/metadata/. Derived from the same sources as the app itself — the
# adaptive-icon geometry, the brand purple, the bundled fonts, and the
# Roborazzi shots — so the listing cannot drift from what ships.
#
#   ./tool/store-assets.sh   (or: mise run store-assets)
#
# Needs rsvg-convert (librsvg) and magick (ImageMagick 7).
set -euo pipefail
cd "$(dirname "$0")/.."

out=fastlane/metadata/android/en-US/images
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$out/phoneScreenshots"

bg='#802D7C' # @color/ic_launcher_background
# Same two paths as res/drawable/ic_launcher_foreground.xml, same 108dp viewport.
pages='<g transform="translate(27,30)">
  <path fill="#FFFFFF" d="M2,4 C10,0 20,0 26,4 L26,44 C20,40 10,40 2,44 Z"/>
  <path fill="#F3D7F0" d="M28,4 C34,0 44,0 52,4 L52,44 C44,40 34,40 28,44 Z"/>
</g>'

# Play icon: crop to the 72dp the launcher mask actually shows, so the store
# icon matches the on-device one instead of looking zoomed out.
cat >"$tmp/icon.svg" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72">
  <rect x="18" y="18" width="72" height="72" fill="$bg"/>$pages</svg>
EOF
cat >"$tmp/book.svg" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="27 26 54 52">$pages</svg>
EOF

rsvg-convert -w 512 -h 512 "$tmp/icon.svg" -o "$tmp/icon.png"
magick "$tmp/icon.png" -depth 8 PNG32:"$out/icon.png" # Play: 32-bit PNG

rsvg-convert -w 250 -h 242 "$tmp/book.svg" -o "$tmp/book.png"
magick -size 1024x500 "xc:$bg" \
	"$tmp/book.png" -geometry +100+130 -composite \
	-font app/src/main/res/font/literata.ttf -pointsize 88 -fill white \
	-annotate +400+248 'QuickWord' \
	-font app/src/main/res/font/inter.ttf -pointsize 32 -fill '#F3D7F0' \
	-annotate +403+305 'Select a word — get its definition.' \
	-annotate +403+350 'Offline. As a notification.' \
	-depth 8 -alpha off PNG24:"$out/featureGraphic.png" # Play: 24-bit, no alpha

# Screenshots, in listing order: the notification is the pitch, so it leads.
i=1
for shot in device_notification device_notification_expanded search_light \
	word_light device_thesaurus_swap word_dark; do
	cp "app/shots/$shot.png" "$out/phoneScreenshots/$i.png"
	i=$((i + 1))
done

echo "store assets → $out"
