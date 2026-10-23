# Handoff

For the next session, wherever it runs. Rewritten 2026-09-20 by the session
that built steps 2, 5, 8 and 10 and finished steps 6, 7 and 9. Section 2b was
added the same day by the session that built the in-app update, and section
2c by the session that did the owner's first list of changes. Sections 2d and
2e came with the second and third lists, on 2026-09-22, and 2f with the open
source pass the same day. Section 2g came with 1.0.0 and the release key,
and 2h with a pass over the app on 2026-09-23. Section 2i came with
the big pass of 2026-10-22, and 2j with the pen pass of 2026-10-23.

Read `CLAUDE.md` first for the rules, then this file for the state.

---

## 1. The one thing that shapes everything now

**All ten steps have code. 1.0.0 is out, signed with the release key.**

The owner used the test builds on the tablet and ended the testing phase on
2026-09-22. Section 2g has the release. The rest of this section is from
before, and still true for any part the owner did not name.

The dev machine can build now. The Android SDK is in `~/Android/Sdk`, and
every step was built, unit tested and linted locally and on GitHub Actions.
What nobody has done is hold the tablet. Every step has a device test list in
`PROGRESS.md`, and until those are run, treat the app as untested where it
touches the glass, the pen and the vendor firmware.

Run this first in any new session:

```
./gradlew assembleViwoodsDebug testViwoodsDebugUnitTest lintViwoodsDebug
```

Then look at the pictures in `app/build/outputs/roborazzi/`. They are the only
eyes there are.

---

## 2. Where the plan stands

| Step | State |
| --- | --- |
| 0. Owner preparation | Owner's job. Ask what ViWoods support said about the ADB tool. |
| 1. Scaffold and design system | Done. |
| 2. Device spike | Code done. **The device test decides everything about the pen.** Decision 0007 is provisional. |
| 3. Launcher shell | Done. |
| 4. Storage layer | Done. The 500 file measurement is still open, see 3.3. |
| 5. Ink engine | Code done. Own stroke model, not Jetpack Ink. Decision 0008 says why. |
| 6. Writing tab | Done. |
| 7. Reading, EPUB | Done, on Readium 3.4.0. **Judge it on the panel**, plan section 8. |
| 8. Reading, PDF | Code done, on the platform `PdfRenderer`. No PDF was rendered off the tablet. |
| 9. Journal | Done. |
| 10. Audit and release | Audit done from code and screenshots. Release workflow ready. **No release was made.** |

The repository has two branches, `main` and `Dev`. Work happens on `Dev`.
`main` gets what is ready. Do not make a new branch. The old step branches
and their pull requests are gone. Every commit from them is in `main` and
`Dev`.

### 2b. The app updates itself now

Settings, Updates, "Check for
updates" looks at the GitHub Releases of this repository, downloads the APK
that fits the build, and hands it to Android. `tools/release.sh 0.1.1 "what
changed"` makes a release with one command. Decision 0012 and
`docs/RELEASING.md` have the whole story. What a new session must know:

- **The whole update ran in the emulator**, from 0.1.0 to 0.1.1, against
  `tools/fake-github.py`. The owner made the first release, `v0.1.0`, on
  2026-09-20. The app has not yet taken an update from a real GitHub Release:
  that needs a second release and the token on the tablet. Check a release
  with `gh run list --workflow=release.yml --limit 1`.
- In the session that built this, the permission system stopped the session
  from pushing. The owner runs `tools/release.sh` himself. Do the work,
  commit, and hand him the command.
- **The repository is public since 2026-09-22.** The owner decided the app
  is free and open source, Apache-2.0, with a Ko-fi link for people who want
  to help. See section 2f and decision 0020. The updater needs no token on a
  public repository. The token screen stays, hidden until a check fails, for
  a private fork.
- The tablet runs the **debug** build while the app is tested. The updater
  in a debug build only takes `-debug.apk` files, and the workflow builds
  those with no release key. Moving to the release build later means an
  uninstall, and an uninstall deletes the data folder: back up first.
- The version is one line, `appVersionName` in `app/build.gradle.kts`. The
  version code comes from it. Do not set either by hand, use the script.
- The app has the internet permission now. `CLAUDE.md` has the network rules
  that keep that honest. Read them before adding anything that goes online.

### 2c. The owner's first list changed the look and the plan

On 2026-09-20 the owner used the first build and asked for eight changes. All
are done: `PROGRESS.md`, top section, and decisions 0013 to 0015. What a new
session must know:

