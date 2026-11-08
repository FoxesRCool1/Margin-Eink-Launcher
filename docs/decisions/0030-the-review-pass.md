# 0030. The review pass of 2026-11-08

Date: 2026-11-08. The owner asked for an in-depth review of the whole app:
bugs, UI and e-ink rules, and the features against the docs. Seven reviewers
read the code and the screenshots, one area each. The merged, ranked result
is `docs/REVIEW-2026-11-08.md`. The owner then asked for everything in it to
be fixed and released. This file records the decisions the fixes needed.
Release 1.4.0.

## What changed and why

1. **The reader shows its messages.** The EPUB reader set notices that only
   the Notes panel drew. "Old notes are unreadable. Nothing new is saved."
   was never seen, so highlights were lost without a word. A one-line black
   status strip now sits at the bottom of the page when no panel is open.
   A standing warning stays all session; a passing notice clears on the
   next page turn or tap.
2. **Settings go into the backup.** The README said the backup "saves
   everything"; it saved six folders. The zip now has a `settings.json` at
   its root with the DataStore values, typed (`int:`, `bool:`, `str:`,
   `set:`). Restore writes back only keys the app knows, with the right
   type. Keys whose names hold token, secret, password, uri, path or folder
   are never exported: they belong to one tablet.
3. **File names are limited in bytes, not characters.** Android allows
   255 bytes per name. The limit is 200 bytes of UTF-8, and a book keeps
   its extension: the stem is cut, not the whole name. A book id keeps its
   size suffix, so the notes file and the ink folder share one name. A long
   title lost its extension before, and the book was imported but never
   listed.
4. **A write syncs before the rename.** `flush()` on a file stream does
   nothing for a power cut. `fd.sync()` runs before the atomic rename. An
   empty note file, if one ever appears, loads as damaged and is never
   written over.
5. **A file parsed with losses is kept aside.** A bad entry in a hand-edited
   habits, log, routine or app folders file was dropped in silence and gone
   at the next save. The file is now copied to `*.unreadable.*` first, as a
   whole unreadable file already was, and the loss is logged.
6. **`FastPenGuard` bans a path after two unfinished tries in a row,** not
   one. A process death during the short "trying" window for any other
   reason (an update install, an out of memory kill) banned the pen path
   for ever. The cost: a real native crash in the vendor library happens
   twice before the path is refused. Decision 0007 section 4 is amended.
7. **The month view never shrinks a day below 56 dp.** When six weeks do not
   fit, the month shows in pages of three weeks with Previous and Next.
   Width in a 320 dp half is what seven columns allow; height holds the
   rule.
8. **Routine items can open something.** `RoutineItem.target` existed, but
   no screen set it. "Start Opens" picks Nothing, Read, Write, Journal or
   Apps. A pinned app as a target can come later.
9. **Day Boundary is a Settings row** (Midnight to 6 AM). The code was
   there since step 9 with no way to reach it.
10. **Device Test is debug only.** Try the Pen stays public. The engineering
    screen with vendor picture modes and home role tests is not for users.
11. **Journal Tab and a Journal day clash.** A day opened from Search could
    sit beside the Journal tab on the same day: two editors on one file.
    `JournalDay` now also holds the `tab:Journal` key.
12. **A renamed or moved note follows into the other half.**
    `SplitState.noteMoved` retargets the open page, as `noteDeleted` already
    closed it. Before, the editor wrote its next autosave to the old name.
13. **Word buttons stay on the Update screen.** Update, Install, Add Token
    and Allow Installs are rare, important actions where a word is clearer
    than an icon. This is a recorded exception to "a control is an icon".
14. **The text selection colour is a recorded exception** to "no alpha
    fades": the platform draws the selection under the text, and a solid
    black would hide the words.
15. **Log and release hygiene.** Logcat mirroring is debug only, crash files
    are kept to the newest five, the release workflow refuses to publish a
    debug-signed file as Latest on the main repository, the Ko-fi link is
    back to three places, and the repo-local git identity is the GitHub
    noreply address. Older commits keep the address they have; rewriting
    history moves every tag and is the owner's call.

## What was not built

The review also listed features a user would expect that no doc promised:
search inside a book, bookmarks and go to percent, share for exports and
open-with for books, a page overview for handwritten notes, reading
statistics, a journal browse list, PDF outline and text selection. They fit
the design and stay on the list in `docs/REVIEW-2026-11-08.md`, section 5.
