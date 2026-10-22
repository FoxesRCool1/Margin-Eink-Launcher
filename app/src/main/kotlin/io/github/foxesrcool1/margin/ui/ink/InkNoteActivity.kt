package io.github.foxesrcool1.margin.ui.ink

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import io.github.foxesrcool1.margin.core.ink.InkNote
import io.github.foxesrcool1.margin.core.ink.InkNoteLoad
import io.github.foxesrcool1.margin.core.ink.InkNotesRepository
import io.github.foxesrcool1.margin.core.ink.PageTemplate
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.threads.AppDispatchers
import io.github.foxesrcool1.margin.core.settings.SettingsStore
import io.github.foxesrcool1.margin.core.storage.DataRoot
import io.github.foxesrcool1.margin.core.window.ScreenWindow
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkTheme
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.CapsLabel
import io.github.foxesrcool1.margin.design.components.ConfirmDialog
import io.github.foxesrcool1.margin.design.components.DialogOption
import io.github.foxesrcool1.margin.design.components.EinkText
import io.github.foxesrcool1.margin.design.components.HairlineDivider
import io.github.foxesrcool1.margin.design.components.IconPressButton
import io.github.foxesrcool1.margin.design.icons.Lucide
import io.github.foxesrcool1.margin.ui.common.RotateButton
import io.github.foxesrcool1.margin.ui.split.PaneHost
import io.github.foxesrcool1.margin.ui.split.PanePage
import io.github.foxesrcool1.margin.ui.split.SplitButton
import io.github.foxesrcool1.margin.ui.split.SplitLayout
import io.github.foxesrcool1.margin.ui.split.SplitPane
import io.github.foxesrcool1.margin.ui.split.SplitState
import io.github.foxesrcool1.margin.ui.split.openBookBeside
import io.github.foxesrcool1.margin.ui.split.splitCarry
import io.github.foxesrcool1.margin.ui.split.watchAndroidSplit
import io.github.foxesrcool1.margin.design.components.OptionsDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "InkNoteActivity"

/**
 * A handwritten note: pages of ink.
 *
 * The Writing tab opens it for a notebook, the Journal for the entry of a day,
 * and the reader for a note card on a highlight. They differ only in the path
 * of the file, so they share this one screen.
 *
 * Its own activity, like the typed editor, so the Home key closes the launcher
 * and not the page being written on.
 */
class InkNoteActivity : ComponentActivity() {

    private var controller: InkNoteController? = null

    /** The note once it is read. The screen shows "Opening" until then. */
    private var opened by mutableStateOf<OpenedNote?>(null)
    private var session: FastPenSession? = null
    private var canvasView: InkCanvasView? = null

    /** The handwriting canvas of the second half of the split screen, while it shows one. */
    private var paneCanvas: InkCanvasView? = null
    private var fastPenMode = SettingsStore.FAST_PEN_OFF
    private var redrawDelay = SettingsStore.DEFAULT_INK_DELAY_MILLIS
    private var mainPage: PanePage? = null
    private val split = SplitState(mainPage = { mainPage })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScreenWindow.attach(this)

        val path = intent.getStringExtra(EXTRA_PATH)
        if (path.isNullOrBlank()) {
            AppLog.e(TAG, "Opened with no path")
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val newTemplate = PageTemplate.fromId(intent.getStringExtra(EXTRA_TEMPLATE))
        AppLog.i(TAG, "Opening $path")
        mainPage = PanePage.InkNote(path, title, newTemplate)
        watchAndroidSplit(split)
        intent.splitCarry()?.let { split.open(it.page, it.swapped) }

        val paneHost = object : PaneHost {
            override fun openBook(book: io.github.foxesrcool1.margin.core.books.LibraryBook) =
                openBookBeside(this@InkNoteActivity, split, book, this)

            override fun keeper(): Intent = io.github.foxesrcool1.margin.ui.split.BesideActivity.then(
                this@InkNoteActivity,
                InkNoteActivity.intent(this@InkNoteActivity, path, title, newTemplate),
            )

            override fun leave() = finish()

            override fun paneInk(canvas: InkCanvasView?) {
                paneCanvas = canvas
                // A swap or a turn moves the note. The fast pen has to follow it.
                canvas?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> startFastPen() }
                // The canvas has no size yet when it is brand new.
                (canvas ?: canvasView)?.post { startFastPen() }
            }
        }

