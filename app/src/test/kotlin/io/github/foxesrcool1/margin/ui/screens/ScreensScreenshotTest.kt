package io.github.foxesrcool1.margin.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.foxesrcool1.margin.core.apps.AppFolder
import io.github.foxesrcool1.margin.core.apps.AppFolders
import io.github.foxesrcool1.margin.core.habits.HabitsRepository
import io.github.foxesrcool1.margin.core.ink.InkNote
import io.github.foxesrcool1.margin.core.ink.InkNotesRepository
import io.github.foxesrcool1.margin.core.ink.InkPageData
import io.github.foxesrcool1.margin.core.ink.InkTestData
import io.github.foxesrcool1.margin.core.ink.PageTemplate
import io.github.foxesrcool1.margin.core.log.LogLevel
import io.github.foxesrcool1.margin.core.log.LogLine
import io.github.foxesrcool1.margin.core.settings.SettingsStore
import io.github.foxesrcool1.margin.core.notes.NotesRepository
import io.github.foxesrcool1.margin.core.reading.BookAnnotations
import io.github.foxesrcool1.margin.core.reading.ReadingRepository
import io.github.foxesrcool1.margin.core.routine.PartOfDay
import io.github.foxesrcool1.margin.core.routine.RoutineRepository
import io.github.foxesrcool1.margin.core.storage.DataRoot
import io.github.foxesrcool1.margin.core.threads.AppDispatchers
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkTheme
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.EinkText
import io.github.foxesrcool1.margin.support.captureTo
import io.github.foxesrcool1.margin.ui.apps.AppsScreen
import io.github.foxesrcool1.margin.ui.apps.LauncherEntry
import io.github.foxesrcool1.margin.ui.home.ContinueFinder
import io.github.foxesrcool1.margin.ui.home.ContinueTarget
import io.github.foxesrcool1.margin.ui.home.HomeScreen
import io.github.foxesrcool1.margin.ui.ink.InkNoteController
import io.github.foxesrcool1.margin.ui.ink.InkNoteScreen
import io.github.foxesrcool1.margin.ui.journal.JournalScreen
import io.github.foxesrcool1.margin.ui.log.LogViewerScreen
import io.github.foxesrcool1.margin.ui.reading.ReadingScreen
import io.github.foxesrcool1.margin.ui.reading.pdf.PdfReaderScreenshotSupport
import io.github.foxesrcool1.margin.ui.settings.PEN_MODES
import io.github.foxesrcool1.margin.ui.settings.PEN_MODE_HELP
import io.github.foxesrcool1.margin.ui.settings.REDRAW_DELAYS
import io.github.foxesrcool1.margin.ui.settings.REDRAW_DELAY_HELP
import io.github.foxesrcool1.margin.ui.settings.RESTORE_HELP
import io.github.foxesrcool1.margin.ui.settings.SettingsTab
import io.github.foxesrcool1.margin.ui.settings.penModeLabel
import io.github.foxesrcool1.margin.design.components.ConfirmDialogContent
import io.github.foxesrcool1.margin.design.components.DialogOption
import io.github.foxesrcool1.margin.design.components.OptionsDialogContent
import io.github.foxesrcool1.margin.design.icons.Lucide
import io.github.foxesrcool1.margin.core.eink.EinkCallResult
import io.github.foxesrcool1.margin.core.eink.EinkDevice
import io.github.foxesrcool1.margin.core.eink.EinkDevices
import io.github.foxesrcool1.margin.core.eink.FastPenPath
import io.github.foxesrcool1.margin.core.eink.PenTool
import io.github.foxesrcool1.margin.core.eink.RefreshMode
import androidx.compose.foundation.layout.widthIn
import io.github.foxesrcool1.margin.ui.settings.SettingsScreen
import io.github.foxesrcool1.margin.ui.writing.NoteEditorScreen
import io.github.foxesrcool1.margin.ui.writing.WritingScreen
import io.github.foxesrcool1.margin.core.books.LibraryBook
import io.github.foxesrcool1.margin.ui.home.LauncherRoute
import io.github.foxesrcool1.margin.ui.ink.InkCanvasView
import io.github.foxesrcool1.margin.ui.split.DetachedPane
import io.github.foxesrcool1.margin.ui.split.PaneHost
import io.github.foxesrcool1.margin.ui.split.PanePage
import io.github.foxesrcool1.margin.ui.split.SplitLayout
import io.github.foxesrcool1.margin.ui.split.SplitPane
import io.github.foxesrcool1.margin.ui.split.SplitState
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A picture of every screen, with the tablet upright and with it on its side.
 *
 * No screen in this app scrolls, so a screen that is too tall simply loses its
 * bottom, and on a home app that is a trap. These pictures are how that is
 * caught: look at each PNG after a layout change. The two classes at the end
 * run every test here once per orientation, and the landscape pictures end in
 * `_land`.
 *
 * The screens that read the data folder get a few files to read first, and
 * they read them in place, not on a background thread, so the picture is the
 * same on every run.
 */
