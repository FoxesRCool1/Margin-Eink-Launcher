package io.github.foxesrcool1.margin.core.storage

import io.github.foxesrcool1.margin.core.log.AppLog
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.LocalDate

private const val TAG = "DataRepository"

/** What an import did. */
sealed interface ImportResult {
    data class Imported(val relativePath: String, val bookId: String) : ImportResult
    data class AlreadyThere(val relativePath: String, val bookId: String) : ImportResult
    data class Failed(val reason: String) : ImportResult
}

/**
 * The one way into the data folder.
 *
 * Screens talk to this, never to [FileStore] and never to a raw path. That
 * keeps the folder layout in one place, so Step 6 to Step 9 can be built
 * without each of them inventing its own idea of where a note lives.
 */
class DataRepository(val store: FileStore) {

    /** Makes the folder look finished on first run. */
    fun ensureFolders(): Boolean {
        var allMade = true
        StorageLayout.topLevelFolders.forEach { folder ->
            if (!store.createDirectories(folder)) {
                AppLog.w(TAG, "Could not create the folder $folder")
                allMade = false
            }
        }
        return allMade
    }

    // -- Books ------------------------------------------------------------

    fun listBooks(): List<StoredEntry> =
        store.listFilesRecursively(StorageLayout.BOOKS)
            .filter { StorageLayout.isBook(it.name) }

    /**
     * Copies a book into `books/`.
     *
     * The app never reads a book from wherever the user picked it. A file
     * outside the data folder can move or be deleted, and then the reading
     * position points at nothing.
     */
    fun importBook(
        input: InputStream,
        displayName: String,
        sizeHintBytes: Long = -1L,
    ): ImportResult {
        if (!StorageLayout.isBook(displayName)) {
            return ImportResult.Failed("Only EPUB and PDF files can be imported")
        }

        val target = StorageLayout.bookPath(displayName)

        if (store.exists(target)) {
            // The same name is not the same book. Two scans can both be called
            // "scan.pdf", and a picker that gives no name makes every file
            // "book.epub". The file is copied in under a free name first, and
            // looked at then: only the same size and the same bytes make it
            // the same book.
            val existingSize = store.sizeOf(target)
            val existing = ImportResult.AlreadyThere(
                target,
                StorageLayout.bookIdFor(RelativePaths.nameOf(target), existingSize),
            )
            val beside = freePath(target)
            if (!store.writeFrom(beside, input)) {
                AppLog.e(TAG, "Import failed for $displayName")
                return ImportResult.Failed("The file could not be copied into the data folder")
            }
            if (store.sizeOf(beside) == existingSize && sameContent(beside, target)) {
                store.delete(beside)
                AppLog.i(TAG, "Book already imported: $target")
                return existing
            }
            val size = store.sizeOf(beside)
            val id = StorageLayout.bookIdFor(RelativePaths.nameOf(beside), size)
            AppLog.i(TAG, "Imported $beside as $id ($size bytes). The name $target was taken by another book.")
            return ImportResult.Imported(beside, id)
        }

        val written = store.writeFrom(target, input)
        if (!written) {
            AppLog.e(TAG, "Import failed for $displayName")
            return ImportResult.Failed("The file could not be copied into the data folder")
        }

        val size = store.sizeOf(target)
        if (sizeHintBytes > 0 && size != sizeHintBytes) {
            AppLog.w(TAG, "Imported $target is $size bytes, expected $sizeHintBytes")
        }

        val id = StorageLayout.bookIdFor(RelativePaths.nameOf(target), size)
        AppLog.i(TAG, "Imported $target as $id ($size bytes)")
        return ImportResult.Imported(target, id)
    }

    /** True when both files hold the same bytes. Streamed through SHA-256, so a big book is not held in memory. */
    private fun sameContent(a: String, b: String): Boolean {
        val first = sha256(a) ?: return false
        return first.contentEquals(sha256(b))
    }

