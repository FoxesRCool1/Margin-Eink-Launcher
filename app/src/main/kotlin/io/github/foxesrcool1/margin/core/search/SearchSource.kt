package io.github.foxesrcool1.margin.core.search

import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.reading.ReadingRepository
import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.RelativePaths
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import io.github.foxesrcool1.margin.ui.journal.JournalStrings
import java.time.LocalDate

private const val TAG = "SearchSource"

/**
 * The documents the search runs over: the notes the user typed, the journal
 * entries they wrote, and the highlights they marked in a book with their note.
 *
 * It reads through [DataRepository] and [ReadingRepository], the same way the
 * screens read, so the paths stay in the storage layer and not in the screen.
 * A file that is gone or cannot be read is left out, not an error.
 */
class SearchSource(private val data: DataRepository) {

    private val reading = ReadingRepository(data)

    /** Everything the search can find, in the order the folders are read. */
    fun documents(): List<SearchDoc> =
        typedNotes() + inkNotes() + journalEntries() + highlights()

    /** Every typed note under `notes/`, in the folders and in their subfolders. */
    private fun typedNotes(): List<SearchDoc> =
        data.store.listFilesRecursively(StorageLayout.NOTES)
            .filter { StorageLayout.isTypedNote(it.name) }
            .map { entry ->
                SearchDoc(
                    kind = SearchKind.Note,
                    path = entry.relativePath,
                    title = RelativePaths.nameOf(entry.relativePath).substringBeforeLast('.'),
                    text = data.readNote(entry.relativePath).orEmpty(),
                )
            }

    /**
     * The handwritten notes have no text to search in, but their title is the
     * user's own word for the page, so it is findable.
     */
    private fun inkNotes(): List<SearchDoc> =
        data.store.listFilesRecursively(StorageLayout.NOTES)
            .filter { StorageLayout.isInkNote(it.name) }
            .map { entry ->
                SearchDoc(
                    kind = SearchKind.Note,
                    path = entry.relativePath,
                    title = RelativePaths.nameOf(entry.relativePath).substringBeforeLast('.'),
                    text = "",
                )
            }

    /** The typed entry of every day that has one, in every year folder. */
    private fun journalEntries(): List<SearchDoc> {
        val years = data.store.list(StorageLayout.JOURNAL)
            .filter { it.isDirectory }
            .mapNotNull { runCatching { it.name.toInt() }.getOrNull() }
        return years.flatMap { year ->
            data.journalDatesIn(year).mapNotNull { date ->
                data.journalEntry(date)?.takeIf { it.isNotBlank() }?.let { text ->
                    SearchDoc(
                        kind = SearchKind.Journal,
                        path = StorageLayout.journalPath(date),
                        title = JournalStrings.dayTitle(date),
                        text = text,
                    )
                }
            }
        }
    }

    /**
     * One document per highlight, from every annotations file. The text is the
     * marked words and the note the user wrote on them, so a search finds the
     * note as well as the words of the book.
     */
    private fun highlights(): List<SearchDoc> {
        val files = data.store.list(StorageLayout.ANNOTATIONS)
            .filter { !it.isDirectory && it.name.endsWith(".json", ignoreCase = true) }
        return files.flatMap { file ->
            val text = runCatching { data.store.readText(file.relativePath) }.getOrNull()
                ?: return@flatMap emptyList()
            val annotations = runCatching {
                io.github.foxesrcool1.margin.core.reading.AnnotationsFile.parse(text)
            }.getOrNull() ?: run {
                AppLog.w(TAG, "The search could not read ${file.relativePath}")
                return@flatMap emptyList()
            }
            val title = annotations.title.ifBlank {
                RelativePaths.nameOf(annotations.bookPath).substringBeforeLast('.')
            }
            annotations.highlights.map { highlight ->
                val note = highlight.note.trim()
                SearchDoc(
                    kind = SearchKind.Highlight,
                    path = annotations.bookPath,
                    title = title,
                    text = if (note.isEmpty()) highlight.text else "${highlight.text} $note",
                    target = highlight.id,
                )
            }
        }
    }
}
