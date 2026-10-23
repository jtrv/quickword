# State

Volatile cursor for work in flight. `PLAN.md` is the stable record; this file
is replaced in place. Last refreshed 2026-10-21.

## Objective

Get QuickWord merged into F-Droid, then keep the distribution channels fed.

## Where things stand

- `main` clean, verify green.
- Released: v1.0.3 (`versionCode` 4, commit `e1e3ec7`): in-app lookup of
  selected text, corrected store description, `allowBackup` declared.
- **F-Droid** — MR !46415 was closed for inactivity on 2026-10-16 (linsui's
  2026-09-18 request for AntiFeatures went unanswered). Reopened 2026-10-21
  with commit `97be1554` adding `NonFreeNet` (Wikipedia lookups, GitHub
  downloads), and replied to linsui and the device reviewer promising 1.0.3.
  2026-10-22 linsui asked to drop old versions and rebase: done as one
  commit `86809a69` on master (1.0.3 only), pipeline 9/9 green with the
  rebuild verified, both threads answered and resolved. Waiting on review.
- **Play** — listing and content done; blocked on Google's closed-testing
  requirement (12 testers for 14 days). Nothing uploaded since 1.0.2's AAB.
- Local fdroiddata clone with full history (needed for pushes — see decisions):
  `~/.cache/quickword-rb/fdroiddata`.

## Next action

Wait for F-Droid review on !46415 and answer it promptly: the last closure
was for a month of silence. Any new release must bump that MR.

Backup decision (2026-10-21): keep recents/favourites in Android backup;
`PRIVACY.md` already discloses it, so its text stays.

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
