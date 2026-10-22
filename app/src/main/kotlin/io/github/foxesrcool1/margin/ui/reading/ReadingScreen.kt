package io.github.foxesrcool1.margin.ui.reading

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlin.math.roundToInt
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import io.github.foxesrcool1.margin.ui.split.PageOpener
import io.github.foxesrcool1.margin.ui.split.rememberPageOpener
import io.github.foxesrcool1.margin.core.books.BooksRepository
import io.github.foxesrcool1.margin.core.books.LibraryBook
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.threads.AppDispatchers
import io.github.foxesrcool1.margin.core.storage.DataRoot
import io.github.foxesrcool1.margin.core.storage.ImportResult
import io.github.foxesrcool1.margin.core.storage.RelativePaths
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.CapsLabel
import io.github.foxesrcool1.margin.design.components.ConfirmDialog
import io.github.foxesrcool1.margin.design.components.DialogOption
import io.github.foxesrcool1.margin.design.components.EinkRow
import io.github.foxesrcool1.margin.design.components.EinkText
import io.github.foxesrcool1.margin.design.components.HairlineDivider
import io.github.foxesrcool1.margin.design.components.InvertPressButton
import io.github.foxesrcool1.margin.design.components.OptionsDialog
import io.github.foxesrcool1.margin.design.components.PagedList
import io.github.foxesrcool1.margin.ui.common.ScreenScaffold
import io.github.foxesrcool1.margin.design.components.IconPressButton
import io.github.foxesrcool1.margin.design.components.Plants
import io.github.foxesrcool1.margin.design.icons.Lucide
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.foxesrcool1.margin.core.habits.DayBoundary
import io.github.foxesrcool1.margin.core.habits.HabitsRepository
import io.github.foxesrcool1.margin.core.reading.ReadingLog
import io.github.foxesrcool1.margin.core.reading.ReadingRepository
import io.github.foxesrcool1.margin.core.settings.ReaderSettings
import io.github.foxesrcool1.margin.core.settings.SettingsStore
import io.github.foxesrcool1.margin.ui.reading.epub.EpubReaderActivity
import io.github.foxesrcool1.margin.ui.reading.epub.GoalLine
import io.github.foxesrcool1.margin.ui.reading.pdf.PdfReaderActivity

private const val TAG = "ReadingScreen"
private const val ROWS_PER_PAGE = 5

private enum class LibrarySort { Recent, Title }

/**
 * The Reading tab: the library.
 *
 * The reader itself is not here. Step 7 builds it on the Readium toolkit, and
 * plan section 8 says to judge Readium on the real panel before building more
 * on it. The library, the import and the title reading do not depend on that
 * decision, so they are here already.
 */
