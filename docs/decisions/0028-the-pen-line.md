# 0028. The pen line: curves, steady pressure, and the stroke on top

Date: 2026-10-23. The owner asked for the writing with the pen to be the
best part of the app: pleasant, clean and fast. Prose by Micah Daigle is the
inspiration, not a model to copy.

## What changed and why

### 1. The stroke being written is drawn on top, not into the bitmap

Before, each new piece of a stroke was painted into the bitmap that holds
the page. Android draws a view with the graphics chip, and a bitmap that
changed goes to the chip whole before the next frame can show. The page
bitmap of this panel is 1440 by 1920 pixels, about 11 MB. That copy ran for
every pen frame, between the pen and the ink on the glass.

Now `onDraw` copies the unchanged bitmap and draws the open stroke on top as
lines. The stroke goes into the bitmap once, when the pen lifts, painted by
the same `InkRenderer` code, so it does not move on the glass. A cancelled
stroke is simply not drawn again.

Ceiling: the open stroke is drawn whole each frame. A scribble of many
seconds has thousands of points and costs more per frame. Handwriting is
short strokes, so this is fine. If a `Slow:` line ever points here, paint the
older part of a long stroke into the bitmap and keep only the tail on top.

### 2. The line curves through the points

A pen that moves fast leaves points a few pixels apart. Straight pieces
between them turned a small "o" into a polygon. The line now runs through
the middle of each pair of points, with the point itself as the bend
(quadratic curves). It still starts and ends exactly where the pen did. The
stored points did not change, so old notes get the smoother line too, on
the screen, in the previews and in the exports.

### 3. The pressure is steadied

The digitiser's pressure jitters from point to point, and the width of the
line jittered with it. Each new point now moves half way from the last
pressure to the new reading. This is stored with the stroke.

### 4. Smaller things for the pen hand

- The pen width is remembered: the next note, and the PDF reader, start
  with the width picked last. A new canvas after a turn of the screen keeps
  the tool and width in use. Before, it went back to the pen at Medium while
  the toolbar showed something else.
- On the last page of a note, Next is "Add a page", as in a paper notebook.
  One button less in the toolbar.
- Home has a "Quick handwritten page" button next to the quick note. It
  opens a blank page at once. No file is made until there is ink, so a page
  left blank leaves nothing behind. A narrow half of the split screen shows
  only the quick note, for room.

## What was not done

- No new library. Android's front buffer drawing (`androidx.graphics`)
  would cut the delay more, but needs a new dependency and does not help
  where the ViWoods fast pen paints the line itself.
- No motion prediction. On e-ink a guessed point that was wrong stays on the
  glass until the next refresh.
- No gestures on the page, such as two fingers to undo. A palm with two
  points of contact would undo by accident.
