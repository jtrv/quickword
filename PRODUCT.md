# Product

## Register

product

## Users

People who read — books, articles, feeds — and hit unfamiliar words. Primary
scene: reading in bed at 11pm, select a word, want the meaning in under five
seconds, back to the book without losing their place. Secondary: writers
reaching for a better word (thesaurus), curious people wanting a quick "what
is X" (Wikipedia card). Android users; power users of text selection.

## Product Purpose

QuickWord answers "what does this word mean" from anywhere on the device with
the least possible interruption: select → tap QuickWord → definition appears
as a notification, no app switch. The app itself is the deep end — full
entries, thesaurus, history, favourites — but success is measured at the
shallow end: lookup speed, zero friction, works offline. Successor in spirit
to NotificationDictionary (MIT), rebuilt with modern data and design.

## Brand Personality

Bookish, quiet, instant. A well-made reference object — feels like quality
paper, acts like a system utility. Typography carries the identity (serif
headwords); color is restrained with one distinctive magenta-rose accent.
Never gamified, never chatty, no streaks, no mascot. Density is contextual:
roomy where you read (word page), dense where you scan (lists/history).
*(Confirmed 2026-07-29 via reports/personality-decision.html.)*

## Anti-references

- Vocabulary-builder apps (Duolingo-style gamification, XP, word-of-the-day
  push spam) — QuickWord is a tool, not a habit product.
- Ad-laden dictionary apps (Dictionary.com app clutter).
- The 2026 AI-slop default: cream/parchment backgrounds "because bookish".
  Bookishness lives in type, not in beige.

## Design Principles

1. **The notification is the product.** Every design decision is judged first
   at the notification/lookup path, second at the app.
2. **Five-second round trip.** Lookup → understanding → back to reading. No
   splash, no spinner theater, no interstitials.
3. **Earned familiarity.** Standard Android affordances (M3 components,
   predictable navigation); distinctiveness comes from typography and one
   accent, never from novel controls.
4. **Offline is the default, online is the garnish.** Core lookup never needs
   network; Wikipedia/online fallbacks appear only when they add something.
5. **Density with hierarchy.** Dictionary entries are dense by nature; POS
   grouping, progressive disclosure, and type scale do the organizing.

## Accessibility & Inclusion

WCAG AA minimum: all text/background pairs ≥4.5:1 (verified programmatically
in DESIGN.md palette; re-verify on any token change). Dynamic type: respect
Android font scale up to 200% without truncating definitions. TalkBack: full
content descriptions on the word page; notification content readable by
screen readers. Reduced motion: honor the system animator-scale settings —
no essential information conveyed by motion alone.
