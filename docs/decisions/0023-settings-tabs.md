# 0023. Settings is four tabs, and a row has no help line

The owner found Settings too wordy. It was a menu of six pages, every row had
one or two lines of help, and the Updates page had one row that opened
another screen.

Now Settings opens on a strip of tabs: Screen, Pen, Files, About. A tab is an
icon with a small word under it. The open tab is inverted. A tab is not a
level, so Back leaves Settings from any tab. Only Credits is a level: Back
from there goes to the About tab. On a tablet on its side the tabs stand in a
column at the left, so the rows keep the height.

- A row is an icon, a name, and at the end a switch, a value in small
  capitals, or an arrow. There is no help line. A name that needs one is the
  wrong name.
- The Pen tab shows only on a tablet with vendor pen control.
- "Set as Home App" shows only while Margin is not the home app. The check
  runs again every time the screen comes back in front, so there is no
  "Check again" row. The written steps are in a dialog.
- Flip Landscape shows only while Landscape Mode is on. Redraw Delay shows
  only while a fast pen mode is on. Design Demo shows in debug builds only.
- "Rescan Files" is gone: it built an index in memory and threw it away.
- Where the files are is one faded line under the Files tab, not a row.
- Messages above the list and in the update code are one short line each.