abstract class ScreensScreenshotBase(private val suffix: String) {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val fixedTime = LocalDateTime.of(2026, 9, 19, 8, 4)
    private val today = LocalDate.of(2026, 9, 19)

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureTo(name + suffix)
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * The tests share one process and one data folder, so each one starts from
     * an empty folder. The screens read the disk in place here, and not on a
     * background thread: see [AppDispatchers] for the race that takes away.
     */
    @Before
    fun emptyDataFolder() {
        AppDispatchers.io = Dispatchers.Unconfined
        val data = DataRoot.repository(context)
        StorageLayout.topLevelFolders.forEach { data.store.delete(it) }
        data.ensureFolders()
    }

    @After
    fun backgroundThreadsAgain() {
        AppDispatchers.io = Dispatchers.IO
    }

    // -- Home -------------------------------------------------------------------------

    /** A book that was read a little way and a note written after it: the Continue row finds the note. */
    private fun seedContinue(bookLast: Boolean = true) {
        val data = DataRoot.repository(context)
        val bookId = StorageLayout.bookIdFor("Middlemarch.pdf", 2048)
        data.store.write(StorageLayout.bookPath("Middlemarch.pdf"), ByteArray(2048))
        ReadingRepository(data).save(bookId, BookAnnotations(progress = 0.12))
        val notes = NotesRepository(data)
        val path = notes.createNote(notes.rootPath, "Notes on the second chapter")!!
        notes.write(path, "# Notes on the second chapter\n\nDorothea and the plans for the cottages.")
        // Set the order by hand: both files were made in the same second.
        val root = java.io.File(data.store.displayPath)
        java.io.File(root, path).setLastModified(1_000_000_000_000L + if (bookLast) 0L else 60_000L)
        java.io.File(root, StorageLayout.annotationsPath(bookId))
            .setLastModified(1_000_000_000_000L + if (bookLast) 60_000L else 0L)
    }

    private fun continueTarget(): ContinueTarget? = ContinueFinder(DataRoot.repository(context)).find()

    @Test
    fun home() {
        compose.setContent {
            EinkTheme {
                HomeScreen(onOpenTab = {}, onOpenSettings = {}, now = fixedTime, use24Hour = true, battery = 82, wifi = true)
            }
        }
        capture("home")
    }

    /** The whole home screen: Continue with a book and its percent, a Next line for the evening, 12 hour time. */
    @Test
    fun homeWithContinueBookAndNext() {
        seedContinue()
        val target = continueTarget()
        compose.setContent {
            EinkTheme {
                HomeScreen(
                    onOpenTab = {},
                    onOpenSettings = {},
                    nextRoutineLabel = "Write the journal",
                    nextRoutinePart = PartOfDay.Evening,
                    continueTarget = target,
                    now = LocalDateTime.of(2026, 9, 19, 20, 59),
                    use24Hour = false,
                    battery = 82,
                    wifi = true,
                )
            }
        }
        capture("home_continue_book")
    }

    /** A note to continue (no percent) with a long title, 24 hour time. */
    @Test
    fun homeWithContinueNote() {
        seedContinue(bookLast = false)
        val target = continueTarget()
        compose.setContent {
            EinkTheme {
                HomeScreen(
                    onOpenTab = {},
                    onOpenSettings = {},
                    continueTarget = target,
                    now = LocalDateTime.of(2026, 9, 19, 12, 30),
                    use24Hour = true,
                    battery = 64,
                    wifi = true,
                )
            }
        }
        capture("home_continue_note")
    }

