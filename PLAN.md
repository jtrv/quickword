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

| M10 ✅ | Cross-model review (Codex, 12 findings) actioned: false "nothing leaves your device" claim corrected, PROCESS_TEXT input bounded, as-typed candidate added so non-ASCII headwords (`Übermensch`) stop falling through to Wikipedia, bundled-DB extraction made atomic, SQLite handles closed in the trampoline and receivers, download verification counts real rows instead of trusting `meta`, stale-download reconciliation, `PAUSED_QUEUED_FOR_WIFI` handled, TTS engine dropped when unusable, word page re-resolves after a dictionary install. Search field moved to the bottom of the screen | `mise run verify` green; bottom-bar layout verified on device with a docked keyboard |

| M11 ✅ | Offline Wikipedia, opt-in: `etl/build_wiki.py` turns a Kiwix mini ZIM into a 37.9 MB SQLite of 49,918 lead paragraphs + 823,870 aliases (their curation, our format — 6.9× smaller than the ZIM, libzim build-time only so nothing GPL ships). Rows are raw DEFLATE against a preset dictionary stored in the file. `DictionaryDownloader` generalised to `CorpusDownloader` + `Corpus` so two data sets can coexist. Offered from About → Storage & data; tried before the network in both the app and the trampoline | 5 `WikiCorpusTest` cases (round-trip, exact-case-beats-NOCASE, alias, missing corpus, unknown title); ETL self-asserts a round-trip; **verified on device in airplane mode** — "Accenture" and "Acrocanthosaurus" answered from the corpus with no network |

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

## Refutation table

### Round 3 — offline Wikipedia corpus (2026-08-05) — **plan REFUTED as specified**

Proposal under test: ~330 MB SQLite of first paragraphs for the ~875k
most-viewed English articles, built by our own ETL, shipped as a second
optional download.

| Claim | Verdict | Disposition |
|---|---|---|
| ~875k article intros ≈ **330 MB** | **REFUTED** (own measurement, 396 top-viewed + 239 random articles sampled live) | The 330 MB came from Kiwix's mini ZIM, which stays **compressed at rest**; a queryable SQLite does not. Measured: top-article intros average **1,615 B** (random articles 512 B — the head is 3× fatter than the tail). 875k rows = **~2.0 GB on disk**, ~600 MB download. Off by 6× on the number that matters. |
| 875k articles cover essentially every proper noun | **REFUTED** — en.wikipedia has **2,164,237 biographies alone**; 875k omits ≥59.6% of people before spending a single row on places or organisations | Coverage target must be derived from a measured hit-rate, not asserted. "Top N" was picked to hit a size budget, then justified backwards. |
| CC BY-SA is satisfied by attribution + a link to the article | **REFUTED** — §3(a)(1)(C) requires a URI to **the licence itself**; `rg 'creativecommons.org/licenses/by-sa' app/src/main` finds nothing, and `LookupNotifier` shows the extract with neither attribution nor source link | **Live bug in the shipping app, not just the plan.** Fixed 2026-08-05: licence URI on the About screen, attribution line in the notification. |
| A ~330 MB post-install download needs no special declaration | **REFUTED** — Play's Deceptive Behavior policy requires prompting and disclosing the size first | We already disclose "≈120 MB" in the banner and listing, so the practice was right and the claim was sloppy. Any new corpus must disclose its own size the same way. |
| Lead paragraphs are obtainable in plain text without a wikitext parser, rankable by popularity | UNREFUTED | Wikimedia Enterprise *Structured Contents* snapshots carry plain-text abstracts; the published pageviews dataset ranks them. Better source than the raw XML dump assumed at planning time. |
| The ETL can build it within a workstation's and CI's limits | UNREFUTED | The existing pipeline already streams a multi-GB compressed input to SQLite in constant memory, inside the 14 GB / 6 h runner budget. |
| Custom SQLite beats embedding libzim for a title/paragraph/URL app | UNREFUTED, but **weakly** — the refuter found no metric either way | Downgraded to an open question. The size measurements now argue *for* ZIM's compressed-at-rest design; the revision below adopts that property without the JNI dependency. |

**Revision (measured, not assumed).** Per-row DEFLATE with a shared preset
dictionary keeps rows queryable while recovering nearly all of the archive's
density: **620 B/article vs 605 B for whole-file gzip** — a 2.5% penalty for
staying a database. `java.util.zip.Deflater.setDictionary` is platform API, so
this stays pure Kotlin. On-device cost becomes 0.68 GB for 875k articles,
1.55 GB for 2M, 4.65 GB for 6M.

