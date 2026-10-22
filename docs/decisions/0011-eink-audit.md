# 0011. The e-ink audit

Date: 2026-09-20. Step 10. Every screen was checked against plan section 5.
The tablet was not at hand, so this is an audit of the code and of the
screenshots at panel size. Ghosting can only be judged on the glass, and
that part is on the owner's test list.

## What was found and fixed

| Rule | What was wrong | What was done |
| --- | --- | --- |
| 1, no animations | **The text cursor blinked**, in every text field, twice a second, for as long as the field had the focus. | `EinkTextField`: the platform cursor is invisible and a still black line is drawn where it stands. All four fields use it. |
| 1, no animations | **Dialogs faded in and out**, and put a grey wash over the screen behind them. On e-ink the wash is a full repaint of dithered grey, twice. | `EinkDialog`, and `Theme.EinkLauncher.Dialog` in `themes.xml`: no window animation, no dim. Every dialog goes through it. |
| 1, no animations | The EPUB engine animates page turns and lets a page follow the finger. | Fixed in step 7: `animated = false` everywhere, and `SwipeInterceptLayout`. |
| 1, no animations | The system text selection toolbar floats and fades. | Fixed in step 7: an empty action mode, and a plain bar of our own. |
| 2, paginate | Settings had grown past one screen. It does not scroll, so the bottom fell off, **and with it the control that leads back to Today**. | Settings is six short pages. The page area is clipped, so no page can ever push the footer off the screen again. |
| 2, paginate | The Text panel of the reader covered the whole page, so the effect of a change could not be seen. | Two short pages that cover less than half of the book page. |
| 6, touch targets | On Today the bottom row did not fit: "Quick note" was cut in half and "Settings" was cut short. The corner drawing sat under both. | The four words take the room that is left and step down one size when the Next line is showing. The controls keep 56 dp. The drawing moved to the top corner. |
| 6, touch targets | Toolbars with seven controls did not fit 480 dp. | A compact text size for crowded toolbars. The target itself stays 56 dp tall. |

## What was checked and was already right

- No `LazyColumn`, no `verticalScroll`, no `animate*`, no `Crossfade`, no
  `AnimatedVisibility` anywhere in the app. Lists go through `PagedList`.
- No Compose Material, so no ripple, no elevation and no animated indication
  can exist.
- Three colours in the whole app: black, white and one grey. The grey is used
  for disabled controls, secondary labels, hairlines and page templates.
- Activity and task transitions are off in the theme.
- The clock repaints on `ACTION_TIME_TICK`, once a minute.
- The ink canvas repaints only the box around what changed.

## What is left, and why

1. **A long typed note scrolls inside its field.** Done. `EinkTextField`
   keeps a field of many lines on whole pages. The page is the height of the
   box in whole lines, less two lines, so the last lines of one page are the
   first lines of the next. Whenever the scroll moves off a page (the field
   following the cursor, a drag, a fling) the field puts it back on one at
   once, with no animation. When the cursor leaves the box the result is one
   jump to the page that has the cursor line near its top, not a crawl of one
   line per key. To let the last page start at the cursor, the field shows
   blank room under the text (an output transformation, the note itself is
   not changed). `TextPages` tells a screen the page and the page count, and
   turns pages with `next()` and `previous()`. The typed note editor shows
   "2 / 3" and two page buttons when the note is longer than the box. The text
   is clipped under the last whole line of the page, so no line is cut in
   half at the bottom. A drag
   on the field does not turn pages, it springs back: use the buttons. The
   Journal entry gets the snapping and no buttons, its box is short.
2. **The highlighter and the page templates are grey.** The plan asks for a
   grey highlighter. Rule 4 says grey is for large inactive text only and has
   to be tried on the panel. If it dithers badly, make the highlighter an
   outline and the template lines black hairlines. Both are one constant
   each in `InkRenderer`.
3. **The system keyboard and the system file picker** animate as they like.
   They are not this app's windows.
4. **Inside the EPUB web view** the text selection handles are the
   platform's.
5. **Screenshot comparison in CI is still off.** CI records the pictures and
   uploads them. One of them, the Storage page of Settings, shows the data
   folder path, which is a different temporary folder on every run. Compare
   can go on once that screen takes its path as a parameter.

## Hardening

- **Start-up.** `HomeActivity` writes "Home screen up N ms after the process
  started" to the log. The application class does three small things on the
  main thread (open the log, install the crash handler, read two tiny guard
  files) and everything else on a background thread.
- **Memory with a large PDF.** See decision 0010: about 11 MB a screen at
  every zoom, one page picture alive at a time.
- **The `generic` flavour** builds, passes the same unit tests, and never
  looks for a vendor class. `EinkDevices` hands it `GenericEinkDevice`.
- **Permissions.** The release build asks for `ACCESS_NETWORK_STATE`, for the
  Wi-Fi word on Today, and for nothing else. A `WAKE_LOCK` that came in with
  Readium's unused audio player is taken out in the manifest. There is no
  internet permission.
- **Crash logs from daily use** do not exist yet, because nobody has used it
  for a day. That review is still to do.
