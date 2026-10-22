# 0017. Keeping the app quick

Date: 2026-09-22. The owner's words after using build 0.2.0 on the tablet:
"The app overall is super quick and snappy, this is great! Make sure it stays
that way."

There is no profiler on the tablet and no logcat. So slowness has to show up
in the app's own log, the first time it happens, and new code has to be
stopped before it adds slowness at all. Three guards do that. None of them
adds a dependency.

## 1. Time budgets in the log

`core/speed/SpeedWatch` holds a budget for each moment the user waits for. A
moment over its budget writes one line that starts with `Slow:`. Nothing is
written when a moment is on time, so the log does not fill up.

| Moment | Budget | Measured from, to |
| --- | --- | --- |
| Start of Home | 1500 ms | the start of the process, to the first frame. Once per run. |
| Change of tab, or of the split screen | 250 ms | the tap, to the frame after the new page |
| Open a note, typed or handwritten | 400 ms | the read from the disk |
| Open a book, EPUB or PDF | 1500 ms | the start of the open, to the first page ready |
| Read the window settings | 60 ms | the one blocking read before the first frame |

The numbers are first guesses for an e-ink tablet. The first tablet log that
has timings in it should set them: a budget that is broken every time is
too tight, and one that is never close is too loose. Change them in
`SpeedWatch.Budget` and here.

## 2. StrictMode in debug builds

Android's StrictMode watches the main thread for disk and network work, and
the app for files that are never closed. Its report goes to the app log, one
line per place in the code and per kind, once per run: `Slow: main thread
DiskRead at <class.method>`, or `Leak:`. The tablet runs the debug build
while the app is tested, so it watches there too.

A few places do disk work on the main thread on purpose, each with its reason
written where it happens: the window settings before the first frame, the
log folder, the fast pen crash guard, and the last save of a note or an entry
when the screen goes. They are listed in `SpeedWatch.ON_PURPOSE` and write at
debug level instead. A report counts as on purpose when any frame of its stack
names one of them, because the frame StrictMode points at is usually a file
helper or a lambda deeper down.

It already paid for itself: in the emulator it caught the EPUB reader
looking up the data folder on the main thread each time a book opened. That
is fixed.

## 3. A test that fails the build

`SpeedRulesTest` reads the app's own source and fails when it finds:

- `runBlocking`, except the one window settings read;
- `Thread.sleep`;
- `Dispatchers.IO` outside `core/`: screens use `AppDispatchers.io`;
- a scrolling list or modifier, and any animation API: e-ink rules 1 and 2;
- an import of Compose Material;
- an import of Readium or the PDF renderer in the files Home starts with.

A failure names the file and the line. When a use really is on purpose, add
the file to the rule with the reason, the way the window settings read is.

## The first numbers

The emulator, Android 13 at 320 dpi, debug build, `am start -W` of Home from
a stopped app, five runs each, middle value:

| Build | Home start |
| --- | --- |
| 0.2.0, before this change | 692 ms |
| With the split screen and the speed guards | 676 ms |

The same within the noise: the split screen costs a screen that is not
split nothing it can measure. The app's own log line for the same start
said 627 to 661 ms. The tablet will be slower than the emulator, and its log
gives the real number.

## What was left out

- **A baseline profile.** The profile installer is already in the app through
  the Compose libraries, and it installs the profiles those libraries ship.
  A profile of this app's own start would need the Macrobenchmark library and
  a Gradle plugin: two new dependencies, and a rooted or emulated device to
  record on. It pays off for a release build. The tablet runs debug builds,
  which Android does not compile ahead of time anyway. Worth doing before the
  first public release, with the owner's yes.
- **A timed start in CI.** The GitHub runners have no emulator with a stable
  speed, so a number from there would fail at random.