@Composable
fun ReadingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val books = remember(context) { BooksRepository(DataRoot.repository(context)) }
    val opener = rememberPageOpener()

    var rows by remember { mutableStateOf<List<LibraryBook>>(emptyList()) }
    var sort by remember { mutableStateOf(LibrarySort.Recent) }
    var refresh by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<LibraryBook?>(null) }
    var deleting by remember { mutableStateOf<LibraryBook?>(null) }

    val reading = remember(context) { ReadingRepository(DataRoot.repository(context)) }
    val settings = remember(context) { SettingsStore(context) }
    val readerSettings by settings.reader.collectAsStateWithLifecycle(initialValue = ReaderSettings())
    var progress by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var secondsToday by remember { mutableStateOf(0L) }

    // Coming back from the reader has to show the new progress and the new
    // reading time, so the list is read again every time the screen resumes.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }

    LaunchedEffect(sort, refresh) {
        val loaded = withContext(AppDispatchers.io) {
            val list = if (sort == LibrarySort.Title) books.byTitle() else books.byRecent()
            val data = DataRoot.repository(context)
            val hour = runCatching { HabitsRepository(data).load().dayBoundaryHour }.getOrDefault(DayBoundary.DEFAULT_HOUR)
            val today = DayBoundary(hour).dateOf(java.time.Instant.now(), java.time.ZoneId.systemDefault())
            Triple(list, list.associate { it.bookId to reading.progressOf(it.bookId) }, reading.secondsReadOn(today))
        }
        rows = loaded.first
        progress = loaded.second
        secondsToday = loaded.third
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            status = "Import cancelled"
            return@rememberLauncherForActivityResult
        }
        busy = true
        status = "Copying the file in"
        scope.launch {
            status = withContext(AppDispatchers.io) { importFrom(context, books, uri) }
            busy = false
            refresh++
        }
    }

    ScreenScaffold(
        title = "Read",
        overline = when (rows.size) {
            0 -> "Library"
            1 -> "1 book"
            else -> "${rows.size} books"
        },
        plant = Plants.Reading,
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconPressButton(
                icon = Lucide.BookPlus,
                label = "Add a book",
                enabled = !busy,
                bordered = true,
                onClick = {
                    runCatching {
                        picker.launch(
                            arrayOf("application/epub+zip", "application/pdf"),
                        )
                    }.onFailure {
                        AppLog.e(TAG, "No app could open a file", it)
                        status = "This tablet has no file picker"
                    }
                },
            )
            IconPressButton(
                icon = Lucide.History,
                label = "Sort by last read",
                selected = sort == LibrarySort.Recent,
                onClick = { sort = LibrarySort.Recent },
            )
            IconPressButton(
                icon = Lucide.ArrowDownAZ,
                label = "Sort by title",
                selected = sort == LibrarySort.Title,
                onClick = { sort = LibrarySort.Title },
            )
        },
    ) {
        if (status != null) {
            CapsLabel(
                text = status.orEmpty(),
                style = EinkType.capsSmall.copy(color = EinkColors.Faded),
                maxLines = 2,
            )
        }

        if (readerSettings.goalMinutes > 0) {
            CapsLabel(
                text = "${secondsToday / 60} / ${readerSettings.goalMinutes} min today",
                style = EinkType.capsSmall,
            )
            Spacer(modifier = Modifier.height(6.dp))
            GoalLine(ReadingLog.goalFraction(secondsToday, readerSettings.goalMinutes))
            Spacer(modifier = Modifier.height(EinkDimens.targetGap))
        }

        PagedList(
            items = rows,
            pageSize = ROWS_PER_PAGE,
            rowHeight = EinkDimens.rowTwoLines,
            emptyText = "No books yet. Press the book with the plus sign.",
            modifier = Modifier.weight(1f),
        ) { _, book ->
            BookRow(
                book = book,
                progress = progress[book.bookId] ?: 0.0,
                onOpen = { openBook(opener, book) { status = it } },
                onOptions = { optionsFor = book },
            )
        }
    }

    val selected = optionsFor
    if (selected != null) {
        OptionsDialog(
            title = selected.metadata.title,
            onDismiss = { optionsFor = null },
            options = listOf(
                DialogOption(
                    label = selected.metadata.author ?: "No author in the file",
                    enabled = false,
                    onSelect = { },
                ),
                DialogOption(label = "Size: ${selected.sizeLabel()}", enabled = false) { },
                DialogOption(label = "Delete", icon = Lucide.Trash2) {
                    deleting = selected
                    optionsFor = null
                },
            ),
        )
    }

    val beingDeleted = deleting
    if (beingDeleted != null) {
        ConfirmDialog(
            title = "Delete ${beingDeleted.metadata.title}?",
            message = "The book and every note you made on it go away and cannot come back.",
            confirmText = "Delete",
            cancelText = "Keep",
            onConfirm = {
                deleting = null
                scope.launch {
                    val gone = withContext(AppDispatchers.io) { books.delete(beingDeleted) }
                    status = if (gone) "Deleted" else "Could not delete it"
                    refresh++
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun BookRow(
    book: LibraryBook,
    progress: Double,
    onOpen: () -> Unit,
    onOptions: () -> Unit,
) {
    EinkRow(onClick = onOpen, onLongClick = onOptions) { pressed ->
        val foreground = if (pressed) EinkColors.Paper else EinkColors.Ink
        val faded = if (pressed) EinkColors.Paper else EinkColors.Faded

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                EinkText(
                    text = book.metadata.title,
                    style = EinkType.rowTitle.copy(color = foreground),
                    maxLines = 1,
                )
                val author = book.metadata.author
                if (author != null && author.isNotBlank()) {
                    CapsLabel(
                        text = author,
                        style = EinkType.capsSmall.copy(color = faded),
                        maxLines = 1,
                    )
                }
            }
            val mark = progressMark(progress)
            if (mark != null) {
                CapsLabel(
                    text = mark,
                    style = EinkType.capsSmall.copy(color = foreground),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
    HairlineDivider(color = EinkColors.Faded)
}

/** The small mark at the right end of a library row: "12%", "Done" at 100%, nothing for a book not started. */
internal fun progressMark(progress: Double): String? {
    if (progress <= 0.0) return null
    val percent = (progress * 100).roundToInt()
    return if (percent >= 100) "Done" else "${percent.coerceAtLeast(1)}%"
}

/** A screen of its own for the book, and the split screen, if one is open, goes along. See [PageOpener]. */
private fun openBook(opener: PageOpener, book: LibraryBook, say: (String) -> Unit) {
    runCatching { opener.openBook(book) }.onFailure {
        AppLog.e(TAG, "Could not open ${book.path}", it)
        say("The book could not be opened")
    }
}

/**
 * Copies the picked file into `books/`.
 *
 * The app never reads a book from where the user picked it. A file outside the
 * data folder can be moved or deleted, and then the reading position and every
 * note point at nothing.
 */
private fun importFrom(context: Context, books: BooksRepository, uri: Uri): String {
    val (pickedName, size) = nameAndSizeOf(context, uri)
    val name = pickedName ?: "book.epub"

    return runCatching {
        val stream = context.contentResolver.openInputStream(uri)
            ?: return "The file could not be opened"

        stream.use { input ->
            when (val result = books.import(input, name, size)) {
                // The stored name, which has a number on it when another book had the name first.
                is ImportResult.Imported -> "Imported ${RelativePaths.nameOf(result.relativePath)}"
                is ImportResult.AlreadyThere -> "$name is already in the library"
                is ImportResult.Failed -> result.reason
            }
        }
    }.getOrElse { error ->
        AppLog.e(TAG, "Import failed", error)
        "Import failed: ${error.message}"
    }
}

/** The name and the size the picker gives for a file. Either can be missing: null, and -1. */
private fun nameAndSizeOf(context: Context, uri: Uri): Pair<String?, Long> = runCatching {
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val name = cursor.getString(0)
            val size = if (cursor.isNull(1)) -1L else cursor.getLong(1)
            name to size
        }
}.getOrNull() ?: (null to -1L)
