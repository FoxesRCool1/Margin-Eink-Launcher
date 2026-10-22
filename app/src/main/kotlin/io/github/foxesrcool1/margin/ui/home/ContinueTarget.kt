package io.github.foxesrcool1.margin.ui.home

import io.github.foxesrcool1.margin.core.books.BooksRepository
import io.github.foxesrcool1.margin.core.books.LibraryBook
import io.github.foxesrcool1.margin.core.notes.NotesRepository
import io.github.foxesrcool1.margin.core.reading.ReadingProgress
import io.github.foxesrcool1.margin.core.reading.ReadingRepository
import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.StorageLayout

/** What the Continue row on Home takes the user back to. */
sealed interface ContinueTarget {
    val title: String

    /** The file behind the row, so a tap can check it is still there. */
    val path: String

    /** [mark] is the progress as the library shows it, null for a book that was opened and not read into yet. */
    data class Book(val book: LibraryBook, val mark: String?) : ContinueTarget {
        override val title: String get() = book.metadata.title
        override val path: String get() = book.path
    }

    data class TypedNote(override val path: String, override val title: String) : ContinueTarget

    data class InkNote(override val path: String, override val title: String) : ContinueTarget
}

/**
 * Finds the thing the user was last busy with: the book read last or the
 * note edited last, whichever came later. Reads the disk, so it runs off the
 * main thread.
 */
class ContinueFinder(private val data: DataRepository) {

    fun find(): ContinueTarget? {
        val lastBook = BooksRepository(data).lastRead()
        val lastNote = NotesRepository(data).newest()

        if (lastBook != null && (lastNote == null || lastBook.second > lastNote.stored.lastModified)) {
            val book = lastBook.first
            val progress = ReadingRepository(data).progressOf(book.bookId)
            return ContinueTarget.Book(book, ReadingProgress.mark(progress))
        }
        if (lastNote == null) return null
        return if (StorageLayout.isInkNote(lastNote.name)) {
            ContinueTarget.InkNote(lastNote.path, lastNote.title)
        } else {
            ContinueTarget.TypedNote(lastNote.path, lastNote.title)
        }
    }
}
