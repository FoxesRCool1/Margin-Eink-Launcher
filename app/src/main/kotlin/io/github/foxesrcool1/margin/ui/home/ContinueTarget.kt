package io.github.foxesrcool1.margin.ui.home

import io.github.foxesrcool1.margin.core.books.BooksRepository
import io.github.foxesrcool1.margin.core.books.LibraryBook
import io.github.foxesrcool1.margin.core.notes.NotesRepository
import io.github.foxesrcool1.margin.core.reading.ReadingRepository
import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.StorageLayout

/** What the Continue row on Home takes the user back to. */
sealed interface ContinueTarget {
    val title: String

    /** [percent] is null for a book that was opened and not read into yet. */
    data class Book(val book: LibraryBook, val percent: Int?) : ContinueTarget {
        override val title: String get() = book.metadata.title
    }

    data class TypedNote(val path: String, override val title: String) : ContinueTarget

    data class InkNote(val path: String, override val title: String) : ContinueTarget
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
            return ContinueTarget.Book(book, (progress * 100).toInt().takeIf { it > 0 })
        }
        if (lastNote == null) return null
        return if (StorageLayout.isInkNote(lastNote.name)) {
            ContinueTarget.InkNote(lastNote.path, lastNote.title)
        } else {
            ContinueTarget.TypedNote(lastNote.path, lastNote.title)
        }
    }
}
