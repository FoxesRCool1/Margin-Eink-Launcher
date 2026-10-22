# 0023. A big clock, Continue, and parts of the day

Date: 2026-10-22. The owner wanted a launcher that is calm and pleasant to
use every day, in the spirit of Prose: a sheet of paper, a way back to what
you were doing, and a plan that shows up at the right time.

## What Home does now

1. **A big clock.** The time is in the display serif (Bodoni Moda) at 88 sp,
   with the date in small capitals under it. In 12 hour time, AM or PM is
   small and stands on the line of the digits. In a half of the split screen
   that is narrow (under 420 dp) or short (under 400 dp) the clock is 56 sp
   and the plant is left out. The clock still changes once a minute.
2. **A Continue row.** It is the book that was read last or the note that
   was edited last, whichever is newer. One tap opens it through the page
   opener, so the split screen keeps working. A book counts as read when the
   reader has saved its place (the annotations file exists). The percent at
   the right end is the saved progress, and is left out when it is 0. The row
   is read in the background each time Home comes back, and the old value
   stays on show until the new one arrives. With nothing to continue, the
   row is not drawn at all.
3. **The four icons stay**, in cells of at most 120 dp. Upright, everything is
   one column. On its side there are two columns: the clock, Continue and
   Next on the left, the icons two by two on the right.

## Parts of the day

A routine item may have a part of the day: Any Time (the default), Morning,
Afternoon or Evening. It is stored as an optional `part` field in
`routine.json` and left out for Any Time, so an old file loads and is written
back as it was.

Home offers the first undone item, in the user's own order, whose part of the
day has begun. Any Time always counts. Morning begins with the day (the day
boundary hour, 04:00 by default), Afternoon at 12:00, Evening at 17:00. A part
that has begun stays on offer until the item is done: a Morning item that is
still undone in the evening is still next. Nothing is ever hidden for being
late. Home reads the routine again each hour, so an Evening item turns up in
the evening without anyone touching the tablet.

The Journal row has no button for it, because a half of the split screen
leaves no room for a third icon. The part is chosen from the options of the
row, which open on a long press, with Remove. The row shows the part in small
capitals ("Evening"). Home's small line reads "Next", or "This Evening",
"This Morning", "This Afternoon".
