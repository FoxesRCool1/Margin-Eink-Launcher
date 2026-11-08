# Margin: build plan

Name: "Margin", chosen by the owner on 2026-09-22 after a search of app stores, GitHub and trademarks. Decision 0021. The working name until then was "Eink Launcher". Do not use the name "Prose". That name belongs to the concept that inspired this project.

Owner: Caleb (GitHub: FoxesRCool1)
Target device: ViWoods AiPaper Mini (8.2 inch e-ink, EMR stylus)
Plan date: 2026-09-20

---

## 1. Rules for Claude Code

Read this section at the start of each session.

1. Do one step per session. Stop at the end of the step. Do not start the next step.
2. Each step has a model tag: `Model: Opus` or `Model: Fable`. The owner sets the model before the step starts. If Opus fails the same task two times, tell the owner to switch to Fable for that task. If Fable declines a part of Step 2, tell the owner to run that part with Opus.
3. The repository has two branches, `main` and `Dev`. Work on `Dev`. Never create a new branch.
4. Keep `PROGRESS.md` current. Write what you did, what works, what does not work, and what the owner must test on the device.
5. Keep `docs/decisions/` current. Write one short file per important decision.
6. Do not trust library versions from memory. Check the current stable version on the web before you add a dependency. Ask the owner before you add a dependency that is not in this plan.
7. Check the licence of each dependency and each asset. Do not add GPL or AGPL code. See section 4.
8. You cannot see the tablet. `adb shell`, `adb install` and `logcat` do not work on this device (see section 3). Use unit tests, screenshot tests and the in-app log files. Give the owner a short device test list at the end of each step.
9. Obey the e-ink rules in section 5 in all UI code. No animations. No exceptions.
10. Write text in the app and in the docs in plain English. Do not use em dashes.

---

## 2. Product summary

An Android launcher for e-ink tablets. It replaces the home screen with a calm, text-first screen. It has four tabs:

| Tab | Purpose |
| --- | --- |
| Reading | Library of imported EPUB and PDF files. Clean reader. Highlights, typed notes, handwritten notes. |
| Writing | Simple file browser for notes. Minimal typing editor. Minimal handwriting pages. |
| Journal | One entry per day (typed or handwritten). Habit tracking with streaks. |
| Apps | A small panel of pinned apps. A plain list of all apps. |

Style: very minimal, black on white, large serif words, small caps labels, fine botanical line art in the corners. No animations. Made for e-ink first.

The owner wants to share the project online later. All code, fonts and art must be safe to publish.

---

## 3. Research facts

### 3.1 Device

| Item | Value |
| --- | --- |
| Model | ViWoods AiPaper Mini, model code SE05 |
| OS | Android 13 (API 33). Google Play is available. |
| Screen | 8.2 inch E Ink Carta 1000, 1440 x 1920, 292 PPI, 16 grey levels, front light |
| SoC / RAM | MediaTek octa-core 2.0 GHz (A73 + A53), 4 GB RAM, 128 GB storage |
| Input | Touch + Wacom EMR stylus with eraser end. Capacitive keys: Back, Home, AI. |
| Audio | Microphones only. No speakers. |
| Rotation | Reviews disagree about a G-sensor. The app does not follow a sensor. Changed by the owner on 2026-09-20: a small icon on every screen turns the screen by hand. Decision 0014. |

### 3.2 Launcher facts

- Third-party launchers install and run on ViWoods tablets.
- The stock launcher (WiskyLauncher) hides the standard Android Settings app. It is not easy to set a new default launcher. One known method: install the DevCheck app, open its Apps tab, select the launcher, tap Manage, then set it as the Home app in the standard app info screen.
- Try `RoleManager.ROLE_HOME` first. Fall back to `Settings.ACTION_HOME_SETTINGS`. Then fall back to written instructions.
- The stock launcher has no notification drawer. ViWoods has its own settings app (front light, refresh mode, Wi-Fi). The user must always be able to open ViWoods settings and the stock launcher from our Apps tab. Never trap the user.

### 3.3 Developer access facts (important)

Source: the public repo `jdkruzr/ViwoodsAppDev` (README and `VIWOODS_APP_DEV.md`). Read both files in Step 2.