- **The plan was changed, by the owner.** Icons in place of words, round
  corners, landscape, a split screen, "Home" in place of "Today". `plan.md`
  sections 3.1, 5 and 6 say so with the date. Do not "fix" the app back to
  words and square boxes because an older document says so.
- **Icons are Lucide, and there is no icon library.** `tools/lucide.py` writes
  `design/icons/LucideIcons.kt` from a pinned release. Add a name to
  `tools/lucide-icons.txt`, run the script. Never edit the Kotlin file.
- **Screens are built for two shapes.** `ScreenScaffold` gives `LocalWideScreen`.
  `ScreensScreenshotTest` draws every screen upright and on its side. Add a
  new screen there and look at both pictures.
- **`PagedList` wants a `rowHeight`.** See trap 8.
- **`ScreenWindow.attach(this)`** is the first line of every user activity.
- **The split screen is on every screen now.** See 2d.
- The emulator runs with no window too, which is how this session used it:
  `emulator -avd eink_tablet -no-window`, then `adb exec-out screencap -p`.
  A book can be pushed straight into the data folder with `adb push`, which
  skips the file picker.

### 2d. The owner's second list: split screen everywhere, speed, ViWoods

On 2026-09-22 the owner asked for a split screen with any app or page, for
the app to stay quick, and for what to ask ViWoods. `PROGRESS.md`, top
section, and decisions 0016 and 0017. What a new session must know:

- **`ui/split/` is the split screen.** `SplitState` is the state,
  `SplitLayout` places the halves, `SplitPane` shows a page in the second
  half. A page opens things through `rememberPageOpener()`. A page going to
  a new activity travels as intent extras, `PanePageCodec`.
- **Android never splits the home screen's task.** An app beside a page goes
  through `BesideActivity` in a task of its own. Proven in the emulator, not
  on the tablet. Without that step the emulator showed a black half.
- **One page is never open twice.** `PanePage.clashesWith`. Keep that true
  for a new page: two editors of one file lose words.
- **Speed is guarded.** `core/speed/SpeedWatch`, `SpeedRulesTest`, and the
  speed rules in `CLAUDE.md`. Read the `Slow:` lines in every log.
- **The letter for ViWoods** is `docs/viwoods-request.md`. The owner sends it.
  ViWoods keeps its fast pen list by package name, so the final package
  name matters before it goes.

### 2e. The owner's third list: refresh, names, pen page, battery

On 2026-09-22, after using 0.2.0 on the tablet. Release 0.3.0. `PROGRESS.md`,
top section, and decisions 0018 and 0019. What a new session must know:

- **The vendor picture mode alone paints nothing.** The owner pressed the old
  refresh button and nothing happened. A full refresh is now
  `ScreenRefresh.run(activity)`: a black cover over the window for 450 ms, in
  mode 17 where the mode can be read. Use it for any new refresh.
- **The owner wants plain names**: "Force Refresh", "Landscape Mode". Name a
  new setting the way a phone would, in title case, not a phrase.
- **Pen modes are called Normal, Fast 1 and Fast 2** in the app. In the code
  they are still `FAST_PEN_OFF`, `FAST_PEN_WRITING` (path A) and
  `FAST_PEN_AUTODRAW` (path B). The stored values did not change.
- **The updater kept the token in 0.3.0.** Overtaken the same day: the
  repository is public now, so no token is needed. See 2f.
- **Battery was checked.** Decision 0019 lists what is fine and what was left
  on purpose. The library keeps book titles in memory, keyed by size and date.

### 2f. The open source pass: public, free, Ko-fi, wanted features

On 2026-09-22 the owner decided against selling the app, made the repository
public, and asked for a pass over everything. `PROGRESS.md`, top section, and
decision 0020. What a new session must know:

- **The repository is public.** No secrets, no personal paths, no email in a
  committed file. `CLAUDE.md`, "The source is public", has the rules. Anyone
  may read every doc here, so write them for a stranger too.
- **Ko-fi is in three places** and nowhere else: README, `.github/FUNDING.yml`,
  Settings, About, "Support This App" (`ui/common/WebLinks.kt`). The app
  never asks for money. Do not add a reminder, a counter or a nag.
- **The updater needs no token.** The token code stays for a private fork.
  The button is hidden until a check fails with "not found".
- **Hidden apps and app search** are on the A to Z page. Hidden apps live in
  `apps/folders.json` under `"hidden"`, through `AppFolders`. The search
  rules are in `core/apps/AppSearch.kt`. The hidden toggle is the last line
  of the list, not a fifth icon: a narrow half has room for four.
- **`ScreenPageOpener` ignores a second tap** on the same thing within 1.5 s.
  If a page ever needs to open the same thing twice at once, that is where
  to look.
