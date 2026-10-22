package io.github.foxesrcool1.margin.core.reading

import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.RelativePaths
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ReadingRepository"

/** Positions, highlights, notes and reading time, on top of the data folder. */
class ReadingRepository(private val data: DataRepository) {

    /**
     * Books whose annotations file is there but could not be read. Nothing can
     * be copied out of such a file, so a save would put an empty one over the
     * notes. Each reader has its own repository, so this lasts while the book
     * is open.
     */
    private val unreadable: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun load(bookId: String): BookAnnotations {
        val text = try {
            data.store.readTextOrThrow(StorageLayout.annotationsPath(bookId))
        } catch (problem: IOException) {
            unreadable += bookId
            AppLog.e(TAG, "Annotations of $bookId could not be read. Nothing is saved over them.", problem)
            return BookAnnotations()
        }
        unreadable -= bookId
        if (text == null) return BookAnnotations()
        return AnnotationsFile.parse(text) ?: run {
            // Keep the unreadable file. The next save would otherwise write
            // an empty one over notes that a person could still rescue.
            val rescue = "${StorageLayout.ANNOTATIONS}/${StorageLayout.safeName(bookId)}.unreadable.json"
            data.keepUnreadable(StorageLayout.annotationsPath(bookId), rescue)
            AppLog.e(TAG, "Annotations of $bookId could not be read.")
            BookAnnotations()
        }
    }

    /** False when the file on disk could not be read: see [savesBlocked]. */
    fun save(bookId: String, annotations: BookAnnotations): Boolean {
        if (savesBlocked(bookId)) return false
        return data.store.writeText(StorageLayout.annotationsPath(bookId), AnnotationsFile.serialise(annotations))
    }

    /** True after [load] met a file it could not read. The reader says so. */
    fun savesBlocked(bookId: String): Boolean = bookId in unreadable

    /** Progress of every book that has been opened, by book id. One small file each. */
    fun progressOf(bookId: String): Double =
        data.store.readText(StorageLayout.annotationsPath(bookId))
            ?.let(AnnotationsFile::parse)
            ?.progress
            ?: 0.0

    fun inkNotePath(bookId: String, highlightId: String): String =
        "${StorageLayout.ANNOTATIONS}/${StorageLayout.safeName(bookId)}/note-${StorageLayout.safeName(highlightId)}.${StorageLayout.INK_NOTE_EXTENSION}"

    /**
     * Deletes the handwritten card of a highlight that was removed. Only a card
     * in the annotations folder: a path from a damaged file reaches nothing else.
     */
    fun deleteInkNote(path: String): Boolean {
        val clean = runCatching { RelativePaths.normalise(path) }.getOrNull() ?: return false
        if (!clean.startsWith("${StorageLayout.ANNOTATIONS}/") || !clean.endsWith(".${StorageLayout.INK_NOTE_EXTENSION}")) return false
        return data.store.delete(clean)
    }

    fun exportMarkdown(annotations: BookAnnotations): String? {
        val name = "${StorageLayout.safeName(annotations.title.ifBlank { "book" })} notes.md"
        val path = data.freePath(RelativePaths.join(StorageLayout.EXPORTS, name))
        return if (data.store.writeText(path, AnnotationsFile.toMarkdown(annotations))) path else null
    }

    // -- Reading time -----------------------------------------------------------

    fun addReadingTime(bookId: String, date: LocalDate, seconds: Long) {
        if (seconds <= 0) return
        val path = StorageLayout.readingLogPath()
        val old = try {
            data.store.readTextOrThrow(path)
        } catch (problem: IOException) {
            // Writing now would start the log again and lose every day in it.
            AppLog.e(TAG, "The reading log could not be read. $seconds seconds were not added.", problem)
            return
        }
        val before = old?.trimEnd().orEmpty().ifEmpty { ReadingLog.HEADER }
        val after = before + "\n" + ReadingLog.line(ReadingLog.Entry(date, bookId, seconds)) + "\n"
        if (!data.store.writeText(path, after)) AppLog.w(TAG, "Could not write the reading log")
    }

    fun secondsReadOn(date: LocalDate): Long =
        ReadingLog.secondsOn(ReadingLog.parse(data.store.readText(StorageLayout.readingLogPath()).orEmpty()), date)
}