- `adb shell` is disabled (`error: not support command`). `adb install` fails. `adb pull` fails. `adb forward` works. Logcat is not accessible.
- Install builds by sideload: copy or download the APK on the tablet, then open it.
- Forum reports say ViWoods support can give an ADB authorization tool or command to developers on request. The owner will ask for it (Step 0). Do not depend on it.
- Conclusion: the app needs its own file logger, its own crash log and an in-app log viewer from Step 1.

### 3.4 Stylus and e-ink display facts

- There is no public ViWoods SDK. ViWoods speeds up pen input only for its own apps and for a fixed whitelist of apps (OneNote, Keep, Xodo and others). A new app is not on that whitelist, so normal Canvas drawing will lag.
- The repo above documents hidden APIs in `android.os.enote.ENoteSetting`, reached by reflection:
  - Display modes through `setPictureMode(int)`: AUTO 0, MIXED/DU 1, BROWSE/A2 2, GL16 3 (default, reading), FAST 4 (pen), GC 17 (full refresh, clears ghosting).
  - Fast pen path A: `setApplicationContext(ctx)` then `initWriting()`. The notes say this works on stock devices when the app has `targetSdkVersion 30`.
  - Fast pen path B: AutoDraw through Binder (`setT1000AutoDrawEnable`, `setAllRegionUnAutoDraw(false)`, `addAutoDrawRect`, tool type 2 = pen and 4 = eraser). The fast overlay clears about 800 ms after pen-up. The app must then draw the final strokes itself about 900 ms after pen-up.
- Warning: that document contradicts itself. One section says path A is solved. An older section says it crashes in a third-party app. Test both on the real device in Step 2. Treat all of it as unverified.
- That repo has no licence file. Use it as reference only. Do not copy its code. Write our own code from the documented facts. Credit the repo in our README.
- Jetpack Ink (`androidx.ink`) has a stable 1.0.0 release. It gives stroke capture, brushes, geometry (eraser hit tests) and stroke storage. Use it for the stroke model and as the fallback renderer on other devices.

### 3.5 Inspiration and licence facts

- The inspiration is "Prose: The distraction-free, e-ink laptop that should exist" by Micah Daigle. Its concepts and images are under CC BY-SA 4.0.
- Do not copy or trace those images. Do not reuse its layouts one to one. Make original layouts and original art. Credit the article as inspiration in the README.
- One mock-up shows a copyrighted short story. Use only public domain books (Standard Ebooks or Project Gutenberg) in our screenshots and sample data.

---

## 4. Technical decisions

| Topic | Decision |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose with all animation turned off. The ink surface is a classic `View` or `SurfaceView` inside `AndroidView`. |
| Build | Gradle wrapper + Android command-line tools. No Android Studio needed. Dev machine is Arch Linux (Omarchy) with VS Code. |
| SDK levels | `minSdk 29`. `compileSdk` = latest stable. |
| Build flavours | `viwoods`: `targetSdk 30` if Step 2 proves that the fast pen path needs it. `generic`: latest `targetSdk`, Jetpack Ink rendering only. |
| Package id | Suggestion: `io.github.foxesrcool1.margin`. The owner confirms in Step 1. |
| Device layer | Interface `EinkDevice` (refresh modes, full refresh, fast pen on/off, pen tool, pen width). Implementations: `ViwoodsEinkDevice` (reflection, fully guarded with try/catch) and `GenericEinkDevice` (no-op). This keeps the app useful on Boox and other tablets later. |
| EPUB | Readium Kotlin Toolkit 3.x (BSD-3). Paginated mode. Page turns with `animated = false`. Highlights through its Decorator API. |
| PDF | Own paged view. Render one page to a bitmap with PdfiumAndroid (through the Readium PDFium adapter or direct) or with the platform `PdfRenderer`. Choose in Step 8. Do not use MuPDF (AGPL). |
| Ink | `androidx.ink` for strokes, geometry and storage. Own page model on top. Note: Jetpack Ink is used only in the debug build (`debugImplementation` in `app/build.gradle.kts`). |
| Data | Plain files are the source of truth, in one user-visible folder. Note: Room is not used; the app keeps plain files plus an in-memory index that it can rebuild from the folder. If Storage Access Framework is too slow in Step 4, fall back to app-private files plus an export and backup command. |
| DI | Keep it simple. Manual DI or Koin. No Hilt unless needed. |
| Tests | JUnit for logic. Roborazzi (Robolectric) screenshot tests at 1440 x 1920 in greyscale, so Claude Code can look at the PNG output. |
| CI | GitHub Actions: build both flavours, unit tests, screenshot tests, lint. |
| Licence | Default: Apache-2.0. The owner confirms in Step 1. No GPL or AGPL dependencies. |
| Distribution | GitHub Releases first. F-Droid later. Play Store only for the `generic` flavour because of the `targetSdk` rule. |

