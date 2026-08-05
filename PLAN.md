# QuickWord — implementation plan

*2026-07-29. Pure Kotlin + Jetpack Compose, Android-only. Supersedes the
Flutter-shell framing in RESEARCH.md (decision refuted & confirmed — see
refutation tables). Design system: PRODUCT.md + DESIGN.md.*

## Architecture

Single Gradle module `:app` (no premature multi-module), Kotlin 2.x, AGP 8.x,
Compose BOM + Material 3, minSdk 26, targetSdk latest.

```
app/src/main/kotlin/dev/quickword/
  lookup/      ProcessTextActivity (translucent trampoline: PROCESS_TEXT + SEND)
               LookupNotifier (BigTextStyle + [Thesaurus][Open][★] actions)
               ThesaurusActionReceiver (swaps notification content in place)
  data/        QuickwordDb (Room, prebuilt DB via createFromFile)
               words/senses/synonyms DAOs; HistoryDao (app-writable DB)
               DbDownloader (first-run download + unzip + verify from GitHub Releases)
               WikipediaApi (REST summary; no-hit fallback + word-page card)
  ui/          theme/ (Color.kt, Type.kt from DESIGN.md)
               search/ (home: search field + history)
               word/ (entry screen: POS groups, senses, synonym chips, wiki card)
               settings/ (theme override, channel-health banner, about/licenses)
```

Key mechanics (from reference app + refutations):

- **Trampoline**: exported activity, `Theme.Translucent.NoTitleBar`,
  `noHistory`, `excludeFromRecents`, `taskAffinity=""` → query DB → post
  notification → `finish()`. No Compose, no setContent — nothing draws.
- **Lookup query**: exact + prefix on `words.word COLLATE NOCASE` (indexed).
  **No FTS in MVP** (refuted — see table). Normalization: lowercase, strip
  punctuation, fall back to stemmed retry (drop -s/-ed/-ing) before "no hit".
- **Notification channel health** (refuted claim → requirement): on app open,
  check `NotificationManager.getNotificationChannel("lookup").importance`;
  if < HIGH show a banner deep-linking to channel settings. Trampoline with
  notifications blocked falls back to launching the word page directly.
- **Two DBs**: read-only dictionary DB (downloaded, replaceable on language
  change) + tiny app DB (history/favourites) — avoids migrating 60MB of
  dictionary on every app-schema change (reference app's single-DB migration
  pain).
- *M1 deviation:* dictionary DB is accessed via plain `android.database.sqlite`
  (3 queries), not Room — Room's prebuilt-DB schema validation fights
  hand-built SQLite for no benefit on a read-only store. Room reconsidered at
  M5 for the app-owned history DB only. Dev builds bundle a 15-word fixture DB
  as an asset; the downloader ships at M6 with the release artifact.
- **Thesaurus action**: `showsUserInterface=false` broadcast → re-`notify()`
  same ID with synonym content. No app launch.

## Data pipeline (`etl/`, Python, runs in CI not on device)

kaikki.org English JSONL + OEWN 2024 SQLite → filter → lean SQLite
(`words`, `senses`, `synonyms`, indexes, VACUUM) → zstd → GitHub Release
asset. **Measured 2026-07-29 (v1 filter):** 1,446,437 words / 1,724,400
senses / 631,509 synonyms, 280 MB raw — above the researched 120–200 MB
estimate; tightening the filter (drop rare/hyphenless-variant entries) is an
M6 packaging concern. OEWN synonym union deferred to M4. License of output:
CC BY-SA 4.0 + attribution files.

## Milestones

