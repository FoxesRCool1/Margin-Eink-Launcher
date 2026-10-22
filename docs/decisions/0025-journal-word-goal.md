# 0025. A daily word goal for the journal

Date: 2026-10-22. The app's inspiration, Prose, keeps a person to 750 words
each evening. This is the small version of that.

## What it does

1. **A goal** of Off, 250, 500, 750 or 1000 words a day. It is kept in
   `SettingsStore` as `journalGoal` (`journal_goal_words`), like the reading
   goal. The default is Off. A value that is not one of the steps reads as Off.
2. **A count** in the label row of the entry, on the day view and while the
   entry is typed: "212 / 750", or "212 words" with no goal. Pressing it moves
   to the next goal, and after 1000 it goes back to Off. The count is worked
   out off the main thread. At rest it is worked out at once. While typing it
   waits for a 400 ms pause, as the note editor does.
3. **A quiet mark** when the day reaches the goal: a check in front of the
   count. Nothing moves and nothing is added.

## Why the label row

A row of its own under the entry pushed a habit off the upright page and did
not fit in half of a split screen. The label row is already 56 dp tall, so the
count is a target big enough to press. Where the column is narrower than 300
dp the word "Entry" gives way to the count. In the narrowest column (under 240
dp, the top half of an upright split) the check is smaller. The icons on the
right keep their room in every case.

## Journal fixes in the same change

- A save that fails keeps the words in the editor, shows "Could not save" and
  does not reload the day. Moving to another day or view saves first and waits
  for the write: if it fails, the user stays where they are.
- The text carries the date it belongs to, so a save never writes it to the
  neighbouring day.
- Habit and routine ticks take the date at the moment of the tap.
- Cancel puts back the text from before the edit, even when a pause saved it.
- An emptied entry deletes the day's file, so the month view stops marking it.
- Month stepping keeps the day the user chose (the 31st comes back after a
  February).
- The month view numbers are pure black and a size up. Today has a thin ring.
- A removed routine item leaves its ticks, so a new item never takes its id
  while ticks of that id are in the log. A routine list or log that cannot be
  read is not written over.
