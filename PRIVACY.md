# QuickWord privacy policy

*Last updated: 2026-08-04. Applies to the QuickWord Android app
(`io.github.jtrv.quickword`).*

**QuickWord has no accounts, no ads, no analytics and no crash reporting. It
collects nothing about you, and there is no server of ours for it to send
anything to.**

## What stays on your device

- **Your lookups.** Definitions are read from a dictionary file stored on the
  device. Looking up a word makes no network request.
- **Recents and favourites.** Words you look up or star are saved in a small
  database inside the app's private storage. Uninstalling the app, or clearing
  its data, deletes them.

If you have Android's own backup enabled in your Google account settings, the
system may include the recents/favourites database in your device backup. That
is a platform feature under [Google's terms](https://policies.google.com/privacy),
not something QuickWord sends anywhere itself. The downloaded dictionary is
explicitly excluded from backup.

## When the app does use the network

QuickWord contacts exactly three kinds of destination, and none of them is ours:

1. **github.com** — once, when you tap *Download* to fetch the full dictionary.
   GitHub sees your IP address and that the file was downloaded. No word you
   looked up is involved.
2. **wikipedia.org** (Wikimedia REST API) — only when a word has *no* dictionary
   entry, so that names and places still resolve. In that case the word is sent
   to Wikimedia, which sees it along with your IP address, under the
   [Wikimedia privacy policy](https://foundation.wikimedia.org/wiki/Policy:Privacy_policy).
   Words that the offline dictionary can answer are never sent anywhere.
3. **Your device's text-to-speech engine**, when you tap *Listen*. QuickWord
   hands the word to whichever TTS engine you have installed; some engines
   synthesise speech in the cloud. What happens then is governed by that
   engine's privacy policy, not this one.

## Permissions

- `INTERNET` — for the two cases above.
- `POST_NOTIFICATIONS` — to show the definition, which is the whole point of the
  app. Notifications are built and shown on the device.

## Children

QuickWord is a dictionary. It has no user accounts, no user-generated content
and no advertising, and is suitable for all ages. Definitions come from
Wiktionary, which is an unfiltered general-purpose dictionary.

## Changes and contact

Any change to this policy will be committed to
[the repository](https://github.com/jtrv/quickword/blob/main/PRIVACY.md), so its
history is public and auditable. Questions or concerns:
[open an issue](https://github.com/jtrv/quickword/issues).