Data folder layout (draft, finalize in Step 4):

```
Margin/
  books/          imported EPUB and PDF copies
  annotations/    <book-id>.json, <book-id>/page-0001.strokes
  notes/          user folders, *.md (typed), *.inknote (handwritten)
  journal/        YYYY/YYYY-MM-DD.md, YYYY/YYYY-MM-DD.inknote
  habits/         habits.json, log.csv
  exports/
  logs/
```

`.inknote` draft: a zip file with `meta.json` (format version, page size, template), `pages/0001.strokes` (Jetpack Ink storage format) and `pages/0001.png` (preview).

---

## 5. Design system (original work)

### E-ink rules (mandatory)

1. No animations. No transitions, no ripples, no crossfades, no animated scroll, no overscroll glow, no progress spinners. Navigation uses `EnterTransition.None` and `ExitTransition.None`.
2. Paginate, do not scroll. Lists show one page of rows with Previous and Next controls. A swipe means one page turn.
3. Pure black `#000000` on pure white `#FFFFFF`. Do not use a cream or beige background. On e-ink a tint becomes dirty grey dither. The panel is already paper coloured.
4. Use grey only for large inactive text. Test it on the device. If it looks bad, use a lighter weight or an outline instead.
5. Pressed state = instant colour invert. No shadows. No elevation. No gradients. Lines are 1 dp or 2 dp.
6. Touch targets are 56 dp or more. Keep space between targets for finger and pen.
7. Change as few pixels as possible per update. Update the clock once per minute only.
8. Ask for a full refresh (GC mode) through `EinkDevice` after a big screen change, if Step 2 proves that it works. Make it a setting. Changed 2026-09-22 after the tablet test: the mode alone painted nothing, so a full refresh is a black flash over the window through `ScreenRefresh`, in GC mode where the mode can be read. Decision 0018.
9. Dark theme is optional and comes later. Large black areas ghost more.

### Look

Changed by the owner on 2026-09-20, after the first build: icons in place of words, round corners, "Home" in place of "Today". Decision 0013. The lines below say what holds now.

- Home screen: date and time at the top centre in small tracked capitals. Below that, four large line icons: Read, Write, Journal, Apps. A lot of white space.
- One fine botanical line drawing in a corner per main screen. The user can turn it off. Save the art as 1-bit or clean vector so it needs no dithering.
- Art sources: public domain botanical engravings (for example Biodiversity Heritage Library scans from before 1900) or original vector drawings. Record each file in `ASSETS.md` with source and licence.
- Fonts, all SIL OFL. Verify each licence:
  - Display serif for large words: Bodoni Moda or Playfair Display. Use only at large sizes. Thin hairlines break at small sizes on e-ink.
  - Labels: Jost, capitals, wide tracking.
  - Reading and writing body: Literata. Second option: Source Serif 4.
- Icons: Lucide line icons (ISC licence) for every control, the app icon and the plants, all with a 2 dp line. Words stay where a picture is not safe: the answer to a confirm question, values, and Settings rows.
- Shapes: no square corners. Controls are fully round, panels have round corners. Still no shadows and no gradients.
- Apps tab: app names as text. No colour icons by default. Optional monochrome icons later.

---

## 6. Scope

### Version 1

Home
- Home screen: date, time, four tabs, battery and Wi-Fi status line, settings entry. The Android status bar is hidden.
- Optional "Next" line: the next routine item for the day (for example Journal) with a Start control.
- Quick note: one tap from Home opens a new note.
- Landscape: a small icon on every screen turns the screen. Added by the owner on 2026-09-20.
- Split screen: a note beside the book, in both readers. Added by the owner on 2026-09-20. Widened by the owner on 2026-09-22: any page of the launcher beside any screen, and another app beside a page where the tablet allows Android's split screen. Decision 0016.

