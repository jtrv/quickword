# QuickWord

Select a word anywhere on Android → its definition appears as a notification.
No app switch, works offline, back to reading in five seconds.

Successor in spirit to [NotificationDictionary](https://github.com/tirkarthi/NotificationDictionary),
rebuilt in Kotlin + Jetpack Compose with modern Wiktionary data.

## How it works

- Select text in any app → **QuickWord** in the selection menu (or Share) →
  a heads-up notification with the definition and `[Thesaurus] [Open] [Save]`
  actions. The Thesaurus button swaps the notification to synonyms in place.
- No dictionary hit? The word is looked up on **Wikipedia** (proper nouns,
  places, people), with attribution.
- The app itself: serif-typeset word pages (Literata), prefix search,
  recents & favourites, pronunciation via TTS.

## Data

- Definitions/synonyms: [kaikki.org](https://kaikki.org) wiktextract of the
  English Wiktionary (**CC BY-SA 4.0**). 1.4M words, built by
  [`etl/build_db.py`](etl/build_db.py), shipped as a
  [GitHub Release](../../releases) the app downloads on first run
  (a small starter dictionary is bundled).
- Wikipedia summaries: Wikimedia REST API, CC BY-SA. Optionally offline: an
  opt-in 38 MB corpus of the top 50,000 article lead paragraphs, built from a
  [Kiwix](https://kiwix.org) mini ZIM by [`etl/build_wiki.py`](etl/build_wiki.py).
  Kiwix has already solved which articles matter and how to get a clean lead out
  of wikitext; libzim is a build-time tool only, so nothing GPL ships.

## Building

```sh
mise install         # JDK, Gradle, Python
mise run verify      # the whole gate: palette/schema parity, ktlint, detekt, lint, tests
mise run build       # debug APK
mise run shots       # render screens to PNGs (Roborazzi)
mise run etl         # rebuild the dictionary DB from a kaikki dump
mise run etl:wiki    # rebuild the offline Wikipedia corpus from a Kiwix ZIM
mise run build:release  # R8-minified release APK
mise run bundle         # Play upload AAB
mise run store-assets   # regenerate listing images in fastlane/metadata
```

Release builds are debug-signed unless an untracked `keystore.properties` sits
at the repo root, so `assembleRelease` works for anyone and only uploads need
the real key. Publishing steps (signing, Play data-safety answers, F-Droid
submission) are in [RELEASING.md](RELEASING.md); privacy in
[PRIVACY.md](PRIVACY.md).

Android SDK: platform 37. See `PLAN.md` for architecture (and its
adversarial-refutation tables), `DESIGN.md`/`PRODUCT.md` for the design
system.

## Support

QuickWord is free and always will be. If it saved you a trip to a browser,
[buy me a coffee](https://ko-fi.com/jtrvs).

## License

Code: [MIT](LICENSE). Bundled fonts: Literata & Inter (OFL, shipped inside the
app at `app/src/main/assets/licenses/` and shown on the app's About screen).
Dictionary data: CC BY-SA 4.0 (Wiktionary contributors).
