package io.github.foxesrcool1.margin.core.storage

import java.time.LocalDate
import java.util.Locale

/**
 * Where everything lives inside the data folder.
 *
 * Plain files are the source of truth. The layout is part of the promise to
 * the user: they can open the folder in a file manager, sync it, and read
 * every file with something else. Nothing here touches Android, so the tests
 * can check every path rule.
 *
 * ```
 * Margin/
 *   books/          imported EPUB and PDF copies
 *   annotations/    <book-id>.json, <book-id>/page-0001.strokes
 *   notes/          user folders, *.md typed, *.inknote handwritten
 *   journal/        YYYY/YYYY-MM-DD.md, YYYY/YYYY-MM-DD.inknote
 *   habits/         habits.json, log.csv
 *   apps/           folders.json, the folders on the Apps tab
 *   exports/
 *   logs/
 * ```
 */
object StorageLayout {

    const val ROOT_FOLDER = "Margin"

    const val BOOKS = "books"
    const val ANNOTATIONS = "annotations"
    const val NOTES = "notes"
    const val JOURNAL = "journal"
    const val HABITS = "habits"
    const val APPS = "apps"
    const val EXPORTS = "exports"
    const val LOGS = "logs"

    /** Created on first run, so the folder reads as a finished thing. */
    val topLevelFolders: List<String> =
        listOf(BOOKS, ANNOTATIONS, NOTES, JOURNAL, HABITS, APPS, EXPORTS, LOGS)

    /** These go into a backup. Exports and logs are left out: both can be made again. */
    val backedUpFolders: List<String> =
        listOf(BOOKS, ANNOTATIONS, NOTES, JOURNAL, HABITS, APPS)

    const val TYPED_NOTE_EXTENSION = "md"
    const val INK_NOTE_EXTENSION = "inknote"

    fun bookPath(fileName: String): String {
        val rawExtension = fileName.substringAfterLast('.', "")
        if (rawExtension.isBlank()) return "$BOOKS/${safeName(fileName)}"
        val extension = safeName(rawExtension, MAX_NAME_BYTES / 2)
        val stem = safeName(
            fileName.substringBeforeLast('.'),
            MAX_NAME_BYTES - 1 - extension.toByteArray(Charsets.UTF_8).size,
        )
        return "$BOOKS/$stem.$extension"
    }

    fun annotationsPath(bookId: String): String = "$ANNOTATIONS/${safeName(bookId)}.json"

    /** One stroke file per page. Page numbers start at 1 and are padded to four digits. */
    fun strokesPath(bookId: String, pageNumber: Int): String {
        require(pageNumber >= 1) { "page numbers start at 1" }
        val padded = pageNumber.toString().padStart(4, '0')
        return "$ANNOTATIONS/${safeName(bookId)}/page-$padded.strokes"
    }

    /** How long the user read, per day and book. */
    fun readingLogPath(): String = "$ANNOTATIONS/reading-log.csv"

    fun journalFolder(date: LocalDate): String = "$JOURNAL/${date.year}"

    fun journalPath(date: LocalDate, handwritten: Boolean = false): String {
        val extension = if (handwritten) INK_NOTE_EXTENSION else TYPED_NOTE_EXTENSION
        return "${journalFolder(date)}/$date.$extension"
    }

    fun habitsPath(): String = "$HABITS/habits.json"

    fun habitLogPath(): String = "$HABITS/log.csv"

    /** The folders the user sorts apps into, on the Apps tab. */
    fun appFoldersPath(): String = "$APPS/folders.json"

    /** The folder the split screen keeps its notes in, one per book. */
    const val READING_NOTES_FOLDER = "Reading notes"

    /**
     * The note that opens beside a book in the split screen. It lives in the
     * notes folder like any other note, so the Writing tab shows it too.
     */
    fun readingNotePath(bookTitle: String, handwritten: Boolean): String {
        val extension = if (handwritten) INK_NOTE_EXTENSION else TYPED_NOTE_EXTENSION
        return "$NOTES/$READING_NOTES_FOLDER/${safeName(bookTitle)}.$extension"
    }

    /**
     * Turns anything into a name that is safe on every file system the folder
     * may end up on, including FAT32 memory cards and a synced Windows folder.
     *
     * The rules: keep letters, digits, space, dot, dash and underscore. Turn
     * everything else into a dash. Never end with a dot or a space, because
     * Windows refuses those. Never return an empty name.
     */
    fun safeName(raw: String, maxBytes: Int = MAX_NAME_BYTES): String {
        val cleaned = buildString {
            raw.trim().forEach { character ->
                when {
                    character.isLetterOrDigit() -> append(character)
                    character in ALLOWED_PUNCTUATION -> append(character)
                    else -> append('-')
                }
            }
        }
            .replace(Regex("-{2,}"), "-")
            .trim(' ', '.', '-')
            .takeBytes(maxBytes)
            .trim(' ', '.', '-')

        return cleaned.ifBlank { "untitled" }
    }

    /**
     * A stable id for an imported book.
     *
     * The name alone is not enough: two files can share a name. The size makes
     * a collision unlikely, and both parts survive a copy to another device,
     * which a random id would not.
     */
    fun bookIdFor(fileName: String, sizeBytes: Long): String {
        val stem = fileName.substringBeforeLast('.', fileName)
        val suffix = "-$sizeBytes"
        val slug = safeName(stem)
            .lowercase(Locale.ROOT)
            .replace(' ', '-')
            .replace('.', '-')
            .replace('_', '-')
            .replace(Regex("-{2,}"), "-")
            .trim('-')
            .takeBytes(MAX_NAME_BYTES - suffix.length)
            .trim('-')
            .ifBlank { "book" }
        return "$slug$suffix"
    }

    /** True when the file is something the Reading tab can open. */
    fun isBook(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("epub", "pdf")

    fun isTypedNote(fileName: String): Boolean =
        fileName.endsWith(".$TYPED_NOTE_EXTENSION", ignoreCase = true)

    fun isInkNote(fileName: String): Boolean =
        fileName.endsWith(".$INK_NOTE_EXTENSION", ignoreCase = true)

    /** Android allows 255 bytes per name; the rest is left for ".part-<nanoTime>" temp suffixes. */
    private const val MAX_NAME_BYTES = 200
    private val ALLOWED_PUNCTUATION = setOf(' ', '.', '-', '_')

    /** The longest prefix whose UTF-8 size is at most maxBytes, cut between code points. */
    private fun String.takeBytes(maxBytes: Int): String {
        var end = 0
        var bytes = 0
        while (end < length) {
            val step = Character.charCount(codePointAt(end))
            val size = substring(end, end + step).toByteArray(Charsets.UTF_8).size
            if (bytes + size > maxBytes) break
            bytes += size
            end += step
        }
        return substring(0, end)
    }
}
