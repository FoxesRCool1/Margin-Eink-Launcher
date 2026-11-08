# 0007. ViWoods refresh control and fast pen

Date: 2026-09-20. Step 2. State: **provisional. Nothing here is proved on the
tablet yet.**

The plan calls this file `0002-viwoods-ink-and-refresh.md`. That number was
taken by the logging decision before Step 2 ran, so it is 0007.

## What this decides

How the app talks to the e-ink panel, which fast pen path it tries first,
which target SDK each flavour gets, and what happens when none of it works.

## Where the facts come from

The public notes in `jdkruzr/ViwoodsAppDev`: `README.md` and
`VIWOODS_APP_DEV.md`, read on 2026-09-20. That repository has no licence file,
so it is a reference only. No code was copied. Everything in `core/eink/` is
written from the facts in the notes: class names, method names, mode numbers
and transaction codes.

The notes contradict themselves, and it matters:

| Section | Says |
| --- | --- |
| "How Fast Ink Actually Works (SOLVED)" | Path A works on a stock tablet. Two calls: `setApplicationContext`, then `initWriting`. The app must target SDK 30. Path B was never the source of fast ink. |
| "Legacy Investigation", path 1 | Path B (AutoDraw through the binder) cannot work alone, because of a bug in the system server: it clears the native draw regions and never sets new ones. |
| "Legacy Investigation", path 2 | Path A crashes in `JNI_OnLoad` from a sideloaded app. |
| `README.md` | Path B is the way and it works. |

The "SOLVED" section is the newest and explains the older results, so it is
the best guess. It is still a guess.

## Decisions

### 1. One interface, two devices

`EinkDevice` holds refresh modes, a full refresh, fast pen on and off, pen
tool and pen width. `ViwoodsEinkDevice` reaches the vendor class by
reflection. `GenericEinkDevice` answers "not supported" to everything.
`EinkDevices.get()` picks one. The `generic` flavour never looks for the
vendor class at all.

No call throws. Every call returns an `EinkCallResult` and writes it to the
log file, because the log file is the only view of the tablet there is.

### 2. Three ways to reach a vendor call

1. The wrapper object, `android.os.enote.ENoteSetting.getInstance()`.
2. The binder service `ENoteSetting`: first through its own
   `Stub.asInterface`, then with a raw `transact` and the documented codes.
3. The shell command `service call`, which the notes say works from an app.

The first one that works wins, and the log says which one it was.

### 3. Path A first, and target SDK 30 for the `viwoods` flavour

The `viwoods` flavour targets SDK 30 by default, because the best guess says
path A needs it. The number is a Gradle property so the other case can be
tested without a code change:

```
tools/deploy.sh viwoods 8000        target SDK 30
tools/deploy.sh viwoods 8000 37     target SDK 37
```

The `generic` flavour targets the newest SDK and holds no hidden API call.

Cost of SDK 30: that flavour cannot go to the Play Store. The plan already
accepts that. Lint checks `ExpiredTargetSdkVersion` and `OldTargetApi` are
off for that reason.

### 4. A native crash may happen once, never twice

`runCatching` cannot stop a crash inside a vendor native library, and one
section of the notes says `initWriting` did exactly that. A home app that
dies on start is a tablet that cannot be used.

`FastPenGuard` writes "trying" to a small file before the risky call and
"fine" after it. If the app starts and finds "trying", the call never came
back. Since 1.4.0 (decision 0030) one such finding puts the path in doubt
and a second in a row refuses it, until someone resets it on the device test
screen. One finding alone was too strict: any process death in that short
window, an update install or a memory kill, banned the pen for ever.
Worst case now: one crash, twice.

### 5. The app always keeps its own strokes

Both fast paths only paint pixels. They save nothing. So the app records
every pen event itself in all cases, and the only thing that changes is who
paints while the pen is down:

- Fast pen off, or not there: the app paints each segment as it arrives, and
  repaints only the small box around that segment.
- Fast pen on: the tablet paints. The app stays still while the pen is down,
  waits, and then paints the real stroke.

The wait is **900 ms** after pen-up to start with. The notes say the fast
overlay clears 800 ms after pen-up. The pen test screen can change the wait
between 600 and 1600 ms, so the owner can find the value with no flicker and
no double line.

### 6. Full refresh

`fullRefresh()` sets picture mode 17 (GC) and puts the old mode back after
700 ms. Nobody has written down whether mode 17 is one repaint or a mode
that stays. Putting the old mode back by hand is right in both cases.

Changed on 2026-09-22, see decision 0018: on the tablet this did nothing,
because a mode alone paints nothing. The refresh the user asks for is now a
black flash over the whole window, in mode 17 where the mode can be read.

### 7. The fallback

If no fast path works, handwriting uses the app's own painting in picture
mode 4 (FAST) while an ink screen is open, and mode 3 (GL16) everywhere
else. If even the mode call fails, it is plain Android drawing. The ink
engine in Step 5 is built so that this is a setting, not a rewrite.

## What the owner has to test

Open Settings, then "Device test". Run the rows in order. Each one writes to
the log. Then press "Copy log to download" and send the file.

The answers that settle this file:

1. Does row 2 list any methods? If it lists none, hidden API rules block the
   vendor class and everything else will fail too.
2. Do the picture modes in row 3 change how a page turn looks?
3. Does row 4 flash the whole panel once?
4. Row 5, both baselines: how bad is the lag with no help from the tablet?
5. Row 6, path A: does the app survive? Is the ink fast? Does the toolbar
   stay clean? Which redraw wait shows no flicker and no double line?
6. Row 7, path B: same questions.
7. Do rows 6 and 7 again with the target SDK 37 build.
8. Rows 9 and 10: can the home role be asked for, and what are the real
   package names of the ViWoods settings app and the stock launcher?

## What would change this decision

- Path A crashes and path B works: make path B the default in the ink
  settings. The code for both is already there.
- Path A works at target SDK 37: raise the `viwoods` flavour and turn the two
  lint checks back on.
- Neither works: handwriting stays on the fallback. The plan, section 8,
  already says what follows: typed notes lead in version 1.