    /** The tightest case: Continue and Next both take room from the four icons, and the names are on. */
    @Test
    fun homeWithNextLineAndNames() {
        seedContinue()
        val target = continueTarget()
        compose.setContent {
            EinkTheme {
                HomeScreen(
                    onOpenTab = {},
                    onOpenSettings = {},
                    nextRoutineLabel = "Read 30 minutes",
                    continueTarget = target,
                    now = fixedTime,
                    use24Hour = false,
                    battery = 9,
                    wifi = false,
                    showLabels = true,
                )
            }
        }
        capture("home_with_next")
    }

    @Test
    fun homeWithoutPlants() {
        seedContinue()
        val target = continueTarget()
        compose.setContent {
            EinkTheme(botanicalArt = false) {
                HomeScreen(
                    onOpenTab = {},
                    onOpenSettings = {},
                    nextRoutineLabel = "Stretch",
                    nextRoutinePart = PartOfDay.Morning,
                    continueTarget = target,
                    now = fixedTime,
                    use24Hour = true,
                    battery = 50,
                    wifi = true,
                )
            }
        }
        capture("home_no_art")
    }

    // -- The four tabs ------------------------------------------------------------------

    @Test
    fun readingLibrary() {
        val data = DataRoot.repository(context)
        listOf("Walden.pdf", "Pride and Prejudice.pdf", "Moby Dick.pdf", "Middlemarch.pdf", "Emma.pdf", "Jane Eyre.pdf", "Dracula.pdf")
            .forEach { data.store.write(StorageLayout.bookPath(it), ByteArray(2048)) }
        compose.setContent { EinkTheme { ReadingScreen(onBack = {}) } }
        // The newest file is first, so the last name written is the one on page one.
        waitForText("Dracula")
        capture("reading")
    }

    @Test
    fun writingBrowser() {
        val notes = NotesRepository(DataRoot.repository(context))
        notes.createFolder(notes.rootPath, "Essays")
        listOf("Shopping", "Ideas for the garden", "Letter to Anna", "Book list", "Packing", "Recipes").forEach { title ->
            val path = notes.createNote(notes.rootPath, title)!!
            notes.write(path, "# $title\n\nThe first line of the note, which shows as the small line.")
        }
        compose.setContent { EinkTheme { WritingScreen(onBack = {}, onOpenNote = {}) } }
        waitForText("Essays")
        capture("writing")
    }

    private fun seedJournal() {
        val data = DataRoot.repository(context)
        val habits = HabitsRepository(data)
        listOf("Read 30 minutes", "Walk outside", "Write a page", "Stretch").forEach { habits.add(it, today.minusDays(20)) }
        data.writeJournalEntry(today, "A quiet morning. I read by the window until the rain stopped, and then I walked to the river.")
    }

    @Test
    fun journalDay() {
        seedJournal()
        compose.setContent { EinkTheme { JournalScreen(onBack = {}, fixedToday = today) } }
        waitForText("Walk outside")
        capture("journal_day")
    }

    /** The routine page, with a part of the day on some of the items. */
    @Test
    fun journalRoutine() {
        seedJournal()
        val routine = RoutineRepository(DataRoot.repository(context))
        routine.add("Plan the day", "journal")
        routine.add("Read 30 minutes", "read")
        routine.add("Stretch")
        routine.add("Write the journal", "journal")
        routine.setPart("plan-the-day", PartOfDay.Morning)
        routine.setPart("read-30-minutes", PartOfDay.Afternoon)
        routine.setPart("write-the-journal", PartOfDay.Evening)
        compose.setContent { EinkTheme { JournalScreen(onBack = {}, fixedToday = today) } }
        waitForText("Walk outside")
        compose.onNodeWithContentDescription("Routine").performClick()
        waitForText("Plan the day")
        capture("journal_routine")
    }

    /** The options of a routine item, held down. The longest list in the Journal. */
    @Test
    fun journalRoutineOptionsDialog() {
        compose.setContent {
            EinkTheme {
                Box(modifier = Modifier.fillMaxSize().background(EinkColors.Paper).padding(16.dp)) {
                    OptionsDialogContent(
                        title = "Write the journal",
                        options = PartOfDay.entries.map { part ->
                            DialogOption(label = part.title, icon = if (part == PartOfDay.Evening) Lucide.Check else null) {}
                        } + DialogOption(label = "Remove", icon = Lucide.Trash2) {},
                        onDismiss = {},
                        modifier = Modifier.widthIn(max = 440.dp),
                    )
                }
            }
        }
        capture("journal_routine_options")
    }

