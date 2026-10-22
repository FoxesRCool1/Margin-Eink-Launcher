package io.github.foxesrcool1.margin.core.books

import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import io.github.foxesrcool1.margin.core.storage.StoredEntry
import java.io.InputStream
import java.util.Locale

private const val TAG = "BooksRepository"

/** One book as the library sees it. */
data class LibraryBook(
    val stored: StoredEntry,
    val metadata: BookMetadata,
    val bookId: String,
) {
    val path: String get() = stored.relativePath
    val isPdf: Boolean
        get() = stored.name.substringAfterLast('.', "").lowercase(Locale.ROOT) == "pdf"

    /** "12 MB", so the owner can see which file is eating the card. */
    fun sizeLabel(): String {
        val megabytes = stored.sizeBytes / (1024.0 * 1024.0)
        return if (megabytes < 1.0) {
            "${(stored.sizeBytes / 1024).coerceAtLeast(1)} KB"
        } else {
            "${"%.1f".format(megabytes)} MB"
        }
    }
}

/**
 * The library, on top of the data folder.
 *
 * It reads the title and the author out of an EPUB itself. The reader in step
 * 7 will use the Readium toolkit, which reads far more than this, but a
 * library has to show a real title before any reader exists, and a list of
 * file names is not a library.
 */
class BooksRepository(private val data: DataRepository) {

    fun list(): List<LibraryBook> {
        val found = data.listBooks()
        // A book that is gone is forgotten, so the memory stays the size of the library.
        val paths = found.mapTo(HashSet()) { it.relativePath }
        synchronized(read) { read[data.store]?.keys?.retainAll(paths) }
        return found.map { stored ->
            LibraryBook(
                stored = stored,
                metadata = metadataOf(stored),
                bookId = StorageLayout.bookIdFor(stored.name, stored.sizeBytes),
            )
        }
    }

    /**
     * The title and the author. For an EPUB that means opening the zip and
     * parsing its XML, and the library is read again every time it comes back
     * on screen, which is every time a book closes. So what was read is kept,
     * for as long as the file keeps its size and its date. A file changed by
     * hand is read again. Battery matters more than the few bytes this holds.
     */
    private fun metadataOf(stored: StoredEntry): BookMetadata {
        if (!EpubMetadata.looksLikeEpub(stored.name)) return BookMetadata.fromFileName(stored.name)
        val stamp = stored.sizeBytes to stored.lastModified
        val known = synchronized(read) { read.getOrPut(data.store) { HashMap() }[stored.relativePath] }
        if (known != null && known.first == stamp) return known.second

        val metadata = EpubMetadata.read { data.store.openInput(stored.relativePath) }
            ?: BookMetadata.fromFileName(stored.name)
        synchronized(read) { read.getOrPut(data.store) { HashMap() }[stored.relativePath] = stamp to metadata }
        return metadata
    }

    fun byTitle(): List<LibraryBook> =
        list().sortedBy { it.metadata.title.lowercase(Locale.ROOT) }

    /**
     * The book read last comes first. The date of the book file is only the
     * day it was imported, because the reader never writes to the book. The
     * reader saves the place in the book to the annotations file each time it
     * closes, so the date of that file is when the book was last read. A book
     * that was never opened counts from the day it came in.
     */
    fun byRecent(): List<LibraryBook> =
        list().map { it to lastReadAt(it) }.sortedByDescending { it.second }.map { it.first }

    /**
     * The book that was opened last, with the time the reader last saved its
     * place, or null when no book was ever opened. Unlike [byRecent] it
     * ignores the day a book came in, and it reads the title of one book only.
     */
    fun lastRead(): Pair<LibraryBook, Long>? {
        val (stored, readAt) = data.listBooks()
            .map { it to data.store.lastModified(StorageLayout.annotationsPath(StorageLayout.bookIdFor(it.name, it.sizeBytes))) }
            .filter { it.second > 0L }
            .maxByOrNull { it.second }
            ?: return null
        val book = LibraryBook(
            stored = stored,
            metadata = metadataOf(stored),
            bookId = StorageLayout.bookIdFor(stored.name, stored.sizeBytes),
        )
        return book to readAt
    }

    private fun lastReadAt(book: LibraryBook): Long = maxOf(
        book.stored.lastModified,
        data.store.lastModified(StorageLayout.annotationsPath(book.bookId)),
    )

    /** Copies a picked file into `books/`. */
    fun import(input: InputStream, displayName: String, sizeHintBytes: Long = -1L) =
        data.importBook(input, displayName, sizeHintBytes)

    fun delete(book: LibraryBook): Boolean {
        AppLog.i(TAG, "Deleting ${book.path}")
        // The book first. If it will not go, the user is told "could not
        // delete", and then the highlights and the handwriting must still be
        // there. The other way round, a failed delete still ate the notes.
        if (!data.deleteBook(book.path)) {
            AppLog.e(TAG, "Could not delete ${book.path}. Its notes were left alone.")
            return false
        }
        // The notes the user made about it go too, or they become orphans that
        // nothing can ever open again.
        data.store.delete(StorageLayout.annotationsPath(book.bookId))
        data.store.delete("${StorageLayout.ANNOTATIONS}/${book.bookId}")
        return true
    }

    private companion object {
        /**
         * What [metadataOf] read, per data folder and then per relative path,
         * with the size and the date it was read at. Shared by every
         * repository in the process, because each screen makes its own.
         */
        val read = java.util.WeakHashMap<Any, HashMap<String, Pair<Pair<Long, Long>, BookMetadata>>>()
    }
}
