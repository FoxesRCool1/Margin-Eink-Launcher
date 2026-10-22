# Rules for Claude Code

Read this file at the start of every session. It is the short form of
`plan.md` sections 1 and 5. `plan.md` wins if the two ever disagree.

## Working rules

1. Do one step of `plan.md` section 7 per session. Stop at the end of the step.
2. Each step has a model tag. If Opus fails the same task two times, tell the
   owner to switch to Fable for that task.
3. Keep `PROGRESS.md` current: what you did, what works, what does not work,
   and what the owner must test on the tablet.
4. Keep `docs/decisions/` current. One short file per important decision.
5. Do not trust library versions from memory. Check the current stable version
   on the web before you add or raise a dependency. Ask the owner before you
   add a dependency that is not in `plan.md`.
6. Check the licence of every dependency and every asset. No GPL. No AGPL.
   Record every asset in `ASSETS.md`.
7. You cannot see the tablet. `adb shell`, `adb install` and logcat do not work
   on this device. Use unit tests, screenshot tests and the in-app log. End
   every step with a short device test list for the owner.
8. Write plain English in the app and in the docs. Do not use em dashes.
9. The repository has two branches, `main` and `Dev`. Work on `Dev`. Never
   create a new branch.

## E-ink rules (mandatory in all UI code)

1. No animations. No transitions, no ripples, no crossfades, no animated
   scroll, no overscroll glow, no progress spinners.
2. Paginate, do not scroll. A list shows one page of rows with Previous and
   Next. A swipe means one page turn.
3. Pure black `#000000` on pure white `#FFFFFF`. No cream. No beige.
4. Grey is for large inactive text only.
5. The pressed state is an instant colour invert. No shadows, no elevation,
   no gradients. Lines are 1 dp or 2 dp. Corners are round: the owner asked
   for that. Shapes come from `EinkShapes`.
6. Touch targets are 56 dp or more, with space between them for finger and pen.
7. Change as few pixels as possible per update. The clock updates once a minute.
8. A full refresh goes through `ScreenRefresh.run(activity)`: a black flash
   over the window, in the vendor full mode where there is one. After a big
   screen change only when the user turned on "Auto Refresh". Decision 0018.
9. Dark theme is optional and comes later.

## Code rules that follow from the above

- The app does not depend on Compose Material. Material brings ripples,
  elevation and animated indication. Use `androidx.compose.foundation` and the
  components in `design/components/`.
- Every tap target goes through `Modifier.einkClickable`, which passes
  `indication = null`.
- A control is an icon, not a word: `IconPressButton`, with a label for the
  screen reader. Icons are Lucide icons, drawn by `EinkIcon`. To add one, put
  its name in `tools/lucide-icons.txt` and run `tools/lucide.py`. Words stay
  for the answer to "Delete this?", for values, and for Settings rows.
- Text goes through `EinkText` or `CapsLabel`, never `BasicText` directly.
- Lists go through `PagedList`. Do not add a `LazyColumn` to a user screen.
  Give it a `rowHeight`, so it works out the page size from the room it has.
  A fixed page size overflowed twice. Leave a row a few dp to spare: text
  that is one pixel too tall loses its last line.
- A screen uses `ScreenScaffold`, which has the way back and the icon that
  turns the screen. It lays itself out by `LocalWideScreen`, not by the
  device: half of a split screen is tall on a wide tablet.
- An activity calls `ScreenWindow.attach(this)` first in `onCreate`, and
  declares `orientation|screenSize` in the manifest like the others.
- Slow work in a screen runs on `AppDispatchers.io`, never `Dispatchers.IO`.
  The screenshot tests need that.
- Dialogs go through `EinkDialog`, never `Dialog`. The platform dialog fades
  and dims the screen behind it.
- Text fields go through `EinkTextField`, never `BasicTextField`. The platform
  cursor blinks.
- A screen that does not scroll can overflow. Add every new screen to
  `ScreensScreenshotTest`, which runs upright and on its side, and look at
  both PNG files. A control that falls off the bottom of a home app is a trap.
- Every reflection call into a vendor API is wrapped in `runCatching` and
  writes its result to `AppLog`. A vendor API must never crash the app.
- Every page works in half of a screen. The split screen can put any page in
  a half 480 dp wide or 320 dp wide. Add a split picture of a new page to
  `ScreensScreenshotTest` and look at it. See decision 0016.
- A page opens a note, a book or an app through `rememberPageOpener()`, never
  with `startActivity` itself. In the second half of the split screen a note
  opens in place, and an app opens beside the page.
- A new activity that shows a page puts its content in `SplitLayout`, gives
  the second half a `PaneHost`, calls `watchAndroidSplit`, and calls
  `AdjacentApps.takeRequest(this)` in `onResume`. Look at
  `NoteEditorActivity` for the smallest one.

## Speed rules

The owner finds the app quick and wants it to stay that way. Decision 0017.