    @Test
    fun journalMonth() {
        seedJournal()
        compose.setContent { EinkTheme { JournalScreen(onBack = {}, fixedToday = LocalDate.of(2026, 8, 31)) } }
        compose.onNodeWithContentDescription("Month").performClick()
        capture("journal_month")
    }

    @Test
    fun journalEditing() {
        seedJournal()
        compose.setContent { EinkTheme { JournalScreen(onBack = {}, fixedToday = today) } }
        waitForText("Walk outside")
        compose.onNodeWithContentDescription("Edit the entry").performClick()
        capture("journal_editing")
    }

    private val someApps = listOf(
        "Calculator", "Calendar", "Camera", "Chrome", "Clock", "Contacts", "DevCheck", "Files", "Gallery", "Kindle",
        "KOReader", "Libby", "Maps", "Notes", "Play Store", "Pocket", "Recorder", "Syncthing", "ViWoods Settings",
    ).map { LauncherEntry("com.example.${it.lowercase().replace(' ', '.')}", "Main", it) }

    private val someFolders = AppFolders(
        listOf(
            AppFolder("Reading", someApps.filter { it.label in setOf("Kindle", "KOReader", "Libby", "Pocket") }.map { it.key }),
            AppFolder("Tools", someApps.filter { it.label in setOf("Calculator", "Files", "DevCheck") }.map { it.key }),
            AppFolder("Writing"),
        ),
    )

    @Test
    fun appsAll() {
        compose.setContent {
            EinkTheme { AppsScreen(onBack = {}, previewApps = someApps, previewFolders = someFolders, initialPage = 2) }
        }
        capture("apps_all")
    }

    @Test
    fun appsFolders() {
        compose.setContent {
            EinkTheme { AppsScreen(onBack = {}, previewApps = someApps, previewFolders = someFolders, initialPage = 1) }
        }
        capture("apps_folders")
    }

    @Test
    fun appsInsideAFolder() {
        compose.setContent {
            EinkTheme { AppsScreen(onBack = {}, previewApps = someApps, previewFolders = someFolders, initialPage = 1) }
        }
        compose.onAllNodesWithText("Reading")[0].performClick()
        capture("apps_folder_open")
    }

    @Test
    fun appsPinnedWhenEmpty() {
        compose.setContent {
            EinkTheme { AppsScreen(onBack = {}, previewApps = someApps, previewFolders = someFolders, initialPage = 0) }
        }
        capture("apps_pinned_empty")
    }

    @Test
    fun appsSearch() {
        compose.setContent {
            EinkTheme {
                AppsScreen(onBack = {}, previewApps = someApps, previewFolders = someFolders, initialPage = 2, initialSearch = "k")
            }
        }
        capture("apps_search")
    }

    // A short list, so the last line, the one that shows or puts away the
    // hidden apps, is on the first page of the picture.
    private val fewApps = someApps.take(5)
    private val hidingTwo = someFolders.hiddenToggled(fewApps[0].key).hiddenToggled(fewApps[3].key)

    @Test
    fun appsWithHiddenAway() {
        compose.setContent {
            EinkTheme { AppsScreen(onBack = {}, previewApps = fewApps, previewFolders = hidingTwo, initialPage = 2) }
        }
        capture("apps_hidden_away")
    }

    @Test
    fun appsWithHiddenShown() {
        compose.setContent {
            EinkTheme {
                AppsScreen(onBack = {}, previewApps = fewApps, previewFolders = hidingTwo, initialPage = 2, initialShowHidden = true)
            }
        }
        capture("apps_hidden_shown")
    }

    // -- Settings ------------------------------------------------------------------------

    private fun settings(tab: SettingsTab, name: String, device: EinkDevice = EinkDevices.get(context)) {
        compose.setContent {
            EinkTheme {
                SettingsScreen(
                    onBack = {},
                    onOpenLog = {},
                    onOpenDemo = {},
                    initialPage = tab.ordinal,
                    device = device,
                    isDefaultHome = { true },
                )
            }
        }
        capture(name)
    }

    /** The Screen tab on a tablet without a vendor pen: three tabs, no Pen. */
    @Test
    fun settingsScreen() = settings(SettingsTab.Screen, "settings_screen")

