package io.github.foxesrcool1.margin.ui.writing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.foxesrcool1.margin.core.ink.InkNoteLoad
import io.github.foxesrcool1.margin.core.ink.InkNotesRepository
import io.github.foxesrcool1.margin.core.ink.PageTemplate
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.threads.AppDispatchers
import io.github.foxesrcool1.margin.ui.ink.InkExport
import io.github.foxesrcool1.margin.core.notes.NoteEntry
import io.github.foxesrcool1.margin.core.notes.NotesRepository
import io.github.foxesrcool1.margin.core.storage.DataRoot
import io.github.foxesrcool1.margin.core.storage.RelativePaths
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.CapsLabel
import io.github.foxesrcool1.margin.design.components.ConfirmDialog
import io.github.foxesrcool1.margin.design.components.DialogOption
import io.github.foxesrcool1.margin.design.components.EinkIcon
import io.github.foxesrcool1.margin.design.components.EinkRow
import io.github.foxesrcool1.margin.design.components.EinkText
import io.github.foxesrcool1.margin.design.components.HairlineDivider
import io.github.foxesrcool1.margin.design.components.IconPressButton
import io.github.foxesrcool1.margin.design.components.InvertPressButton
import io.github.foxesrcool1.margin.design.components.OptionsDialog
import io.github.foxesrcool1.margin.design.components.PagedList
import io.github.foxesrcool1.margin.design.components.Plants
import io.github.foxesrcool1.margin.design.components.TextPromptDialog
import io.github.foxesrcool1.margin.design.icons.Lucide
import io.github.foxesrcool1.margin.ui.common.ScreenScaffold
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "WritingScreen"
private const val ROWS_PER_PAGE = 5

/**
 * The Writing tab: a file browser over the notes folder.
 *
 * Moving is done by marking something and then pressing Paste in the folder it
 * should land in. Typing a path on a tablet with no keyboard is worse than two
 * taps, and the pen cannot drag and drop on e-ink without an animation.
 *
 * A handwritten note opens in the ink screen, a typed note in the editor.
 */