| # | Deliverable | Gate |
|---|---|---|
| M0 ✅ | Gradle scaffold, ktlint+detekt+lint wired to `check`, `tool/verify.sh` green; CI live (GitHub Actions runs the same gate, first run green 2026-07-31) | mutation-tested 2026-07-29: test/ktlint/detekt/lint each redden `check` |
| M1 ✅ | ETL v1 (1.45M words / 280 MB measured, assertions pinned); search UI + word page over fixture DB; Roborazzi rig (4 shots reviewed) | ETL row-count/size assertions ✅ |
| M2 ✅ | Trampoline + notification path; full-phrase-first lookup (user policy); channel-health banner; 3 Robolectric contract tests | verified on Android 16 emulator 2026-07-29: PROCESS_TEXT → heads-up notification w/ [Open][Share], screenshots in app/shots/ |
| M3 ✅ | Literata+Inter typography (bundled OFL variable fonts), word page polish | shots re-recorded + reviewed both themes |
| M4 ✅ | Thesaurus notification action (in-place swap, no app launch) + expandable synonym chips | 3 notifier contract tests; button verified on emulator (app/shots/device_thesaurus_swap.png) |
| M5 ✅ | Wikipedia no-hit fallback (notification + in-app page, live-verified with "Nairobi"); history/favourites (recents on empty search, ★ page toggle, ★ Save notification action — tap-verified via uiautomator) | notifier/history/wiki-parse contract tests |
| M6 ✅* | TTS (word page); affix filter (proper nouns kept per PRODUCT principle 4 — measured: names are only ~14 MB of gloss); DB release `db-en-v1` (267 MB raw / 122 MB gz) + first-run downloader (5 contract tests, verified swap-in); README + fastlane metadata | *deferred: settings screen (no toggle worth a screen yet) |
| M7 ✅* | Store readiness. Release build type (R8 + resource shrinking, no keep rules needed — 2.2 MB APK / 4.0 MB AAB), optional keystore signing via untracked `keystore.properties`, `bundleRelease` in CI; release APK smoke-tested on emulator (PROCESS_TEXT notification, search, word page — fonts survive resource obfuscation). About screen carrying CC BY-SA attribution and the verbatim OFL texts as APK assets. `PRIVACY.md` (Play requires a policy URL), `RELEASING.md`, listing images generated from app sources by `tool/store-assets.sh` | licence compliance contract-tested (`AboutScreenTest`: entry point reachable + OFL text present in-APK); `mise run verify` green; OFL/CC assets confirmed present in the R8 release APK. *remaining is account-only: upload keystore, Play listing, F-Droid RFP |

| M8 ✅ | Pre-launch functionality audit: no-hit dead end fixed (app was quieter than its own notification), TTS pinned to `Locale.ENGLISH`, TTS engine released when init outlives the screen, storage & data controls (remove the ~120 MB download, clear recents/favourites behind a confirm) | `QuickWordAppTest` mutation-tested — reinstating the fall-through to search reddens it; `mise run verify` green |

| M9 ✅ | Dictionary acquisition handed to `DownloadManager`: resumes a dropped connection instead of restarting ~120 MB, refuses metered networks unless the user says otherwise (confirm dialog / "Use mobile data"), survives leaving the app, and shows a system progress notification. Gunzip + verify + atomic swap stay ours and stay tested | 6 contract tests on the install path; verified end to end on emulator 2026-08-05: download → 122 MB archive → 280 MB DB swapped in live, banner cleared, archive reclaimed; Remove download returns to starter |

## Known gaps at launch (deliberate, not forgotten)

- **Launcher icon is still the M0 placeholder** (`ic_launcher_foreground.xml`
  says so in its own comment). It reads fine at every size and the store assets
  derive from it, so this is a branding decision, not a blocker.
- **English only.** The dictionary, the TTS locale and the Wikipedia endpoint
  are all `en`. Multi-language means a per-language DB release, a language-aware
  TTS locale and Wikipedia host, and a picker. The picker does *not* imply a
  settings screen: the dictionary is the language, so it belongs in About's
  Storage & data section, where downloading and removing already live.
- **No definition full-text search** — refuted at plan time (AOSP SQLite has no
  FTS5); would need `androidx.sqlite:sqlite-bundled`.
- **No settings screen.** Theme follows the system, notifications are managed by
  the platform's own channel UI, and storage/data now live on the About screen —
  there is still no toggle that earns a screen of its own.

## Geiger audit (2026-07-29, direct-read mode — repo < 50 files)

| Finding | Class | Evidence | Disposition |
|---|---|---|---|
| ETL schema ↔ DictionaryRepository SQL (cross-language hidden contract) | **erosion** (managed-manual), refuter UNREFUTED | schema lives in build_db.py, queries in Kotlin; nothing forced fixture regeneration | **Fixed**: verify.sh rebuilds fixture from sample.jsonl and dump-diffs vs asset (mutation-tested) |
| DESIGN.md ↔ Color.kt ↔ palette.py three-way mirror | **erosion** (managed-manual), refuter UNREFUTED | sync documented in CLAUDE.md, unenforced | **Fixed**: `palette.py --check` compares Color.kt literals, in verify.sh (mutation-tested) |
| EXTRA_WORD constant tri-duplicated | minor | 3 self-consistent copies of one string | **Fixed**: single `lookup/Extras.kt` constant |
| root ↔ lookup package cycle (LookupNotifier/trampoline name MainActivity; MainActivity uses LookupChannel) | intentional | Android idiom — notification intents must name the target activity | Keep; revisit only if the app package grows |
| `data` package fan-in from all packages | intentional | it is the data layer; fan-in is its job | — |
| Tests ↔ sample.jsonl word coupling | intentional | fixture exists for exactly this | — |