- Nothing slow on the main thread: no disk, no network, no binder call that
  can wait. Use `AppDispatchers.io`. The few places that must do disk work
  there are listed, with the reason, in `SpeedWatch.ON_PURPOSE`.
- No `runBlocking`, no `Thread.sleep`. `SpeedRulesTest` fails the build.
- Home starts without Readium, the PDF renderer or any other large library.
  `SpeedRulesTest` checks the files Home is built from.
- A moment the user waits for has a budget in `SpeedWatch.Budget` and a
  `SpeedWatch.check` call. A new screen or a new wait gets one.
- A new screen composes nothing it does not show. A part that is hidden,
  like the second half of the split screen, is not composed until it opens.
- Read the `Slow:` and `Leak:` lines in every log the owner sends. Fix the
  cause, or raise the budget in 0017 with the reason.

## Storage rules

- Plain files are the truth. The index is a cache and must always be
  rebuildable from the folder. If a file the user copied in by hand would be
  invisible, the design is wrong.
- Screens talk to `DataRepository`, never to `FileStore` and never to a raw
  path string.
- Every path goes through `RelativePaths.normalise`. A path that leaves the
  data folder is refused, not repaired. This matters most on restore: a zip
  can name an entry `../../somewhere`.
- Every write is atomic: temporary file, then rename. `LocalFileStore.write`
  already does this, so use it rather than opening a stream yourself.
- New kinds of file get a path builder in `StorageLayout`, not a string
  somewhere in a screen.

## The source is public and the app is free

The owner decided on 2026-09-22: the app is free, open source under
Apache-2.0, and the repository is public. There is no paid version and no
plan for one. People who want to help pay for it can, at
https://ko-fi.com/foxesrcool. That link lives in three places: the README,
`.github/FUNDING.yml`, and Settings, About, "Support This App". Decision 0020.

- Nothing that identifies the owner beyond the GitHub name goes in the
  repository: no email address, no home path, no token.
- Secrets never enter the repository: no access token, no release key, no
  `local.properties`. The debug keystore is checked in on purpose, so debug
  builds from any machine install over one another.
- Anyone may build the app, so the docs are written for them too. A step
  that only the owner can do says so.
- The updater needs no token now. The token screen stays, hidden until a
  check fails, for a fork that is private.

## Network rules

- The app goes online in one place, `core/update/`, and only when the user
  presses "Check for updates". Do not add a second place, a background check
  or a check at start-up. See `docs/decisions/0012-in-app-updates.md`.
- Release builds talk https only. Plain http is for the debug LAN update.
- A web view that shows book content has its network loads blocked.
- The version lives in one line, `appVersionName` in `app/build.gradle.kts`.
  `tools/release.sh` changes it. Do not set a version code by hand.

## Layout of the code

```
app/src/main/kotlin/io/github/foxesrcool1/margin/
  core/log/      file logger, crash handler
  core/storage/  data folder, file store, backup, index
  core/apps/     the folders on the Apps tab. No Android.
  core/window/   screen orientation and the status bar, for every activity
  core/threads/  the background dispatcher the screens use
  core/settings/ the few values that live in DataStore
  core/launcher/ becoming the home app
  core/eink/     EinkDevice, the ViWoods layer by reflection, the crash guard
  core/ink/      strokes, file formats, undo, eraser geometry. No Android.
  core/reading/  annotations, reading log and timer. No Readium.
  core/journal/  saving a journal entry, the daily word goal. No Android.
  core/pdf/      screens of a page, margin search, ink sidecar files
  core/update/   the in-app update from GitHub Releases. The only network code.
  core/speed/    time budgets and StrictMode, written to the log
  ui/ink/        the ink canvas, the handwriting screen, export
  ui/split/      the split screen: the second half, its pages, other apps beside
  ui/reading/epub/  the Readium reader
  ui/reading/pdf/   the PDF reader
  design/        colours, type, sizes, shapes, theme
  design/icons/  the Lucide icons, written by tools/lucide.py
  design/components/  the design system
  ui/            screens
app/src/debug/   the dev panel, internet permission, file provider
app/src/release/ stubs for the debug only parts
app/src/test/    unit tests and Roborazzi screenshot tests
```

## Before you start

Read `docs/HANDOFF.md`. It says what is finished, what is deliberately not
finished, and which traps have already cost a day.

## Useful commands

```
./gradlew assembleViwoodsDebug          build the tablet APK
./gradlew testViwoodsDebugUnitTest      unit tests
./gradlew recordRoborazziViwoodsDebug   write the screenshots
tools/deploy.sh viwoods 8000            build and serve the APK on the LAN
tools/release.sh 0.1.1 "what changed"   tag a release. GitHub builds it, the app finds it
tools/emulator.sh                       run the app in an emulator on the dev machine
tools/lucide.py                         write the icon file again after a change to the icon list
```
