package io.github.foxesrcool1.margin.ui.reading.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.foxesrcool1.margin.core.books.LibraryBook
import io.github.foxesrcool1.margin.ui.ink.InkCanvasView
import io.github.foxesrcool1.margin.ui.ink.InkMode
import io.github.foxesrcool1.margin.ui.split.PaneHost
import io.github.foxesrcool1.margin.ui.split.PanePage
import io.github.foxesrcool1.margin.ui.split.SplitLayout
import io.github.foxesrcool1.margin.ui.split.SplitPane
import io.github.foxesrcool1.margin.ui.split.SplitState

/** The PDF reader screen with nothing behind it, so a test can show its layout. */
object PdfReaderScreenshotSupport {

    /** [split] opens the second half on the notes of the book, as the reader does. */
    @Composable
    fun Screen(split: Boolean) {
        val state = remember {
            PdfUiState().apply {
                loading = false
                title = "Walden"
                pageIndex = 11
                pageCount = 240
            }
        }
        val halves = remember {
            SplitState(firstPage = { PanePage.BookNotes(state.title) }).apply { if (split) open() }
        }
        val actions = remember {
            object : PdfActions {
                override fun close() = Unit
                override fun attach(canvas: InkCanvasView) = Unit
                override fun step(direction: Int) = Unit
                override fun goToPage(pageNumber: Int) = Unit
                override fun pick(mode: InkMode) { state.mode = mode }
                override fun nextZoom() = Unit
                override fun toggleCrop() = Unit
                override fun nextPenWidth() = Unit
                override fun undo() = Unit
                override fun redo() = Unit
                override fun show(panel: PdfPanel) = Unit
                override fun exportPage() = Unit
                override fun exportNotes() = Unit
                override fun toggleMouse() = Unit
            }
        }
        val host = remember {
            object : PaneHost {
                override val bookTitle: String get() = state.title
                override fun openBook(book: LibraryBook, highlightId: String?) = Unit
                override fun paneInk(canvas: InkCanvasView?) = Unit
            }
        }
        SplitLayout(
            split = halves,
            main = { PdfReaderScreen(state, actions) },
            pane = { SplitPane(halves, host) },
        )
    }
}