- **The web research** (GitHub issues of Olauncher, KISS, inkOS, mLauncher,
  E-Ink-Launcher, MobileRead threads, F-Droid) is summed up in the PROGRESS
  section. What is left on that list either needs root, is vendor locked, or
  is against the design. Gestures on Home were the one open idea.
- **The name is Margin**, decision 0021. Package `io.github.foxesrcool1.margin`,
  data folder `Margin/`, release files `margin-<tag>-<flavour>.apk`,
  repository `FoxesRCool1/Margin-Eink-Launcher`. "Eink Launcher" in an old
  doc is the working name, not a different app. The tablet needs a fresh
  install once, with a backup and restore around it.

### 2g. 1.0.0: the release key, and GitHub only

On 2026-09-22 the owner ended testing and asked for 1.0.0, a clean-up of the
debug releases, and a secure key. Decision 0022. What a new session must
know:

- **The release key exists.** The owner has it in `~/margin-release/` on the
  dev machine, and GitHub has it in four secrets. It is never in the
  repository. `docs/RELEASING.md`, "The release key".
- **A release holds signed files only**: `margin-v1.0.0-viwoods.apk`,
  `margin-v1.0.0-generic.apk`, `SHA256SUMS.txt`. No debug file, because the
  debug key is public. The workflow refuses a file that is not signed with
  the fingerprint in `.github/workflows/release.yml`.
- **Releases 0.1.0 to 0.4.0 are gone from GitHub.** Their tags stay.
- **A debug build finds no update on GitHub now.** That is expected.
- **The release build was run in the emulator before 1.0.0**: Home and every
  tab opened, no crash. It had never run anywhere before.
- **No Play Store.** GitHub Releases only. The owner registers with Google's
  developer verification for the package name and the release key.

### 2h. A pass over the app: bugs and small improvements

On 2026-09-23 the owner asked for any improvement that could be found.
`PROGRESS.md`, top section. Released as 1.0.1 before any tablet test, at
the owner's word. What a new session must know:

- **A rename of a typed note changes its first heading too**, because the
  Writing tab shows the heading as the name. A note that starts with plain
  text keeps it. `NoteText.withTitle` and `NotesRepository.rename`.
- **The shared storage of Android does not tell capitals apart.** Proven in
  the emulator: "GROCERIES.md" exists when "groceries.md" does. A rename
  that only changes capitals goes by way of a free name.
- **A one-line field can take Enter**: `EinkTextField(onSubmit = ...)`.
  `TextPromptDialog` and the app search use it.
- **Back goes one level up**: `LauncherRoute.parent`. The log and the
  updates are pages of Settings, and Home remembers which one.
- **"Sort by last read" uses the date of the annotations file**, not of the
  book. The reader never writes to the book.
- **Adding a habit with the name of an archived one brings it back.** There
  is no list of archived habits in the Journal.
- The emulator holds the debug build now, not the signed 1.0.0.

### 2i. The big pass of 2026-10-22: freezes, Settings, Home, icons

The owner said the UI "sometimes gets stuck", Settings had too many words,
and asked for something pleasant and unique to use every day, with Prose as
the inspiration. Two code audits, two runs in the emulator, and many fixes.
`PROGRESS.md`, the top sections, and decisions 0023 to 0026. What a new
session must know:

- **The freezes were waits on the main thread**, not a stuck overlay. Every
  vendor e-ink call now runs on one thread, `core/eink/EinkThread`, in order.
  A failed binder call no longer waits on a shell command with the screen
  frozen. Leaving a handwritten note waits only for the strokes. Use
  `EinkThread.run` for any new vendor call.
- **A double tap guard ate real taps.** `ScreenPageOpener` forgets the last
  page when its screen comes back. The emulator retest opened the note 10
  times out of 10.
- **Settings is four tabs** (Screen, Pen, Files, About), short rows, no help
  lines. The Pen tab exists only with vendor control. Decision 0023.
- **Home is a Today page**: a big clock, Continue (last book or note), and
  the routine item for this part of the day. Routine items have an optional
  part of the day in `routine.json`. Decision 0024.
- **The Journal has a word goal**, Off by default. Decision 0025.
- **Icons are drawn the Lucide way**: the line grows with the icon. This is
  the one exception to "lines are 1 or 2 dp". Decision 0026.
- **Long typed notes turn pages.** `EinkTextField` snaps a field of many
  lines to whole pages and hides a half-cut last line. `TextPages` gives a
  screen the page number and the buttons. Decision 0011, item 1.
