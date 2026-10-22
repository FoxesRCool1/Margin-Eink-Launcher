package io.github.foxesrcool1.margin.core.reading

import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.RelativePaths
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import java.io.IOException
import java.time.LocalDate

private const val TAG = "ReadingRepository"

/** Positions, highlights, notes and reading time, on top of the data folder. */
class ReadingRepository(private val data: DataRepository) {

    fun load(bookId: String): BookAnnotations {
        val text = data.store.readText(StorageLayout.annotationsPath(bookId)) ?: return BookAnnotations()
        return AnnotationsFile.parse(text) ?: run {
            // Keep the unreadable file. The next save would otherwise write
            // an empty one over notes that a person could still rescue.
            val rescue = "${StorageLayout.ANNOTATIONS}/${StorageLayout.safeName(bookId)}.unreadable.json"
            data.keepUnreadable(StorageLayout.annotationsPath(bookId), rescue)
            AppLog.e(TAG, "Annotations of $bookId could not be read.")
            BookAnnotations()
        }
    }

    fun save(bookId: String, annotations: BookAnnotations): Boolean =
        data.store.writeText(StorageLayout.annotationsPath(bookId), AnnotationsFile.serialise(annotations))

    /** Progress of every book that has been opened, by book id. One small file each. */
    fun progressOf(bookId: String): Double =
        data.store.readText(StorageLayout.annotationsPath(bookId))
            ?.let(AnnotationsFile::parse)
            ?.progress
            ?: 0.0

    fun inkNotePath(bookId: String, highlightId: String): String =
        "${StorageLayout.ANNOTATIONS}/${StorageLayout.safeName(bookId)}/note-${StorageLayout.safeName(highlightId)}.${StorageLayout.INK_NOTE_EXTENSION}"

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