Reading
- Import EPUB and PDF with the system file picker. The app copies the file into `books/`.
- Library: paginated list with title, author, progress. Sort by recent or title.
- EPUB: paginated, font choice, size, margins, line spacing, justify on or off. Tap zones and swipe for page turn. Position is saved.
- EPUB notes: select text to highlight. Add a typed note or a handwritten note card to a highlight. Handwriting does not go directly on reflowed text, because the text moves when the font size changes.
- PDF: one page per screen. Fixed zoom steps (fit page, fit width, 150 %, 200 %). Crop margins option (important on an 8.2 inch screen). Pen and highlighter draw directly on the page. Strokes are saved in a sidecar file in page coordinates. The PDF file is not changed.
- Annotation list per book. Export to Markdown. Export an annotated PDF page to PNG.
- Reading timer and a daily reading goal with a thin progress line.

Writing
- File browser: folders, new, rename, move, delete with confirm. Paginated.
- Typed note: plain Markdown text, autosave, word count, works with a Bluetooth keyboard (Ctrl+N new note, Esc back).
- Handwritten note: pages with blank, lined or dot grid template. Pen, highlighter, stroke eraser, stylus eraser end, undo, redo, add page, delete page. Palm rejection: when the pen is near, ignore finger touch on the canvas.
- Export to PDF and PNG.

Journal
- One entry per day. Typed or handwritten. Month view shows which days have entries.
- Habits: a short list of daily habits. One tap marks a habit done. Streak count. A row of dots shows the last 14 days.
- Routine: an ordered list for the day (for example Journal, Plan my day, Read 30 minutes). Each item opens a screen in this app or a pinned app.

Apps
- Up to 8 pinned apps shown as text.
- Folders of apps, made by the user. Added by the owner on 2026-09-20. Decision 0015.
- All apps: paginated A to Z text list. Long press: pin, unpin, app info, uninstall.
- Fixed entries: ViWoods settings, stock launcher, Android settings (if reachable).

Settings
- Set as default launcher (with help text). Data folder. Backup to zip and restore. Botanical art on or off. Refresh behaviour. Log viewer. About and licences.

### Later (not in version 1)

- Handwriting to text (ML Kit Digital Ink, works offline after the model download).
- Text selection highlights in PDF.
- Lasso select and move in the ink editor.
- Focus mode: hide the Apps tab for a set time. A three position connection control like the Prose slider. Note: an app on Android 10+ cannot switch Wi-Fi itself. It can only open the system panel.
- Dark theme. Boox device layer. Sync helpers (Syncthing works already, because the data is plain files).
- Simple silent meditation timer (the tablet has no speaker).

### Out of scope

RSS, podcasts, email, cloud accounts, AI features, widgets, wallpapers, notification panel.

---

## 7. Steps

Ten build steps. Steps 2, 5 and 8 use Fable. All other steps use Opus.

### Step 0. Owner preparation
Model: none (manual)

- Create the GitHub repo. Add this file as `plan.md`.
- Install on the dev machine: JDK 17 or newer, Android command-line tools, platform-tools.
- Email ViWoods support. Ask for the ADB authorization tool or command for developers for the AiPaper Mini.
- Install DevCheck on the tablet. It is the fallback method to set the default launcher.
- Allow "install unknown apps" for the tablet browser or file manager.

### Step 1. Project scaffold and design system
Model: Opus

- Check the toolchain on the dev machine. Give the owner the exact missing install commands for Arch Linux. Note: on this machine `python3` on PATH is a PlatformIO venv. Scripts must call `/usr/bin/python3`.
- Create the Gradle project: Kotlin, Compose, two flavours, stable debug keystore so each build installs over the last one.
- Add `CLAUDE.md` (short project rules taken from sections 1 and 5), `PROGRESS.md`, `ASSETS.md`, `docs/decisions/`, licence file, `.gitignore`, GitHub Actions workflow.
- Build the design system: theme, type scale, fonts, no-animation defaults, `PagedList`, `WordMenu`, `CapsLabel`, `HairlineDivider`, `InvertPressButton`, confirm dialog. Add Roborazzi screenshot tests for each component.
- Add the file logger, crash handler and in-app log viewer. Logs go to `logs/` and to `Download/Margin/`.
- Add `tools/deploy.sh`: build the debug APK, serve it on the LAN with `/usr/bin/python3 -m http.server`, print the URL.
- Add a debug-only dev screen: "Get latest build" downloads the APK from the dev machine URL and starts the installer.

