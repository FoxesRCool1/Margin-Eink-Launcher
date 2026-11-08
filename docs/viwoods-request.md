# What to ask ViWoods

Date: 2026-09-22. Written for the owner to send to ViWoods support, or to a
ViWoods developer contact. Part 1 is the letter. Part 2 says what each answer
would change in the app.

Before you send it: the fast pen list at ViWoods is kept by package name. The
package name in the letter is `io.github.foxesrcool1.margin`, which is
fixed by decision 0021 and will not change. An entry on ViWoods' list under any
other package name would be of no use.

---

## Part 1. The letter

Subject: Developer request: fast pen input for a third-party app on the AiPaper Mini

Hello ViWoods team,

I am building an Android home screen app for the AiPaper Mini: a calm,
paper-like launcher with a reader, notes, handwriting and a journal. It runs
well on the tablet. The one thing it cannot match is the pen. In your own
notes app the line follows the pen at once. In a third-party app the line
lags, because the app has to draw it itself.

The package name of the app is `io.github.foxesrcool1.margin`.

I have four requests, most important first.

1. **Fast pen input for this app.** Your firmware gives fast handwriting to a
   list of apps: OneNote, Google Keep, Xodo and others. Could you add this
   app to that list? Or, better for every developer, could you publish a
   supported way for an app to use the fast pen layer in its own drawing
   view, as Onyx does for Boox tablets with its public Pen SDK
   (`onyxsdk-pen`: `TouchHelper` for raw pen input and `EpdController` for
   refresh modes)?

2. **Is `ENoteSetting` supported for third-party apps?** Developers found a
   hidden API, `android.os.enote.ENoteSetting`, with `initWriting()` and an
   "AutoDraw" set of calls. Their notes say it works for an app that targets
   SDK 30, and also that it can crash in native code (`JNI_OnLoad` in
   `libpaintworker.so`) in a sideloaded app. Please tell us:
   - Is this the right way for an app to get fast ink? Is it safe to call?
   - Which target SDK does it need? Does it work at target SDK 33 or later?
   - Will it stay the same in future firmware, or change without notice?

3. **The details that make fast ink look right.** Today the app waits a fixed
   900 ms after the pen lifts, then draws its own line where the fast line
   was. Too early gives a double line, too late gives a visible jump.
   - Is there an event, or a call, that says when the fast line has been
     cleared from the screen? Or a way to keep it until the app has drawn?
   - Can the fast line follow pen pressure? What are the minimum and maximum
     pen widths, and in what unit?
   - Which tool numbers are pen and eraser, and does the eraser end of the
     pen switch the fast layer to erasing by itself?
   - Can there be more than one fast drawing area at a time, for a split
     screen with a note on each side? Do areas that should not be drawn on,
     such as a toolbar, work with `initWriting()`?
   - Please confirm the refresh modes: 0 auto, 1 DU, 2 A2, 3 GL16, 4 fast,
     17 full refresh (GC). Is 17 one full refresh, or a mode that stays on?
     Can a refresh be limited to one part of the screen?

4. **Developer access and the rest of the system.**
   - Could I have the ADB authorization tool or command for the AiPaper Mini,
     so I can read the system log while I test?
   - Does the AiPaper Mini allow Android's split screen for apps? Does
     `FLAG_ACTIVITY_LAUNCH_ADJACENT` put an app beside another one on this
     firmware, or is multi-window turned off?
   - What are the package names of the ViWoods settings app and of the stock
     launcher? A third-party home screen must always offer a way back to
     them, so the user is never trapped.
   - Is there a supported way for a third-party app to become the default
     home app, other than through the Android settings screen that the stock
     launcher hides?

I can share test builds and logs with your engineers. Thank you for your
time.

Kind regards,
Caleb

---

## Part 2. What each answer changes

| Answer | What changes in the app |
| --- | --- |
| The app is added to the fast pen list | The fast pen may work with no app code at all, as it does for OneNote. The app then turns its own fast pen off, so the two do not fight. Test with the pen test screen. |
| A public pen SDK | A new dependency, with the owner's yes. It replaces the reflection in `core/eink/ViwoodsEinkDevice.kt` behind the same `EinkDevice` interface. |
| `ENoteSetting` is supported, at target SDK N | Decision 0007 loses the word "provisional". The `viwoods` build targets N. The fast pen becomes the default in Settings. |
| An event when the fast line clears | The fixed 900 ms wait in `InkCanvasView` goes, and the app draws the instant it may. This is the biggest visible gap with ViWoods' own app. |
| Pressure on the fast layer, and the width range | `PenWidths.vendorRange` is set from the numbers, not by eye. |
| More than one fast area | Both halves of the split screen get the fast pen, not just one. |
| A refresh limited to one part of the screen | A split screen can clean one half without flashing the other. |
| The ADB tool | Logcat on the tablet: every other question gets faster to answer. |
| Split screen is allowed | Another app beside a page works on the tablet as it does in the emulator. See decision 0016. |
| The package names | `AppsRepository.escapeEntries` stops guessing. See `docs/HANDOFF.md` 4.2. |

The research behind this list, with its sources, is summed up in decision
0007 and decision 0016. The main public sources are
`github.com/jdkruzr/ViwoodsAppDev` (reference only, it has no licence), the
Onyx Pen SDK documentation in `github.com/onyx-intl/OnyxAndroidDemo`, and
ViWoods' firmware notes, which list the third-party apps they tuned the pen
for.
