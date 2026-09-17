# State

Volatile cursor for work in flight. `PLAN.md` is the stable record; this file
is replaced in place. Last refreshed 2026-09-17.

## Objective

QuickWord 1.0.x is shipped and in both stores' pipelines. Remaining work is
closing out the two findings from the F-Droid device review, then keeping the
distribution channels fed.

## Where things stand

- `main` at `d42462c`, clean, CI green ([run 34912299927](https://github.com/jtrv/quickword/actions/runs/34912299927)).
- Released: v1.0.2 (`versionCode` 3, commit `1346b69`), APK attached to the
  GitHub release and pinned by the F-Droid recipe.
- **F-Droid** — MR !46415, one commit on `io.github.jtrv.quickword` (fork
  `jtrv/fdroiddata`), head `2305c958`, labels `New App`,
  `reproducible-builds`, `review-requested`. Pipeline 9/9 green; packagers are
  working through a long queue. A volunteer reviewer posted a full device test
  on 2026-09-17 (see next actions). **Any new release must update this MR** —
  bump `versionName`/`versionCode`/`commit` in
  `metadata/io.github.jtrv.quickword.yml` and push to that branch.
- **Play** — listing and content done; blocked on Google's closed-testing
  requirement (12 testers for 14 days), which is an accounts problem, not a
  code one. The 1.0.2 AAB builds with `mise run bundle` but has not been
  uploaded.
- Local fdroiddata clone with full history (needed for pushes — see decisions):
  `~/.cache/quickword-rb/fdroiddata`.

## Next action

Cut **v1.0.3** carrying both fixes from the device review:

1. **Store description is wrong about the bundled dictionary.**
   `fastlane/metadata/android/en-US/full_description.txt` says "a small starter
   dictionary is bundled so the app works immediately" and that the full
   dictionary "is downloaded once on first run". The bundle is a 15-word
   fixture (`bona fide, dictionary, dog, don't, hound, ice, ice cream, lexicon,
   petrichor, quick, serendipity, thesaurus, well-being, word`) and nothing
   downloads automatically — the user taps **Download** on the home-screen
   banner. Reword to match what ships; the metered-network confirm dialog is
   better than the current text promises, so describe it.
2. **`android:allowBackup` defaults to true**, so `databases/history.db`
   (recents and favourites) goes to cloud backup. `PRIVACY.md` already
   discloses this accurately, so it is a decision rather than a bug: exclude
   the database (manifest flag or a backup rule) and simplify the disclosure,
   or keep it and leave the text as is.

Correction to the counts the reviewer quoted: the fixture is 15 rows but 14
distinct headwords — `dog` appears twice. And words outside it are not all
dead ends; names and places still resolve through the Wikipedia fallback.

Then: `mise run verify` → commit → tag (needs your GPG passphrase, so you run
`git tag -a v1.0.3 … && git push --follow-tags`) → `mise run release:apk
v1.0.3` → `gh release create` → update MR !46415 to versionCode 4 → reply to
the reviewer.

## Decisions

- **Reproducible builds: accepted** (2026-08-24, reversing an earlier decline).
  F-Droid republishes our signed APK instead of signing with its own key, so
  GitHub↔F-Droid installs are interchangeable. Cost: the signing key is now
  load-bearing for both channels — losing `quickword-upload.jks` means neither
  can ever update in place again. Play is unaffected (Google re-signs).
- **`dependenciesInfo { includeInApk = false; includeInBundle = true }`:
  provisional.** F-Droid rejects AGP's Google-encrypted dependency blob in the
  APK; the AAB keeps it so Play can still flag vulnerable SDKs. The reviewer
  asked for both to be false — if a packager insists, flip it, but that is a
  one-line change plus a version bump.
- **Release APKs are built only by `mise run release:apk <tag>`**, never from
  the working tree. v1.0.0's APK was built from a later commit than its tag and
  can therefore never verify — that is why v1.0.1 exists.
- `UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$` is deliberate: the repo
  also carries data tags (`db-en-v1`, `wiki-en-top-v1`) that must not be read
  as app releases.

## Failed attempts (do not retry)

- **Never clone fdroiddata shallow to amend the MR commit.** With `--depth=1`
  the branch tip *is* the shallow boundary, so `git commit --amend` drops its
  parent and force-pushing leaves a root commit: the MR showed 3000 files
  "added" and ran the full-repo pipeline (the `weblate merge conflict` job
  failed on the unrelated history). Recovery was `git fetch --unshallow`, then
  rebuilding the branch on current `master`. Use the full clone above.
- Pushing to GitLab from this machine's agent shell needs
  `git -c credential.helper= -c 'credential.helper=!glab auth git-credential'`;
  a stale global helper otherwise shadows glab's token and the push fails with
  `HTTP Basic: Access denied`. SSH is fine from your own terminal.

## Loose ends

- `link.txt` (the Play closed-testing URL) is untracked and undecided: commit
  it, fold it into `RELEASING.md`, or gitignore it.
