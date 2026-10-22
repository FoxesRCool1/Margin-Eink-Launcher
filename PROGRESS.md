# Progress

One section per step. Newest step at the top.

---

## Journal: a daily word goal, and eight fixes

Date: 2026-10-22. Branch `Dev`. Decision 0025.

### What was done

1. **A daily word goal.** Off, 250, 500, 750 or 1000 words. The entry shows
   "212 / 750" in its label row, upright, on its side and in a split half.
   Press it to change the goal. A day that has reached the goal gets a check.
2. **A failed save keeps the words.** The editor stays open and says "Could
   not save". Moving to another day waits for the save.
3. **An unsaved edit cannot land on the next day.** The text carries its date.
4. **Cancel works after a pause**, and an emptied entry removes the day's file
   so the month view does not mark it.
5. **Ticks use the date of the tap**, so a tablet left on across the day
   boundary ticks the right day.
6. **Month stepping keeps the day**: 31 Mar, back, forward is 31 Mar.
7. **Routine**: a new item never takes the id of a removed one that still has
   ticks. A list or tick file that cannot be read is no longer written over.
8. **Month view numbers** are pure black and a size up. Today has a thin ring.

### What the owner must test on the tablet

- Type an entry with a goal set. The count follows the typing after a short
  pause. Press the count to cycle Off, 250, 500, 750, 1000.
- Reach the goal: a check appears before the count.
- Type, press Home, come back, press Cancel: the old text is back.
- Empty an entry and save: the day has no rule under it in the month view.
- Month view: the numbers are black and readable. Today has a thin ring.

---

## Home: a big clock, Continue, and parts of the day

Date: 2026-10-22. Branch `Dev`. Decision 0024.

### What was done

1. **A big clock** on Home in the display serif, with the date under it. 12
   hour time shows a small AM or PM on the line of the digits. A narrow or
   short half of the split screen gets a smaller clock and no plant.
2. **A Continue row** under the clock: the book read last or the note edited
   last. One tap opens it through the page opener. A book shows its percent.
   It is read in the background when Home comes back, and the old value stays
   until the new one arrives. With nothing to continue the row is left out.
3. **Landscape has two columns.** The clock, Continue and Next on the left.
   The four icons two by two on the right. The four icons are smaller
   (at most 120 dp) in every layout.
4. **Parts of the day for routine items**: Any Time, Morning, Afternoon,
   Evening. Home offers the first undone item whose part has begun, and reads
   the routine again each hour. In the Journal, a long press on a routine
   item opens its options: the part of the day, and Remove. Removing used to
   happen at once on a long press, and now it is one of the options.
5. **Two bugs.** After one Ctrl+N the new note box opened every time the
   Writing tab was opened. The request is taken once now. The Writing tab also
   reads its list again when the user comes back from a note, so a changed
   title or preview is not stale.
6. The routine rows in the Journal were 86 dp tall, which cut the bottom half
   off the small line under the name. They are 96 dp now.

### What does not work, or is not checked

- The screenshot tests cannot show the line spacing of the clock: Robolectric
  ignores `lineHeight`, so the clock leaves the font's own line height alone
  and trims the empty space with a layout. How the spacing looks on the real
  panel is the first thing to check.
- The Bodoni Moda digits are set as all one width (`tnum`). If the font has no
  such feature the clock moves a few pixels when the digits change. That is
  harmless, and the owner can say if it bothers.

### Device test list for the owner

1. Look at Home upright. The time should be large and in the middle, the date
   under it, and nothing should touch the plant in the top corner. Try 12 hour
   time (tablet date and time settings) and look for AM or PM beside the digits.
2. Read a few pages of a book, press Home. A Continue row with the book and its
   percent should be there. Press it. The book should open.
3. Write a note, press Home. The row should show the note. Press it.
4. In the Journal, open Routine. Hold a routine item. Choose Evening. The row
   should show EVENING. Go Home before 17:00: that item must not be offered
   until the evening. It should read "This Evening" then.
5. Turn the tablet on its side. Two columns: clock and Continue on the left,
   the four icons on the right. The bottom bar must be whole.
6. Open the split screen with Home in one half. Check both a half that is wide
   and short and one that is narrow and tall.
7. With a keyboard: press Ctrl+N once, make a note, come back, then open the
   Write tab. The new note box must not open again.

---

## A pass over the app: bugs and small improvements

Date: 2026-09-23. Branch `Dev`. The owner asked for a pass over the whole
app and for any improvement that could be found. Released as 1.0.1 the same
day, at the owner's word, before the tablet tests below.

### What was done

Bugs:

1. **"Sort by last read" sorted by the day a book was added.** The reader
   never writes to the book file, so its date is the import date. The sort
   now uses the date of the book's annotations file, which the reader writes
   each time it closes. A book never opened counts from the day it came in.
2. **"Check again" on the Home app page of Settings asked Android on the
   main thread.** It is a binder call. It runs in the background now, and
   the answer comes back as the status line. The answer also shows after
   the system's own "choose a home app" question closes.
3. **"Copy to Download" in the log froze the screen** for up to two
   seconds: it waited for the log writer and copied the files on the main
   thread. It runs in the background now, and the button is off while it
   works. "Copied 1 file(s)" is now "Copied 1 file".
4. **Back from the log and from the updates went to Home.** The arrow on
   the screen went to Settings. Now Back does the same, and lands on the
   page of Settings the log or the updates came from (Help or Updates), not
   on the menu. `LauncherRoute.parent` says where Back goes.
5. **Renaming a note to its own name made "Name 2".** The same for a change
   of capitals only, on the tablet's shared storage, which does not tell
   "note" from "Note". Both keep the name now.
6. **A rename of a typed note did not change the name on the row.** The row
   shows the first heading of the note, and the rename only changed the file
   name. Now the heading changes too. A note that starts with plain text
   keeps its text: only the file name changes then. The rename box starts
   with the name the row shows.
7. **Renaming a folder with a dot in its name**, such as "Drafts v1.2",
   kept ".2" on the end of the new name. Fixed.
8. **A note marked for a move stayed marked** after its folder was renamed
   or deleted, and Paste then failed. The mark goes now. A failed rename
   says so.

Small improvements:

9. **The clock on Home follows the tablet's 12 or 24 hour setting.** It was
   always 24 hour. It is read once a minute, with the clock.
10. **Enter confirms a dialog that asks for a name**, from a Bluetooth
    keyboard or the Done key of the screen keyboard. In the app search on
    the A to Z page, Enter opens the first app found. `EinkTextField` has
    an `onSubmit` for this.
11. **Margin is not in its own list of apps** any more. It only led back to
    where the user was.
12. **An archived habit can come back.** The Journal has no list of
    archived habits. Adding a habit with the same name as an archived one
    now brings the old one back, with the days it was done.
13. **A note row shows its first line at once.** The list read each note
    for its title, and then each row read the note again for its first
    line. The row showed "MD" first and the line a moment later: a second
    repaint on e-ink. Now one read gives both.
14. "1 books" is "1 book". Three compiler warnings are gone.
    `plan.md` named the working name wrong.

### What works