    /** The first run: Margin is not the home app yet, so "Set as Home App" is the first row. */
    @Test
    fun settingsScreenNotHome() {
        compose.setContent {
            EinkTheme {
                SettingsScreen(onBack = {}, onOpenLog = {}, onOpenDemo = {}, isDefaultHome = { false })
            }
        }
        waitForText("Set as Home App")
        capture("settings_screen_not_home")
    }

    /** The Pen tab as the tablet shows it: with vendor control, so the tab is there. */
    @Test
    fun settingsPen() = settings(SettingsTab.Pen, "settings_pen", VendorLikeDevice)

    /** The tallest Pen tab: a fast mode is on, so Redraw Delay is there too. */
    @Test
    fun settingsPenFast() {
        val store = SettingsStore(context)
        runBlocking { store.setFastPenMode(SettingsStore.FAST_PEN_WRITING) }
        try {
            compose.setContent {
                EinkTheme {
                    SettingsScreen(
                        onBack = {},
                        onOpenLog = {},
                        onOpenDemo = {},
                        initialPage = SettingsTab.Pen.ordinal,
                        device = VendorLikeDevice,
                    )
                }
            }
            waitForText("Redraw Delay")
            capture("settings_pen_fast")
        } finally {
            runBlocking { store.setFastPenMode(SettingsStore.FAST_PEN_OFF) }
        }
    }

    @Test
    fun settingsFiles() = settings(SettingsTab.Files, "settings_files")

    @Test
    fun settingsAbout() = settings(SettingsTab.About, "settings_about")

    /** The credits are a page of their own, one step down from the About tab. */
    @Test
    fun settingsCredits() {
        compose.setContent {
            EinkTheme {
                SettingsScreen(onBack = {}, onOpenLog = {}, onOpenDemo = {}, initialPage = SettingsTab.About.ordinal)
            }
        }
        compose.onNodeWithText("Credits").performClick()
        capture("settings_credits")
    }

    /** The choice of pen mode. */
    @Test
    fun settingsPenModeDialog() {
        compose.setContent {
            EinkTheme {
                Box(modifier = Modifier.fillMaxSize().background(EinkColors.Paper).padding(16.dp)) {
                    OptionsDialogContent(
                        title = "Pen Mode",
                        message = PEN_MODE_HELP,
                        options = PEN_MODES.mapIndexed { index, mode ->
                            DialogOption(
                                label = penModeLabel(mode) + if (index == 1) " (closed the app)" else "",
                                icon = if (index == 0) Lucide.Check else Lucide.Minus,
                            ) {}
                        },
                        onDismiss = {},
                        modifier = Modifier.widthIn(max = 440.dp),
                    )
                }
            }
        }
        capture("settings_pen_dialog")
    }

    @Test
    fun settingsRedrawDelayDialog() {
        compose.setContent {
            EinkTheme {
                Box(modifier = Modifier.fillMaxSize().background(EinkColors.Paper).padding(16.dp)) {
                    OptionsDialogContent(
                        title = "Redraw Delay",
                        message = REDRAW_DELAY_HELP,
                        options = REDRAW_DELAYS.mapIndexed { index, millis ->
                            DialogOption(label = "$millis ms", icon = if (index == 1) Lucide.Check else Lucide.Minus) {}
                        },
                        onDismiss = {},
                        modifier = Modifier.widthIn(max = 440.dp),
                    )
                }
            }
        }
        capture("settings_redraw_dialog")
    }

    @Test
    fun settingsRestoreDialog() {
        compose.setContent {
            EinkTheme {
                Box(modifier = Modifier.fillMaxSize().background(EinkColors.Paper).padding(16.dp)) {
                    ConfirmDialogContent(
                        title = "Restore Backup?",
                        message = RESTORE_HELP,
                        confirmText = "Restore",
                        cancelText = "Cancel",
                        onConfirm = {},
                        onDismiss = {},
                        modifier = Modifier.widthIn(max = 440.dp),
                    )
                }
            }
        }
        capture("settings_restore_dialog")
    }

    @Test
    fun logViewer() {
        val lines = (1..9).map { index ->
            LogLine(
                timeMillis = 0L,
                level = if (index % 4 == 0) LogLevel.ERROR else LogLevel.INFO,
                tag = "HomeActivity",
                message = "Line $index of the log. A message can be long, and then it takes the second line of its row as well.",
            )
        }
        compose.setContent { EinkTheme { LogViewerScreen(linesProvider = { lines }, onCopyToDownloads = { 0 }, onBack = {}) } }
        capture("log")
    }

