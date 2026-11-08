# 0019. Battery

Date: 2026-09-22. The owner: "focus on making the launcher not drain battery".

A home app is on screen all day and stays in memory all night, so any work
it does in the background costs battery every hour. An e-ink panel holds its
picture with no power, so every change of pixels costs power too.

## How it was checked

Five readers went through the code, each for one kind of drain: timers and
wake-ups, work that goes on after a screen stops, flash writes and CPU, the
panel and its redraws, and radios and wake locks. A second reader tried to
disprove each finding. A last reader merged them and looked for gaps. They
read the debug build too, because that is the build on the tablet.

## What was found and fixed

1. **The Read tab opened every book again each time it came back.** It read
   the list again on every return, which is right for progress and reading
   time, but it also opened every EPUB zip and parsed its XML for the title
   and author. That happened each time a book closed. `BooksRepository` now
   keeps what it read for as long as the file keeps its size and date. A
   book changed by hand is read again.
2. **The Home clock listened while the tablet slept.** The minute broadcast
   was taken for as long as Home existed, which is always. It is now taken
   only while Home is on show, and the time is read again when Home comes
   back. With the tablet asleep, the app has nothing to wake up for.
3. **The log opened its file once per line.** A burst, such as the vendor
   method list, was hundreds of opens and writes to the flash. Lines now wait
   in a queue and a burst is written with one open.

## What was checked and is fine

- No timers, no polling loops, no alarms, no jobs, no wake locks. The wake
  lock permission that Readium brings is removed in the manifest.
- The network is used only when Settings, About, Updates checks when it opens; the refresh icon checks again.
- Battery and Wi-Fi on Home are read once a minute, with the clock.
- The fast pen is stopped whenever a screen with ink goes to the back, so the
  panel is never left in the pen mode.
- StrictMode in the debug build costs nothing until a rule is broken.

## Left as it is, on purpose

- **Two AndroidX start-up helpers**, emoji and the profile installer, run
  once when the process starts. The profile installer makes a release build
  faster, which saves battery. The emoji helper finds no font provider on a
  tablet without Google services and stops. Once per start is not worth a
  manifest trick.
- **The EPUB reader's two listeners** stay on while the reader is in the
  back. They wait for a change and do nothing while there is none.
- **Auto Refresh** costs two full screen changes each time. It is off unless
  the user turns it on.
