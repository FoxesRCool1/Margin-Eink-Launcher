package io.github.foxesrcool1.margin.ui.reading.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.github.foxesrcool1.margin.core.books.LibraryBook
import io.github.foxesrcool1.margin.core.eink.ScreenRefresh
import io.github.foxesrcool1.margin.core.habits.DayBoundary
import io.github.foxesrcool1.margin.core.habits.HabitsRepository
import io.github.foxesrcool1.margin.core.ink.InkNote
import io.github.foxesrcool1.margin.core.ink.InkNotesRepository
import io.github.foxesrcool1.margin.core.ink.InkPageData
import io.github.foxesrcool1.margin.core.ink.InkStroke
import io.github.foxesrcool1.margin.core.ink.PageTemplate
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.pdf.AnnotatedPage
import io.github.foxesrcool1.margin.core.pdf.PageBox
import io.github.foxesrcool1.margin.core.pdf.PageInkLoad
import io.github.foxesrcool1.margin.core.pdf.PdfInkRepository
import io.github.foxesrcool1.margin.core.pdf.PdfPosition
import io.github.foxesrcool1.margin.core.pdf.PdfViewport
import io.github.foxesrcool1.margin.core.pdf.PdfZoom
import io.github.foxesrcool1.margin.core.reading.BookAnnotations
import io.github.foxesrcool1.margin.core.reading.ReadingRepository
import io.github.foxesrcool1.margin.core.reading.ReadingTimer
import io.github.foxesrcool1.margin.core.settings.SettingsStore
import io.github.foxesrcool1.margin.core.storage.DataRoot
import io.github.foxesrcool1.margin.core.storage.RelativePaths
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import io.github.foxesrcool1.margin.core.window.ScreenWindow
import io.github.foxesrcool1.margin.design.EinkTheme
import io.github.foxesrcool1.margin.ui.ink.FastPenSession
import io.github.foxesrcool1.margin.ui.ink.InkCanvasView
import io.github.foxesrcool1.margin.ui.ink.InkExport
import io.github.foxesrcool1.margin.ui.ink.InkMode
import io.github.foxesrcool1.margin.ui.ink.PenWidths
import io.github.foxesrcool1.margin.ui.split.PaneHost
import io.github.foxesrcool1.margin.ui.split.PanePage
import io.github.foxesrcool1.margin.ui.split.SplitLayout
import io.github.foxesrcool1.margin.ui.split.SplitPane
import io.github.foxesrcool1.margin.ui.split.SplitState
import io.github.foxesrcool1.margin.ui.split.handSplitBack
import io.github.foxesrcool1.margin.ui.split.openBookBeside
import io.github.foxesrcool1.margin.ui.split.splitCarry
import io.github.foxesrcool1.margin.ui.split.watchAndroidSplit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors

private const val TAG = "PdfReader"

/** What is open on top of the page. */
enum class PdfPanel { None, More, GoTo, InkPages }

/** Everything the PDF reader's Compose layer shows. */
class PdfUiState {
    var loading by mutableStateOf(true)
    var failure by mutableStateOf<String?>(null)
    var title by mutableStateOf("")
    var pageIndex by mutableStateOf(0)
    var pageCount by mutableStateOf(0)
    var screenIndex by mutableStateOf(0)
    var screenCount by mutableStateOf(1)
    var zoom by mutableStateOf(PdfZoom.FitPage)
    var cropMargins by mutableStateOf(false)
    var mode by mutableStateOf(InkMode.Pen)
    var penWidth by mutableStateOf(PenWidths.MEDIUM)
    var panel by mutableStateOf(PdfPanel.None)
    var notice by mutableStateOf<String?>(null)
    var inkPages by mutableStateOf<List<AnnotatedPage>>(emptyList())

    /** Emulator only: whether the mouse draws, or taps and swipes like a finger. */
    var mouseDraws by mutableStateOf(false)
}

/** What the Compose layer can ask for. */
interface PdfActions {
    fun close()
    fun attach(canvas: InkCanvasView)
    fun step(direction: Int)
    fun goToPage(pageNumber: Int)
    fun pick(mode: InkMode)
    fun nextZoom()
    fun toggleCrop()
    fun nextPenWidth()
    fun undo()
    fun redo()
    fun show(panel: PdfPanel)
    fun exportPage()
    fun exportNotes()

    /** Emulator only. */
    fun toggleMouse()
}

