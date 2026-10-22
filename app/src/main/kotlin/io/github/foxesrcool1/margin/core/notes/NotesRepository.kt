package io.github.foxesrcool1.margin.core.notes

import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.RelativePaths
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import io.github.foxesrcool1.margin.core.storage.StoredEntry

private const val TAG = "NotesRepository"

/** One row in the Writing tab. */
data class NoteEntry(
    val stored: StoredEntry,
    val title: String,
    /** The start of a typed note, under its title. Empty for anything else. */
    val preview: String = "",
) {
    val isFolder: Boolean get() = stored.isDirectory
    val path: String get() = stored.relativePath
    val name: String get() = stored.name
}

/**
 * The notes folder, as the Writing tab sees it.
 *
 * Everything takes and returns a path relative to the data folder, and every
 * path goes through the storage layer, so a note can never be written outside
 * the user's folder.
 */
class NotesRepository(private val data: DataRepository) {

    val rootPath: String = StorageLayout.NOTES

    /** True when [path] is the notes folder itself, so the browser knows to hide Up. */
    fun isRoot(path: String): Boolean = RelativePaths.normalise(path) == rootPath

    fun parentOf(path: String): String {
        if (isRoot(path)) return rootPath
        val parent = RelativePaths.parentOf(path)
        return if (parent.isEmpty()) rootPath else parent
    }

    /**
     * One folder, folders first and then files.
     *
     * The title of a typed note is read from the file, because the first
     * heading tells the user far more than `note-3.md` does. The preview
     * comes from the same read. Read by the row itself, a moment later, it
     * was a second trip to the disk for each note and a second repaint of
     * each row.
     */
    fun list(folderPath: String): List<NoteEntry> = data.store.list(folderPath).map(::describe)

    private fun describe(stored: StoredEntry): NoteEntry = when {
        stored.isDirectory -> NoteEntry(stored, stored.name)
        StorageLayout.isTypedNote(stored.name) -> {
            val text = data.store.readText(stored.relativePath).orEmpty()
            NoteEntry(
                stored = stored,
                title = NoteText.titleFrom(text, stored.name.substringBeforeLast('.')),
                preview = NoteText.preview(text),
            )
        }
        // A handwritten note has no text to take a title from.
        StorageLayout.isInkNote(stored.name) -> NoteEntry(stored, stored.name.substringBeforeLast('.'))
        else -> NoteEntry(stored, stored.name)
    }

    /**
     * The typed or handwritten note that was changed last, in any folder, or
     * null when there is none. Home offers it as a way back to the writing.
     */
    fun newest(): NoteEntry? =
        data.store.listFilesRecursively(rootPath)
            .filter { StorageLayout.isTypedNote(it.name) || StorageLayout.isInkNote(it.name) }
            .maxByOrNull { it.lastModified }
            ?.let(::describe)

    fun read(path: String): String = data.store.readText(path).orEmpty()

    /**
     * Null when the note is there but could not be read. The editor must not
     * show that as an empty note: the first autosave would then write the
     * empty page over the real one. A note that is not there at all is "".
     */
    fun readOrNull(path: String): String? =
        data.store.readText(path) ?: if (data.store.exists(path)) null else ""

    fun write(path: String, text: String): Boolean = data.store.writeText(path, text)

    fun exists(path: String): Boolean = data.store.exists(path)

    /** [path], or the same name with a number when something is there already. */
    fun freePath(path: String): String = data.freePath(path)

    /** Makes a new empty note and returns its path, or null. */
    fun createNote(folderPath: String, title: String): String? {
        val safe = StorageLayout.safeName(title)
        val wanted = RelativePaths.join(
            folderPath,
            "$safe.${StorageLayout.TYPED_NOTE_EXTENSION}",
        )
        val path = data.freePath(wanted)

        val body = "# ${title.trim()}\n\n"
        return if (data.store.writeText(path, body)) {
            AppLog.i(TAG, "Made a note at $path")
            path
        } else {
            AppLog.e(TAG, "Could not make a note at $path")
            null
        }
    }

    fun createFolder(folderPath: String, name: String): Boolean {
        val path = RelativePaths.join(folderPath, StorageLayout.safeName(name))
        return data.store.createDirectories(path)
    }

    /**
     * Renames in place, keeping the extension a typed note needs. The
     * heading of a typed note gets the new name too, because the Writing tab
     * shows the heading and not the file name.
     */
    fun rename(path: String, newName: String): String? {
        val clean = RelativePaths.normalise(path)
        // A folder called "Drafts v1.2" has no extension to keep.
        val extension = if (data.store.isDirectory(clean)) "" else RelativePaths.extensionOf(clean)
        val safe = StorageLayout.safeName(newName)
        val fileName = if (extension.isEmpty()) safe else "$safe.$extension"
        val wanted = RelativePaths.join(RelativePaths.parentOf(clean), fileName)

        val target = when {
            // The same name. Looking for a free name would find this very
            // file in the way and make it "Name 2".
            wanted == clean -> clean
            // Only the capitals change. The shared storage of Android does
            // not tell "note" from "Note", so the file itself is in the way
            // there too. It goes by way of a name nothing else has.
            wanted.equals(clean, ignoreCase = true) ->
                if (moveByWayOfFreeName(clean, wanted)) wanted else null
            else -> data.freePath(wanted).takeIf { data.store.move(clean, it) }
        }
        if (target == null) {
            AppLog.w(TAG, "Could not rename $clean")
            return null
        }
        if (target != clean) AppLog.i(TAG, "Renamed $clean to $target")

        if (StorageLayout.isTypedNote(target)) {
            val text = data.store.readText(target)
            val retitled = text?.let { NoteText.withTitle(it, newName) }
            if (retitled != null && retitled != text && !data.store.writeText(target, retitled)) {
                AppLog.w(TAG, "Renamed $target, but could not change its heading")
            }
        }
        return target
    }

    private fun moveByWayOfFreeName(from: String, to: String): Boolean {
        val between = data.freePath("$from.renaming")
        if (!data.store.move(from, between)) return false
        if (data.store.move(between, to)) return true
        data.store.move(between, from)
        return false
    }

    /**
     * Moves something into [targetFolder].
     *
     * A folder cannot be moved into itself or into one of its own children.
     * Without that check the folder and everything in it would disappear.
     */
    fun move(path: String, targetFolder: String): Boolean {
        val clean = RelativePaths.normalise(path)
        val folder = RelativePaths.normalise(targetFolder)

        if (folder == clean || folder.startsWith("$clean/")) {
            AppLog.w(TAG, "Refused to move $clean into itself")
            return false
        }
        if (RelativePaths.parentOf(clean) == folder) return true

        val target = data.freePath(RelativePaths.join(folder, RelativePaths.nameOf(clean)))
        val moved = data.store.move(clean, target)
        AppLog.i(TAG, "Moved $clean to $target: $moved")
        return moved
    }

    fun delete(path: String): Boolean {
        AppLog.i(TAG, "Deleting $path")
        return data.store.delete(path)
    }

    /** The path a Quick note from Today should use. */
    fun quickNotePath(): String = RelativePaths.join(rootPath, "quick")
}
