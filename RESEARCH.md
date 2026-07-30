# QuickWord — research & direction

*2026-07-29. Deep-research pass on rebuilding "Notification Dictionary" ideas in Flutter (QuickDict / QuickThes / QuickWik notes).*

## Verdict: one app, not three

QuickDict, QuickThes, and QuickWik converge naturally into **one app** (working name: **QuickWord** — note "QuickDic" is an [established Android Wiktionary app](https://github.com/rdoeffinger/Dictionary), so avoid "QuickDict" as a product name):

- **Dictionary + thesaurus share one database.** The best English pipeline (kaikki.org Wiktionary extract + Open English WordNet) yields definitions *and* sense-grouped synonyms/antonyms in a single SQLite file. A separate QuickThes app would ship the same DB twice.
- **Thesaurus is a notification action, not an app.** Android caps expanded notifications at 3 actions: `[Thesaurus] [Open] [Favourite]`. Tapping Thesaurus swaps the notification content in place via a background Dart isolate — no app switch. This is exactly the "notification buttons: thes, view word" note.
- **QuickWik is a card, not an app.** Wikipedia's REST summary endpoint is purpose-built for preview cards (title, 2–3 line extract, thumbnail). In-app it's a section on the word page; in the notification flow it's the fallback when the word isn't in the dictionary (proper nouns, places, people) — arguably *better* than the original app, which just says "No meaning found".

## How the reference app works (reference/NotificationDictionary)

- `ProcessTextActivity` registers `ACTION_PROCESS_TEXT` + `ACTION_SEND` (manifest) → appears in every app's text-selection toolbar and share sheet.
- Looks up the word in a prebuilt SQLite DB (Wiktionary via go-wiktionary-parse; ~915k words, 127 MB raw / 51 MB compressed; downloaded per-language from a CDN on first run).
- Posts a high-priority BigTextStyle notification with 3 actions (Share / Read TTS / Favourite), then `finish()`es immediately.
- Known wart (author's own comment): white screen flashes when the app isn't already running — the activity isn't translucent.

## Platform reality

| Platform | Notification-lookup UX | Path |
|---|---|---|
| Android | **Faithful, 100%** | Translucent native Kotlin activity (`Theme.Translucent.NoTitleBar`, `noHistory`, `excludeFromRecents`) does lookup + notification in ~50 lines, no Flutter engine spin-up → also fixes the white-flash wart. Flutter carries the app shell. |
| iOS | Not possible (no PROCESS_TEXT equivalent; can't touch system selection menu) | Swift **Action Extension**: select text → Share → "Define" renders the definition inline in the sheet (Kotoba-style) + "Open in app". Flutter engines can't run in iOS extensions, so this stays native Swift over a shared App Group SQLite. Add an App Intent for Shortcuts/Action Button (iOS 18+). |
| Linux/Win/macOS | Approximation | Tray app: global hotkey → clipboard/selection → notification or popup near cursor. `hotkey_manager` + `tray_manager` + `local_notifier`. (Biyi proves the pattern.) |

Key architectural principle from the feasibility research: **Flutter is the right tool for the app shell on every platform, and the wrong tool for the platform hook on every platform.** Hot paths stay native and tiny; Flutter renders history, favourites, search, and the full word page.

## Data: skip StarDict as the core

- StarDict: format frozen since ~2007, definitions are unstructured blobs (can't extract per-sense synonyms), **zero Dart packages exist**, and the popular dictionary archives are a licensing minefield (unauthorized Oxford/Longman/Collins conversions; the project was removed from SourceForge over it). Keep at most as a later "import your own dictionary" power-user feature.
- Instead, **build-time ETL → one SQLite DB** (the reference app's architecture, with better inputs):
  1. [kaikki.org English JSONL](https://kaikki.org/dictionary/English/index.html) (wiktextract; ~1.38M entries; structured `senses[].glosses`, synonyms, IPA, examples; CC BY-SA 4.0) — definitions.
  2. [Open English WordNet 2024](https://github.com/globalwordnet/english-wordnet) (CC-BY 4.0; 121k synsets, ready-made SQLite) — thesaurus graph (synonyms/antonyms/hyper-/hyponyms).
  3. Python ETL script in CI → lean schema (`words`, `senses`, `synonyms` + FTS5 for prefix/fuzzy search) → ~120–200 MB raw / ~45–70 MB compressed. Merged DB license: CC BY-SA 4.0 with attribution.
- App side: **drift** (≥2.32 bundles sqlite3 with FTS5) — no custom binary parsing anywhere.
- Distribution: tiny app binary + first-run DB download (GitHub Releases; show progress/resume). Optionally bundle a ~25–50 MB "common words" tier as an asset so the app works offline instantly. Play Store base module cap is 200 MB compressed, so bundling the full DB is possible but wasteful.

## Online layer (fallback + QuickWik)

| API | Role | Notes |
|---|---|---|
| Wiktionary REST `…/page/definition/{term}` | Online dictionary fallback | Live, keyless, 200 req/s org-wide cap, needs descriptive User-Agent. "Experimental" label — watch changelog. |
| Wikipedia REST `…/page/summary/{title}` | QuickWik card + proper-noun fallback | Live, keyless, edge-cached; formal schema (Specs/Summary/1.2.0). |
| Datamuse (`rel_syn`, `rel_ant`, `ml`) | Online thesaurus fallback | Free 100k req/day; **key required from Jan 2027** — plan for it. |
| dictionaryapi.dev | Last-resort fallback | One volunteer's project, opaque IP rate limits. Never primary. |
| Wordnik | Skip | 100 calls/hr free tier is unusable. |

## Entry UX (what "optimal UI/UX for entries" means per prior art)

- Headword + IPA on top; senses **grouped by part of speech**.
- **Progressive disclosure**: ~2 definitions per POS with "see more"; etymology collapsed by default (Dictionary.com pattern).
- **Tap-to-cross-reference**: Wiktionary payloads link every defining word — intercept taps, re-query (Aard2/GoldenDict pattern).
- Language sections as tabs/accordions for multilingual later.
- Design benchmark: LookUp (iOS, Webby honoree); Flutter prior art worth reading: [ajinasokan/ditto](https://github.com/ajinasokan/ditto). No polished FOSS Flutter Wiktionary reader exists — that's the gap.

## Suggested MVP order

1. **ETL pipeline** (Python, CI): kaikki + OEWN → `quickword-en.sqlite` + compressed release artifact.
2. **Android app**: Flutter shell (search, word page, history/favourites) + native Kotlin PROCESS_TEXT trampoline → notification with `[Thesaurus] [Open] [Favourite]`.
3. **Wikipedia card** in word page + no-hit fallback.
4. Desktop tray build; iOS Action Extension; StarDict import — all later.

## Full research reports

Agent reports with all sources preserved below.

---

### Report A: data formats & sources

(StarDict structure/licensing, kaikki/wiktextract, WordNet, Moby, distribution math — see sources inline above; key links:
[StarDict spec](https://stardict-4.sourceforge.net/StarDictFileFormat) ·
[StarDict legal history](https://en.wikipedia.org/wiki/StarDict) ·
[wiktextract](https://github.com/tatuylonen/wiktextract) ·
[kaikki raw data](https://kaikki.org/dictionary/rawdata.html) ·
[OEWN SQLite](https://github.com/x-englishwordnet/sqlite) ·
[Moby Thesaurus](https://www.gutenberg.org/ebooks/3202) ·
[go-wiktionary-parse](https://github.com/macdub/go-wiktionary-parse) ·
[drift FTS5](https://drift.simonbinder.eu/sql_api/extensions/) ·
[Play size limits](https://support.google.com/googleplay/android-developer/answer/9859372))

### Report B: platform feasibility

([receive_intent](https://pub.dev/packages/receive_intent) ·
[flutter_local_notifications 22.x](https://pub.dev/packages/flutter_local_notifications) ·
[BigTextStyleInformation](https://pub.dev/documentation/flutter_local_notifications/latest/flutter_local_notifications/BigTextStyleInformation-class.html) ·
[PROCESS_TEXT explainer](https://medium.com/google-developers/custom-text-selection-actions-with-action-process-text-191f792d2999) ·
[Flutter engine in iOS extension — not supported](https://github.com/flutter/flutter/issues/165904) ·
[Apple Action Extensions](https://developer.apple.com/library/ios/documentation/General/Conceptual/ExtensibilityPG/Action.html) ·
[App Intents](https://developer.apple.com/documentation/appintents) ·
[Kotoba](https://github.com/willhains/Kotoba) ·
[hotkey_manager](https://pub.dev/packages/hotkey_manager) · [tray_manager](https://pub.dev/packages/tray_manager) · [local_notifier](https://pub.dev/packages/local_notifier))

### Report C: prior art & online APIs

([QuickDic name collision](https://github.com/rdoeffinger/Dictionary) ·
[SilverDict](https://f-droid.org/en/packages/com.gmail.blandilyte.silverdict/) ·
[Aard2 formatting praise](https://github.com/koreader/koreader/issues/11467) ·
[LookUp design blog](https://medium.com/lookup-design/designing-lookup-for-macos-bf5b8fea1a01) ·
[Wikimedia REST API](https://www.mediawiki.org/wiki/Wikimedia_REST_API) ·
[RESTBase deprecation](https://www.mediawiki.org/wiki/RESTBase/deprecation) ·
[Datamuse API](https://www.datamuse.com/api/) ·
[Wordnik pricing](https://developer.wordnik.com/pricing) ·
[dictionaryapi.dev rate-limit issues](https://github.com/meetDeveloper/freeDictionaryAPI/issues/73) ·
[wikipedia-preview component](https://github.com/wikimedia/wikipedia-preview) ·
[Page Previews spec](https://www.mediawiki.org/wiki/Page_Previews/API_Specification) ·
[Wordsmyth entry anatomy](https://blog.wordsmyth.net/2023/02/comprehensive-dictionary-map-of-an-entry/))