@Composable
fun WritingScreen(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
    newNoteRequests: Int = 0,
    /** True once for each request. The tab is opened again later without a new request. */
    takeNewNoteRequest: () -> Boolean = { false },
    onOpenInkNote: (path: String, title: String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notes = remember(context) { NotesRepository(DataRoot.repository(context)) }
    val inkNotes = remember(context) { InkNotesRepository(DataRoot.repository(context)) }

    var folder by remember { mutableStateOf(notes.rootPath) }
    var rows by remember { mutableStateOf<List<NoteEntry>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }

    var optionsFor by remember { mutableStateOf<NoteEntry?>(null) }
    var renaming by remember { mutableStateOf<NoteEntry?>(null) }
    var deleting by remember { mutableStateOf<NoteEntry?>(null) }
    var marked by remember { mutableStateOf<NoteEntry?>(null) }
    var addingNote by remember { mutableStateOf(false) }
    var choosingKind by remember { mutableStateOf(false) }
    var addingInkNote by remember { mutableStateOf<PageTemplate?>(null) }
    var addingFolder by remember { mutableStateOf(false) }

    LaunchedEffect(folder, refresh) {
        rows = withContext(AppDispatchers.io) { notes.list(folder) }
    }

    // Back goes up one folder, like the arrow does, and leaves only from the top.
    BackHandler(enabled = !notes.isRoot(folder)) { folder = notes.parentOf(folder) }

    // A request is taken once. This runs again with every new composition of
    // the tab, and an old request must not open the dialog a second time.
    LaunchedEffect(newNoteRequests) {
        if (takeNewNoteRequest()) addingNote = true
    }

    // Coming back from a note has to show its new title and preview.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    // The activity stays resumed when a note was written in a window beside
    // this one, with Android's split screen, and then no resume comes. The
    // window getting the focus back is the sign. The list is read again, and a
    // list that did not change draws nothing.
    val focused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
    var lostFocus by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (!focused) {
            lostFocus = true
        } else if (lostFocus) {
            lostFocus = false
            refresh++
        }
    }

    fun openNewNote(title: String) {
        scope.launch {
            val path = withContext(AppDispatchers.io) { notes.createNote(folder, title) }
            refresh++
            if (path != null) {
                onOpenNote(path)
            } else {
                status = "The note could not be made"
            }
        }
    }

    fun openNewInkNote(title: String, template: PageTemplate) {
        scope.launch {
            // A pen user may have no keyboard in reach. No title is fine.
            val name = title.ifBlank {
                "Handwritten " + java.time.LocalDateTime.now().withNano(0).toString().replace(':', '-')
            }
            val path = withContext(AppDispatchers.io) { inkNotes.create(folder, name, template) }
            refresh++
            if (path != null) {
                onOpenInkNote(path, name)
            } else {
                status = "The note could not be made"
            }
        }
    }

    fun export(entry: NoteEntry) {
        scope.launch {
            val written = withContext(AppDispatchers.io) {
                runCatching {
                    val stem = entry.name.substringBeforeLast('.')
                    if (StorageLayout.isInkNote(entry.name)) {
                        val load = inkNotes.load(entry.path) as? InkNoteLoad.Loaded ?: return@runCatching null
                        inkNotes.writeExport("$stem.pdf", InkExport.notePdf(load.note))
                    } else {
                        inkNotes.writeExport("$stem.pdf", TypedNoteExport.pdf(context, notes.read(entry.path)))
                    }
                }.onFailure { AppLog.e(TAG, "Export of ${entry.path} failed", it) }.getOrNull()
            }
            status = if (written != null) "Saved as $written" else "The export did not work"
        }
    }

    ScreenScaffold(
        title = "Write",
        overline = if (notes.isRoot(folder)) "All notes" else folder.removePrefix("${StorageLayout.NOTES}/"),
        plant = Plants.Writing,
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconPressButton(icon = Lucide.FilePlus, label = "New note", bordered = true, onClick = { choosingKind = true })
            IconPressButton(icon = Lucide.FolderPlus, label = "New folder", onClick = { addingFolder = true })
            IconPressButton(
                icon = Lucide.FolderUp,
                label = "Up one folder",
                enabled = !notes.isRoot(folder),
                onClick = { folder = notes.parentOf(folder) },
            )
        },
    ) {
        if (marked != null || status != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EinkDimens.targetGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val pending = marked
                if (pending != null) {
                    InvertPressButton(
                        text = "Paste here",
                        icon = Lucide.FolderInput,
                        onClick = {
                            scope.launch {
                                val moved = withContext(AppDispatchers.io) {
                                    notes.move(pending.path, folder)
                                }
                                status = if (moved) {
                                    "Moved ${pending.name}"
                                } else {
                                    "That move is not possible"
                                }
                                marked = null
                                refresh++
                            }
                        },
                    )
                    IconPressButton(
                        icon = Lucide.X,
                        label = "Cancel the move",
                        onClick = {
                            marked = null
                            status = null
                        },
                    )
                }
                if (status != null) {
                    CapsLabel(
                        text = status.orEmpty(),
                        style = EinkType.capsSmall.copy(color = EinkColors.Faded),
                        maxLines = 2,
                    )
                }
            }
        }

        PagedList(
            items = rows,
            pageSize = ROWS_PER_PAGE,
            rowHeight = EinkDimens.rowTwoLines,
            emptyText = "No notes in here yet",
            modifier = Modifier.weight(1f),
        ) { _, row ->
            NoteRow(
                entry = row,
                marked = marked?.path == row.path,
                onOpen = {
                    if (row.isFolder) {
                        folder = row.path
                        status = null
                    } else if (StorageLayout.isTypedNote(row.name)) {
                        onOpenNote(row.path)
                    } else if (StorageLayout.isInkNote(row.name)) {
                        onOpenInkNote(row.path, row.title)
                    } else {
                        status = "${row.name} is not a note this app can open"
                    }
                },
                onOptions = { optionsFor = row },
            )
        }
    }

    val selected = optionsFor
    if (selected != null) {
        OptionsDialog(
            title = selected.title,
            onDismiss = { optionsFor = null },
            options = listOfNotNull(
                DialogOption(label = "Export as PDF", icon = Lucide.Share) {
                    export(selected)
                    optionsFor = null
                }.takeIf { !selected.isFolder },
                DialogOption(label = "Rename", icon = Lucide.Pencil) {
                    renaming = selected
                    optionsFor = null
                },
                DialogOption(label = "Move", icon = Lucide.FolderInput) {
                    marked = selected
                    status = "Open a folder, then press Paste here"
                    optionsFor = null
                },
                DialogOption(label = "Delete", icon = Lucide.Trash2) {
                    deleting = selected
                    optionsFor = null
                },
            ),
        )
    }

    if (choosingKind) {
        OptionsDialog(
            title = "New note",
            onDismiss = { choosingKind = false },
            options = listOf(
                DialogOption(label = "Typed", icon = Lucide.Keyboard) {
                    choosingKind = false
                    addingNote = true
                },
            ) + PageTemplate.entries.map { template ->
                DialogOption(label = "Handwritten, ${template.label.lowercase()}", icon = Lucide.Signature) {
                    choosingKind = false
                    addingInkNote = template
                }
            },
        )
    }

    val inkTemplate = addingInkNote
    if (inkTemplate != null) {
        TextPromptDialog(
            title = "Name, or leave it empty",
            confirmText = "Write",
            allowEmpty = true,
            onConfirm = { title ->
                addingInkNote = null
                openNewInkNote(title, inkTemplate)
            },
            onDismiss = { addingInkNote = null },
        )
    }

    if (addingNote) {
        TextPromptDialog(
            title = "New note",
            confirmText = "Write",
            onConfirm = { title ->
                addingNote = false
                openNewNote(title)
            },
            onDismiss = { addingNote = false },
        )
    }

    if (addingFolder) {
        TextPromptDialog(
            title = "New folder",
            confirmText = "Make",
            onConfirm = { name ->
                addingFolder = false
                scope.launch {
                    val made = withContext(AppDispatchers.io) { notes.createFolder(folder, name) }
                    if (!made) status = "The folder could not be made"
                    refresh++
                }
            },
            onDismiss = { addingFolder = false },
        )
    }

    val beingRenamed = renaming
    if (beingRenamed != null) {
        TextPromptDialog(
            title = "Rename",
            // The name the row shows. For a typed note that is its heading.
            initialValue = when {
                beingRenamed.isFolder -> beingRenamed.name
                StorageLayout.isTypedNote(beingRenamed.name) -> beingRenamed.title
                else -> beingRenamed.name.substringBeforeLast('.')
            },
            onConfirm = { name ->
                renaming = null
                scope.launch {
                    val renamed = withContext(AppDispatchers.io) { notes.rename(beingRenamed.path, name) }
                    // The move waited for the old name, which is gone now.
                    if (isAtOrInside(marked, beingRenamed.path)) {
                        marked = null
                        status = null
                    }
                    if (renamed == null) status = "Could not rename it"
                    refresh++
                }
            },
            onDismiss = { renaming = null },
        )
    }

    val beingDeleted = deleting
    if (beingDeleted != null) {
        ConfirmDialog(
            title = "Delete ${beingDeleted.name}?",
            message = if (beingDeleted.isFolder) {
                "The folder and everything in it goes away and cannot come back."
            } else {
                "The file goes away and cannot come back."
            },
            confirmText = "Delete",
            cancelText = "Keep",
            onConfirm = {
                deleting = null
                scope.launch {
                    val gone = withContext(AppDispatchers.io) { notes.delete(beingDeleted.path) }
                    status = if (gone) "Deleted ${beingDeleted.name}" else "Could not delete it"
                    AppLog.i(TAG, "Delete of ${beingDeleted.path}: $gone")
                    // A note marked for a move is gone too when its folder is.
                    if (isAtOrInside(marked, beingDeleted.path)) marked = null
                    refresh++
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

/** True when [entry] is the thing at [path], or inside the folder at [path]. */
private fun isAtOrInside(entry: NoteEntry?, path: String): Boolean =
    entry != null && (entry.path == path || entry.path.startsWith("$path/"))

@Composable
private fun NoteRow(
    entry: NoteEntry,
    marked: Boolean,
    onOpen: () -> Unit,
    onOptions: () -> Unit,
) {
    EinkRow(onClick = onOpen, onLongClick = onOptions) { pressed ->
        val foreground = if (pressed) EinkColors.Paper else EinkColors.Ink
        val faded = if (pressed) EinkColors.Paper else EinkColors.Faded

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        EinkIcon(
            icon = when {
                entry.isFolder -> Lucide.Folder
                StorageLayout.isInkNote(entry.name) -> Lucide.Signature
                else -> Lucide.FileText
            },
            color = foreground,
        )
        Column(modifier = Modifier.weight(1f)) {
            EinkText(
                text = if (marked) "${entry.title}   (moving)" else entry.title,
                style = EinkType.rowTitle.copy(color = foreground),
                maxLines = 1,
            )
            val second = when {
                entry.isFolder -> "Folder"
                entry.preview.isNotBlank() -> entry.preview
                StorageLayout.isInkNote(entry.name) -> "Handwritten note"
                else -> RelativePaths.extensionOf(entry.name).uppercase()
            }
            CapsLabel(
                text = second,
                style = EinkType.capsSmall.copy(color = faded),
                maxLines = 1,
            )
        }
        }
    }
    HairlineDivider(color = EinkColors.Faded)
}
