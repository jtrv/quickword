# Releasing QuickWord

Everything the repo can do for a release is already automated; what is left
needs accounts and a private key, which is why it is a checklist rather than a
script.

## 1. Cut the version

`versionCode` must increase on every upload and never be reused; `versionName`
is what users see. Both live in `app/build.gradle.kts`.

**1.0.0 (`versionCode` 1) is already cut and tagged `v1.0.0`** — it is what goes
to Play and F-Droid first. The steps below are for the release *after* it, so
substitute the new version throughout:

```sh
# bump versionCode/versionName, add fastlane/metadata/android/en-US/changelogs/<versionCode>.txt
mise run verify
git commit -am "release: 1.0.1"
git tag -a v1.0.1 -m "QuickWord 1.0.1" && git push --follow-tags
```

The tag matters beyond bookkeeping: F-Droid's update checker watches tags, so a
release without one is a release F-Droid will never see.

## 2. Upload keystore (once, ever)

```sh
keytool -genkeypair -v -keystore quickword-upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Then write `keystore.properties` next to it (both are gitignored):

```properties
storeFile=quickword-upload.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

**Back the `.jks` up somewhere you will still have in five years.** Play Signing
means Google holds the *app* signing key, so a lost upload key is recoverable
via support — but F-Droid publishes under its own key with no such escape
hatch, and losing a key there means the app can never be updated in place.

Verify the build picked the real key up rather than the debug fallback:

```sh
mise run bundle
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## 3. Google Play

Upload `app/build/outputs/bundle/release/app-release.aab`.

The listing text and images come straight out of `fastlane/metadata/android/en-US/`
(regenerate images with `mise run store-assets` after any palette or UI change):

| Play field | File |
|---|---|
| App name | `title.txt` |
| Short description | `short_description.txt` |
| Full description | `full_description.txt` |
| App icon (512×512) | `images/icon.png` |
| Feature graphic (1024×500) | `images/featureGraphic.png` |
| Phone screenshots (≥2) | `images/phoneScreenshots/*.png` |
| Privacy policy URL | `https://github.com/jtrv/quickword/blob/main/PRIVACY.md` |

**Re-recording the three device screenshots.** `mise run shots` covers the
in-app ones; the shade shots (`app/shots/device_*.png`) are captured by hand and
are easy to get wrong — the first set shipped with Android's "Notification
cooldown is now on" card sitting above the app, and the expanded one had no
QuickWord notification in it at all. On a freshly booted emulator:

```sh
adb shell settings put system notification_cooldown_enabled 0   # or it posts its own card
adb shell settings put global sysui_demo_allowed 1              # clean status bar
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e fully true
adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile hide
```

Swipe away any leftover system notifications first — the emulator posts "Serial
console enabled" on every boot. Then trigger, open the shade and capture in one
scripted run: `LookupNotifier` sets `setTimeoutAfter(30_000)`, so a shot taken
more than 30 s after the lookup catches an empty shade. `petrichor` and `quick`
both live in the bundled starter dictionary, so this needs no 280 MB download.
Drop `-e fully true` and the Wi-Fi icon renders with a "no internet" `!`.

**Data safety form.** No accounts, no ads, no analytics, no crash reporting, and
nothing is persisted off-device — so *Data collected: none* and *Data shared:
none* is the honest answer. One nuance worth deciding deliberately rather than
clicking past: when a word has no dictionary entry, that word is sent to the
Wikimedia API. QuickWord neither stores nor receives it back, which fits
Google's ephemeral-processing carve-out, but if you would rather over-disclose
than argue the point later, declare it under *App activity → Other user-generated
content*, processed ephemerally, not shared. `PRIVACY.md` describes it either way.

Content rating: dictionary/reference, no user interaction, no ads. Note in the
questionnaire that definitions come from an unfiltered general-purpose
dictionary (Wiktionary includes profanity and sexual vocabulary, as every
dictionary does).

## 4. F-Droid

QuickWord ships on both stores, with Play as the primary channel — F-Droid
review takes weeks and should run in parallel rather than gate the launch.

F-Droid builds from source on their own infrastructure and signs with their own
key, so **the F-Droid build and the Play build are not interchangeable** — a
user cannot update from one to the other without uninstalling. That is a
property of publishing to both, not a problem to solve; it only needs saying out
loud if a user ever asks why switching stores wants a reinstall.

1. Open a request at [gitlab.com/fdroid/rfp](https://gitlab.com/fdroid/rfp/-/issues)
   with the repo URL and the tag from step 1. Ask for `Donate: https://ko-fi.com/jtrvs`
   in the metadata — F-Droid renders a donate button from that field, and it is
   easier to include up front than to add later.
2. They will write a build recipe in `fdroiddata`. Nothing in this repo blocks
   it: dependencies are androidx/Compose only, the build needs no proprietary
   SDK, and `fastlane/metadata/` is already in the layout their bot reads.
3. Expect a question about `assets/dictionary/quickword-en.db`. It is a small
   fixture generated from public data by `etl/build_db.py`; the full dictionary
   is downloaded at runtime from a GitHub release, which is fine for F-Droid
   (free content, free host) but should be described in the RFP rather than
   discovered by a reviewer.

## What CI already guarantees

`tool/verify.sh` plus the `bundleRelease` step in `.github/workflows/verify.yml`
mean an unbuildable release, an R8 regression, a palette that drifted from
`DESIGN.md`, or a dictionary schema that drifted from the ETL cannot reach a
tag. The gate is the same script locally and in CI, so "works on my machine"
is not a category of failure here.

## 5. The offline Wikipedia corpus

Built from a Kiwix mini ZIM, not from a Wikimedia dump — Kiwix has already
decided which articles matter and extracted clean lead sections:

```sh
curl -O https://download.kiwix.org/zim/wikipedia/wikipedia_en_top_mini_<date>.zim
mv wikipedia_en_top_mini_*.zim etl/data/wikipedia_en_top_mini.zim
mise run etl:wiki                       # ~5 min → etl/data/quickword-wiki-top.db
gzip -9 -c etl/data/quickword-wiki-top.db > quickword-wiki.db.gz
```

Publish the gzip as `quickword-wiki.db.gz` on a release tagged
`wiki-en-top-v1`, which is the URL `Corpus.WIKIPEDIA` points at. Changing the
tag means shipping an app update, so bump the tag only when the schema changes
— a refreshed corpus can reuse it.

**Keep the source ZIM.** Kiwix has generated `wikipedia_en_top1m_mini` exactly
once and has had English Wikipedia runs fail outright, so the archive you built
from may not exist next year (PLAN.md refutation round 4).