/**
 * The PDF reader: one screen of a page at a time, with ink on top.
 *
 * - The page is a picture under an [InkCanvasView]. The strokes are kept in
 *   PDF points, so zoom and crop only change which part of the page is shown
 *   and never where the ink sits on it.
 * - A page larger than the screen is cut into screens. Next walks through
 *   them and then on to the next page. Nothing scrolls.
 * - The pen draws at any time. A finger taps the left or right third to move,
 *   the middle for the menu, or swipes.
 * - The PDF file is never written to.
 */
class PdfReaderActivity : ComponentActivity() {

    private val ui = PdfUiState()
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "eink-pdf").apply { isDaemon = true } }

    private lateinit var reading: ReadingRepository
    private lateinit var ink: PdfInkRepository
    private var bookId = ""
    private var bookPath = ""
    private var pages: PdfPages? = null
    private var canvas: InkCanvasView? = null

    /** The handwriting canvas of the second half of the split screen, while it is on show. */
    private var noteCanvas: InkCanvasView? = null

    /** The split screen. It opens on the notes of this book. */
    private val split = SplitState(firstPage = { PanePage.BookNotes(ui.title) })

    private val paneHost = object : PaneHost {
        override val bookTitle: String get() = ui.title

        override fun openBook(book: LibraryBook) = openBookBeside(this@PdfReaderActivity, split, book, this)

        override fun keeper(): Intent =
            io.github.foxesrcool1.margin.ui.split.BesideActivity.then(this@PdfReaderActivity, intent)

        override fun leave() = finish()

        override fun paneInk(canvas: InkCanvasView?) {
            noteCanvas = canvas
            session?.stop()
            session = null
            // A swap or a turn moves the note. The fast pen has to follow it.
            canvas?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> startFastPen() }
            val target = canvas ?: this@PdfReaderActivity.canvas ?: return
            // The canvas has no size yet when it is brand new.
            target.post { startFastPen() }
        }
    }
    private var session: FastPenSession? = null
    private var fastPenMode = SettingsStore.FAST_PEN_OFF
    private var redrawDelay = SettingsStore.DEFAULT_INK_DELAY_MILLIS
    private var refreshEvery = 0
    private var turns = 0

    /** The page whose ink is in the canvas right now, or -1. */
    private var inkPage = -1
    private var inkDirty = false
    private var inkWritable = true
    private var shown: Bitmap? = null
    private var currentPart: PageBox? = null
    private var request = 0

    private val timer = ReadingTimer()
    private var dayBoundaryHour = DayBoundary.DEFAULT_HOUR
    private var annotations = BookAnnotations()
    private val autosave = Runnable { saveInk() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScreenWindow.attach(this)
        bookPath = intent.getStringExtra(EXTRA_PATH).orEmpty()
        bookId = intent.getStringExtra(EXTRA_BOOK_ID).orEmpty()
        ui.title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (bookPath.isBlank() || bookId.isBlank()) {
            AppLog.e(TAG, "Opened with no book")
            finish()
            return
        }
        val data = DataRoot.repository(this)
        reading = ReadingRepository(data)
        ink = PdfInkRepository(data, bookId)

        watchAndroidSplit(split)
        intent.splitCarry()?.let { split.open(it.page, it.swapped) }
        setContent {
            EinkTheme(botanicalArt = false) {
                SplitLayout(
                    split = split,
                    main = { PdfReaderScreen(ui, actions) },
                    pane = { SplitPane(split, paneHost) },
                )
            }
        }

        lifecycleScope.launch {
            val settings = SettingsStore(this@PdfReaderActivity)
            fastPenMode = settings.fastPenMode.first()
            redrawDelay = settings.inkRedrawDelayMillis.first()
            refreshEvery = settings.reader.first().refreshEveryPages
            worker.execute { openBook() }
        }
    }

    /** On the worker thread. */
    private fun openBook() {
        val started = System.currentTimeMillis()
        val file = File(DataRoot.folder(this), RelativePaths.normalise(bookPath))
        val opened = PdfPages.open(file)
        val loaded = reading.load(bookId)
        val hour = runCatching { HabitsRepository(DataRoot.repository(this)).load().dayBoundaryHour }
            .getOrDefault(DayBoundary.DEFAULT_HOUR)

        main.post {
            if (isDestroyed) {
                opened?.close()
                return@post
            }
            dayBoundaryHour = hour
            if (opened == null || opened.pageCount == 0) {
                opened?.close()
                ui.loading = false
                ui.failure = "This PDF is locked or damaged."
                return@post
            }
            pages = opened
            annotations = loaded.copy(bookPath = bookPath, title = ui.title)
            val position = PdfPosition.fromJson(loaded.position, opened.pageCount)
            ui.pageCount = opened.pageCount
            ui.pageIndex = position.pageIndex
            ui.zoom = position.zoom
            ui.cropMargins = position.cropMargins
            ui.loading = false
            timer.resume(nowSeconds())
            val took = System.currentTimeMillis() - started
            AppLog.i(TAG, "Opened $bookPath: ${opened.pageCount} pages, $took ms")
            io.github.foxesrcool1.margin.core.speed.SpeedWatch.check("Opening the PDF", took, io.github.foxesrcool1.margin.core.speed.SpeedWatch.Budget.OPEN_BOOK)
            showScreen()
        }
    }

    // -- Showing a screen ------------------------------------------------------------------

    /**
     * Renders the screen that [ui] points at and hands it to the canvas. The
     * slow part runs on the worker. A newer request makes an older one give
     * up, so holding Next down does not queue twenty renders.
     */
    private fun showScreen(keepPlace: PageBox? = null) {
        val source = pages ?: return
        val view = canvas ?: return
        if (view.width == 0 || view.height == 0) return

        val pageIndex = ui.pageIndex
        val zoom = ui.zoom
        val crop = ui.cropMargins
        val wantedScreen = ui.screenIndex
        val wantLast = wantLastScreen
        val width = view.width
        val height = view.height
        val mine = ++request
        val needInk = pageIndex != inkPage

        if (needInk) saveInk()

        worker.execute {
            if (mine != request) return@execute
            val started = SystemClock.elapsedRealtime()
            val whole = source.pageBox(pageIndex)
            val content = if (crop) source.contentBox(pageIndex) else whole
            val screens = PdfViewport.screens(content, width, height, zoom)
            val screen = when {
                keepPlace != null -> PdfViewport.screenNearest(screens, keepPlace)
                wantLast -> screens.size - 1
                else -> wantedScreen.coerceIn(0, screens.size - 1)
            }
            val part = screens[screen]
            val picture = source.render(pageIndex, part, width, height)
            val pageInk = if (needInk) ink.load(pageIndex + 1) else null
            val took = SystemClock.elapsedRealtime() - started

            main.post {
                if (mine != request || isDestroyed) {
                    picture?.recycle()
                    return@post
                }
                if (took > 400) AppLog.i(TAG, "Page ${pageIndex + 1} screen ${screen + 1} took $took ms")

                if (pageInk != null) {
                    // Strokes drawn on the old page while this one rendered are still in the canvas. Save them before setPage replaces the editor.
                    saveInk()
                    val strokes = (pageInk as? PageInkLoad.Loaded)?.strokes ?: emptyList()
                    inkWritable = pageInk is PageInkLoad.Loaded
                    if (!inkWritable) ui.notice = "Old ink is unreadable. New ink is not saved."
                    view.unitScale = whole.width / InkNote.DEFAULT_PAGE_WIDTH
                    view.setPage(strokes, whole.width, whole.height, PageTemplate.Blank)
                    inkPage = pageIndex
                    inkDirty = false
                }
                view.showPart(RectF(part.left, part.top, part.right, part.bottom), picture)
                shown?.takeIf { it !== picture }?.recycle()
                shown = picture
                currentPart = part
                wantLastScreen = false
                ui.screenIndex = screen
                ui.screenCount = screens.size
                savePositionSoon()
            }
        }
    }

    /** True from a turn back onto the page before, until that page has been measured. */
    private var wantLastScreen = false

    private fun step(direction: Int) {
        if (pages == null || ui.panel != PdfPanel.None) return
        timer.activity(nowSeconds())
        val nextScreen = ui.screenIndex + direction
        when {
            nextScreen in 0 until ui.screenCount -> ui.screenIndex = nextScreen
            direction > 0 && ui.pageIndex < ui.pageCount - 1 -> {
                ui.pageIndex++
                ui.screenIndex = 0
                ui.screenCount = 1
                wantLastScreen = false
            }
            direction < 0 && ui.pageIndex > 0 -> {
                // Going back lands on the last screen of the page before, and
                // how many screens that page has is only known once it is
                // measured. The wish is kept here, not in ui.screenIndex: the
                // status line reads that, and for the length of one render it
                // said "part -2147483648". One screen is claimed until then,
                // which keeps the word "part" off the line.
                ui.pageIndex--
                ui.screenIndex = 0
                ui.screenCount = 1
                wantLastScreen = true
            }
            else -> return
        }
        ui.notice = null
        turns++
        if (refreshEvery > 0 && turns % refreshEvery == 0) ScreenRefresh.run(this)
        showScreen()
    }

    // -- Ink ----------------------------------------------------------------------------------

    private fun saveInk() {
        main.removeCallbacks(autosave)
        val view = canvas ?: return
        if (!inkDirty || inkPage < 0 || !inkWritable) return
        view.settle()
        inkDirty = false
        val strokes: List<InkStroke> = view.editor.strokes
        val pageNumber = inkPage + 1
        worker.execute {
            if (!ink.save(pageNumber, strokes)) AppLog.e(TAG, "Could not save the ink of page $pageNumber")
        }
    }

    /**
     * The tablet draws its fast line in one place at a time. With a
     * handwritten note open beside the page, that place is the note: it is
     * where the long writing happens. A mark on the PDF is then drawn by this
     * app, which is slower and still correct.
     */
    private fun startFastPen() {
        val view = noteCanvas ?: canvas ?: return
        if (fastPenMode == SettingsStore.FAST_PEN_OFF || view.width == 0 || ui.panel != PdfPanel.None) return
        if (session?.canvas !== view) {
            session?.stop()
            session = null
        }
        val current = session ?: FastPenSession(this, view, fastPenMode, redrawDelay).also { session = it }
        current.start()
    }

    // -- Position and time ----------------------------------------------------------------------

    private val savePosition = Runnable { savePositionNow() }

    private fun savePositionSoon() {
        main.removeCallbacks(savePosition)
        main.postDelayed(savePosition, 4_000)
    }

    private fun currentAnnotations(): BookAnnotations = annotations.copy(
        position = PdfPosition(ui.pageIndex, ui.zoom, ui.cropMargins).toJson(),
        progress = if (ui.pageCount <= 1) 1.0 else ui.pageIndex.toDouble() / (ui.pageCount - 1),
    )

    private fun savePositionNow() {
        if (pages == null) return
        val snapshot = currentAnnotations()
        worker.execute { reading.save(bookId, snapshot) }
    }

    private fun nowSeconds(): Long = SystemClock.elapsedRealtime() / 1000

    /** The reading day right now, so a session over midnight is booked to the day it ends on. */
    private fun today(): LocalDate = DayBoundary(dayBoundaryHour).dateOf(Instant.now(), ZoneId.systemDefault())

    // -- What the Compose layer may ask for ---------------------------------------------------------

    private val actions = object : PdfActions {
        override fun close() = finish()

        override fun attach(canvas: InkCanvasView) {
            // A turn of the screen, or the split screen, lays the page out
            // again on a new canvas. The ink of the old one is saved first,
            // and the page is loaded again into the new one.
            if (this@PdfReaderActivity.canvas !== canvas) {
                saveInk()
                inkPage = -1
                // The tablet must stop drawing over the old canvas at once.
                if (session?.canvas === this@PdfReaderActivity.canvas) {
                    session?.stop()
                    session = null
                }
            }
            this@PdfReaderActivity.canvas = canvas
            // On the emulator the mouse starts as a finger here, so a click
            // turns the page. The toolbar has a switch to make it draw.
            canvas.fingerDraws = ui.mouseDraws
            canvas.onInkChanged = {
                inkDirty = true
                timer.activity(nowSeconds())
                main.removeCallbacks(autosave)
                main.postDelayed(autosave, 2_500)
            }
            canvas.onPageSwipe = { direction -> step(direction) }
            canvas.onFingerTap = { x, _ ->
                val width = canvas.width.toFloat()
                when {
                    x < width * 0.3f -> step(-1)
                    x > width * 0.7f -> step(1)
                    else -> show(PdfPanel.More)
                }
            }
            canvas.onSized = { _, _ ->
                showScreen(keepPlace = currentPart)
                startFastPen()
            }
            if (canvas.width > 0) showScreen()
        }

        override fun step(direction: Int) = this@PdfReaderActivity.step(direction)

        override fun goToPage(pageNumber: Int) {
            ui.notice = null
            ui.panel = PdfPanel.None
            startFastPen()
            val index = (pageNumber - 1).coerceIn(0, (ui.pageCount - 1).coerceAtLeast(0))
            ui.pageIndex = index
            ui.screenIndex = 0
            wantLastScreen = false
            showScreen()
        }

        override fun pick(mode: InkMode) {
            ui.mode = mode
            canvas?.mode = mode
        }

        override fun nextZoom() {
            ui.zoom = ui.zoom.next()
            showScreen(keepPlace = currentPart)
        }

        override fun toggleCrop() {
            ui.cropMargins = !ui.cropMargins
            ui.panel = PdfPanel.None
            startFastPen()
            showScreen(keepPlace = currentPart)
        }

        override fun nextPenWidth() {
            val next = PenWidths.all[(PenWidths.all.indexOf(ui.penWidth) + 1) % PenWidths.all.size]
            ui.penWidth = next
            canvas?.penWidth = next
            session?.applyWidth(next)
        }

        override fun undo() {
            canvas?.undo()
        }

        override fun redo() {
            canvas?.redo()
        }

        override fun show(panel: PdfPanel) {
            ui.panel = panel
            if (panel == PdfPanel.None) {
                startFastPen()
            } else {
                // The tablet must not draw on a dialog.
                session?.stop()
                ui.notice = null
            }
            if (panel == PdfPanel.InkPages) {
                saveInk()
                worker.execute {
                    val list = ink.annotatedPages()
                    main.post { ui.inkPages = list }
                }
            }
        }

        override fun exportPage() {
            val source = pages ?: return
            val view = canvas ?: return
            view.settle()
            val strokes = view.editor.strokes
            val pageIndex = ui.pageIndex
            val name = "${ui.title.ifBlank { "pdf" }} page ${pageIndex + 1}.png"
            ui.panel = PdfPanel.None
            startFastPen()
            worker.execute {
                val written = runCatching {
                    val whole = source.pageBox(pageIndex)
                    val scale = EXPORT_WIDTH / whole.width
                    val picture = source.render(pageIndex, whole, EXPORT_WIDTH.toInt(), (whole.height * scale).toInt() + 1)
                    val note = InkNote(whole.width, whole.height, PageTemplate.Blank)
                    val png = InkExport.pagePng(note, InkPageData(strokes), scale, picture)
                    picture?.recycle()
                    InkNotesRepository(DataRoot.repository(this@PdfReaderActivity)).writeExport(name, png)
                }.onFailure { AppLog.e(TAG, "Export of page ${pageIndex + 1} failed", it) }.getOrNull()
                main.post { ui.notice = if (written != null) "Saved as $written" else "The export did not work" }
            }
        }

        override fun toggleMouse() {
            ui.mouseDraws = !ui.mouseDraws
            canvas?.fingerDraws = ui.mouseDraws
        }

        override fun exportNotes() {
            saveInk()
            val title = ui.title
            worker.execute {
                val data = DataRoot.repository(this@PdfReaderActivity)
                val path = data.freePath(
                    RelativePaths.join(StorageLayout.EXPORTS, "${StorageLayout.safeName(title.ifBlank { "pdf" })} notes.md"),
                )
                val ok = data.store.writeText(path, ink.markdown(title))
                main.post { ui.notice = if (ok) "Saved as $path" else "The export did not work" }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_VOLUME_DOWN -> {
            step(1)
            true
        }

        KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_VOLUME_UP -> {
            step(-1)
            true
        }

        KeyEvent.KEYCODE_ESCAPE -> {
            if (ui.panel != PdfPanel.None) actions.show(PdfPanel.None) else finish()
            true
        }

        else -> super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        if (pages != null) timer.resume(nowSeconds())
        startFastPen()
        io.github.foxesrcool1.margin.ui.split.AdjacentApps.takeRequest(this)
    }

    override fun onPause() {
        handSplitBack(split)
        session?.stop()
        saveInk()
        main.removeCallbacks(savePosition)
        timer.pause(nowSeconds())
        val seconds = timer.take()
        if (pages != null) {
            val snapshot = currentAnnotations()
            val day = today()
            worker.execute {
                reading.save(bookId, snapshot)
                reading.addReadingTime(bookId, day, seconds)
            }
        }
        super.onPause()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        val source = pages
        pages = null
        // After everything that is still queued, so no save is lost.
        worker.execute { source?.close() }
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PATH = "book_path"
        const val EXTRA_BOOK_ID = "book_id"
        const val EXTRA_TITLE = "book_title"

        private const val EXPORT_WIDTH = 1440f

        fun intent(context: Context, bookPath: String, bookId: String, title: String): Intent =
            Intent(context, PdfReaderActivity::class.java)
                .putExtra(EXTRA_PATH, bookPath)
                .putExtra(EXTRA_BOOK_ID, bookId)
                .putExtra(EXTRA_TITLE, title)
    }
}
