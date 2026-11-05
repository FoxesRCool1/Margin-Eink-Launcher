# 0029. Search, Look Up, the document before, and fewer grey controls

Date: 2026-11-05. The owner asked for the features that came out of a web
search about e-ink tablet software, and for a pass that makes the app
simpler. The research is in `docs/research/2026-11-eink-tablet-software.md`.

## What changed and why

1. **Search.** Users of other e-ink tablets name weak organisation and no
   search as a top complaint. Home has a Search button. It searches typed
   notes, typed journal entries, highlights with their notes, and the names
   of handwritten notes. There is no index: the files are read on each
   search, on `AppDispatchers.io`, with a budget of 800 ms
   (`SpeedWatch.Budget.SEARCH`). The search runs on the button, not on each
   key, so the screen changes once per search. An index can come if the
   log shows `Slow:` lines for it.
2. **Look Up** in the EPUB reader's selection bar. It sends the words to an
   installed app that takes `ACTION_PROCESS_TEXT`. Margin has no dictionary
   of its own and no network for one. With several such apps the user picks
   one in an `EinkDialog`; the last one chosen comes first next time. The
   PDF reader has no text selection, so it has no Look Up.
3. **"Before" on Home.** Under Continue, a smaller row shows the document
   opened before it, so the user can jump back. It comes from file dates,
   like Continue, with no new stored state. A split half leaves it out.
4. **Hidden, not grey.** E-ink rule 4 keeps grey for large inactive text.
   "Up one folder" at the top folder, Export with nothing to export, and
   information lines shown as greyed options are now hidden or plain text.
   "Pin" when Pinned is full says so instead of being grey. Paging buttons
   and busy states stay as they were.
5. **Words.** "Delete" everywhere, shorter confirm texts ("This cannot be
   undone."), one pattern for empty pages ("No ... yet"), one form for a
   book that will not open, Title Case in the reader's text settings.
6. **A typed note's name is optional,** like a handwritten one. An empty
   name gets the default name (`core/notes/NoteNames.kt`).
7. **Where a hit opens.** A highlight opens its book at that highlight
   (`EpubReaderActivity.EXTRA_HIGHLIGHT_ID`, first start only). A journal
   entry opens that day (`JournalScreen(initialDate)`, `PanePage.JournalDay`
   in a split half). Back from that day returns to the same results: the
   words and hits live in `SearchState`, held by the launcher and cleared
   when Home comes back.
8. **Swipes on Home.** Up opens Apps, down opens Search, one screen change
   per swipe like a page turn (80 dp). A split half has no swipes.
9. **Look Up finds more dictionaries.** Besides `ACTION_PROCESS_TEXT`, it
   finds apps that take the ColorDict intent, which many offline
   dictionaries use. One row per app (`core/reading/DictionaryApps.kt`).
10. **Readable default names.** An untitled note is "5 November 2026
    17.33" (`timestampedNoteName`). A name that is taken gets " 2".

## What was not done

- Wi-Fi book transfer: a second network place, against decision 0012.