Build, all 582 unit and screenshot tests, and lint pass. New tests cover the
sort, every rename case, the heading change, the note preview, Enter in a
field (a real key event, and the screen keyboard's action), the app list,
the habit that comes back, and where Back goes.

Checked by hand in the emulator, with the debug build:

- Settings, Help, Log, "Copy to Download" said "Copied 1 file". The Back key
  then went to Settings, Help. Settings opened on its menu the next time.
- "Check again" said "Margin is not the home app yet", which was true.
- A new folder was made with the Enter key. The Done key of the screen
  keyboard shows a tick.
- "Shopping" was renamed to "Groceries" with Enter: file and heading both
  changed. Then to "groceries": the emulator's storage does not tell
  capitals apart (it finds "GROCERIES.md"), and the note still became
  "groceries.md" with no "2".
- In the app search, "chr" and Enter opened Chrome. The list has 19 apps,
  one fewer than the emulator has, because Margin is left out.
- Home showed "4:41 PM", and "16:48" after the emulator was set to 24 hour.

The emulator held the signed 1.0.0 from the update test, with an empty data
folder. It holds the debug build now.

### What does not work, or was left

- The long note that scrolls inside its box (decision 0011) is still there.
- Settings such as pinned apps are not in the backup zip. They live in
  DataStore, which the plan calls cheap to rebuild. After a move to a new
  tablet, the pinned apps have to be pinned again.

### What the owner must test on the tablet

1. Read, sort by last read: open a book that was near the bottom, read a
   page, go back. It is at the top now.
2. Settings, Home app, "Check again": the line at the top answers.
3. Settings, Help, Log, "Copy to Download": the screen does not stop, and
   the line says how many files. Then press the tablet's Back key: it goes
   to Settings, Help.
4. Write: hold a typed note, Rename, type a new name. The row shows the new
   name. Rename it to the same name again: no "2" comes.
5. With the tablet set to 12 hour time, Home shows "8:04 AM".
6. With a Bluetooth keyboard: Write, new folder, type a name, press Enter.
   Apps, A to Z, search, type three letters, press Enter.
7. Journal: archive a habit, then add a habit with the same name. The old
   one comes back with its dots.

---

## The README as a landing page

Date: 2026-09-22. Branch `Dev`. The owner asked for an audit of the README
as the page a new user lands on, then for the top five fixes.

### What was done

1. A download link and a device line at the top: Android 10 or newer,
   tested on the ViWoods AiPaper Mini, other tablets not tested yet.
2. A "Why Margin" section: what goes wrong with a phone home screen on
   e-ink.
3. The note for test versions before 1.0.0 moved from Install to the end of
   Updates.
4. Version and licence badges first, the CI badge after them.
5. The word "launcher" once, near the top, for search.

The full audit, with copy for a later FAQ and a final download link, is in
`founder/landing-page.md`. That folder is not in the repository.

### What the owner must do

- Look at the README on the `Dev` branch on GitHub.
- In the GitHub settings: add a social preview image, set the homepage to
  the latest release, add the topics `android-launcher`, `e-reader`,
  `distraction-free` and `boox`.

No app code changed. Nothing to test on the tablet.

---

## 1.0.0: the first full release, with a release key

Date: 2026-09-22. Branch `Dev`. The owner said the testing phase is over
and asked for 1.0.0, a clean-up of the old debug releases, and a secure key.
Decision 0022.

### What was done

1. **The release key.** RSA 4096, alias `margin`, valid to 2054. It is in
   `~/margin-release/` on the dev machine, with a random password in a file
   beside it, both readable by the owner only. It is in the GitHub secrets.
   It is not in the repository. Fingerprint in `docs/RELEASING.md`.
2. **The Release workflow** publishes signed files only when the key is
   there, and debug files only when it is not (a fork). It checks every file
   before it publishes: signed, and with this project's key.
3. **The signed build was tested before the release.** Built on the dev
   machine with the key, checked with `apksigner`: signed, not debuggable.
   Installed in the emulator: Home, Read, Write, Journal, Apps and Settings
   opened with no crash.
4. **The real update was tested after the release.** In the emulator, a
   0.4.0 build signed with the release key found 1.0.0 on GitHub, downloaded
   the 38 MB file, checked it, and Android installed it over the old one.
   1.0.0 then started with no crash. The Release workflow's signature check
   passed for both files.
5. **The old releases 0.1.0 to 0.4.0 were deleted from GitHub.** They held
   debug files only. The git tags stay.
6. **Docs:** README install steps name the signed files and say how to move
   from a test version. `docs/RELEASING.md` has the key, the secrets and the
   fingerprint. Three places said Settings, Help for updates; it is
   Settings, Updates.

### What the owner must do

1. Copy `~/margin-release/margin-release.keystore` and
   `~/margin-release/password.txt` to a safe place away from this computer,
   such as a password manager. Without them, no update can ever reach
   anyone.
2. On the tablet: back up, uninstall the test build, install
   `margin-v1.0.0-viwoods.apk`, restore.
3. Register with Google's developer verification (personal account), and
   register the package name with the release key fingerprint.

### Device test list

- 1.0.0 installs from the releases page in the browser.
- Restore brings back books, notes, journal and habits.
- Settings, Home app, "Set as home app" makes Margin the home screen again.
- Settings, Updates, "Check for updates" says there is nothing newer.

---

## A simple README, and the way the app reaches people

Date: 2026-09-22. Branch `Dev`. Docs only, no code changed.

### What was done

1. **The README is short and checked against the code.** It is written for
   a person who wants to install the app. The build details moved to
   `CONTRIBUTING.md`. Five lines were wrong and are fixed:
   - Updates are under Settings, Updates, not Settings, Help.
   - The home app row is Settings, Home app, "Set as home app".
   - Backup is under Settings, "Backup and files", not Settings, Storage.
   - The releases hold only `-debug.apk` files, because there is no release
     key yet. The README named a `-viwoods.apk` file that does not exist.
   - The log has no Share button. Its download icon copies the files to
     `Download/Margin`. Also fixed in `CONTRIBUTING.md` and the bug report
     template.
   The README now also says that an uninstall deletes the data folder.
2. **The owner asked if the app must go on Google Play.** It does not.
   GitHub Releases and the in-app updater stay the way to get the app. The
   plan already kept Play for the `generic` flavour only, and the updater
   would have to come out for it (decision 0012).

### What the owner must know

- **Release key.** Every release is signed with the debug key, and that key
  is in the public repository. Anyone can sign a file with it that installs
  over Margin. That is fine for testing, not for strangers. Make the release
  key before the app is shared widely. `docs/RELEASING.md` has the steps.
- **Google developer verification.** From 2027, certified Android devices
  refuse apps from developers who have not registered with Google, even as
  APK files. It starts on 2026-09-30 in Brazil, Indonesia, Singapore and
  Thailand. Registration is a one-time fee of 25 US dollars and an ID check.
  Users can still install an app from a developer who did not register,
  through a one-time "advanced flow". The tablet has Google Play, so this
  probably applies to it.

---

## The open source pass: free, public, Ko-fi, wanted features, bug check

Date: 2026-09-22. Branch `Dev`. Not released yet: the notes for 0.4.0 are
written in `docs/RELEASE_NOTES.md`. The owner decided against selling the
app, made the repository public, and asked for a pass over everything: a
link to Ko-fi, the features people ask of an e-ink launcher, a bug check,
and a clean, professional repository. Decision 0020.

### What was done

1. **The repository is public and the app is free.** The tree and the whole
   git history were searched for tokens, keys, passwords, home paths and
   email addresses. None were found. `CLAUDE.md`, `docs/HANDOFF.md`,
   `docs/RELEASING.md` and decision 0012 no longer say the source is
   private. New: `CONTRIBUTING.md`, `.github/FUNDING.yml` (the Sponsor
   button), a bug report template, and a description and topics on GitHub.
2. **Ko-fi in three places**: the README, the GitHub Sponsor button, and
   Settings, Help, "Support this app", which hands the address to the
   browser. A "Source code" row beside it. The app never asks.
3. **The updater needs no token.** The footer of the update screen is empty
   for a normal user. The token button shows only after a check fails with
   "not found", for a private fork, or when a token is already saved.
4. **What people ask of an e-ink launcher** was researched on the web (GitHub
   issues of Olauncher, KISS, inkOS, mLauncher, E-Ink-Launcher; MobileRead;
   F-Droid). The two most wanted things this app lacked are now in:
   - **Hide an app.** Hold an app on the A to Z page, "Hide from the A to Z
     page". The last line of the list says "2 hidden apps" and shows them;
     press it again to put them away. Hidden apps stay pinned and stay in
     their folders. They live in `apps/folders.json` under `"hidden"`, so a
     backup keeps them.
   - **Type to find an app.** The search icon on the A to Z page opens a
     field. One or two letters match the start of a word, three or more match
     anywhere. "ca" finds Calculator and Google Calendar, not Scan.
   Everything else on the list was already there (no animations, pure black
   and white, a manual and an automatic full refresh, no tracking, plain
   clock and battery), needs root (system refresh modes, front light), or
   goes against the design (widgets, wallpapers).
5. **Bugs found by a code review and fixed:**
   - One app with a broken label emptied the whole app list, and could take
     the way back to the stock launcher with it. Each app is guarded alone.
   - A journal entry saved with Save and then left at once could be lost:
     Save cleared the editing flag before its write ran, and the save on the
     way out looked at that flag. Journal writes now take turns and always
     write the newest text.
   - A note open in one half of the split screen, deleted or renamed from
     the Writing tab in the other half, was written back by the next
     autosave. The editor now refuses to write a note that has gone from the
     disk, and says "This note was moved or deleted".
   - A double tap on a note opened it twice, and the two copies saved over
     each other. A second tap on the same thing within 1.5 seconds is ignored.
   - The home role check ran a binder call on the main thread when Settings
     opened. It runs on the background thread now.
   - A text prompt did not take the focus, so every prompt cost one extra
     tap. Known gap 6 in the handoff. It takes the focus now.

6. **The app is called Margin.** Decision 0021. Eighteen names were checked
   against the app stores, GitHub and trademark listings. The package is
   `io.github.foxesrcool1.margin`, the data folder is `Margin/`, the release
   files are `margin-<tag>-<flavour>.apk`, and the repository is
   `FoxesRCool1/Margin-Eink-Launcher`. The old name stays in the older parts
   of this file and in the older decisions, as history.

### What works

Build, 568 unit tests, lint: green. New tests: hidden apps in the model and
the file, the search rules, and pictures of the search and of the hidden
apps, upright and on the side. The Help page and the update screen were
looked at in both shapes.

### What does not work, or is not known

- Nothing here has run on the tablet.
- The Ko-fi row needs a browser on the tablet. If none opens, the log says
  "No browser could open".
- The keyboard on the search field and on a prompt: the focus is requested,
  and whether the ViWoods keyboard comes up by itself is for the tablet to
  say.
- The saveAndWait of a handwritten note still blocks the main thread for up
  to 4 seconds in onPause, on purpose, so ink is not lost. Left as it is.
  See decision 0017.

### What the owner must test on the tablet

0. **First: back up, uninstall, install.** Margin is a new package, so the
   0.3.0 app cannot update into it. In the old app: Settings, Storage,
   "Back up". Copy the backup file off the tablet. Uninstall the old app.
   Install `margin-v0.4.0-viwoods-debug.apk`. Settings, Storage, "Restore".
   Then set Margin as the home app again.
1. Apps, A to Z: press the search icon. The keyboard should come by itself.
   Type "k". Only apps with a word starting with k show. Close the search.
2. Hold an app, "Hide from the A to Z page". Go to the last page: a line
   "1 hidden app". Press it: the app is back with HIDDEN beside it. Hold it,
   "Show on the A to Z page".
3. Settings, Help, page 2: "Support this app" opens Ko-fi in the browser.
   "Source code" opens GitHub.
4. Settings, Help, "Check for updates": the screen has no token button. A
   check finds the release, since the repository is public.
5. Write: hold a note, Rename. The keyboard comes with no extra tap.
6. Journal: type an entry, press Save, press Home at once. Come back. The
   entry is there.

---

## The owner's third list: Force Refresh, plain names, the Pen page, battery

Date: 2026-09-22. Branch `Dev`. Release 0.3.0. The owner used 0.2.0 on the
tablet and said: "Clean the screen now" does not refresh; call it "Force
Refresh" and "Landscape Mode"; the Pen page makes no sense; and make the
launcher easy on the battery. He also asked for updates with no access token.
He then chose to keep the token for now, because the app must stay secure to
sell. So nothing changed there. Decisions 0018 and 0019.

### What was done

1. **Force Refresh works another way.** The old button only set the vendor
   picture mode, which paints nothing by itself. Now the whole window goes
   black for 450 ms and comes back, in the vendor full mode where the mode
   can be read. This works on any e-ink panel. Auto Refresh (on each change
   of screen) and the readers' "every N pages" use the same flash.
2. **Plain names** on Look and screen: Force Refresh, Auto Refresh,
   Landscape Mode, Flip Landscape, Icon Names, Status Bar. Shorter help
   lines, so none ends in three dots.
3. **The Pen page explains itself.** Pen Mode is Normal, Fast 1 or Fast 2.
   The choice has three lines of help and a tick on the one in use. "Try
   the Pen" opens a blank page with that mode. Redraw Delay says what it is.
   A fast mode that closed the app says so, and picking it again allows one
   more try.
4. **Battery.** The Read tab no longer opens every book file each time a
   book closes. The Home clock stops listening while the tablet sleeps. The
   log writes a burst of lines with one open of the file.

### What works

Build, all unit tests, lint, green. New tests: the black flash and the mode
put back, the book title cache, the log under four threads at once. New
pictures: the Pen page as the tablet shows it, and both new dialogs, upright
and on the side. Everything fits.

### What does not work, or is not known

- **Nothing here has run on the tablet.** The flash length, 450 ms, is a
  guess. If the tablet shows no black, or only a grey flicker, it is too
  short. See decision 0018.
- Fast 1 and Fast 2 are still the untested vendor paths of step 2.

### What the owner must test on the tablet

1. Settings, Look and screen, page 2: press Force Refresh. The screen goes
   black for a moment, then comes back clean. Say if it did not go black.
2. Turn on Auto Refresh. Change tabs. Each change flashes once. Turn it off.
3. Settings, Pen: press Pen Mode. Read the three lines. Pick Fast 1. Press
   Try the Pen and write. Then Fast 2. Keep the one that looks best, or
   Normal.
4. Read tab: open a book, close it. The library comes back as before.
5. Leave the tablet asleep on Home for a night. Note the battery before and
   after, and compare with 0.2.0 if you know that number.
6. Send the log. Look for `ScreenRefresh` lines: they say if the vendor
   full mode was used.

---

## The owner's second list: the split screen for every page, speed guards, questions for ViWoods

Date: 2026-09-22. Branch `Dev`. The owner asked three things: what to ask
ViWoods for a smooth pen, a split screen that works with any app or page, and
that the app stays as quick as it is. Decisions 0016 and 0017.

### What was done

1. **A split screen on every screen.** Home, every tab, the typed note, the
   handwritten note and both readers have the split icon. The second half
   starts on a choice of pages: Read, Write, Journal, Apps, Settings. In a
   reader it opens on the notes of the book, as before. Any page works there,
   with a way back. Two controls: swap the halves, close.
2. **A book or a note chosen in the second half opens where it was tapped**,
   and the page beside it comes along into the new screen.
3. **Another app beside a page**, through Android's own split screen, where
   the tablet allows it. Android never splits the home screen's own task, so
   the page first opens again in a task of its own, `BesideActivity`.
4. **One page is never open twice.** The second half refuses a page the other
   half shows, and says so.
5. **Speed guards.** Time budgets write a `Slow:` line to the log when a
   start, a change of tab, or an open is over budget. StrictMode, in debug
   builds, writes each slow main thread call to the log once. A new test,
   `SpeedRulesTest`, fails the build on `runBlocking`, `Thread.sleep`,
   scrolling lists, animations, Compose Material, and a book engine in the
   files Home starts with. New rules in `CLAUDE.md`.
6. **A letter for ViWoods**: `docs/viwoods-request.md`. What to ask for, most
   important first, and what each answer would change in the app.

### What works

Build, all unit tests, the speed rules, and lint, green. New pictures of the
split screen in both orientations: Home with the choice, the Journal beside
Write, a note beside the library, the halves swapped, a handwritten note
beside a typed one, and the PDF reader with the notes of the book.

In the emulator, Android 13 at 320 dpi, with this app as the home app, by
hand: the split on Home, a note opened and typed in the second half and saved
to its file, a PDF opened from the second half landing where it was tapped
with Write carried beside it, swap, a turn with the split open, the EPUB
reader with the notes of the book, and the Clock app beside the Journal
through Android's split screen.

Home starts as fast as before: 692 ms before this change and 676 ms after,
in the emulator, middle of five cold starts each.

The speed log found a real slow spot on its first run: the EPUB reader looked
up the data folder on the main thread each time a book opened. Fixed.

### Bugs found and fixed on the way

1. **A black half.** The first try at "an app beside a page" asked Android
   from the home screen's task. Android split the screen and left our half
   black. Fixed with `BesideActivity`.
2. **Tools off the screen in half a screen.** The handwritten note and the
   PDF reader picked their layout by the shape of the room, and in half a
   screen neither the rail nor the row fit. They now pick the one that fits.
3. **A false "Slow:" line** for the change to Home after a book or an app
   took the page away. The timer now only measures a tap.

A review in five parts, each finding checked by a second reader, found six
more. All fixed:

4. Each "app beside" left a task behind. Now there is only ever one.
5. The fast pen could keep drawing over a canvas that the split screen had
   just replaced, for a moment. It stops at once now.
6. One tap could clean the whole screen twice, with "Clean the screen on
   each change" on.
7. A note half typed on a highlight in the EPUB reader was closed without a
   save when the split screen changed.
8. The speed test did not read the debug build's own code, and that code
   had two slow calls. Both fixed, and the test reads it now.
9. Titles now get smaller before they end in three dots, on every screen.

### What does not work, or is not known

- **Nothing here has run on the tablet.**
- **Another app beside a page depends on the ViWoods firmware.** If it has
  multi-window turned off, the app opens on its own screen and the log says
  so. ViWoods' own blog says the AiPaper has no split screen of its own.
- **Two books side by side** is not possible. A book needs a screen of its
  own. A book beside the library, a note, the Journal or an app is.
- **Half of a screen is small** when the tablet reports 480 dp. Some long
  lines end in three dots. At 320 dpi, which the emulator script guesses the
  tablet reports, each half has half again as much room.
- **The fast pen** serves one handwritten canvas at a time: the one in the
  second half, when there is one. Still off by default.

### What the owner must test on the tablet

1. Home: press the split icon in the bottom row. The lower half shows five
   icons. Open Journal there. Swap the halves. Close.
2. Open Write in the second half, open a note, type a line. Close the split.
   Open the note from Write: the line is there.
3. Open a PDF. Split: the notes of the book come up, as before. Press the
   back arrow in that half: the choice of pages. Open Read there and pick
   another book: it opens in that half.
4. The big one. Split on Home, open Apps in the second half, pick any app.
   Does the app open beside the page, or on its own screen? Say which.
5. Turn the screen with the split open, upright and on its side.
6. Send the log file. Look for lines that start with `Slow:` or `Leak:`, and
   for `AdjacentApps` and `multi-window`: they say what the tablet did.

### For ViWoods

Send the letter in `docs/viwoods-request.md`. Choose the final package name
first if it will change: ViWoods keeps its fast pen list by package name.

---

## The owner's first list: icons, round shapes, landscape, split screen, folders, Settings

Date: 2026-09-20. Branch `Dev`. The owner used the first build and sent eight
points. All eight are done. Decisions 0013, 0014 and 0015 have the reasoning.

### What was done

1. **Lucide icons in place of words**, in every screen and toolbar. The app
   icon is the Lucide sprout, and the plants are Lucide icons drawn large.
   No icon library was added: `tools/lucide.py` writes the path data of the
   icons in use into the app. Licence ISC, and MIT for a part. `ASSETS.md`.
2. **The Android status bar is hidden**, in every screen and every dialog. A
   swipe down from the top shows it for a moment. Settings can bring it back.
3. **Landscape.** A small grey icon at the top of every screen turns the
   screen. It is kept for the whole app. Every screen has a layout for the
   tablet on its side. A turn does not close what is open.
4. **Split screen.** In the EPUB reader and the PDF reader, one icon opens a
   note beside the book. Typed or handwritten, one of each per book, in
   `notes/Reading notes/`. Beside the page in landscape, under it upright.
5. **No square corners.** Controls are round, dialogs and text boxes have
   round corners, lines are finer. `design/EinkShapes.kt`.
6. **Folders on the Apps tab.** Hold an app, choose Folders. They are a plain
   file, `apps/folders.json`, and they are in a backup.
7. **"Home", not "Today"**, in the app, the code and the docs.
8. **A new Settings.** A menu of six groups. Every row has a short name, one
   or two lines of help, and a switch, a value or an arrow.

### What works

Build, 513 unit tests in each flavour, and lint, all green, and the release
build assembles. Every screen has a
picture upright and on its side in `app/build/outputs/roborazzi/`, 56 new
pictures, and each one was looked at. In the emulator, on Android 13, by hand:
the turn on Home and inside both readers, the status bar hidden, the split
screen in the EPUB reader with a real book and in the PDF reader, typed and
handwritten, upright and on its side, a turn with the split open, and both
note files on the disk afterwards.

### Bugs found and fixed on the way

1. **The Apps tab could not show every app.** The list asked for eight rows a
   page and six fit, and the screen does not scroll. The last two apps of
   every page could not be seen or pressed. Lists work out their own page size
   now, from the room they have.
2. **The device test had the same fault**: seven tests a page where four fit.
   The owner has not run it yet, and would have missed the tests at the end.
3. **The pinned page was taller than the screen** with eight pins and the
   three ways out. It is a paged list now.
4. **The Writing tab said "notes" above the title** at the top of the notes
   folder. It was meant to say "All notes".
5. **The EPUB reader lost its place by a page or two** each time the width of
   the book changed. Found in the emulator. It is sent back to its place.
6. **The Journal did not fit the tablet on its side at all**, and its day view
   was tight upright. The entry and the habits stand side by side when wide.
7. **Screenshot tests of a screen that reads the disk were a race.** The
   screen went on with its work on a background thread and wrote its state in
   the middle of a layout pass. `AppDispatchers` lets a test run that work in
   place. This is the old "fails once a month for no reason".

### What does not work, or is not known

- **Nothing here has run on the tablet.** The emulator has no e-ink and no
  ViWoods firmware.
- **The fast pen in the split screen** is a guess. The tablet draws its fast
  line in one box, so with a handwritten note beside a PDF the fast pen goes
  to the note, and marks on the PDF are drawn by the app. Fast pen is still
  off by default.
- **Hiding the status bar on this firmware** is not proven. If the bar does
  not hide, or does not come back on a swipe, Settings, Look and screen,
  "Android status bar" is the way out.
- **Typing in landscape with the on-screen keyboard** leaves very little room.
  The keyboard takes more than half of the height. A Bluetooth keyboard is
  what landscape typing wants.
- The split screen always opens the note of the book. It cannot open another
  note, and a note cannot open a book beside it.

### What the owner must test on the tablet

1. Home shows four icons, a plant, and no Android bar at the top. Swipe down
   from the top edge: the bar comes for a moment. The front light panel still
   opens from it.
2. Press the small grey icon at the top left of Home. The screen turns. Press
   it again. Look for grey marks after the turn. If they are bad, turn on
   "Clean the screen on each change" and say so.
3. With the screen on its side: are the tablet keys under your hand? If yes,
   Settings, Look and screen, "Turn it the other way".
4. Open each tab upright and on its side. Nothing may be cut off at the
   bottom or the right.
5. Apps: hold an app, Folders, make a folder, put two apps in it. Open the
   folder. Hold the folder: rename it, delete it.
6. Open an EPUB. Tap the middle, then the split icon at the top right. Type a
   line. Switch to handwriting and write a line. Close the split. The book
   must be on the same page as before. Both notes must be in Write, in
   "Reading notes".
7. The same in a PDF. Then turn the screen with the split open. The page and
   the note must both still be there.
8. Settings: can you find each thing without help? Tell me any row whose help
   text is not clear.
9. Send the log file. It holds `ScreenWindow: Read the window settings in N
   ms`, which is what the turn costs at start-up.

---

## In-app updates, and a second bug hunt

Date: 2026-09-20. Branch `in-app-updates`, on top of `dev-emulator`.

### What was done

- **The app updates itself from GitHub Releases.** Settings, Help, "Check for
  updates". It checks, downloads the APK that fits the build, checks the file
  twice, and hands it to Android. `docs/decisions/0012-in-app-updates.md` has
  the reasoning, and what it changes about the internet permission.
- **One command makes a release:** `tools/release.sh 0.1.1 "what changed"`.
  The Release workflow builds debug APKs always, and signed release APKs once
  the release key is in the GitHub secrets. `docs/RELEASING.md`.
- One version number, `appVersionName` in `app/build.gradle.kts`. The version
  code comes from it.
- `tools/fake-github.py`, and a switch in the Dev panel, to try the whole
  update with no release on GitHub.

### What works

Build, 447 unit tests in each flavour, and lint, all green. In the emulator,
on Android 13, by hand:

- The whole update from 0.1.0 to 0.1.1: check, access token dialog, download
  through a redirect, SHA-256, the look at the real APK (package, version,
  signing key), the install session, Android's "update this app?", the restart
  on 0.1.1, "updated from build 100 to build 101" in the log, old file removed.
- Cancel in Android's window: the reason comes back in words and the Install
  button stays.
- "Allow installs" opens the right Android page, and the screen notices the
  switch when the user comes back.
- The real GitHub: a private repository gives "needs an access token", and a
  wrong token gives "GitHub refused the access token".
- The LAN update in the Dev panel, which had never run. It did not work. See
  the first bug below.

### Bugs found and fixed

1. **The Dev panel's "Get latest build" could not have worked on the tablet.**
   Android blocks plain http for an app that targets SDK 28 or later, and the
   LAN server is plain http. Debug builds allow it now. Release builds do not.
2. **Journal: Save could write today's text over another day.** Previous and
   Next moved the day under an open edit, and Save used the new day. An edit
   is now saved to its own day before the view changes.
3. **Journal: the Home key threw an open edit away.** So did Back and "Today".
   It is saved as the screen goes now, and when the app goes to the back.
4. **Note editor: the Home key left the last words in memory only**, for up to
   a second and a half. The save "on the way out" only ran when the editor
   closed, and the Home key does not close it. It saves on stop now.
5. **Note editor: two saves could cross**, and the older text could land last.
   `NoteSaver` makes them take turns.
6. **Note editor: a note that could not be read opened as an empty page**, and
   the first autosave would have written that page over the real note.
7. **Every text field could eat a letter** when the caller was slow to hand
   the text back. `EinkTextField` knows its own echo now. A test shows the
   old loss.
8. **A Bluetooth keyboard that wakes up made Android rebuild the screen.** The
   cursor jumped to the end of the note, and the home screen went back to
   Today. The editors and the home screen now keep running.
9. **A book could have gone online** once the app had the internet permission.
   Every web view of the book engine has its network loads blocked. Checked in
   the emulator: the pages still show, and the log says "offline: true".
10. A failed ink save was not tried again until the next pen stroke.
11. **Two habits ticked one after the other could lose the first tick.** Each
    tap read the whole log, changed it and wrote it back on its own thread.
    The same for routine items. The changes take turns now. A test runs the
    race 40 times, and fails without the fix.
12. **A `habits.json` with a typo in it was wiped by the next "Add habit".**
    It read as an empty list, and the new list went over it. A copy is kept
    first, as `habits.unreadable.json`. The same for `routine.json`.
13. **Deleting a book that would not delete still deleted its highlights and
    handwriting.** The book goes first now, and the notes only after it.
14. **A second book with the same file name was dropped**, with the message
    "already in the library". A picker that gives no name calls every file
    "book.epub". The size tells two books apart now, and the second one is
    stored as "book 2.epub".
15. **The Journal worked out "today" once.** A tablet left on the Journal
    overnight put the first ticks of the new day onto yesterday. It works the
    date out again each time the screen comes back. Today does the same for
    the Next line.
16. **After the last row of the last page was deleted, the first press of
    Previous did nothing**, in every list of the app.
17. **PDF: a turn back onto a page with more than one screen showed "part
    -2147483648"** for the length of one render, which on this panel is long
    enough to read.
18. **Apps listed a home app with no name.** It is the blank screen inside
    Android's own settings app, and every Android has it. It is left out now.
    The way out to the stock launcher also asks with the HOME category, which
    is what Android 13 wants.

Bugs 11 to 17 came from two review passes by a second model. Each one was
checked in the code before it was fixed, and the fix was run in the emulator
where a screen was involved.

### What does not work, or is not known

- The owner made the first release, `v0.1.0`, on 2026-09-20. The app has not
  yet taken an update from a real GitHub Release. That needs a second release
  and the token on the tablet.
- **The repository is private and stays private**, the owner's decision: the
  app may be sold one day. So the tablet needs a read-only access token, once.
  `docs/RELEASING.md` has the steps. That token can read the source: give it
  an expiry date, and delete it on GitHub if the tablet is lost.
- Right after an update Android shows the stock launcher, because for a moment
  this app was not there to be the home screen. The Home key brings it back.
  The update screen says so before it happens.

### Device tests for the owner

Do these after the step 2 tests, or before, as you like. They need Wi-Fi.

1. Settings, Help, "Check for updates". The first time it says GitHub cannot
   find the repository and asks for a token. Press "Enter access token", put
   the token in, Save. It checks again by itself, and with no newer release it
   says "This is the newest version".
2. If the screen says Android must allow installs: press "Allow installs".
   **Does the ViWoods firmware open the page with the switch?** If not, use
   DevCheck, the same way as for the home app: Apps, Eink Launcher, Manage,
   "Install unknown apps".
3. When a newer release is out: "Download and install". The percent moves in
   steps of ten. **Does the number ghost on the panel?**
4. Android asks "update this app?". Press Update. The app closes.
5. **Which screen comes next?** If it is the stock launcher, press Home. You
   should be on Today, and Settings should show the new version.
6. Send the log. I look for "Install session ... committed". If it says "The
   install session failed. Trying the system installer." instead, the second
   route ran, and I want to know whether that one worked.
7. Journal: write a few words, do **not** press Save, press the Home key, go
   back to the Journal. The words must be there.
8. Write: open a note, type, press Home at once, open the note again. The
   last word must be there.
9. With a Bluetooth keyboard: put the cursor in the middle of a long note,
   let the keyboard fall asleep, wake it with a key. The cursor must stay.

---

## Testing on the dev machine, in an emulator

Date: 2026-09-20. `tools/emulator.sh` starts an Android 13 tablet at 1440 x
1920 on the dev machine, builds the app, installs it, puts two sample files
in its Download folder and opens the app. The top of the script lists the
other commands: `install`, `log`, `shot`, `grey`, `colour`, `stop`.

It is not e-ink. It cannot show ghosting, refresh modes or the fast pen, and
the ViWoods layer falls back to the generic one there. It is for layout, flow
and bugs. The tablet test lists below still stand.

In a debug build on an emulator, and only there, the mouse draws on the ink
canvas (`core/eink/DevEnvironment.kt`). In a PDF the toolbar has a switch
between "Mouse turns" and "Mouse draws".

The first run on real Android found three bugs that no unit test could see.
All three are fixed:

1. **The EPUB reader showed a blank page.** The layer that holds its menus
   painted the white paper background over the whole book. `EinkTheme` now
   has `paintPaper`, and the reader passes false.
2. **Every imported book lost its title and author.** The XML parser on
   Android does not know the security feature names the desktop one does,
   and throws on each. The doctype is now refused by looking for it.
3. **The Journal said "Handwrite" for a day that had just been written on.**
   The ink screen saved in `onStop`, which comes after the Journal resumes.
   It saves in `onPause` now.

Checked by hand in the emulator after the fixes: Today, a handwritten note,
EPUB import, reading, page turns by tap, swipe and key, Contents, the reader
menu, PDF import, all four zoom steps, ink on a PDF page that stays on the
same words across zoom steps, the handwritten journal page, Apps, and all six
Settings pages. No crash.

---

## Where everything stands

2026-09-20. All ten steps have code. **None of it has run on the tablet.**
Each step below ends with a device test list. Start with step 2, at the
bottom of the new entries, because the pen setting for everything else comes
out of it. `docs/HANDOFF.md` says what to do with the results.

---

## Step 10. E-ink audit, hardening and release

Date: 2026-09-20. State: the audit and the release setup are done. **No
release was made**, on purpose: nothing has run on the tablet yet.

### What was done

- An audit of every screen against plan section 5, from the code and from
  screenshots at panel size. `docs/decisions/0011-eink-audit.md` has the full
  list. The three that mattered most:
  - The text cursor blinked in every text field. It is now a still line
    (`EinkTextField`).
  - Dialogs faded and laid a grey wash over the screen. Both are off
    (`EinkDialog` and the dialog theme).
  - Settings had grown past one screen and the control back to Today had
    fallen off the bottom. It is six short pages now, and the footer cannot
    be pushed off any more.
- Today: the bottom row was cut off. The four words now take the room that is
  left, and step down one size when the Next line shows.
- Settings, About: credits and licences, one a page.
- The release build asks for one permission, `ACCESS_NETWORK_STATE`. An
  unused `WAKE_LOCK` from Readium's audio player is removed.
- The launcher writes its start-up time to the log.
- Release signing from four environment variables. The key is never in the
  repository. Proved with a throwaway key that was deleted afterwards.
- `.github/workflows/release.yml`: push a tag like `v0.1.0`, and it tests,
  builds, signs and publishes both APK files.
- `docs/RELEASING.md`, `docs/RELEASE_NOTES.md`, the final `README.md` with
  the install guide for ViWoods tablets and the way back to the stock
  launcher, `licenses/DEPENDENCIES.md`, and five screenshots with public
  domain text only.
- The Gradle distribution checksum is pinned.

### What is not done

- The first GitHub Release. It needs the device tests, a release key that
  only the owner should hold, and the final name.
- A review of crash logs from daily use. There are none yet.
- A typed note that is longer than the screen scrolls inside its field. That
  breaks e-ink rule 2 and needs a paged editor. Decision 0011.
- Code shrinking is off. See the comment in `app/build.gradle.kts`.

### The owner has to decide

1. **`desugar_jdk_libs`.** Readium needs it, and it is GPL-2.0 with the
   Classpath Exception, which is the licence of the Java class library
   itself. It does not make the app GPL. Read the paragraph in
   `licenses/DEPENDENCIES.md` and say yes or no.
2. **The final name**, before the first public release.
3. Still open from step 1: the package id and the licence.

### Device test list for the owner

1. Open every text field you can find: a new note, a journal entry, a rename,
   a note on a highlight. The cursor must stand still.
2. Open a dialog, for example hold an app in Apps. It must appear at once,
   with no fade, and the screen behind it must stay white.
3. Settings: open all six pages. "Today" must be at the bottom of each one.
4. Add a routine item in the Journal, then look at Today. "Quick note" and
   "Settings" must both be whole.
5. Open Settings, Help, Log. Find the line "Home screen up ... ms". Send it.
6. Use the tablet with this launcher for one day. Then Settings, Help, Log,
   "Copy to download", and send the files.
7. Install the `generic` build on a phone. It must start, and Settings, Pen
   must say that the device has no fast pen.

---

## Step 9. The handwritten journal entry

Date: 2026-09-20. State: complete. Step 9 has nothing left to build.

- "Handwrite" on a day opens the ink screen on
  `journal/YYYY/YYYY-MM-DD.inknote`, on a lined page. When the day already
  has one, the control reads "Open handwriting".
- A day can hold a typed entry, a handwritten one, or both. The month view
  marks the day either way.
- Coming back from the ink screen does not throw away a typed entry that is
  being edited.

### Device test list for the owner

1. Journal, "Handwrite". Write a line. Press Close.
2. The control should now read "Open handwriting". Press Month. Today should
   carry the rule under its number.
3. Go to yesterday with Previous. Press "Handwrite" and write. Go back to
   today. Each day must show its own page.

---

## Step 8. PDF and handwritten annotations

Model: Fable. Date: 2026-09-20. State: code and tests complete. Waiting for a
device test.

### What was built

- The renderer is the platform `PdfRenderer`. No new dependency. See
  `docs/decisions/0010-pdf-renderer.md` for the comparison and the memory
  rules.
- `PdfReaderActivity`: one screen of a page at a time. Tap the left or right
  third, swipe, or use the page keys. Tap the middle for More.
- Zoom steps: fit page, fit width, 150 %, 200 %. A page larger than the
  screen is cut into overlapping screens, and Next walks through them.
  Nothing scrolls and nothing is pinched.
- Crop margins, found per page.
- Go to page. The place, the zoom and the crop are remembered per book.
- The pen, the marker and the eraser draw straight on the page, with undo and
  redo. Strokes are in PDF points in
  `annotations/<book-id>/page-0001.strokes`. The PDF is never changed.
- "Pages with handwriting": the annotation list, with a jump to each page and
  an export to Markdown.
- "Export this page as a picture": the page and its ink as a PNG in
  `exports/`.
- EPUB: a highlight can now carry a handwritten note card. Tap a highlight,
  then "Handwrite".
- Reading time and the daily goal count PDF reading too.
- Tests: the screen arithmetic at every zoom step, the margin search, the
  sidecar files, the saved position, and a canvas test that draws at 200 %
  and checks the ink at fit page.

### What does not work yet

- `PdfRenderer` has no test sandbox, so no PDF was rendered off the tablet.
  The arithmetic around it is tested. The rendering itself is not.
- Password protected PDFs do not open. The reader says so.
- No read-ahead. The log records every screen that took over 400 ms.

### Device test list for the owner

1. Import a PDF. Tap it. The first page should fill the screen.
2. Turn ten pages. Write down how long a page turn feels, and send the log.
3. Press the zoom control until it says 200 %. Press the right third of the
   page again and again. You should walk across the page, then down, then on
   to the next page.
4. At 200 %, circle a word with the pen. Go back to "Fit page". The circle
   must still be around the same word.
5. Press More, "Crop the margins". The text should get larger. The circle
   must still be around the same word.
6. Use the marker over a line of text. The text must stay readable.
7. Press Close. Open the PDF again. Same page, same zoom, same ink.
8. More, "Pages with handwriting". Tap a row. Then "Export as Markdown".
9. More, "Export this page as a picture". Look in `EinkLauncher/exports/`.
10. Open a large scanned PDF, 100 MB or more. Turn fifty pages. The app must
    not close by itself. Send the log.
11. In an EPUB, tap a highlight, press "Handwrite", write a line, press
    Close. Tap the highlight again and press "Open card".

---

## Step 7 part two. The EPUB reader

Date: 2026-09-20. State: code and tests complete. **Judge it on the tablet
before building more on it**, as plan section 8 says.

### What was built

- Readium Kotlin Toolkit 3.4.0 (BSD-3). The build now compiles Kotlin with
  the 2.4.20 plugin, because Readium needs it. See decision 0009.
- `EpubReaderActivity`: paginated, one column, black on white. Tap the left
  or right third to turn a page, or swipe, or use the page keys or volume
  keys. No page turn is animated, and a swipe never drags the page.
- Tap the middle for the menu: Close book, Contents, Notes, Text. It shows
  the title, the place in the book, and the daily reading goal as a thin
  line.
- Text: font (Literata, serif, sans, the book's own), size, margins, line
  spacing, justify. A second page has the highlight style, a full refresh
  every N pages, and the daily goal.
- Hold a word to select text. A plain bar offers Highlight, Note and Cancel.
  Tap a highlight to add or edit a note, or to remove it.
- Notes: the list of highlights in reading order. Tap to jump there. "Export
  as Markdown" writes to `exports/`.
- Position, progress, highlights and notes are in
  `annotations/<book-id>.json`. Reading time is in
  `annotations/reading-log.csv`.
- The library list shows "34 % read", and the goal line for today.
- Tests: annotation file format with damaged input, the reading log, the
  timer, the settings limits, Readium opening a generated EPUB, a locator
  round trip, and the reader activity starting and saving on the way out.

### What does not work yet

- Nobody has seen it on the panel. The web view may ghost.
- Cover pictures are not shown in the library. The plan calls them optional.
- PDF files still say "step 8" when tapped.

### Device test list for the owner

1. Import a public domain EPUB from Standard Ebooks. Tap it.
2. Turn twenty pages with taps and with swipes. Write down how each page turn
   looks: clean, grey smear, or flash.
3. Tap the middle. Open Text. Change the font and the size. The page behind
   the panel must change.
4. Hold a word, drag the handles over a sentence, press Highlight. Then
   select another and press Note, type a line, press Save.
5. Text, Reading, Highlights: try the grey block. Say which one looks better.
6. Open Notes. Tap a highlight. You should land on its page. Press "Export as
   Markdown" and look in `EinkLauncher/exports/`.
7. Open Contents and jump to a chapter.
8. Press Home. Open the book again. It must open on the same page with the
   highlights in place.
9. If pages ghost: Text, Reading, Full refresh, every 5 pages. Say whether
   that helps. It needs a working Step 2 result.

---

## Step 6. Writing tab, handwritten half

Date: 2026-09-20. State: complete. Waiting for a device test.

### What was built

- "New note" now asks which kind: typed, or handwritten on a blank, lined or
  dot grid page. A handwritten note may be left without a name, because a pen
  user may have no keyboard in reach. It is then named after the moment.
- A handwritten note opens in the ink screen from Step 5, with pages, add
  page, delete page and finger swipe.
- Hold a note, then "Export as PDF". A typed note is set in Literata on A5
  pages. A handwritten note goes out as lines, not as a picture. Both land in
  `EinkLauncher/exports/`. A single handwritten page can also go out as a PNG
  from the More menu of the ink screen.
- Rename, move and delete work on handwritten notes the same way as on typed
  ones.

### Device test list for the owner

1. Write, New note, "Handwritten, lined". Leave the name empty. Write a line.
   Press Close. The note must be in the list under a date and time name.
2. Hold it. Rename it. Move it into a folder. Open it there.
3. Hold a typed note and a handwritten note, and export each. Open both PDF
   files on a computer.
4. Delete the handwritten note. It must ask first.

---

## Step 5. Ink engine

Model: Fable. Date: 2026-09-20. State: code and tests complete. Waiting for a
device test.

### What was built

- `core/ink/`, no Android in it: `InkStroke`, `InkStrokeBuilder`, `InkNote`,
  `StrokesCodec` (`.strokes`), `InkNoteCodec` (`.inknote`), `InkPageEditor`
  (undo and redo), `EraserGeometry`, `TemplateGeometry`, `InkNotesRepository`.
- `ui/ink/`: `InkRenderer`, `InkCanvasView`, `InkExport` (PNG and PDF),
  `InkNoteController` (pages and autosave), `FastPenSession`, and
  `InkNoteActivity`, the handwriting screen that Writing, Journal and the
  reader will share.
- Tools: pen in three widths, grey highlighter, stroke eraser, the eraser end
  of the pen, the side button, undo, redo, clear page.
- Pages: add, delete, previous, next, finger swipe, three templates.
- Palm rejection: a finger never draws, and a finger swipe does nothing for
  700 ms after the pen touched or hovered.
- Fast pen hand-over, built on the Step 2 device layer. Off by default.
- Settings is now in pages: Home app, Look, Pen, Storage, Help, About. The pen
  path, the redraw wait and the full refresh switch are under Pen and Look.
- A note that cannot be read is shown empty and is never saved over.
- 50 new tests: file formats with damaged and lying input, eraser geometry,
  the undo stack, and the canvas itself with made-up pen events, including
  the fast pen wait.
- `docs/decisions/0008-ink-engine.md`. **Read it: the engine does not use
  Jetpack Ink for its model, and the file says why.**

### What does not work yet

- Nothing was tried with a real pen. Pressure range, the side button and the
  hover events all depend on what the tablet reports.
- The fast pen width pairs in `PenWidths.vendorRange` are a guess. They have
  to be matched by eye.
- The PDF export could not be tested off the tablet. The test sandbox has no
  PDF writer.

### Device test list for the owner

1. Settings, Help, Device test, row "7b". A page opens.
2. Write a full page with the pen. Rest your hand on the glass as you would
   on paper. No stroke may be lost, and no page may turn by itself.
3. Try Marker over your writing. The writing must stay black.
4. Turn the pen around and rub out a word. Then pick Eraser and rub out
   another. Press Undo twice. Both words must come back.
5. Press the side button of the pen while drawing, if it has one. Write down
   what happens.
6. Press Add page, write a line, swipe right with a finger. You should be on
   page 1 again.
7. Press More, then each export. Look in `EinkLauncher/exports/` with a file
   manager. Open the PDF on a computer.
8. Press Close. Open row 7b again. Everything must be there.
9. Copy `EinkLauncher/notes/Ink test.inknote` to a computer, rename it to
   `.zip` and open it. It should hold `meta.json` and the pages.
10. If the Step 2 test found a working fast pen path: Settings, Pen,
    pick it, and do step 2 again. Watch for a double line or a flicker when
    the real stroke replaces the fast one. Change the wait if you see one.
11. Send the log. It holds the read and paint times.

---

## Step 2. Device spike: refresh control and fast pen

Model: Fable. Date: 2026-09-20. State: code and tests complete. **The device
test is not done**, and this step is only finished when it is.

The build now runs on the dev machine. The Android SDK is in `~/Android/Sdk`
and `local.properties` points at it. Every step from here on was built, tested
and linted locally before it was pushed.

### What was built

- `core/eink/`: `EinkDevice`, `GenericEinkDevice`, `ViwoodsEinkDevice`,
  `EinkDevices` and `FastPenGuard`. All vendor calls go through reflection,
  none can throw, and each one writes its result to the log.
- Three ways to reach a vendor call: the wrapper object, the binder service,
  and the `service call` shell command. The log says which one worked.
- `FastPenGuard`: a fast pen path that kills the app with a native crash is
  switched off at the next start. One crash at most.
- The `viwoods` flavour targets SDK 30. `tools/deploy.sh viwoods 8000 37`
  builds it with target SDK 37 for the comparison.
- Settings, then "Device test": device info, vendor API list, each picture
  mode, full refresh, four pen canvases (this app draws, Jetpack Ink, path A,
  path B), the crash guard, the home role, and a search for the ViWoods
  settings app and the stock launcher.
- The pen canvas has a toolbar that is handed to the tablet as "do not draw
  here", an eraser switch, four width ranges and four redraw waits.
- Jetpack Ink 1.0.0 is a debug only dependency, for the baseline canvas.
- 16 new unit tests. The vendor class is tested against a fake of the same
  shape.
- `docs/decisions/0007-viwoods-ink-and-refresh.md`.

### What does not work yet

- Nothing in `ViwoodsEinkDevice` has touched a real tablet. The public notes
  it is written from contradict themselves. Treat every call as unproved.

### Device test list for the owner

1. Run `tools/deploy.sh viwoods 8000` and install the build.
2. Open Settings, then "Device test". Press each row from the top. Read the
   answer under the row.
3. For each picture mode in row 3: leave the screen, turn some pages in the
   All apps list, and write down how the page turn looks.
4. Row 5, both canvases: write fast with the pen. Write down how far the ink
   is behind the pen tip.
5. Row 6, path A. If the app closes by itself, open it again, run row 8, and
   write that down. If it stays open: is the ink fast? Does ink appear on the
   toolbar? Press "Redraw" to change the wait, and find the wait where the
   stroke neither flickers nor doubles.
6. Row 7, path B. Same questions.
7. Try the Eraser and the Width button on the path that worked.
8. Run rows 9 and 10.
9. Press "Copy log to download" and send the log file.
10. Run `tools/deploy.sh viwoods 8000 37`, install, and do steps 5, 6 and 9
    again. That tells us whether target SDK 30 is really needed.

---

## Step 7 part one. The library and import

Model: Opus. Date: 2026-09-19. State: the library is built. **The reader is
not**, and that is on purpose. See below.

### What was built

- Import with the system file picker. The file is copied into `books/`. The
  app never reads a book from where the user picked it: a file outside the
  data folder can be moved or deleted, and then the reading position and every
  note point at nothing.
- `EpubMetadata`: reads the title and the author out of an EPUB by hand. An
  EPUB is a zip, `META-INF/container.xml` says which file inside is the
  package document, and that document holds `dc:title` and `dc:creator`. About
  a hundred lines, with 14 tests that build real EPUB files and read them
  back.
  - It refuses a document that declares a doctype. A book is a file from
    somewhere else, and an XML document can name an external entity and make a
    careless parser read a file off the device. There is a test for that
    attack.
- The library list: title, author, kind and size, sorted by recent or by
  title, paginated. Hold a row for the details and Delete.
- Deleting a book takes its annotations with it, so nothing is left behind
  that no book can open.

### Why there is no reader yet

Step 7 builds the reader on the Readium toolkit. Plan section 8 lists
"Readium WebView is slow or ghosts on e-ink" as a risk and says to judge it
before building more on it. That judgement needs the tablet.

The library, the import and the title reading do not depend on that decision,
so they are here now. Tapping a book says so rather than pretending.

**When you add Readium:** check its current version and licence on the web
first. It should be BSD-3. `EpubMetadata` can go once Readium is reading the
same fields, or stay as the fast path for the list. Either is fine; say which
in a decision file.

### Device test list for the owner

1. Open Read from Today. It should say the library is empty.
2. Press Import. Pick a public domain EPUB from Standard Ebooks.
3. The row should show the real title and author from inside the file, not the
   file name.
4. Import the same file again. It should say it is already in the library.
5. Import a PDF. It should appear with the file name as its title.
6. Hold a row and delete it.
7. Check the data folder with a file manager. The books should be in
   `EinkLauncher/books/`.

---

## CI went green

2026-09-19, at commit `9cbfc04`, run 9.

Everything passes at once for the first time:

- Both flavours build. The APKs are about 24 MB together.
- Every unit test passes.
- All twelve screenshots are recorded at 1440 by 1920 and uploaded.
- Lint passes with `abortOnError = true`.

It took nine runs. What was wrong each time, in order:

1. AGP 9 compiles Kotlin itself and refuses `org.jetbrains.kotlin.android`.
2. AGP still needs the Compose compiler plugin, which I had removed too eagerly.
3. The SDK install step looked for `sdkmanager` in a place this runner does
   not keep it.
4. Robolectric could not reach into `java.io` on a sealed JDK, and at API 36 it
   takes a path that needs to. Fixed with `--add-opens` and by pinning the test
   sandbox to API 35.
5. One test was wrong: it expected `///` to mean the data folder.

Two of those runs were cancelled by my own pushes before they could report.
The workflow no longer cancels a run that is already going.

**The screenshots have no baseline yet.** CI records them and uploads them as
an artifact; it does not compare them. Look at them, commit them, then turn on
comparison. That is the next thing to do.

---

## Step 6. Writing tab, typed half

Model: Opus. Date: 2026-09-19. State: the typed half is built. The handwritten
notebook waits for the ink engine, which is step 5 and is tagged for Fable.

### What was built

- `NotesRepository`: list, read, write, make a note, make a folder, rename,
  move and delete, all over the storage layer, so a note can never be written
  outside the user's folder. A folder cannot be moved into itself, which would
  otherwise make it and everything in it disappear.
- `NoteText`: word count, character count, and the title of a note. The title
  is the first Markdown heading, or the first line with something on it, or
  the file name. A heading marker does not make the word count go up.
- The file browser: folders first, paginated at five rows a page. A row shows
  the title from inside the note and the first line after it.
- Moving is done by marking something and pressing "Paste here" in the folder
  it should land in. Typing a path on a tablet with no keyboard is worse than
  two taps, and dragging on e-ink needs an animation, which rule 1 forbids.
- Delete asks first, and says whether it is about to take a folder with it.
- `NoteEditorActivity`: its own activity, as the plan says, so the Home key
  closes the launcher and not the note being written. It autosaves once the
  typing stops for a second and a half, and again on the way out.
- Bluetooth keyboard: Escape leaves the editor, Ctrl+S saves, Ctrl+N makes a
  note from anywhere and opens the Writing tab.
- Quick note from Today: one tap makes a note named after the moment and opens
  it.

### What is left

- The handwritten notebook, after step 5.
- Export to PDF and PNG.

### Device test list for the owner

1. Open Write from Today. Press "New note", type a title, press Write.
2. The editor should open. Type a sentence. Wait two seconds. The line under
   the text should change from "Not saved yet" to "Saved".
3. Press the Home key. You should land on Today, not in the note.
4. Open Write again. The note should be there with your first line under the
   title.
5. Make a folder. Hold the note, choose Move, open the folder, press "Paste
   here".
6. Hold the note and delete it. It should ask first.
7. From Today, press "Quick note". A note named after the time should open.
8. With a Bluetooth keyboard: press Ctrl+N from Today, then Escape in the
   editor. Write down whether both worked.

---

## Step 9. Journal tab

Model: Opus. Date: 2026-09-19. State: most of it built. Waiting for CI and for
a device test.

Two parts are missing on purpose. The handwritten entry needs the ink engine,
which is step 5 and is tagged for Fable. The routine list and the "Next" line
on Today are still to build.

### What was built

- A small JSON reader and writer (`core/json/`). The plan writes
  `habits.json` and `annotations/<book-id>.json`, so something had to read
  JSON. `org.json` is stubbed out in a plain unit test, which would let a test
  pass while the real code did nothing, and every Kotlin serialisation library
  carries a compiler plugin tied to a Kotlin version that AGP now owns. This
  is about 250 lines, refuses a document nested more than 64 deep, and has 16
  tests including every broken input I could think of.
- `DayBoundary`: where one day ends. Midnight is the wrong line for a habit
  tracker, because someone who reads until half past one has not started
  tomorrow. The hour is a setting and the default is 04:00, as the plan says.
  Tested across time zones and across the day New Zealand puts its clocks
  forward.
- `HabitStreaks`: current streak, longest streak, and the row of 14 dots. A
  day that is not finished yet does not break a streak: if yesterday is marked
  and today is not, the streak still stands and the user still has today.
- `HabitsFile` and `HabitLogFile`: `habits/habits.json` and `habits/log.csv`,
  both meant to be read and edited by a person. One broken habit or one broken
  line is dropped rather than losing the rest.
- `HabitsRepository`: add, rename, archive, mark a day, and a summary for each
  row of the Journal tab. Archiving never deletes, so a year of ticks cannot
  be lost to one tap.
- About 60 more unit tests.

### The screens

- Day view: the date, Previous and Next, the typed entry with Write, Edit,
  Save and Cancel, and the habits.
- Month view: whole weeks starting on Monday. A day with an entry carries a
  short rule under its number, not a filled cell, because a filled cell on
  this panel ghosts. Tap a day to open it.
- Habit rows: the name, the streak in words, and fourteen dots for the last
  fourteen days. Tap to mark today done or undone. Hold for rename, archive
  and the longest streak.
- Add a habit through a one line prompt.
- New design system parts: `TextPromptDialog`.

### The routine

- `habits/routine.json` holds the list, `habits/routine-log.csv` holds what
  was ticked off on which day. Two files, so ticking something off never
  rewrites the list.
- The Journal tab has a Routine page. A tap ticks an item off. The order is
  changed with Up and Down arrows rather than by dragging, because a drag
  needs a moving picture under the finger and rule 1 has no exceptions.
- Today shows the next item that is not ticked off, with a Start control.
  "Next" means the first one in the user's own order, not the nearest in time.
  When the routine is empty or finished the line is left out entirely rather
  than shown blank, because a blank line on e-ink is a repaint for nothing.
- An item can open a tab in this app, open another app, or open nothing at
  all. An item that opens nothing is ticked off by its own Start control.

### What is left for step 9

- The handwritten entry. The "Handwrite" control is there and disabled until
  step 5 exists.

### Device test list for the owner

1. Open Journal from Today. You should land on today's date.
2. Press "Write", type a line, press "Save". Leave the tab and come back. The
   line should still be there.
3. Press "Add habit" and add one. Tap its row. The big dot should fill.
4. Tap it again. It should empty.
5. Press "Month". The month should fill one screen with no scrolling. The days
   you wrote on should carry a rule under the number.
6. Tap a day in the month. It should open that day.
6a. Press "Routine", add two items, then use Up and Down to swap them.
6b. Go back to Today. The first item should show under "Next" with a Start
    control. Press Start, then check the Journal shows it ticked off.
7. Hold a habit row. Rename it. The streak must not reset.
8. Watch for ghosting when you turn from Day to Month and back. Write down
   what you see.

---

## Step 4. Storage layer

Model: Opus. Date: 2026-09-19. State: code and tests complete, waiting for CI
and for a device test.

### What was built

- `FileStore`: everything the app may do with the data folder, as one
  interface. `LocalFileStore` is the only implementation today and uses plain
  `java.io.File`.
- `StorageLayout`: which folder and which file name for every kind of thing,
  plus a name cleaner that survives FAT32 and a synced Windows folder.
- `RelativePaths`: the path rules. `..`, a leading slash, a drive letter and a
  stray space are all refused rather than repaired.
- Safe writes: every write goes to a temporary file next to the target and is
  renamed over it, so a tablet that loses power keeps the last good version.
- `BackupArchive`: backup to zip and restore from zip, with the zip slip attack
  refused and counted.
- `LibraryIndex`: a list of what is in the folder, thrown away and rebuilt from
  the folder on demand. Written as lines, so no library is needed and a
  damaged line does not lose the rest.
- `DataRepository`: the one way in. Import a book, read and write notes and
  journal entries, find a free file name, back up, restore, rebuild the index.
- `StorageBenchmark`: the 500 file measurement the plan asks for, run from the
  Dev screen.
- Settings now shows the data folder path and has Back up, Restore and Rebuild
  index. Restore asks first.
- 60 or so unit tests across paths, layout, the file store, backup and
  restore, the index and the repository. All plain JUnit, no Robolectric, so
  they run fast.
- `docs/decisions/0005-storage-layer.md`.

### What does not work yet

- **The measurement is not done.** The plan asks for 500 files through the
  Storage Access Framework before choosing where the data folder lives. That
  has to happen on the tablet. Until then the folder is
  `Android/data/<package>/files/EinkLauncher`, which needs no permission but
  which a sync program cannot reach on Android 11 and later. The decision file
  explains what would change the choice.
- **Room is not in.** The plan names Room for the index. Room needs KSP, AGP 9
  compiles Kotlin itself now, and the KSP version has to match the Kotlin
  version AGP brings. That pairing cannot be tested on this machine. The index
  works without it and keeps the same shape, so swapping it in later is a
  contained change. The app writes its Kotlin version into the log at start-up
  so the right KSP version can be picked.
- No `SafFileStore` yet. It fits behind `FileStore` when the measurement says
  it is fast enough.

### Device test list for the owner

1. Open Settings. Read the data folder path. It should start with
   `/storage/emulated/0/Android/data/io.github.foxesrcool1.einklauncher`.
2. Press "Back up". A file picker should open. Save the zip somewhere you can
   find it.
3. Copy that zip to a computer and open it. It should hold `notes/`,
   `journal/` and the rest, with readable names.
4. Press "Rebuild index". Write down how long it takes.
5. Press "Restore" and pick the zip back. It should ask first, then say how
   many files it read.
6. Open Settings, then Design demo, then the Dev tab. Press "Time 500 files".
   Send me every number. That measurement decides where the data folder lives.

---

## Step 3. Launcher shell

Model: Opus. Date: 2026-09-19. State: code complete, waiting for CI and for a
device test.

### What was built

- `HomeActivity` with the HOME, DEFAULT and LAUNCHER categories, `singleTask`
  and `stateNotNeeded`. The Home key calls `onNewIntent`, which always goes
  back to Today. Back goes to Today from a tab and does nothing on Today.
- `DemoActivity` holds the design demo, the log viewer and the dev panel. Its
  own activity, so the launcher task stays clean.
- Today screen: date and time in small tracked capitals at the top centre, the
  four large serif words, a Wi-Fi and battery line, and a way into Settings.
  The clock listens to `ACTION_TIME_TICK`, so it repaints once a minute and
  never on a timer.
- Reading, Writing and Journal are placeholder screens that say which step
  builds them.
- Apps tab: `LauncherApps` with a `PackageManager` fallback, package add and
  remove events, pins in DataStore with a limit of 8, two pages (Pinned and
  All apps), long press for pin, unpin, app info and uninstall.
- "Always available" block: every other home app, any package with "viwoods"
  in its name, and the Android settings app. Plan section 3.2: never trap the
  user.
- Settings screen: home app state and the three ways to set it, the corner
  drawing switch, the log viewer and the design demo.
- New design system parts: `EinkRow` (invert on press, long press, no ripple)
  and `OptionsDialog`.
- Tests: `HomeStringsTest`, `LauncherRouteTest`, `LauncherEntryTest` and three
  more screenshots (Today, Today without art, a placeholder tab).
- `docs/decisions/0004-launcher-shell.md`.

### What does not work yet

- The build still has not run on this machine, for the same network reason as
  Step 1. `tools/parse-check.sh` is clean. CI decides.
- The third CI run got past configuration and then sat in the build step until
  the job timed out. The workflow now installs the Android SDK packages in a
  step of its own, runs Gradle once instead of three times, reads from
  `/dev/null` so nothing can block on a console prompt, and gives every long
  step its own timeout. A hang now names the step it is in.
- The ViWoods settings app is found by looking for "viwoods" in a package name.
  That is a guess. The owner must read the real package name off the tablet
  from the log, and then this becomes an exact match.
- Step 2 has not run, so there is no `EinkDevice` and no full refresh yet.
- The "Next" line and Quick note on Today are not built. They belong to Step 9
  and Step 6.

### Device test list for the owner

1. Install the build. Open Eink Launcher from the stock launcher.
2. You should see Today: the date and time at the top centre, then Read,
   Write, Journal and Apps in large serif, then a status line.
3. Press Apps. The Pinned page should list "Always available" entries. Write
   down the exact names you see there.
4. Press each "Always available" entry in turn. Write down which ones open the
   ViWoods settings and which one opens the stock launcher.
5. Go to All apps. Turn the pages. Hold an app and press Pin. Go back to
   Pinned and check that it is there.
6. Hold a pinned app and press Unpin.
7. Go to Settings and press "Set as home". Write down exactly what happens:
   a system dialog, a settings screen, or the written steps.
8. If it worked, press the Home key from inside another app. You should land
   on Today, not on the last tab you had open.
9. Press the Back key on Today. Nothing should happen. Press Back inside a tab.
   You should land on Today.
10. Leave the tablet on Today for two minutes and watch the clock. It should
    change once a minute and leave no smear.
11. Open Settings, press Log, then "Copy to download". Send me the log file.
    It tells me which route the home app flow took and what the ViWoods
    settings package is really called.

---
## Step 1. Project scaffold and design system

Model: Opus. Date: 2026-09-19. State: code complete, waiting for CI and for a
device test.

### What was built

**Build**

- Gradle 9.6.0 wrapper, Android Gradle Plugin 9.4.0, Kotlin 2.4.20.
- `compileSdk 37`, `targetSdk 37`, `minSdk 29`.
- Two flavours: `viwoods` and `generic`. Both build the same code today. Step 2
  decides whether `viwoods` has to drop to `targetSdk 30`.
- Fixed debug keystore at `keystore/debug.keystore`, checked in on purpose, so
  every debug build installs over the last one on the tablet.
- Version catalogue at `gradle/libs.versions.toml`. Every version was checked on
  the web on 2026-09-19.
- GitHub Actions workflow: builds both flavours, runs the unit tests, records
  the screenshots, runs lint, and uploads the APKs, the screenshots and the
  reports as artifacts.

**Design system** (`app/src/main/kotlin/.../design/`)

- `EinkColors`: black, white and one grey. Nothing else.
- `EinkType`: Bodoni Moda for large words, Jost for capitals, Literata for
  body. All three SIL OFL, all listed in `ASSETS.md`.
- `EinkDimens`: 56 dp touch targets, 1 dp and 2 dp rules.
- `EinkTheme` and `Modifier.einkClickable`, which passes `indication = null` so
  no ripple can ever appear.
- Components: `EinkText`, `CapsLabel`, `HairlineDivider`, `InvertPressButton`,
  `WordMenu`, `PagedList`, `ConfirmDialog`, `BotanicalSprig`.
- The app does not depend on Compose Material at all. That removes ripples,
  elevation and animated indication at the dependency level, not by discipline.

**Logging** (`app/src/main/kotlin/.../core/log/`)

- `AppLog`: one file per day in `filesDir/logs/`, a 600 line memory buffer for
  the viewer, a background writer thread, and files older than 14 days deleted
  at start-up.
- `CrashHandler`: writes a crash file and copies it straight into
  `Download/EinkLauncher/`, then hands over to the previous handler.
- Log viewer screen with Refresh and "Copy to download".

**Getting a build onto the tablet**

- `tools/deploy.sh viwoods 8000` builds the APK, serves it on the local network
  and prints the URL.
- Debug builds have a Dev screen with the build URL and a "Get latest build"
  button. It downloads the APK and opens the system installer. Only the debug
  build asks for the internet and install permissions; the release build asks
  for neither.

**Tests**

- `PaginationTest`: 8 tests on the page maths, including an empty list, a part
  full last page and an out of range page index.
- `LogFormatTest`: 6 tests on file names, time stamps and file expiry.
- `DesignSystemScreenshotTest`: 8 Roborazzi screenshots at 480 dp by 640 dp at
  xxhdpi, which is 1440 by 1920 pixels, the panel size. One of them presses a
  button and checks the colour invert.

### What does not work yet

- **The build was not run on this machine.** The machine that wrote this step
  cannot reach `dl.google.com`, so it could not download the Android SDK or the
  Android Gradle Plugin. Every Kotlin file was parsed with the standalone
  Kotlin compiler instead (`tools/parse-check.sh`) and no syntax error and no
  unknown name inside this project was found. **CI is the first real build.**
  If CI is red, that is the first thing to fix next session.
- The screenshots have no recorded baseline yet, so CI records them and uploads
  them as an artifact. It does not compare them. Turn on comparison once the
  first set is reviewed and committed.
- `gradle/wrapper/gradle-wrapper.properties` has no `distributionSha256Sum`,
  because `services.gradle.org` could not be reached for the checksum file.
- No `EinkDevice` yet. That is Step 2.

### The owner has to decide

1. **Package id.** It is `io.github.foxesrcool1.einklauncher` today. Confirm or
   change it now. Changing it later means every tester reinstalls from scratch.
2. **Licence.** Apache-2.0 is in `LICENSE`. Confirm or change.
3. **Name.** "Eink Launcher" is the working name.

### Device test list for the owner

Do these in order and write down what happens.

1. Run `tools/deploy.sh viwoods 8000` on the dev machine. It should print a URL.
2. Open that URL in the tablet browser and download the APK.
3. Install it. Android may ask to allow installs from the browser. Allow it.
4. Open the app. You should see the Design screen: four large words, three
   controls, and a paged list with Previous and Next.
5. Press and hold "Press me". It must turn to white text on a black box at
   once, with no fade and no ripple. Let go. It must come straight back.
6. Press "Next" on the paged list a few times, then "Previous". Watch for grey
   smear or ghosting. Write down what you see.
7. Swipe left and right across the list. One swipe must move exactly one page.
8. Press "Delete". A bordered panel should appear with Keep and Delete. Press
   Keep.
9. Look at the corner drawing. Say whether the 1 dp line is too thin on the
   panel.
10. Go to the Log tab. You should see lines, starting with "Eink Launcher
    0.1.0-viwoods ... started".
11. Press "Copy to download". Open the tablet file manager and check that
    `Download/EinkLauncher/` holds a `log-...txt` file.
12. Go to the Dev tab. Check that the build URL is the one the script printed.
    Press "Get latest build". It should download and open the installer.
13. Tell me: which text is too small, which is too large, and whether the grey
    in the disabled control is readable or just dirty.
