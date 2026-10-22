package io.github.foxesrcool1.margin.ui.split

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.foxesrcool1.margin.core.ink.InkNote
import io.github.foxesrcool1.margin.core.ink.InkNoteLoad
import io.github.foxesrcool1.margin.core.ink.InkNotesRepository
import io.github.foxesrcool1.margin.core.ink.PageTemplate
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.threads.AppDispatchers
import io.github.foxesrcool1.margin.core.storage.DataRoot
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.CapsLabel
import io.github.foxesrcool1.margin.design.components.EinkText
import io.github.foxesrcool1.margin.design.components.HairlineDivider
import io.github.foxesrcool1.margin.design.components.IconPressButton
import io.github.foxesrcool1.margin.design.icons.Lucide
import io.github.foxesrcool1.margin.ui.ink.InkCanvasView
import io.github.foxesrcool1.margin.ui.ink.InkMode
import io.github.foxesrcool1.margin.ui.ink.InkNoteController
import io.github.foxesrcool1.margin.ui.writing.NoteField
import io.github.foxesrcool1.margin.ui.writing.rememberNoteEditor
import kotlinx.coroutines.withContext

private const val TAG = "NotePane"

/**
 * The note beside a book: the writing half of the split screen.
 *
 * Every book has one typed note and one handwritten note of its own, in
 * `notes/Reading notes/`, named after the book. They are plain notes like any
 * other, so the Writing tab shows them, a backup holds them, and a note the
 * user made there by hand under the same name is the one that opens here.
 *
 * One icon at the top switches between typing and handwriting. The pane keeps
 * its tools to one or two rows, because in landscape it is only half a tablet
 * wide. It stands in the second half of the split screen, and the controls of
 * that half end its top line.
 *
 * [onInkCanvas] tells the screen around it which canvas is on show, or null
 * when there is none, so that screen can point the fast pen of the tablet at
 * it. The tablet draws the fast line in one place at a time.
 */
@Composable
fun NotePane(
    bookTitle: String,
    onInkCanvas: (InkCanvasView?) -> Unit,
    modifier: Modifier = Modifier,
    /** To the choice of pages for the second half. */
    onBack: (() -> Unit)? = null,
) {
    var handwritten by rememberSaveable { mutableStateOf(false) }
    val title = bookTitle.ifBlank { UNTITLED_BOOK }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EinkColors.Paper)
            .imePadding(),
    ) {
        if (handwritten) {
            InkPane(
                path = StorageLayout.readingNotePath(title, handwritten = true),
                templateForNew = PageTemplate.Lined,
                leading = {
                    if (onBack != null) BackButton(onBack)
                    KindButtons(handwritten = true, onPick = { handwritten = it })
                },
                leadingCount = if (onBack != null) 2 else 1,
                onInkCanvas = onInkCanvas,
            )
        } else {
            val editor = rememberNoteEditor(
                notePath = StorageLayout.readingNotePath(title, handwritten = false),
                textWhenNew = "# $title\n\n",
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) BackButton(onBack)
                KindButtons(handwritten = false, onPick = { handwritten = it })
                Spacer(modifier = Modifier.weight(1f))
                PaneControls()
            }
            HairlineDivider()
            NoteField(
                editor = editor,
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
        }
    }
}

/**
 * One button that leads to the other kind of note. The pane itself shows which
 * kind is open, and a pair of buttons with one of them black sat right beside
 * the pen and the eraser, where one of them is black as well.
 */
@Composable
private fun KindButtons(handwritten: Boolean, onPick: (Boolean) -> Unit) {
    if (handwritten) {
        IconPressButton(icon = Lucide.Keyboard, label = "Typed note", bordered = true, onClick = { onPick(false) })
    } else {
        IconPressButton(icon = Lucide.Signature, label = "Handwritten note", bordered = true, onClick = { onPick(true) })
    }
}

/** Back to the page before, in the second half. */
@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconPressButton(icon = Lucide.ArrowLeft, label = "Back", onClick = onBack)
}

/** What the pane knows about the handwritten note once it has been read. */
private class OpenInkNote(val controller: InkNoteController, val problem: String?)

/**
 * A handwritten note in the second half: a small line of tools and the page.
 * [leading] is the first control of the line: the switch to the typed note of
 * a book, or the way back to the page the note was opened from.
 */