## Refutation table (plan-refute protocol, codex-cli 0.144.6 cross-model)

### Round 1 — Kotlin vs Flutter (2026-07-29)

| Claim | Verdict | Disposition |
|---|---|---|
| Flutter shell adds ~8–20 MB APK vs Compose | **REFUTED** — official figures: 4.8 MB minimal Flutter vs 2.97 MB Compose ≈ 1.8 MB delta | Size dropped as an argument; decision rests on single-language/toolchain + native hot path |
| Compose Multiplatform is a viable later desktop path | UNREFUTED | Multi-platform door stays open without Flutter |

### Round 2 — architecture (2026-07-29)

| Claim | Verdict | Disposition |
|---|---|---|
| Framework SQLite has FTS5 on API ≥24 | **REFUTED** — AOSP builds enable only FTS3/4; Room docs point FTS5 at the bundled driver | MVP uses indexed prefix lookup, no FTS. Definition full-text search deferred; if built, use `androidx.sqlite:sqlite-bundled` |
| POST_NOTIFICATIONS granted ⇒ heads-up lookup notification works (API 31–36) | **REFUTED** — channel importance is user-controlled, app cannot restore it | Channel-health check + settings deep-link banner is a requirement, not polish; blocked-notification fallback opens word page |
| Roborazzi + RNG renders Compose M3 to PNG headlessly on Linux JVM, maintained, rig-grade | UNREFUTED | Visual rig as planned (kotlin-verify-loop) |
| mise provisions JDK+Gradle; Android SDK must be separate | **REFUTED (favorably)** — mise registry maps `android-sdk` (vfox plugin); `mise use android-sdk@latest` is documented | mise.toml may pin the SDK too; this machine already has platforms 34–36, so local config uses system SDK, CI may use mise |
| kaikki+OEWN ETL ≈120–200 MB raw / 45–70 MB compressed, ~1M senses | UNREFUTED | Stands; pin real numbers in ETL assertions at M1 |

Protocol notes: refuters ran `codex exec --sandbox read-only` with context
asymmetry (claim only, never planner reasoning). Unrefuted ≠ proven —
agreement is not evidence; numbers get re-pinned by runnable assertions as
soon as the thing exists.

## M0 notes (learned during scaffold, 2026-07-29)

- **AGP 9 has built-in Kotlin**: `org.jetbrains.kotlin.android` must NOT be
  applied (hard error). Bundled KGP is 2.2.10 — the
  `org.jetbrains.kotlin.plugin.compose` version in libs.versions.toml is
  pinned to 2.2.10 to match; upgrade both together (buildscript-classpath
  route per AGP release notes) or not at all.
- Toolchain: Gradle 9.6.1 wrapper (AGP 9.3.1 requires ≥9.5), Temurin 21 via
  mise, compileSdk/targetSdk 37 (SDK package now versioned `android-37.0` —
  minor-version scheme).
- ktlint (official style) and detekt disagree on max line length (140 vs
  120); aligned at 120 via `.editorconfig` `max_line_length`.
- Lint's version-currency checks (`AndroidGradlePluginVersion`,
  `NewerVersionAvailable`, `GradleDependency`) are disabled: as
  warnings-as-errors they redden the gate whenever upstream releases,
  which checks the calendar, not the code. Version bumps are a bot's job.
- Composable naming: exempted in both tools (`.editorconfig` ktlint key +
  `config/detekt/detekt.yml` `ignoreAnnotated: [Composable]`).

## Open questions

- ~~App id~~ — resolved 2026-07-29: `io.github.jtrv.quickword` (user choice,
  F-Droid-friendly GitHub pattern).
- ~~Font licensing~~ — resolved at M7: Literata + Inter are OFL; the licence
  texts ship as APK assets (`app/src/main/assets/licenses/`) and render verbatim
  on the About screen, alongside the CC BY-SA attribution the dictionary data
  requires. Both are contract-tested, not documented-and-hoped.