        // The screen is up before the note is read, so a big note never
        // leaves a blank window.
        setContent {
            EinkTheme {
                SplitLayout(
                    split = split,
                    main = {
                        val ready = opened
                        if (ready == null) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(EinkColors.Paper).systemBarsPadding(),
                                contentAlignment = Alignment.Center,
                            ) { CapsLabel(text = "Opening") }
                        } else {
                            val made = ready.controller
                            InkNoteScreen(
                                title = title,
                                controller = made,
                                problem = ready.problem,
                                onCanvas = { view ->
                                    // A new canvas: the split screen or a turn
                                    // made the page change its layout. The
                                    // tablet must stop drawing over the old one
                                    // at once, not at the next layout.
                                    if (canvasView != null && canvasView !== view && session?.canvas === canvasView) {
                                        session?.stop()
                                        session = null
                                    }
                                    canvasView = view
                                    made.attach(view)
                                    // A turn of the screen, or the split
                                    // screen, moves the canvas. The fast pen
                                    // has to follow it.
                                    view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> startFastPen() }
                                },
                                onDialog = { open -> if (open) session?.stop() else startFastPen() },
                                onWidth = { session?.applyWidth(it) },
                                onExport = { asPdf -> export(made, ready.repository, title.ifBlank { "note" }, asPdf) },
                                onClose = { finish() },
                            )
                        }
                    },
                    pane = { SplitPane(split, paneHost) },
                )
            }
        }

        lifecycleScope.launch {
            val settings = SettingsStore(this@InkNoteActivity)
            fastPenMode = settings.fastPenMode.first()
            redrawDelay = settings.inkRedrawDelayMillis.first()

            val repository = InkNotesRepository(DataRoot.repository(this@InkNoteActivity))
            val started = System.currentTimeMillis()
            val load = withContext(AppDispatchers.io) { repository.load(path) }
            val (note, problem) = when (load) {
                is InkNoteLoad.Loaded -> load.note to null
                InkNoteLoad.Missing -> InkNote(template = newTemplate) to null
                is InkNoteLoad.Damaged -> InkNote() to load.reason
            }
            val took = System.currentTimeMillis() - started
            AppLog.i(
                TAG,
                "Read $path in $took ms: " +
                    "${note.pages.size} pages, ${note.pages.sumOf { it.strokes.size }} strokes",
            )
            io.github.foxesrcool1.margin.core.speed.SpeedWatch.check("Opening the handwritten note", took, io.github.foxesrcool1.margin.core.speed.SpeedWatch.Budget.OPEN_NOTE)

            val made = InkNoteController(repository, path, note, mayWrite = problem == null)
            controller = made
            opened = OpenedNote(made, problem, repository)
        }
    }

    /**
     * The tablet draws its fast line in one place at a time. With a
     * handwritten note in the second half, that place is the second half, as
     * beside a book. The page of this screen is then drawn by this app.
     */
    private fun startFastPen() {
        val view = paneCanvas ?: canvasView ?: return
        if (session?.canvas !== view) {
            session?.stop()
            session = null
        }
        if (fastPenMode == SettingsStore.FAST_PEN_OFF || view.width == 0) return
        val current = session ?: FastPenSession(this, view, fastPenMode, redrawDelay).also { session = it }
        current.start()
    }

    private fun export(
        controller: InkNoteController,
        repository: InkNotesRepository,
        name: String,
        asPdf: Boolean,
    ): String {
        val note = controller.snapshot()
        val page = controller.pageIndex
        lifecycleScope.launch(AppDispatchers.io) {
            runCatching {
                val written = if (asPdf) {
                    repository.writeExport("$name.pdf", InkExport.notePdf(note))
                } else {
                    repository.writeExport("$name page ${page + 1}.png", InkExport.pagePng(note, note.pages[page]))
                }
                AppLog.i(TAG, "Exported to $written")
            }.onFailure { AppLog.e(TAG, "Export failed", it) }
        }
        return if (asPdf) "The PDF goes to the exports folder" else "The picture goes to the exports folder"
    }

    override fun onResume() {
        super.onResume()
        startFastPen()
        io.github.foxesrcool1.margin.ui.split.AdjacentApps.takeRequest(this)
    }

    override fun onPause() {
        // The tablet must stop drawing on the glass before another screen shows.
        session?.stop()
        // Saved here and not in onStop. The screen that opened this one
        // resumes before this one stops, and it looks for the file when it
        // resumes. Found on the emulator: the Journal said "Handwrite" for a
        // day that had just been written on.
        controller?.saveAndWait()
        super.onPause()
    }

    override fun onDestroy() {
        controller?.close()
        super.onDestroy()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            finish()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    companion object {
        const val EXTRA_PATH = "ink_path"
        const val EXTRA_TITLE = "ink_title"
        const val EXTRA_TEMPLATE = "ink_template"

        fun intent(
            context: Context,
            path: String,
            title: String,
            templateForNew: PageTemplate = PageTemplate.Blank,
        ): Intent = Intent(context, InkNoteActivity::class.java)
            .putExtra(EXTRA_PATH, path)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_TEMPLATE, templateForNew.id)
    }
}

