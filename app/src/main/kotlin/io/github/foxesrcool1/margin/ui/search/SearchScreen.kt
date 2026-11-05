package io.github.foxesrcool1.margin.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import io.github.foxesrcool1.margin.core.books.BooksRepository
import io.github.foxesrcool1.margin.core.books.LibraryBook
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.search.SearchHit
import io.github.foxesrcool1.margin.core.search.SearchKind
import io.github.foxesrcool1.margin.core.search.SearchSource
import io.github.foxesrcool1.margin.core.search.search
import io.github.foxesrcool1.margin.core.speed.SpeedWatch
import io.github.foxesrcool1.margin.core.storage.DataRoot
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import io.github.foxesrcool1.margin.core.threads.AppDispatchers
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.CapsLabel
import io.github.foxesrcool1.margin.design.components.EinkRow
import io.github.foxesrcool1.margin.design.components.EinkText
import io.github.foxesrcool1.margin.design.components.EinkTextField
import io.github.foxesrcool1.margin.design.components.HairlineDivider
import io.github.foxesrcool1.margin.design.components.IconPressButton
import io.github.foxesrcool1.margin.design.components.PagedList
import io.github.foxesrcool1.margin.design.components.Plants
import io.github.foxesrcool1.margin.design.components.einkFieldBorder
import io.github.foxesrcool1.margin.design.icons.Lucide
import io.github.foxesrcool1.margin.ui.common.ScreenScaffold
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val TAG = "SearchScreen"

/**
 * The search over the words the user wrote themselves: the notes they typed,
 * the journal entries they wrote, and the highlights they marked in a book
 * with their note.
 *
 * The search runs when the button is pressed or the keyboard says done, not on
 * every key. The documents are read and the match is worked out off the main
 * thread, and the time is kept to a budget.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenInkNote: (path: String, title: String) -> Unit,
    onOpenBook: (LibraryBook, String?) -> Unit,
    /** The day to open the journal on, or null for today. */
    onOpenJournal: (LocalDate?) -> Unit,
    /** The words and the results, held so a tab change does not lose them. */
    state: SearchState = remember { SearchState() },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data = remember(context) { DataRoot.repository(context) }
    val books = remember(context) { BooksRepository(data) }

    // The field takes the focus as the screen opens, so the keyboard comes at
    // once. The search hides it again, so the results are not under it. When
    // the screen comes back with results already on show, the focus stays off:
    // the keyboard would come up over them for no reason.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!state.searched) focus.requestFocus()
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // The search runs on the button and on the keyboard done key, not on every
    // key. The documents are read and the match is worked out off the main
    // thread, and the time is kept to a budget.
    fun runSearch() {
        val words = state.query.trim()
        if (words.isEmpty()) return
        keyboard?.hide()
        focusManager.clearFocus()
        scope.launch {
            val started = android.os.SystemClock.uptimeMillis()
            val found = withContext(AppDispatchers.io) {
                search(SearchSource(data).documents(), words)
            }
            SpeedWatch.check(
                "The search for $words",
                android.os.SystemClock.uptimeMillis() - started,
                SpeedWatch.Budget.SEARCH,
            )
            state.hits = found
            state.searched = true
        }
    }

    ScreenScaffold(
        title = "Search",
        plant = Plants.Writing,
        onBack = onBack,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EinkTextField(
                value = state.query,
                onValueChange = { state.query = it },
                singleLine = true,
                onSubmit = { runSearch() },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .einkFieldBorder()
                    .padding(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconPressButton(
                icon = Lucide.Search,
                label = "Search",
                bordered = true,
                onClick = { runSearch() },
            )
        }

        Spacer(modifier = Modifier.height(EinkDimens.targetGap))

        if (state.searched) {
            PagedList(
                items = state.hits,
                pageSize = 5,
                rowHeight = 74.dp,
                emptyText = "Nothing found",
                modifier = Modifier.weight(1f),
            ) { _, hit ->
                SearchRow(
                    hit = hit,
                    onOpen = {
                        // The library list reads the disk, so the open is worked out off the main thread.
                        scope.launch { openHit(hit, books, onOpenNote, onOpenInkNote, onOpenBook, onOpenJournal) }
                    },
                )
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * What a row opens. A typed note goes to the note editor, a handwritten one to
 * the ink screen, a highlight to the book it is in, and a journal entry to the
 * day it was written on.
 */
private suspend fun openHit(
    hit: SearchHit,
    books: BooksRepository,
    onOpenNote: (String) -> Unit,
    onOpenInkNote: (path: String, title: String) -> Unit,
    onOpenBook: (LibraryBook, String?) -> Unit,
    onOpenJournal: (LocalDate?) -> Unit,
) {
    val doc = hit.doc
    when (doc.kind) {
        SearchKind.Note -> {
            if (StorageLayout.isInkNote(doc.path)) {
                runCatching { onOpenInkNote(doc.path, doc.title) }
                    .onFailure { AppLog.e(TAG, "Could not open ${doc.path}", it) }
            } else {
                onOpenNote(doc.path)
            }
        }
        SearchKind.Journal -> {
            // The entry's day is the file name, "journal/2026/2026-09-02.md".
            // The journal opens on that day, and on today when the name is not
            // a date.
            val day = runCatching {
                LocalDate.parse(doc.path.substringAfterLast('/').removeSuffix(".md"))
            }.getOrNull()
            onOpenJournal(day)
        }
        SearchKind.Highlight -> {
            // The book the highlight is in, opened the way the Reading tab opens it,
            // at the highlight itself when the doc carries its id.
            val book = withContext(AppDispatchers.io) { books.list().firstOrNull { it.path == doc.path } }
            if (book != null) {
                onOpenBook(book, doc.target.ifEmpty { null })
            } else {
                AppLog.e(TAG, "The book for ${doc.path} is not in the library")
            }
        }
    }
}

@Composable
private fun SearchRow(
    hit: SearchHit,
    onOpen: () -> Unit,
) {
    EinkRow(onClick = onOpen) { pressed ->
        val foreground = if (pressed) EinkColors.Paper else EinkColors.Ink

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EinkText(
                    text = hit.doc.title,
                    style = EinkType.rowTitle.copy(color = foreground),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                CapsLabel(
                    text = hit.doc.kind.label(),
                    style = EinkType.capsSmall.copy(color = foreground),
                )
            }
            EinkText(
                text = hit.snippet,
                style = EinkType.help.copy(color = foreground),
                maxLines = 2,
            )
        }
    }
    HairlineDivider(color = EinkColors.Faded)
}

private fun SearchKind.label(): String = when (this) {
    SearchKind.Note -> "Note"
    SearchKind.Journal -> "Journal"
    SearchKind.Highlight -> "Highlight"
}