- **A file that exists but cannot be read is not "missing"** any more.
  `FileStore.readTextOrThrow` and `readBytesOrThrow` throw for it, and the
  habits, routine, reading log and PDF ink refuse to write over it.
- **"Read the folder again" is gone.** It built an index and threw it away.
  The lists read the folder each time. `LibraryIndex` is still unused.
- **Rows grow with the system text size.** `PagedList` scales `rowHeight` by
  the font scale above 1.
- **The Gradle daemon went stale once** when the JDK was updated under it
  ("Failed to exec spawn helper"). `./gradlew --stop` or `--no-daemon`.
- None of this has run on the tablet. The device test lists are in
  `PROGRESS.md`.

### 2j. The pen pass of 2026-10-23

The owner asked for the pen writing to be the best part of the app, and for
a cleaner README. Released as 1.2.0 at the owner's word, before any tablet
test. `PROGRESS.md`, top section, and decision 0028. What a new session must
know:

- **The open stroke is drawn in `onDraw`, on top of the page bitmap.** It
  goes into the bitmap once, at pen-up. Do not go back to painting pieces
  into the bitmap while the pen is down: every change re-sends the whole
  bitmap to the graphics chip.
- **`InkRenderer` draws curves through the midpoints of the points.** The
  screen, the previews and both exports use it, so they still match. The
  stored points are raw. The pressure is steadied when it is recorded.
- **The pen width lives in `SettingsStore.penWidth`.** `PenWidths.clean`
  turns a stored value into one of the three widths.
- **A quick handwritten page has no file until there is ink.**
  `InkNotesRepository.freshPath` gives the name only.

---

## 3. What to do next, in order

### 3.1 The owner runs the device tests

`PROGRESS.md`, from the bottom up: step 2 first, because the pen setting for
everything else comes out of it. Then send the log file. The log holds the
vendor method list, which call route worked, render times, paint times and
the start-up time.

### 3.2 Turn what the log says into code

- The real package names of the ViWoods settings app and the stock launcher
  replace the name guess in `AppsRepository.escapeEntries`. See 4.2.
- The working fast pen path becomes the default in `SettingsStore`, and
  `PenWidths.vendorRange` gets matched by eye.
- Decision 0007 loses the word "provisional".
- If path A works at target SDK 37, raise the `viwoods` flavour and turn the
  two lint checks back on.

### 3.3 Run the storage measurement

Still open from step 4. Settings, About, "Design Demo", Dev, "Time 500 files".
`docs/decisions/0005-storage-layer.md` explains what the numbers decide.

### 3.4 Then the known gaps

1. A paged typed editor. A long note scrolls inside its field today, which
   breaks e-ink rule 2. See decision 0011.
2. Screenshot comparison in CI. Blocked by one screenshot that shows a
   temporary path. See decision 0011.
3. A read-ahead for PDF screens, only if the log says the render is slow.
4. Cover pictures in the library. The plan calls them optional.
5. Done on 2026-09-22: the first signed release, 1.0.0. See 2g.
5b. F-Droid, now that the source is public. It needs a build F-Droid can
   repeat, and the `generic` flavour is the one to offer, since the
   `viwoods` flavour targets SDK 30. F-Droid signs with its own key, so
   check Google's developer verification rules for a second key first.
   Decision 0022. Not started.
6. Done on 2026-09-22: `TextPromptDialog` takes the focus when it opens.
   Whether the ViWoods keyboard then comes by itself is for the tablet to say.
7. Done on 2026-09-22: any page in the second half, and a book opened from
   it. See 2d.
8. With a handwritten note beside a PDF, the fast pen serves the note only.
   Once the fast pen works on the tablet, check how that feels.

---

## 4. Things that are deliberately not finished

### 4.1 Room is not in the index

The plan names Room. Room needs KSP. AGP 9 compiles Kotlin itself now, and the
KSP version has to match the Kotlin version AGP brings, which could not be
discovered or tested on a machine with no Android SDK.

`LibraryIndex` holds the same shape Room would and is rebuilt from the folder
on demand, which is what the plan says the index is for. To add Room:

1. Start the app and read the log. It writes `Kotlin <version>` at start-up,
   put there for exactly this.
2. Add the KSP plugin at that Kotlin version. KSP has supported AGP built-in
   Kotlin since KSP 2.3.1.
3. Keep `IndexEntry` and `IndexSnapshot`. Replace only how they are stored.

### 4.2 The ViWoods settings app is found by guessing

`AppsRepository.escapeEntries` looks for any package whose name contains
"viwoods". The real package name is not documented anywhere I could find.

**Ask the owner to send a log file after opening the Apps tab.** The log lists
every app. Replace the name match with the exact package once it is known.

