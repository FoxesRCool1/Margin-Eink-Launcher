# 0026. Lucide icons are drawn like Lucide

Date: 2026-10-22. The owner asked for "nice looking Lucide icons instead of
the ones you drew".

## Why

Every icon was already a Lucide icon. But `EinkIcon` drew them with a fixed
2 dp line at every size, and the corner plants with a 1 dp line at 96 to 120
dp. A big icon with a thin line is not how Lucide looks. It looked spindly and
hand-sketched. A few shapes really were hand-drawn: the Settings switch and
the tick circles on habit and routine rows.

## The rule

- An icon is drawn the Lucide way: the line is set in the icon's own 24-unit
  grid, so it grows with the icon, like on lucide.dev.
- The line is 2 units for an icon up to 32 dp. A 24 dp control icon has a 2 dp
  line, as before. A larger icon takes Lucide's light 1.5 units: a 56 dp Home
  icon has a 3.5 dp line.
- The rule lives in one place, `defaultStrokeUnits` in `EinkIcon.kt`. A caller
  can pass `strokeUnits` to override it.
- E-ink rule 5 (lines are 1 dp or 2 dp) stays for rules, borders and dividers.
  Icons are the exception.

## What changed

- `EinkIcon` takes `strokeUnits` instead of `strokeWidth`.
- `PlantArt` is a normal Lucide icon at 1.5 units, 56 dp beside a title and 72
  dp on Home. The hairline is gone.
- The Settings switch is Lucide toggle-left (off) and toggle-right (on), 36 dp.
  The "on" knob is filled, so on shows at a glance on the panel.
- Habit and routine rows use Lucide circle (not done) and circle-check (done).
- Left as they are: the history dots under a habit (data marks), the month
  view day rings, the ink canvas, the page templates and the refresh cover.