    // -- Writing and reading ----------------------------------------------------------------

    @Test
    fun typedNote() {
        val notes = NotesRepository(DataRoot.repository(context))
        val path = notes.createNote(notes.rootPath, "Letter to Anna")!!
        notes.write(path, "# Letter to Anna\n\nDear Anna,\n\nThe garden is full of apples this year, and I thought of you.")
        compose.setContent { EinkTheme { NoteEditorScreen(notePath = path, onClose = {}) } }
        waitForText("Dear Anna")
        capture("typed_note")
    }

    @Test
    fun inkNote() {
        val data = DataRoot.repository(context)
        val note = InkNote(
            template = PageTemplate.Lined,
            pages = listOf(InkPageData(InkTestData.fullPage(240)), InkPageData(emptyList())),
        )
        val controller = InkNoteController(InkNotesRepository(data), "notes/test.inknote", note, mayWrite = true)
        compose.setContent {
            EinkTheme {
                InkNoteScreen(
                    title = "Ideas",
                    controller = controller,
                    problem = null,
                    onCanvas = { controller.attach(it) },
                    onDialog = {},
                    onWidth = {},
                    onExport = { "" },
                    onClose = {},
                )
            }
        }
        capture("ink_note")
    }

    @Test
    fun pdfReader() {
        compose.setContent { EinkTheme(botanicalArt = false) { PdfReaderScreenshotSupport.Screen(split = false) } }
        capture("pdf_reader")
    }

    @Test
    fun pdfReaderSplitWithATypedNote() {
        compose.setContent { EinkTheme(botanicalArt = false) { PdfReaderScreenshotSupport.Screen(split = true) } }
        waitForText("word")
        capture("pdf_reader_split_typed")
    }