Done when: CI is green. The owner installs the APK on the tablet from the URL. A demo screen shows all components. The log viewer shows entries.

### Step 2. Device spike: refresh control and fast pen
Model: Fable

- Read `README.md` and `VIWOODS_APP_DEV.md` in `jdkruzr/ViwoodsAppDev`. Do not copy code.
- Define `EinkDevice`. Write `GenericEinkDevice` and `ViwoodsEinkDevice`. All reflection calls are guarded. A failure must never crash the app. Each call writes its result to the log file.
- Build a debug test screen with these tests: read device info (display metrics, density, wave version), set each picture mode, force a full refresh, fast pen path A (`initWriting`), fast pen path B (AutoDraw with rects), eraser tool type, pen width range, exclude a toolbar area from fast drawing.
- Build a plain Jetpack Ink canvas as the baseline for latency comparison.
- Test `targetSdk 30` against the latest `targetSdk` for path A. Test `RoleManager.ROLE_HOME`. Test if ViWoods settings and the stock launcher can be started by intent.
- The owner runs the tests on the tablet and sends back the log files. Repeat until the results are clear.
- Write `docs/decisions/0002-viwoods-ink-and-refresh.md`: what works, what does not work, the chosen pen path, the chosen `targetSdk` per flavour, timing values for the final stroke redraw.

Done when: the decision file exists. A test canvas on the tablet shows pen strokes with low lag, or the file documents clearly that only the fallback is possible.

### Step 3. Launcher shell
Model: Opus

- Home activity with `HOME` and `DEFAULT` categories, `singleTask`. Back does nothing on Today. Home key always returns to Today. The launcher process stays light and starts fast.
- Reader and editors run in their own activities, so the Home key and Recents behave correctly.
- Today screen and the four tab screens (empty states for now). Clock updates on `ACTION_TIME_TICK`.
- Apps tab: `LauncherApps` API, `<queries>` for `MAIN`/`LAUNCHER`, package add and remove events, pins saved in DataStore, fixed escape entries.
- "Set as default launcher" flow with the three fallbacks from section 3.2.
- Settings screen skeleton.

Done when: the app works as the default launcher on the tablet for one day without a trap or crash. Screenshot tests cover Today and Apps.

### Step 4. Storage layer
Model: Opus

- Finalize the folder layout. Implement the repository layer over plain files. Implement the Room index with a full rebuild command.
- Measure Storage Access Framework speed with 500 files. Choose SAF tree or app-private storage with export. Write the decision file.
- File import (copy into `books/`), safe writes (write temp file, then rename), backup to zip, restore from zip.
- Unit tests for all file operations and for index rebuild.

Done when: tests pass. Backup and restore work on the tablet.

### Step 5. Ink engine
Model: Fable

- Reusable `InkCanvas` component, used later by Writing, Journal and Reader.
- Input: stylus only draws. Finger does not draw. Pressure, stylus eraser end, side button if reported. Palm rejection.
- Fast path: use the Step 2 result. Hand over from the device fast overlay to our final render without flicker or double lines. Match the final stroke width to the fast stroke width.
- Tools: pen (3 widths), highlighter (grey, drawn under pen strokes), stroke eraser. Undo and redo.
- Page model, templates (blank, lined, dot grid), `.inknote` read and write with a format version, PNG preview, export to PNG and PDF.
- Performance: a page with 2 000 strokes loads in under 1 second on the tablet. Render finished strokes into a cached bitmap. Autosave without blocking input.
- Unit tests for the file format, eraser geometry and undo stack.

Done when: the owner writes one full page on the tablet with no lost strokes, low lag and a correct reload.

### Step 6. Writing tab
Model: Opus

- File browser on the Step 4 storage. Typed Markdown editor. Handwritten notebook on `InkCanvas` with multi-page navigation.
- Bluetooth keyboard support. Autosave. Word count. Export.
- Quick note from Today.

Done when: the owner creates, edits, moves and deletes typed and handwritten notes on the tablet. The files appear in the data folder in the documented formats.

### Step 7. Reading tab, part 1: library and EPUB
Model: Opus

