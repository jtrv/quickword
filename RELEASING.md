# Releasing QuickWord

Everything the repo can do for a release is already automated; what is left
needs accounts and a private key, which is why it is a checklist rather than a
script.

## 1. Cut the version

`versionCode` must increase on every upload and never be reused; `versionName`
is what users see. Both live in `app/build.gradle.kts`.

**1.0.1 (`versionCode` 2) is the current release, tagged `v1.0.1`.** The steps
below are for the release *after* it, so substitute the new version throughout:

```sh
# bump versionCode/versionName, add fastlane/metadata/android/en-US/changelogs/<versionCode>.txt
mise run verify
git commit -am "release: 1.0.2"
git tag -a v1.0.2 -m "QuickWord 1.0.2" && git push --follow-tags
mise run release:apk v1.0.2          # → dist/quickword-1.0.2.apk, verified reproducible
gh release create v1.0.2 dist/quickword-1.0.2.apk --title "QuickWord 1.0.2" \
  --notes-file fastlane/metadata/android/en-US/changelogs/<versionCode>.txt
```

The tag matters beyond bookkeeping: F-Droid's update checker watches tags, so a
release without one is a release F-Droid will never see. The APK matters just as
much: F-Droid downloads `quickword-<version>.apk` from the GitHub release and
publishes it only if its own build of the tagged commit is byte-identical, so
always build it with `mise run release:apk` (clean checkout of the tag, never
the working tree) and attach it right after pushing the tag. 1.0.0 was built
from a later commit than its tag and can never be published by F-Droid, which is
why 1.0.1 exists.

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
via support. The GitHub and F-Droid builds, though, are signed with this key
directly (F-Droid reproduces the build and republishes the signed APK; its
recipe pins the certificate), and losing it means neither can ever update in
place again.

Verify the build picked the real key up rather than the debug fallback:

```sh
mise run bundle
keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab
```

The Owner must be `CN=QuickWord`, not `CN=Android Debug`. Check the AAB, not
`app/build/outputs/apk/release/app-release.apk`: `mise run bundle` does not
rebuild the APK, so a stale debug-signed one can sit there and mislead.

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
are easy to get wrong. The first set shipped with Android's "Notification
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

Swipe away any leftover system notifications first; the emulator posts "Serial
console enabled" on every boot. Then trigger, open the shade and capture in one
scripted run: `LookupNotifier` sets `setTimeoutAfter(30_000)`, so a shot taken
more than 30 s after the lookup catches an empty shade. `petrichor` and `quick`
both live in the bundled starter dictionary, so this needs no 280 MB download.
Drop `-e fully true` and the Wi-Fi icon renders with a "no internet" `!`.

**Data safety form.** No accounts, no ads, no analytics, no crash reporting, and
nothing is persisted off-device, so *Data collected: none* and *Data shared:
none* is the honest answer. One nuance worth deciding deliberately rather than
clicking past: when a word has no dictionary entry, that word is sent to the
Wikimedia API. QuickWord sends it once, keeps no copy, and gets none back,
which fits Google's ephemeral-processing carve-out. If you would rather
over-disclose than argue the point later, declare it under *App activity →
Other user-generated content* with the *processed ephemerally* flag set and
sharing declared as none. `PRIVACY.md` describes it either way.

Content rating: dictionary/reference, no user interaction, no ads. Note in the
questionnaire that definitions come from an unfiltered general-purpose
dictionary (Wiktionary includes profanity and sexual vocabulary, as every
dictionary does).

## 4. F-Droid

QuickWord ships on both stores, with Play as the primary channel. F-Droid
publishes the **same signed APK as the GitHub release**, after rebuilding the
tagged commit on its own infrastructure and checking the result is
byte-identical ([reproducible builds](https://f-droid.org/docs/Reproducible_Builds/)).
So F-Droid and GitHub installs update across each other; only Play, which
re-signs with Google's key, needs a reinstall to switch.

The recipe lives in [fdroiddata `metadata/io.github.jtrv.quickword.yml`](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/io.github.jtrv.quickword.yml)
(first submitted as [RFP #4275](https://gitlab.com/fdroid/rfp/-/issues/4275)
and [MR !46415](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/46415)).
`AutoUpdateMode: Version` means every `vX.Y.Z` tag gets picked up by their bot
without a manual MR; `Binaries:` points at
`github.com/jtrv/quickword/releases/download/v%v/quickword-%v.apk`, and
`AllowedAPKSigningKeys` pins the `CN=QuickWord` certificate. Nothing in this
repo needs touching per release beyond step 1.

Things that would break it:

- Building the release APK from anything other than a clean checkout of the
  tagged commit (`mise run release:apk` exists so this cannot happen by
  accident). Even `META-INF/version-control-info.textproto` embeds the commit.
- Renaming the release asset, or changing the signing key.
- Anything that makes the build nondeterministic — verify with
  `mise run release:apk`, which compares a signed and an unsigned build of the
  tag with `apksigcopier` the same way `fdroid publish` will.
- `app/src/main/assets/dictionary/quickword-en.db` is a small fixture generated
  from public data by `etl/build_db.py`; the full dictionary is downloaded at
  runtime from a GitHub release (free content, free host). Keep it that way or
  expect a question from a reviewer.

## What CI already guarantees

`tool/verify.sh` plus the `bundleRelease` step in `.github/workflows/verify.yml`
mean an unbuildable release, an R8 regression, a palette that drifted from
`DESIGN.md`, or a dictionary schema that drifted from the ETL cannot reach a
tag. The gate is the same script locally and in CI, so "works on my machine"
is not a category of failure here.

## 5. The offline Wikipedia corpus

Built from a Kiwix mini ZIM, not from a Wikimedia dump: Kiwix has already
decided which articles matter and extracted clean lead sections.

```sh
curl -O https://download.kiwix.org/zim/wikipedia/wikipedia_en_top_mini_<date>.zim
mv wikipedia_en_top_mini_*.zim etl/data/wikipedia_en_top_mini.zim
mise run etl:wiki                       # ~5 min → etl/data/quickword-wiki-top.db
gzip -9 -c etl/data/quickword-wiki-top.db > quickword-wiki.db.gz
```

Publish the gzip as `quickword-wiki.db.gz` on a release tagged
`wiki-en-top-v1`, which is the URL `Corpus.WIKIPEDIA` points at. Changing the
tag means shipping an app update, so bump the tag only when the schema
changes; a refreshed corpus can reuse it.

**Keep the source ZIM.** Kiwix has generated `wikipedia_en_top1m_mini` exactly
once and has had English Wikipedia runs fail outright, so the archive you built
from may not exist next year (PLAN.md refutation round 4).