@Composable
internal fun InkPane(
    path: String,
    templateForNew: PageTemplate,
    leading: @Composable () -> Unit,
    onInkCanvas: (InkCanvasView?) -> Unit,
    /** How many targets [leading] holds, for the sum of what fits one row. */
    leadingCount: Int = 1,
) {
    val context = LocalContext.current
    var open by remember(path) { mutableStateOf<OpenInkNote?>(null) }
    var canvas by remember { mutableStateOf<InkCanvasView?>(null) }
    var mode by remember { mutableStateOf(InkMode.Pen) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(1) }

    LaunchedEffect(path) {
        val repository = InkNotesRepository(DataRoot.repository(context))
        val started = System.currentTimeMillis()
        val (stamp, load) = withContext(AppDispatchers.io) { repository.stampOf(path) to repository.load(path) }
        io.github.foxesrcool1.margin.core.speed.SpeedWatch.check("Opening the handwritten note", System.currentTimeMillis() - started, io.github.foxesrcool1.margin.core.speed.SpeedWatch.Budget.OPEN_NOTE)
        val (note, problem) = when (load) {
            is InkNoteLoad.Loaded -> load.note to null
            InkNoteLoad.Missing -> InkNote(template = templateForNew) to null
            is InkNoteLoad.Damaged -> InkNote() to load.reason
        }
        if (problem != null) AppLog.e(TAG, "Could not read $path: $problem")
        val controller = InkNoteController(repository, path, note, mayWrite = problem == null)
        controller.diskStamp = stamp
        pageCount = controller.pageCount
        open = OpenInkNote(controller, problem)
    }

    // The ink is saved when the pane goes and when the app goes to the back,
    // for the same reason as in the full screen note: Android may end a
    // stopped app without another word.
    DisposableEffect(path) {
        onDispose {
            onInkCanvas(null)
            open?.controller?.let {
                it.saveAndWait()
                it.close()
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { open?.controller?.saveAndWait() }

    fun refreshPages() {
        val controller = open?.controller ?: return
        pageIndex = controller.pageIndex
        pageCount = controller.pageCount
    }

    // Another screen may have written this note while the app was away. A pane
    // that saves its older copy later would put that copy over the newer file.
    // With nothing unsaved here, a file newer than what this pane wrote or read
    // last is read again. Ink that is not saved yet is never touched.
    val resumes = remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes.intValue++ }
    LaunchedEffect(resumes.intValue) {
        val controller = open?.controller ?: return@LaunchedEffect
        if (!controller.isClean()) return@LaunchedEffect
        val repository = InkNotesRepository(DataRoot.repository(context))
        val (stamp, load) = withContext(AppDispatchers.io) {
            val stamp = repository.stampOf(path)
            stamp to if (stamp != controller.diskStamp) repository.load(path) else null
        }
        if (load is InkNoteLoad.Loaded && controller.isClean()) {
            controller.reload(load.note, stamp)
            refreshPages()
            AppLog.i(TAG, "Read $path again: it was changed elsewhere")
        }
    }

    fun turn(by: Int) {
        val controller = open?.controller ?: return
        val onLastPage = controller.pageIndex == controller.pageCount - 1
        if (by > 0 && onLastPage) {
            // Forward from the last page makes a new one. The pane has no room
            // for a button of its own for that. An empty last page stays the
            // last page, so a row of taps cannot make a stack of blank ones.
            if (canvas?.editor?.strokes?.isNotEmpty() == true) controller.addPage()
        } else {
            controller.goTo(controller.pageIndex + by)
        }
        refreshPages()
    }

    val noteTools: @Composable () -> Unit = {
        IconPressButton(
            icon = Lucide.PenLine,
            label = "Pen",
            selected = mode == InkMode.Pen,
            onClick = {
                mode = InkMode.Pen
                canvas?.mode = InkMode.Pen
            },
        )
        IconPressButton(
            icon = Lucide.Eraser,
            label = "Eraser",
            selected = mode == InkMode.Eraser,
            onClick = {
                mode = InkMode.Eraser
                canvas?.mode = InkMode.Eraser
            },
        )
        IconPressButton(icon = Lucide.Undo2, label = "Undo", onClick = { canvas?.undo() })
        IconPressButton(icon = Lucide.ChevronLeft, label = "Previous page", enabled = pageIndex > 0, onClick = { turn(-1) })
        IconPressButton(
            icon = if (pageIndex >= pageCount - 1) Lucide.FilePlus else Lucide.ChevronRight,
            label = if (pageIndex >= pageCount - 1) "Add a page" else "Next page",
            onClick = { turn(1) },
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Targets of 56 dp: the first ones, five tools, and the two of the
        // split screen. Upright the pane is as wide as the tablet and they
        // mostly fit one row. On its side they need two.
        val oneRow = maxWidth >= 56.dp * (leadingCount + 7) + 16.dp
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                leading()
                if (oneRow) noteTools()
                Spacer(modifier = Modifier.weight(1f))
                if (!oneRow) CapsLabel(text = "${pageIndex + 1} of $pageCount", style = EinkType.capsSmall)
                PaneControls()
            }
            if (!oneRow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) { noteTools() }
            }
        }
    }
    HairlineDivider()

    val ready = open
    when {
        ready == null -> Box(modifier = Modifier.fillMaxSize())

        else -> {
            if (ready.problem != null) {
                EinkText(
                    text = "This note could not be read and will not be changed: ${ready.problem}",
                    style = EinkType.help,
                    maxLines = 3,
                    modifier = Modifier.padding(12.dp),
                )
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    InkCanvasView(viewContext).also { view ->
                        view.onPageSwipe = { direction -> turn(direction) }
                        canvas = view
                        ready.controller.attach(view)
                        onInkCanvas(view)
                    }
                },
            )
        }
    }
}
