# Design

Visual system for QuickWord (Android, Jetpack Compose, Material 3). Register:
product (see PRODUCT.md). Theme scene: *reading in bed at 11pm — the app must
be glanceable in the dark and unremarkable in daylight.* Both themes are
first-class; follow the system setting.

## Color

Strategy: **Restrained** — neutral surfaces, one brand accent (magenta-rose,
hue 330) for primary actions/selection/links, one reserved role color
(green, hue 155) exclusively for thesaurus/synonym affordances. Fixed brand
palette; **no Material You dynamic color** (user decision 2026-07-29 —
identity and testability over wallpaper-matching).

All values authored in OKLCH, shipped as sRGB hex. Every text/bg pair below
verified ≥ 4.5:1 (WCAG AA) by `tool/palette.py` — re-run it whenever a token
changes; lowest passing pair today is 7.34:1.

### Light scheme (M3 ColorScheme roles)

| Role | OKLCH | Hex |
|---|---|---|
| primary | 0.450 0.150 330 | `#802D7C` |
| onPrimary | 1 0 0 | `#FFFFFF` |
| primaryContainer | 0.910 0.045 330 | `#F3D7F0` |
| onPrimaryContainer | 0.300 0.120 330 | `#4C0C49` |
| secondary (muted mauve, chips/support) | 0.480 0.055 340 | `#735167` |
| tertiary (thesaurus green) | 0.450 0.085 155 | `#276340` |
| tertiaryContainer | 0.920 0.045 155 | `#CEEED8` |
| onTertiaryContainer | 0.280 0.069 155 | `#003219` |
| background / surface | 1 0 0 (pure white) | `#FFFFFF` |
| surfaceContainer | 0.955 0.007 330 | `#F3EEF2` |
| surfaceContainerHigh | 0.930 0.010 330 | `#ECE6EB` |
| onSurface | 0.220 0.012 330 | `#1E191D` |
| onSurfaceVariant | 0.400 0.020 330 | `#4E444D` |
| outline | 0.600 0.020 330 | `#877C86` |
| error | 0.500 0.180 27 | `#B32322` |

### Dark scheme

| Role | OKLCH | Hex |
|---|---|---|
| primary | 0.780 0.110 330 | `#DF9ED9` |
| onPrimary | 0.280 0.120 330 | `#460544` |
| primaryContainer | 0.360 0.120 330 | `#5E1F5A` |
| onPrimaryContainer | 0.900 0.050 330 | `#F2D3EE` |
| secondary | 0.780 0.045 340 | `#CCADC0` |
| tertiary | 0.780 0.090 155 | `#88C99E` |
| tertiaryContainer | 0.340 0.075 155 | `#0D4326` |
| onTertiaryContainer | 0.900 0.050 155 | `#C5E8D0` |
| background / surface (near-black plum) | 0.180 0.010 330 | `#141014` |
| surfaceContainer | 0.230 0.012 330 | `#201B20` |
| surfaceContainerHigh | 0.265 0.014 330 | `#292328` |
| onSurface | 0.920 0.006 330 | `#E7E3E6` |
| onSurfaceVariant | 0.760 0.015 330 | `#B7AEB6` |
| outline | 0.560 0.018 330 | `#7B717A` |
| error | 0.750 0.130 27 | `#F58C81` |

### Usage rules

- Surfaces are near-neutral (chroma ≤ 0.014, tinted toward 330). Light bg is
  literal `#FFFFFF` — no hidden warmth, no cream (see PRODUCT.md
  anti-references).
- **Tertiary green is reserved**: synonym chips, the Thesaurus notification
  action, thesaurus screen accents. It never decorates anything else, so the
  color itself carries meaning.
- Primary appears on: FAB/primary buttons, selected states, tappable
  cross-reference words inside definitions, the headword accent. Not on
  body text, card borders, or decoration.
- Semantic tokens only in composables — raw hex lives once, in
  `ui/theme/Color.kt`, mirrored from this file.

## Typography

Pair on the serif/sans contrast axis; both variable fonts, bundled:

- **Literata** (variable, Google Fonts, OFL) — headwords, entry display,
  word-of-nothing (no gimmicks): `displayLarge`→`titleMedium` slots.
  Headword on the word page: Literata 36/44, weight 500 (opsz auto).
- **Inter** (variable, OFL) — everything else: labels, body, definitions,
  buttons, navigation. Definitions are Inter `bodyLarge` 16/24; dense lists
  `bodyMedium` 14/20.
- Fixed rem-equivalent scale (M3 type scale, ratio ~1.17), no fluid sizing.
- IPA pronunciation: Inter, `onSurfaceVariant`, never italic (IPA glyphs
  must stay upright).
- Definition prose max measure ~70ch; on wide screens the entry column caps
  at 640dp.

## Shape & spacing

- M3 shape scale, modest: small 8dp, medium 12dp, large 16dp. Cards top out
  at 16dp radius. Full-pill only on chips and the search field.
- 4dp base grid; entry internals: 16dp screen margins, 12dp between senses,
  20dp between POS groups.
- Elevation restrained: tonal (surfaceContainer steps), shadows only on
  genuinely floating things (FAB, menus, sheets).

## Components (canonical vocabulary)

- **Entry sense block**: sense number (tabular, `onSurfaceVariant`) + gloss
  (Inter body) + optional example (italic, `onSurfaceVariant`) + synonym
  chips (tertiary, max 6 shown, "+n" overflow).
- **POS group header**: Literata italic titleSmall ("noun", "verb") — the
  only italic serif in the app.
- **Search field**: full-pill, `surfaceContainer`, leading search icon,
  voice input trailing.
- **Word card** (history/favourites lists): headword (Literata titleMedium)
  + first gloss one-liner. No card borders — tonal surface + spacing.
- **Wikipedia card**: thumbnail right (56dp, 12dp radius), 2–3 line extract,
  "From Wikipedia" attribution caption — matches the Page Previews contract.
- **Notification**: BigTextStyle; title = headword · POS, body = top 1–2
  glosses; actions `[Thesaurus] [Open] [Save]`.
- Every interactive component ships default/pressed/focused/disabled states;
  loading is skeleton lines (never a centered spinner on content surfaces).

## Motion

150–250ms, state-conveying only (M3 default easing, emphasized-decelerate on
enters). Container transform from word card → word page; crossfade for
in-place content swaps (thesaurus tab). No page-load choreography; honor
system reduced-motion (animator scale) — everything degrades to crossfade.
