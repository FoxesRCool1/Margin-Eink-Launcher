# 0027. The keyboard at Home: pan, except in a stacked split

Date: 2026-10-22. Found in the emulator QA: in a split with one half above
the other, a typed note in the lower half was about 3 lines tall with the
screen keyboard up, and its page buttons were under the keyboard.

## Why

Home is the one activity that did not resize for the keyboard. Android
panned the window instead, so the lower half slid under the keyboard. The
readers, the note editor and `BesideActivity` already resize.

Resizing Home all the time breaks it. `ScreenScaffold` picks its wide shape
from the room it has (`maxWidth > maxHeight`). An upright screen that loses
the keyboard's height turns wide, builds its content again, and the text
field loses the focus. In the emulator the Journal entry lost its keyboard at
once.

In a stacked split each half is already wider than it is tall. A shorter
window leaves it wide, so nothing changes shape.

## The rule

- Home resizes for the keyboard while the split is open with one half above
  the other. Every other time it pans, as before. `HomeActivity`, the
  `DisposableEffect` around `SplitLayout`.
- The two halves still share the room above the keyboard equally.
- Side by side (the tablet on its side) still pans. A half there is tall, and
  the keyboard takes more than half of the height, so the half would turn
  wide. That case waits for the tablet test.