- Library screen. Import flow. Cover is optional and drawn in greyscale.
- EPUB reader with Readium: paginated, no page animation, typography settings, tap zones, table of contents, position save and restore.
- Text selection menu in our style: Highlight, Note. Highlights through the Decorator API (underline or grey block, test both on e-ink). Typed notes. Data saved to `annotations/<book-id>.json` with Readium locators.
- Reading timer and daily goal line.
- Optional full refresh every N pages through `EinkDevice`.

Done when: the owner reads a public domain EPUB on the tablet, adds highlights and typed notes, closes the app and finds all of it again.

### Step 8. Reading tab, part 2: PDF and handwritten annotations
Model: Fable

- Choose the PDF renderer (section 4). Write the decision file. Watch memory: 4 GB RAM, large scanned PDFs. Render at screen resolution, cache only the pages near the current page.
- Paged PDF view with fixed zoom steps, crop margins and page jump.
- `InkCanvas` overlay on PDF pages. Strokes are stored in PDF page coordinates, so they stay correct at each zoom step and crop setting.
- Handwritten note cards attached to EPUB highlights.
- Annotation list per book with jump to location. Export to Markdown. Export annotated page to PNG.

Done when: the owner marks up a PDF on the tablet, changes zoom, reopens the book and sees all strokes in the correct position.

### Step 9. Journal tab
Model: Opus

- Daily entry, typed or handwritten. Month view. Previous and next day.
- Habits: add, edit, archive, daily check, streak logic with unit tests (time zone change, missed day, day boundary at 04:00 as a setting).
- Routine list and the "Next" line on Today.

Done when: the owner uses the Journal for three days. Streak counts are correct.

### Step 10. E-ink audit, hardening and release
Model: Opus

- Audit each screen against section 5. Remove all remaining animation. Check ghosting and refresh behaviour with the owner.
- Review the crash logs from daily use. Fix the top problems. Check cold start time of the launcher and memory use with a large PDF.
- Make sure the `generic` flavour runs on a normal Android phone or emulator without ViWoods APIs.
- Write the README: what it is, screenshots (public domain content only), install guide for ViWoods tablets (sideload, set default launcher, return to the stock launcher), build guide, credits (Micah Daigle article, `jdkruzr/ViwoodsAppDev`, Readium, fonts, art sources), licence.
- Release signing key outside the repo. GitHub Actions release workflow. First GitHub Release.

Done when: a new user can install the release on a ViWoods tablet with only the README.

---

## 8. Main risks

| Risk | Effect | Response |
| --- | --- | --- |
| The hidden ViWoods pen APIs do not work from our app, or a firmware update breaks them | Handwriting lags | Step 2 finds this early. The fallback is Jetpack Ink in FAST picture mode. If the lag is too high, handwriting becomes a smaller part of version 1 and typed notes lead. |
| `targetSdk 30` is needed for the fast pen | No Play Store for that flavour | Two flavours. GitHub Releases and F-Droid for the ViWoods flavour. |
| No ADB and no logcat | Slow debug loop | File logger, log viewer, one-tap update from the dev machine, screenshot tests, ADB tool from ViWoods support if they give it. |
| Default launcher cannot be set from the app | Poor first-run experience | Three fallbacks and clear instructions with DevCheck. |
| User gets trapped without system settings | Tablet hard to use | Fixed escape entries in Apps. Test in Step 3. |
| Readium WebView is slow or ghosts on e-ink | Poor reading experience | No animation, paginated mode, optional full refresh every N pages. Judge in Step 7 before building more on it. |
| Licence problem blocks publication | Cannot share | Rules in sections 3.5 and 4. `ASSETS.md`. Licence check for each dependency. |

---

## 9. Sources

- Inspiration article: https://medium.com/this-should-exist/prose-a-distraction-free-e-ink-laptop-for-thinkers-writers-4182a62d63b2
- ViWoods specifications: https://viwoods-eu.com/pages/aipaper-mini-specification
- ViWoods developer notes and fast pen research: https://github.com/jdkruzr/ViwoodsAppDev
- Default launcher workaround: https://www.splitbrain.org/blog/2025-09/22-viwoods_ai_paper_review
- ViWoods ADB reports: https://www.mobileread.com/forums/showthread.php?t=372886
- Jetpack Ink releases: https://developer.android.com/jetpack/androidx/releases/ink
- Readium Kotlin Toolkit: https://github.com/readium/kotlin-toolkit