### 4.2b There is a JSON reader in this project, on purpose

`core/json/` is about 250 lines and is used by `habits.json`. Do not replace
it with kotlinx.serialization without checking first: that library needs a
compiler plugin pinned to a Kotlin version, and AGP owns the Kotlin version
now. The same reasoning kept Room out. If you do swap it, the tests in
`core/json/JsonTest.kt` should keep passing unchanged.

### 4.3 The fast pen is off by default

`SettingsStore.fastPenMode` starts at "off", so this app paints the ink and
the pen will lag. That is on purpose: one of the two fast paths is said to
crash the process in native code, and a home app should not try that on its
own. The owner turns it on after the device test. `FastPenGuard` makes sure
a crash can happen once and not twice.

### 4.3b The engine does not use Jetpack Ink

The plan names it. Decision 0008 says why not: its model needs native code,
so the unit tests the plan asks for could not run. Jetpack Ink is a debug
only dependency, for the latency baseline on the device test screen.

### 4.4 `desugar_jdk_libs` is GPL-2.0 with the Classpath Exception

Readium needs core library desugaring. The library behind that is a cut of
OpenJDK, under the same licence as the Java class library itself. The
exception means linking it does not make the app GPL. It is written up in
`licenses/DEPENDENCIES.md`. The owner should read that paragraph once and
agree, because plan section 4 says "no GPL".

---

## 5. Traps that already cost time

1. **AGP 9 compiles Kotlin itself.** Applying `org.jetbrains.kotlin.android`
   fails the build. Do not add it back.
2. **The Compose compiler plugin is still separate.** AGP refuses
   `buildFeatures { compose = true }` without
   `org.jetbrains.kotlin.plugin.compose`.
3. **`sourceSets { kotlin.srcDir(...) }` is deprecated** under built-in
   Kotlin. Use `kotlin.directories.add(...)`.
4. **`--stacktrace` in CI is useless here.** It fills the log with Gradle
   internals and hides the real message. The workflow reads the reports
   instead.
5. **This project does not depend on Compose Material.** That is on purpose:
   Material brings ripples, elevation and animated indication, and e-ink rule
   1 forbids all three. Use the components in `design/components/`. If you
   find yourself adding Material to get one widget, write the widget instead.
6. **The Kotlin compiler is set in the root build file.** AGP 9.4.0 would
   compile with Kotlin 2.2.10. Readium 3.4.0 needs 2.4, so
   `build.gradle.kts` puts the Kotlin Gradle plugin 2.4.20 on the build
   classpath. If a dependency ever says "compiled with an incompatible
   version of Kotlin", that line is where to look.
7. **A fixed page size overflows.** No screen scrolls, so a list that asks for
   more rows than fit just loses them. The Apps tab lost two apps a page that
   way and nobody saw it, because it had no screenshot test. Give `PagedList`
   a `rowHeight` and it works out the count itself.
8. **Text that is one pixel too tall loses its last line.** Compose ends the
   line before with three dots and drops the rest, with no warning. A row
   height that is the exact sum of its line heights will do this. Leave a few
   dp to spare, and look at the picture.
9. **A screen that loads from the disk races in a screenshot test.** In a
   test, the code after `withContext(Dispatchers.IO)` goes on running on the
   background thread, and writes Compose state in the middle of a layout pass
   on the main thread. Sometimes the screen never hears of it. It shows up as
   a test that passes alone and fails after another one. Screens use
   `AppDispatchers.io`, and `ScreensScreenshotTest` sets it to run in place.
10. **Robolectric runs at API 35**, set in
   `app/src/test/resources/robolectric.properties`, while the app compiles
   against API 37. Raise it when Robolectric ships an API 37 sandbox.

---

## 6. Useful commands

```
./gradlew assembleViwoodsDebug          build for the tablet
./gradlew assembleGenericDebug          build for any other Android device
./gradlew testViwoodsDebugUnitTest      unit tests, and write the screenshots
./gradlew lintViwoodsDebug              lint
./gradlew assembleViwoodsRelease        release build, signed if the key is set
tools/deploy.sh viwoods 8000            build and serve the APK on the LAN
tools/deploy.sh viwoods 8000 37         the same, with target SDK 37
tools/parse-check.sh                    syntax check with no Android SDK
tools/release.sh 0.1.1 "what changed"   tag a release, GitHub builds it
tools/emulator.sh                       the app in an emulator on the dev machine
tools/fake-github.py <apk> 0.1.1        try the in-app update with no release
```

Screenshots land in `app/build/outputs/roborazzi/`.