That is the honest curve, and it is what makes the feature a real product
decision rather than a packaging detail: meaningful proper-noun coverage costs
1.5 GB+ on someone's phone, on top of the 280 MB dictionary. Deferred until
there is a measured fallback hit-rate to size it against. The cheap win — a
cache of summaries already fetched — is unaffected by any of this.

### Round 4 — offline Wikipedia, "their curation, our format" (2026-08-06)

Proposal under test: build two opt-in corpora at build time from Kiwix mini
ZIMs — `top` (49,981 articles, 25 MB download / 48 MB on disk) and `top1m`
(991,894 articles, 290 MB / 412 MB) — as SQLite with per-row DEFLATE against a
shared preset dictionary plus an alias table from Kiwix's redirects, delivered
through the existing DownloadManager path. **Measured, not estimated:** three
ZIMs converted, 6.4–6.9× smaller than the source archive each time.

| Claim | Verdict | Disposition |
|---|---|---|
| Kiwix regenerates these ZIMs on a dependable cadence | **REFUTED** — `wikipedia_en_top1m_mini` has exactly **one** release ever (2026-04, the file we measured); `top_mini` has 2026-03 and 2026-06; Kiwix has publicly documented an English Wikipedia generation failure with a slipped ETA | The `top1m` tier rests on a file generated once. Mitigated but not removed by snapshotting: we already publish our own release asset, so Kiwix is a build-time input we pin, not a runtime dependency. Refreshing the corpus, however, is at their mercy. **`top` is the safer tier on this axis too.** |
| `DictionaryDownloader` can carry a second corpus without restructuring | **REFUTED** — single-corpus throughout: one prefs file/key (`dictionary_download`/`id`), `enqueue()` calls `cancel()` so a second download *removes the first*, one destination name, one installed-file check | Must be parameterised by corpus (id, URL, filenames, prefs key) before a second download exists. Not the "free reuse" the plan assumed. |
| ZIM first-`<p>` extraction equals what the app shows today | **REFUTED** — `WikipediaApi.parse()` deliberately rejects `type:"disambiguation"` (pinned by `WikipediaApiTest`); taking the first `<p>` would happily serve "Mercury" as though it were an article | The ETL must replicate the disambiguation filter. My own fidelity check (40/40 match vs the live API) missed this because it sampled articles, not the class that fails. |
| Redistribution is satisfied by attribution + licence URI | **REFUTED** — §3(a)(1)(A)(v) also requires retaining a URI to *the material*; the notification discards `summary.pageUrl`, which the parser already captures | Fixed 2026-08-06: the notification carries the article URL. Third distinct CC BY-SA clause this project has tripped — the licence is not one requirement. |
| ~700 MB of optional downloads needs no special handling | **REFUTED** (wording) — Play requires prompt *and* size disclosure, and `getMaxBytesOverMobile` makes metered handling mandatory at this size | We already do all three. The claim was sloppy, the implementation was not. |
| Raw DEFLATE with a preset dictionary decodes on Android, fast enough for the hot path | UNREFUTED — 3.8 µs/decode measured locally, 1.5 µs by the refuter | **Landmine found while testing:** with raw deflate `needsDictionary()` never fires, so `setDictionary()` must be called *before* `inflate()`. The documented-looking order fails with `DataFormatException: invalid distance too far back`. Also: the preset dictionary must be stored **inside** the corpus, or the file cannot decode itself. |
| ~992k articles is sufficient coverage for the fallback's job | UNREFUTED, **weakly** — refuter found nothing measuring real dictionary-miss selections either way | Stays an open question, not a validated assumption. The honest test is the fallback hit-rate, which we do not collect and (no analytics) will not. |

**Verdict:** the *format* is validated — three independent conversions, a
6.4–6.9× win over shipping ZIMs, fidelity confirmed against the live API on 40
articles. The *delivery* is not: two of the four kills (downloader,
disambiguation) are straightforward work, but the `top1m` supply risk is
structural. Recommended shape: ship **`top` (25 MB)** first, snapshot the ZIM
we built from, and treat `top1m` as a later tier taken only if Kiwix resumes
generating it.

### Round 1 & 2 (plan-refute protocol, codex-cli 0.144.6 cross-model)

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
