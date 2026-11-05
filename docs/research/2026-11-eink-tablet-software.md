# What people like and dislike about e-ink tablet software

Date: 2026-11-05. A web search of reviews, Reddit, Lemmy and MobileRead
threads about Boox, reMarkable, Supernote, Kindle Scribe, Daylight,
ViWoods and KOReader. The evidence is user posts and reviews, not surveys.
An earlier pass over launcher issue trackers is in `PROGRESS.md` (2f).

## What people like

1. **Focus.** reMarkable and Daylight are praised for having no
   notifications and nothing that pulls you away.
2. **Battery for weeks.** Supernote stops the CPU after 20 seconds idle and
   leads on battery. Margin does no background work (decision 0019).
3. **Fast, paged, no grey.** KOReader is loved for no animation, paged
   menus, quick page turns and its dictionary.
4. **Android apps when needed.** The main reason people pick Boox and
   ViWoods over reMarkable.

## What people dislike

1. **Clutter that cannot be removed.** Boox firmware 4.0 moved to a busy,
   Android style launcher. Users miss the old minimal one.
2. **Too many settings.** Boox and KOReader are both called a labyrinth.
3. **Weak organisation and no search.** Kindle Scribe had no subfolders at
   launch. Supernote has no full text search. reMarkable users name
   organisation as their main complaint.
4. **Getting files on.** Boox Wi-Fi transfer is unreliable. USB sometimes
   connects as PTP, not MTP.
5. **Hidden background behaviour.** Boox "App Freeze" kills apps, even the
   keyboard, and battery use is not shown. Telemetry and forced accounts
   annoy people.

## What Margin took from this

- Search over notes, journal and highlights (Home, Search).
- Look Up in the EPUB reader, through an installed dictionary app.
- Home shows the document before the current one, to jump back.
- Highlight export as Markdown already existed in both readers.

## What Margin does not take

- Wi-Fi book transfer. It would be a second network place, against
  decision 0012.
- Refresh modes per app and sleep screens. The vendor firmware owns them.
- A big settings tree. Settings stays at four tabs (decision 0023).

## Sources

- Boox launcher, firmware 3.5.4 against 4.0:
  https://redlib.astrial.org/r/Onyx_Boox/comments/1hj4lgu/firmware_40_appreciation_post/m378lsn/
- Boox App Freeze: https://www.zubersoft.com/mobilesheets/forum/printthread.php?tid=14196
- reMarkable missing features: https://nyc1.lr.ggtyler.dev/r/RemarkableTablet/comments/1jzq507/missing_features
- Returning the reMarkable Move: https://honeypot.net/2025/10/24/why-im-returning-the-remarkable.html
- Supernote battery: https://supernote.com/blogs/supernote-blog/new-kernel-release-brings-significant-performance-improvements-to-supernote-x-series
- Supernote Manta review: https://www.itpro.com/hardware/tablets/supernote-a5-x2-manta-review-a-premium-e-ink-tablet-thats-still-somewhat-spartan
- Kindle Scribe organisation: https://palmsolo.substack.com/p/amazon-improves-kindle-scribe-file-organization
- KOReader: https://ilikeorangutans.github.io/2025/12/19/a-love-letter-to-koreader
- Daylight: https://adventure.com/daylight-computer-tablet-gear-review/
- ViWoods AiPaper: https://www.techradar.com/tablets/ereaders/viwoods-aipaper-review
