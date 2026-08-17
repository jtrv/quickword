# QuickWord

Pure Kotlin + Jetpack Compose reimagining of NotificationDictionary (select
text anywhere → definition as notification). Android-only.

- `PLAN.md`: architecture, milestones, refutation tables (authoritative).
- `PRODUCT.md` / `DESIGN.md`: product register, verified palette, type system.
- `RESEARCH.md`: background research (its Flutter-shell framing is superseded
  by PLAN.md).
- `reference/NotificationDictionary/`: read-only prior art; never edit, lint,
  or build it.
- Toolchain/tasks: `mise.toml` (`mise run verify|build|test|lint|shots|palette`).

## Standing workflow (non-negotiable)

- **Verify gate:** `tool/verify.sh` is the whole gate; run it before every
  commit, and run the narrow steps (format/analyze/touched tests) after every
  edit.
  Discipline details: user-global `kotlin-verify-loop` skill (and
  `flutter-verify-loop` if/while any Dart packages exist).
- **Plan refutation:** every plan/design/architecture decision is adversarially
  refuted before implementation via Codex cross-model refuters. Protocol: the
  user-global `plan-refute` skill. A plan without a refutation table is not done.
- Pure Kotlin decision (2026-07-29, refuted & confirmed; table in PLAN.md):
  no Flutter. Compose for UI, native trampoline for the lookup hot path.
- On any palette/token change: `mise run palette` must still pass all contrast
  pairs, and DESIGN.md + `ui/theme/Color.kt` stay mirrored.