private class OpenedNote(val controller: InkNoteController, val problem: String?, val repository: InkNotesRepository)

/** The fine line between a rail of tools and the page. */
@Composable
internal fun VerticalRule() {
    Box(modifier = Modifier.fillMaxHeight().width(EinkDimens.hairline).background(EinkColors.Ink))
}

@Composable
internal fun InkNoteScreen(
    title: String,
    controller: InkNoteController,
    problem: String?,
    onCanvas: (InkCanvasView) -> Unit,
    onDialog: (open: Boolean) -> Unit,
    onWidth: (Float) -> Unit,
    onExport: (asPdf: Boolean) -> String,
    onClose: () -> Unit,
) {
    var canvas by remember { mutableStateOf<InkCanvasView?>(null) }
    var mode by remember { mutableStateOf(InkMode.Pen) }
    var width by remember { mutableFloatStateOf(PenWidths.MEDIUM) }
    var pageIndex by remember { mutableIntStateOf(controller.pageIndex) }
    var pageCount by remember { mutableIntStateOf(controller.pageCount) }
    var menuOpen by remember { mutableStateOf(false) }
    var templatesOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf(problem?.let { "This note could not be read and will not be changed: $it" }) }

    fun refreshPages() {
        pageIndex = controller.pageIndex
        pageCount = controller.pageCount
    }

    fun turn(by: Int) {
        if (controller.goTo(controller.pageIndex + by)) refreshPages()
    }

    fun pick(next: InkMode) {
        mode = next
        canvas?.mode = next
    }

    val drawTools: @Composable () -> Unit = {
        IconPressButton(icon = Lucide.X, label = "Close", onClick = onClose)
        InkModeButtons(mode = mode, onPick = ::pick)
        PenWidthButton(
            width = width,
            onNext = {
                width = PenWidths.all[(PenWidths.all.indexOf(width) + 1) % PenWidths.all.size]
                canvas?.penWidth = width
                onWidth(width)
            },
        )
        IconPressButton(icon = Lucide.Undo2, label = "Undo", onClick = { canvas?.undo() })
        IconPressButton(icon = Lucide.Redo2, label = "Redo", onClick = { canvas?.redo() })
    }
    val pageTools: @Composable () -> Unit = {
        IconPressButton(icon = Lucide.ChevronLeft, label = "Previous page", enabled = pageIndex > 0, onClick = { turn(-1) })
        IconPressButton(icon = Lucide.ChevronRight, label = "Next page", enabled = pageIndex < pageCount - 1, onClick = { turn(1) })
        IconPressButton(
            icon = Lucide.FilePlus,
            label = "Add a page",
            onClick = {
                controller.addPage()
                refreshPages()
            },
        )
        IconPressButton(
            icon = Lucide.Ellipsis,
            label = "More",
            onClick = {
                onDialog(true)
                menuOpen = true
            },
        )
    }
    val page: @Composable (Modifier) -> Unit = { pageModifier ->
        AndroidView(
            modifier = pageModifier,
            factory = { context ->
                InkCanvasView(context).also { view ->
                    view.onPageSwipe = { direction -> turn(direction) }
                    canvas = view
                    onCanvas(view)
                }
            },
        )
    }
    val pageLabel = "${pageIndex + 1} of $pageCount"

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(EinkColors.Paper)
            .systemBarsPadding(),
    ) {
        // Seven tools of 56 dp need a rail of 400 dp or a row of 408 dp. The
        // shape of the room says which, unless that one does not fit and the
        // other does: half of a split screen is wide and short upright, and
        // narrow and tall on its side.
        val railFits = maxHeight >= 400.dp
        val rowFits = maxWidth >= 408.dp
        val rails = if (maxWidth > maxHeight) railFits || !rowFits else !rowFits && railFits
        if (rails) {
            // On its side the tablet has width to spare and no height, so the
            // tools stand in a rail down each edge and the page keeps the
            // whole height.
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxHeight().padding(4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) { drawTools() }
                VerticalRule()
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    notice?.let {
                        EinkText(text = it, style = EinkType.body, maxLines = 2, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    }
                    page(Modifier.fillMaxSize())
                }
                VerticalRule()
                Column(
                    modifier = Modifier.fillMaxHeight().padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RotateButton()
                    SplitButton()
                    pageTools()
                    Spacer(modifier = Modifier.weight(1f))
                    CapsLabel(text = pageLabel, style = EinkType.capsSmall)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) { drawTools() }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    pageTools()
                    CapsLabel(
                        text = pageLabel + if (title.isBlank()) "" else "  .  $title",
                        style = EinkType.capsSmall,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    SplitButton()
                    RotateButton()
                }
                notice?.let {
                    EinkText(text = it, style = EinkType.body, maxLines = 2, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
                HairlineDivider()
                page(Modifier.fillMaxSize())
            }
        }
    }

    if (menuOpen) {
        OptionsDialog(
            title = "Page ${pageIndex + 1}",
            options = listOf(
                DialogOption("Page template", icon = Lucide.FileText) { menuOpen = false; templatesOpen = true },
                DialogOption("Export this page as a picture", icon = Lucide.Image) { menuOpen = false; onDialog(false); notice = onExport(false) },
                DialogOption("Export the note as a PDF", icon = Lucide.Share) { menuOpen = false; onDialog(false); notice = onExport(true) },
                DialogOption("Clear this page", icon = Lucide.Eraser) { menuOpen = false; confirmClear = true },
                DialogOption("Delete this page", icon = Lucide.Trash2) { menuOpen = false; confirmDelete = true },
            ),
            onDismiss = { menuOpen = false; onDialog(false) },
        )
    }

    if (templatesOpen) {
        OptionsDialog(
            title = "Page template",
            options = PageTemplate.entries.map { template ->
                DialogOption(template.label) {
                    controller.changeTemplate(template)
                    templatesOpen = false
                    onDialog(false)
                }
            },
            onDismiss = { templatesOpen = false; onDialog(false) },
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear this page?",
            message = "All ink on this page goes. Undo brings it back until you leave the page.",
            confirmText = "Clear",
            cancelText = "Keep",
            onConfirm = { canvas?.clearPage(); confirmClear = false; onDialog(false) },
            onDismiss = { confirmClear = false; onDialog(false) },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this page?",
            message = "The page and its ink go for good.",
            confirmText = "Delete",
            cancelText = "Keep",
            onConfirm = { controller.deletePage(); refreshPages(); confirmDelete = false; onDialog(false) },
            onDismiss = { confirmDelete = false; onDialog(false) },
        )
    }
}
