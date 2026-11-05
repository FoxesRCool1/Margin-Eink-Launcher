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
 * Finds the things the user was last busy with: the books read last and the
 * notes edited last, merged by date, newest first. Reads the disk, so it runs
 * off the main thread.
 */
class ContinueFinder(private val data: DataRepository) {

    fun find(): ContinueTarget? = findRecent(1).firstOrNull()

    /**
     * The [count] things the user was last busy with, newest first. A book
     * stands in for the day the reader last saved its place, and a note for
     * the day its file was last changed. A thing the user has not touched
     * does not come up.
     */
    fun findRecent(count: Int = 2): List<ContinueTarget> {
        if (count <= 0) return emptyList()
        val books = BooksRepository(data).recentlyRead(count)
        val notes = NotesRepository(data).newest(count)

        val dated: List<Pair<Long, ContinueTarget>> =
            books.map { (book, readAt) ->
                val progress = ReadingRepository(data).progressOf(book.bookId)
                readAt to ContinueTarget.Book(book, ReadingProgress.mark(progress))
            } + notes.map { note ->
                note.stored.lastModified to
                    if (StorageLayout.isInkNote(note.name)) {
                        ContinueTarget.InkNote(note.path, note.title)
                    } else {
                        ContinueTarget.TypedNote(note.path, note.title)
                    }
            }

        return dated
            .sortedByDescending { it.first }
            .map { it.second }
            .distinctBy { it.path }
            .take(count)
    }
}
