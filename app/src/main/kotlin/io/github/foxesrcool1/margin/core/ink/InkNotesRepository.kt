package io.github.foxesrcool1.margin.core.ink

import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.RelativePaths
import io.github.foxesrcool1.margin.core.storage.StorageLayout

private const val TAG = "InkNotesRepository"

/** What opening an ink note gave. */
sealed interface InkNoteLoad {
    class Loaded(val note: InkNote) : InkNoteLoad

    /** There is no such file yet. The caller starts an empty note. */
    data object Missing : InkNoteLoad

    /** The file is there and cannot be read. The caller must not save over it. */
    class Damaged(val reason: String) : InkNoteLoad
}

/**
 * Handwritten notes on disk. Every path is relative to the data folder and
 * every write is the atomic one of the storage layer, so a tablet that loses
 * power in the middle of a save keeps the last good note.
 */
class InkNotesRepository(private val data: DataRepository) {

    fun load(path: String): InkNoteLoad {
        if (!data.store.exists(path)) return InkNoteLoad.Missing
        val bytes = data.store.readBytes(path)
            ?: return InkNoteLoad.Damaged("The file could not be read")
        if (bytes.isEmpty()) return InkNoteLoad.Missing
        return try {
            InkNoteLoad.Loaded(InkNoteCodec.decode(bytes))
        } catch (problem: StrokesFormatException) {
            AppLog.e(TAG, "Could not read $path: ${problem.message}")
            InkNoteLoad.Damaged(problem.message ?: "The file is damaged")
        }
    }

    /** When the file was last written. A different value than before means someone wrote it. 0 for a file that is not there. */
    fun stampOf(path: String): Long = data.store.lastModified(path)

    fun save(path: String, note: InkNote): Boolean {
        val ok = data.store.write(path, InkNoteCodec.encode(note))
        if (!ok) AppLog.e(TAG, "Could not save $path")
        return ok
    }

    /** Makes a new empty ink note in [folderPath] and returns its path, or null. */
    fun create(folderPath: String, title: String, template: PageTemplate): String? {
        val name = "${StorageLayout.safeName(title)}.${StorageLayout.INK_NOTE_EXTENSION}"
        val path = data.freePath(RelativePaths.join(folderPath, name))
        return if (save(path, InkNote(template = template))) path else null
    }

    /** Writes an export and returns where it went, or null. */
    fun writeExport(fileName: String, bytes: ByteArray): String? {
        val path = data.freePath(RelativePaths.join(StorageLayout.EXPORTS, StorageLayout.safeName(fileName)))
        return if (data.store.write(path, bytes)) path else null
    }
}