    @Test
    fun pdfReaderSplitWithAHandwrittenNote() {
        compose.setContent { EinkTheme(botanicalArt = false) { PdfReaderScreenshotSupport.Screen(split = true) } }
        waitForText("word")
        compose.onNodeWithContentDescription("Handwritten note").performClick()
        // The page has an Undo of its own. The second one is the note's, and it
        // is there once the note has been read from the disk.
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription("Undo").fetchSemanticsNodes().size == 2
        }
        capture("pdf_reader_split_ink")
    }

    /** The second half on its own, as the EPUB reader shows it beside the book. */
    @Test
    fun notePaneAlone() {
        val split = SplitState(firstPage = { PanePage.BookNotes("Walden") }).apply { open() }
        compose.setContent {
            EinkTheme(botanicalArt = false) {
                Box(modifier = Modifier.fillMaxSize().background(EinkColors.Paper)) {
                    DetachedPane(split, paneHost)
                }
            }
        }
        waitForText("word")
        capture("note_pane")
    }

    // -- The split screen -----------------------------------------------------------------

    private val paneHost = object : PaneHost {
        override fun openBook(book: LibraryBook) = Unit
        override fun paneInk(canvas: InkCanvasView?) = Unit
    }

    /** Any screen as the main half, with [page] in the second half. */
    private fun split(
        page: PanePage,
        name: String,
        swapped: Boolean = false,
        mainPage: PanePage? = null,
        waitFor: String? = null,
        main: @Composable () -> Unit,
    ) {
        val split = SplitState(mainPage = { mainPage }).apply { open(page, swapped) }
        compose.setContent {
            EinkTheme {
                SplitLayout(split = split, main = main, pane = { SplitPane(split, paneHost) })
            }
        }
        waitFor?.let(::waitForText)
        capture(name)
    }

    @Test
    fun splitHomeAndTheChoice() {
        seedContinue()
        val target = continueTarget()
        split(PanePage.Choose, "split_home_choice") {
            HomeScreen(
                onOpenTab = {},
                onOpenSettings = {},
                nextRoutineLabel = "Write the journal",
                nextRoutinePart = PartOfDay.Evening,
                continueTarget = target,
                now = LocalDateTime.of(2026, 9, 19, 20, 59),
                use24Hour = false,
                battery = 72,
                wifi = true,
            )
        }
    }

    @Test
    fun splitJournalBesideWriting() {
        seedJournal()
        val notes = NotesRepository(DataRoot.repository(context))
        notes.createNote(notes.rootPath, "Garden plan")
        split(PanePage.Tab(LauncherRoute.Writing), "split_journal_writing", waitFor = "Garden plan") {
            JournalScreen(onBack = {}, fixedToday = today)
        }
    }

    @Test
    fun splitTypedNoteBesideTheLibrary() {
        val notes = NotesRepository(DataRoot.repository(context))
        val path = notes.createNote(notes.rootPath, "Reading list")!!
        notes.write(path, "# Reading list\n\nWalden, then Middlemarch.")
        split(
            PanePage.Tab(LauncherRoute.Reading),
            "split_note_library",
            mainPage = PanePage.TypedNote(path),
            waitFor = "Middlemarch",
        ) { NoteEditorScreen(notePath = path, onClose = {}) }
    }

    @Test
    fun splitSwappedAppsBesideSettings() = split(
        PanePage.Tab(LauncherRoute.Apps),
        "split_swapped_apps",
        swapped = true,
    ) { SettingsScreen(onBack = {}, onOpenLog = {}, onOpenDemo = {}) }

    /** Settings as the main half, 320 dp wide on its side, with all four tabs: the narrowest strip. */
    @Test
    fun splitSettingsPenTabBesideApps() = split(
        PanePage.Tab(LauncherRoute.Apps),
        "split_settings_pen",
        swapped = true,
    ) {
        SettingsScreen(
            onBack = {},
            onOpenLog = {},
            onOpenDemo = {},
            initialPage = SettingsTab.Pen.ordinal,
            device = VendorLikeDevice,
        )
    }

    /** Settings in the second half, which has the smaller margins. */
    @Test
    fun splitSettingsInTheSecondHalf() = split(PanePage.Tab(LauncherRoute.Settings), "split_settings_pane") {
        AppsScreen(onBack = {}, previewApps = fewApps, previewFolders = someFolders)
    }

    @Test
    fun splitInkNoteBesideATypedNote() {
        val data = DataRoot.repository(context)
        val notes = NotesRepository(data)
        val path = notes.createNote(notes.rootPath, "Draft")!!
        notes.write(path, "# Draft\n\nThe first line of the letter.")
        val note = InkNote(template = PageTemplate.Lined, pages = listOf(InkPageData(InkTestData.fullPage(120))))
        val controller = InkNoteController(InkNotesRepository(data), "notes/sketch.inknote", note, mayWrite = true)
        split(PanePage.TypedNote(path), "split_ink_typed", waitFor = "first line") {
            InkNoteScreen(
                title = "Sketch",
                controller = controller,
                problem = null,
                onCanvas = { controller.attach(it) },
                onDialog = {},
                onWidth = {},
                onExport = { "" },
                onClose = {},
            )
        }
    }

    @Test
    fun anOverflowMarker() {
        // A plain page with one line at the very bottom. If this line is not
        // in the picture, the capture itself is cutting the screen off, and
        // every other picture in this class proves nothing.
        compose.setContent {
            EinkTheme {
                Box(modifier = Modifier.fillMaxSize().padding(4.dp), contentAlignment = androidx.compose.ui.Alignment.BottomEnd) {
                    EinkText(text = "bottom right corner", style = EinkType.capsSmall)
                }
            }
        }
        capture("frame_check")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "sw480dp-w480dp-h640dp-port-xxhdpi")
class ScreensPortraitScreenshotTest : ScreensScreenshotBase("")

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "sw480dp-w640dp-h480dp-land-xxhdpi")
class ScreensLandscapeScreenshotTest : ScreensScreenshotBase("_land")

/** A panel with vendor control that does nothing, so Settings draws what the tablet shows. */
private object VendorLikeDevice : EinkDevice {
    private fun done(call: String) = EinkCallResult(call, true, "")
    override val name = "Vendor like"
    override val hasVendorControl = true
    override val activeFastPen: FastPenPath? = null
    override fun deviceInfo(): List<Pair<String, String>> = emptyList()
    override fun refreshMode(): RefreshMode = RefreshMode.Reading
    override fun setRefreshMode(mode: RefreshMode) = done("setRefreshMode")
    override fun fullRefresh() = done("fullRefresh")
    override fun startFastPen(context: Context, path: FastPenPath, drawRegion: android.graphics.Rect, excluded: List<android.graphics.Rect>) =
        done("startFastPen")
    override fun stopFastPen() = done("stopFastPen")
    override fun setPenTool(tool: PenTool) = done("setPenTool")
    override fun setPenWidthRange(min: Int, max: Int) = done("setPenWidthRange")
}