    private fun sha256(path: String): ByteArray? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        store.openInput(path)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest()
        }
    }.getOrNull()

    fun deleteBook(relativePath: String): Boolean = store.delete(relativePath)

    // -- Notes ------------------------------------------------------------

    fun listNotes(folder: String = ""): List<StoredEntry> =
        store.list(RelativePaths.join(StorageLayout.NOTES, folder))

    fun readNote(relativePath: String): String? = store.readText(relativePath)

    fun writeNote(relativePath: String, text: String): Boolean =
        store.writeText(relativePath, text)

    fun newNotePath(folder: String, title: String): String {
        val name = "${StorageLayout.safeName(title)}.${StorageLayout.TYPED_NOTE_EXTENSION}"
        return RelativePaths.join(RelativePaths.join(StorageLayout.NOTES, folder), name)
    }

    /**
     * Keeps a copy of a file this app could not make sense of, before the
     * next save writes a fresh one over it. A file the user edited by hand
     * and left a typo in is still the user's work. One copy per broken
     * version: the same bytes are not copied twice.
     */
    fun keepUnreadable(relativePath: String, rescuePath: String) {
        runCatching {
            if (!store.exists(relativePath)) return
            // Every copy kept so far, "X.unreadable.json" and "X.unreadable 2.json".
            // The same bytes as any of them: nothing more to keep.
            val parent = RelativePaths.parentOf(rescuePath)
            val name = RelativePaths.nameOf(rescuePath)
            val stem = name.substringBeforeLast('.', name)
            val suffix = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
            val numbered = Regex("${Regex.escape(stem)} \\d+${Regex.escape(suffix)}")
            val broken = store.readBytes(relativePath) ?: return
            val copies = store.list(parent).filter { !it.isDirectory && (it.name == name || numbered.matches(it.name)) }
            if (copies.any { it.sizeBytes == store.sizeOf(relativePath) && store.readBytes(it.relativePath).contentEquals(broken) }) return
            val kept = freePath(rescuePath)
            if (store.copy(relativePath, kept)) AppLog.e(TAG, "$relativePath could not be read. A copy was kept as $kept")
        }.onFailure { AppLog.w(TAG, "Could not keep a copy of $relativePath", it) }
    }

    /** Adds a number to the name until it does not clash. */
    fun freePath(relativePath: String): String {
        if (!store.exists(relativePath)) return relativePath
        val parent = RelativePaths.parentOf(relativePath)
        val name = RelativePaths.nameOf(relativePath)
        val stem = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
        val suffix = if (extension.isEmpty()) "" else ".$extension"

        var counter = 2
        while (counter < 1000) {
            val candidate = RelativePaths.join(parent, "$stem $counter$suffix")
            if (!store.exists(candidate)) return candidate
            counter++
        }
        return RelativePaths.join(parent, "$stem ${System.currentTimeMillis()}$suffix")
    }

    // -- Journal ----------------------------------------------------------

    fun journalEntry(date: LocalDate): String? =
        store.readText(StorageLayout.journalPath(date))

    fun writeJournalEntry(date: LocalDate, text: String): Boolean =
        store.writeText(StorageLayout.journalPath(date), text)

    /** True when the day has a handwritten page with at least something in the file. */
    fun hasInkJournalEntry(date: LocalDate): Boolean {
        val path = StorageLayout.journalPath(date, handwritten = true)
        return store.exists(path) && store.sizeOf(path) > 0
    }

    fun journalDatesIn(year: Int): List<LocalDate> =
        store.list("${StorageLayout.JOURNAL}/$year")
            .filter { !it.isDirectory }
            .mapNotNull { entry ->
                runCatching { LocalDate.parse(entry.name.substringBeforeLast('.')) }.getOrNull()
            }
            .distinct()
            .sorted()

    // -- Index ------------------------------------------------------------

    fun buildIndex(nowMillis: Long = System.currentTimeMillis()): IndexSnapshot =
        LibraryIndex.build(store, nowMillis)

    // -- Backup -----------------------------------------------------------

    fun backupTo(output: OutputStream): BackupResult {
        val result = BackupArchive.backup(store, output)
        AppLog.i(TAG, "Backup wrote ${result.fileCount} files, ${result.byteCount} bytes")
        if (result.skipped.isNotEmpty()) {
            AppLog.w(TAG, "Backup skipped ${result.skipped.size} files: ${result.skipped.take(5)}")
        }
        return result
    }

    fun restoreFrom(input: InputStream, overwrite: Boolean = true): RestoreResult {
        ensureFolders()
        val result = BackupArchive.restore(store, input, overwrite = overwrite)
        AppLog.i(TAG, "Restore read ${result.fileCount} files, ${result.byteCount} bytes")
        if (result.refused.isNotEmpty()) {
            AppLog.w(TAG, "Restore refused ${result.refused.size} entries: ${result.refused.take(5)}")
        }
        return result
    }
}
