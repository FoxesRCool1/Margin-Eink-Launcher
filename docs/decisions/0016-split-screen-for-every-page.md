# 0016. A split screen for every page, and for other apps

Date: 2026-09-22. Asked for by the owner: "Make splitscreen work with any app
or page of the launcher, not just notes." This replaces the part "The split
screen" of decision 0014, which put one note beside a book and nothing else.

## What the user sees

- **Every screen has the split icon.** Home has it in the bottom row, beside
  the quick note and Settings. Every other screen has it at the top, beside
  the icon that turns the screen. The readers keep it where it was: the PDF
  status line and the EPUB menu.
- **The second half starts on a choice of pages**: Read, Write, Journal,
  Apps, Settings, as large icons. In a reader it opens on the notes of the
  book, as before, and the choice has "Notes on this book" first.
- **Any page can stand there**, with its own way back, which leads to the
  choice. A note opened from Write in the second half opens in the second
  half. The Journal's handwriting does too.
- **Two controls end the top line of the second half**: swap the halves, and
  close. Upright the halves are one above the other, on its side one beside
  the other, the same rule as before.
- **A book chosen in the second half opens where it was tapped.** A book
  always has a screen of its own, so the reader opens, and the page that was
  beside the second half goes into the new reader's second half. When the
  main half held a book, or the Home screen, the new book takes the main
  half and the library stays beside it.
- **An app chosen in the second half is asked to open beside the page.** This
  is Android's own split screen. See below.
- **One page is never open twice.** A page that the other half already shows
  is refused, with a line that says so. Two editors of one note would each
  save their own copy, and one would be lost.

## How it is built

- `ui/split/SplitState` is the state: open or not, which side, and a short
  trail of pages for the way back. Plain Kotlin and Compose state, unit
  tested in `SplitStateTest`.
- `ui/split/SplitLayout` places the two halves. The main screen keeps its
  place in the composition whether the split is open or not and whichever
  side it is on, so nothing it holds is lost. The second half is not
  composed at all while it is closed: a screen that is never split pays
  nothing for it.
- `ui/split/SplitViewLayout` does the same for the EPUB reader, whose book
  engine is a fragment. It never moves the book view in the view tree, only
  where it is laid out, because a web view does not like being taken off
  the screen.
- `ui/split/SplitPane` shows the page. The tab screens are the same screens
  as on Home. They learn that they are in the second half from
  `LocalPane`: `ScreenScaffold` then shrinks its margins, drops the plant,
  and shows the controls of the split screen in place of the icons that turn
  and split the screen.
- `ui/split/PageOpener` is how a page opens a note, a book or an app. On a
  screen of its own it starts a new activity, as before. In the second half
  a note opens in place. So `ReadingScreen`, `JournalScreen` and
  `AppsScreen` ask `rememberPageOpener()` and do not start activities
  themselves.
- A page travels to a new activity as intent extras, `PanePageCodec`. A bad
  extra opens nothing rather than a wrong file.
- Typed notes and journal entries now also save on pause. A new activity
  reads the note as it starts, before the old one stops.

## The way back

Home gives its split screen to a note, a book or the Continue row, and lets
go of it. Back from that screen used to land on a plain Home. Now
`SplitReturn` (in `ui/split/`) holds a few values in the process. Home says
which tab was on show. The screen hands its second half back as it pauses
while closing, but only if it was started with a split screen. Home asks for
both when it resumes, and shows that tab with that page beside it.

- A page the tab shows too stays in the second half, and Home goes back to
  Home: one page is never open twice.
- A note that is gone is not restored. The second half opens on the choice.
- A split screen closed in the screen stays closed. Home still returns to the
  tab.
- The Home key forgets it: it always lands on a plain Home.

## Another app beside a page

Android 12L and later open an app beside the current one when it is started
with `FLAG_ACTIVITY_LAUNCH_ADJACENT`. The research for this decision found
the Android developers' own article saying so, and the emulator, Android 13,
does it.

What the emulator also showed: Android never puts the task of the home
screen in a half. Everything this app opens from Home lives in that task.
The first try made a split with the app in one half and nothing, black, in
the other. So the page that should stay first opens again in a task of its
own, and asks for the app from there. In the emulator that gives the Journal
in one half and the Clock app in the other, and a PDF beside the Clock app.

- **There is one such task, never a pile.** `BesideActivity` has a task
  affinity of its own and is started with `FLAG_ACTIVITY_CLEAR_TASK`, so a
  new request closes what the last one left. A tab or the choice of pages
  shows in `BesideActivity` itself. A book or a note opens its own screen in
  that task (`BesideActivity.then`), which asks for the app.
- **A book opens again to get there.** A screen cannot move from one task to
  another, so a book beside an app costs one more open of the book, at the
  place it was saved. For a PDF that is a fraction of a second.

Whether the tablet does this is up to its firmware. The maker can turn
multi-window off. ViWoods say on their blog that the AiPaper has no split
screen of its own, and built a floating notes panel instead. If the firmware
says no, Android ignores the flag and the app opens on its own screen, as it
does from the Apps page, and the log says so. Nothing breaks either way.

## Kept on purpose after the review

A review of this change found these, and they stay:

- A typed note and a journal entry now save on pause, on the main thread,
  when they hold words not yet saved. That is only in the second or so after
  typing; otherwise the autosave has already written. A lost sentence costs
  more than a short wait.
- A handwritten note in the second half waits for its save when it leaves,
  as the note beside a book always did. Without the wait, going back to the
  same note at once could read the file before the save lands, and the next
  save would then drop strokes.
- To know whether a screen is in the home screen's task, `AdjacentApps`
  asks Android once per tap on an app. It is one call to the system, and the
  tap starts an app anyway.

## What it costs

- A screen that is never split: one more icon at the top, and a `Layout`
  around the screen that measures one child.
- The tabs, the notes and the Journal are built for a whole screen. In half
  of a screen 480 dp wide some long lines end in three dots, and the page
  shows fewer rows. The screenshot tests have a picture of each case.
- A book beside a book works only as a PDF or EPUB main half with a library
  beside it. Two books side by side is not possible: each needs a screen of
  its own.
- A handwritten note in each half: the fast pen of the tablet serves the
  second half, as it served the note beside a PDF before. The vendor call
  takes one box.

## Checked in the emulator

Android 13, 1440 x 1920 at 320 dpi, with this app as the home app: the split
on Home, the choice, Write and a note typed in the second half and saved to
the file, a PDF opened from the second half landing where it was tapped with
Write carried beside it, swap, a turn with the split open, the EPUB reader
with the notes of the book, and the Clock app beside the Journal. Not
checked, because only the tablet can: e-ink repaints, the fast pen in either
half, and whether the ViWoods firmware allows Android's split screen.
