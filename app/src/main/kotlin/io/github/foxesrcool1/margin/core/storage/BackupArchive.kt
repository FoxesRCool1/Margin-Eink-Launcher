package io.github.foxesrcool1.margin.core.storage

import io.github.foxesrcool1.margin.core.log.AppLog
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val TAG = "BackupArchive"

/** What a backup wrote. */
data class BackupResult(
    val fileCount: Int,
    val byteCount: Long,
    val skipped: List<String>,
)

/** What a restore read. */
data class RestoreResult(
    val fileCount: Int,
    val byteCount: Long,
    /** Entries the archive held that were refused. See [BackupArchive.restore]. */
    val refused: List<String>,
    /** True when the zip was damaged or cut short. The files counted above were still restored. */
    val stoppedEarly: Boolean = false,
)

/**
 * Backup to a zip file, and restore from one.
 *
 * The zip holds the same paths the data folder uses, so the owner can open it
 * on a computer and read a note without this app. `exports/` and `logs/` stay
 * out: both can be made again and both can be large.
 */
object BackupArchive {

    const val MANIFEST_NAME = "margin-backup.txt"
    const val FORMAT_VERSION = 1

    fun backup(
        store: FileStore,
        output: OutputStream,
        folders: List<String> = StorageLayout.backedUpFolders,
    ): BackupResult {
        var fileCount = 0
        var byteCount = 0L
        val skipped = mutableListOf<String>()

        ZipOutputStream(output.buffered()).use { zip ->
            val manifest = buildString {
                append("format=").append(FORMAT_VERSION).append('\n')
                append("folders=").append(folders.joinToString(",")).append('\n')
            }
            zip.putNextEntry(ZipEntry(MANIFEST_NAME))
            zip.write(manifest.toByteArray())
            zip.closeEntry()

            folders.forEach { folder ->
                store.listFilesRecursively(folder).forEach { entry ->
                    // Open the file first. An entry added before a failed read
                    // would stay in the zip empty, and a restore would then
                    // write that empty file over the real one.
                    // ponytail: a read that fails half way still leaves a short entry.
                    val written = runCatching {
                        val input = store.openInput(entry.relativePath) ?: return@runCatching false
                        input.use {
                            zip.putNextEntry(ZipEntry(entry.relativePath))
                            it.copyTo(zip)
                            zip.closeEntry()
                        }
                        true
                    }.getOrDefault(false)

                    if (written) {
                        fileCount++
                        byteCount += entry.sizeBytes
                    } else {
                        skipped += entry.relativePath
                    }
                }
            }
        }

        return BackupResult(fileCount, byteCount, skipped)
    }

    /**
     * Restores into [store].
     *
     * Every entry name goes through [RelativePaths.normalise] first. An
     * archive can name an entry `../../somewhere/else`, and a restore that
     * trusted it would write outside the data folder. Those entries are
     * refused and listed in the result rather than silently dropped.
     *
     * A folder in [folders] is the only place an entry may land, so a backup
     * from somewhere else cannot drop files at the top of the data folder.
     */
    fun restore(
        store: FileStore,
        input: InputStream,
        folders: List<String> = StorageLayout.backedUpFolders,
        overwrite: Boolean = true,
    ): RestoreResult {
        var fileCount = 0
        var byteCount = 0L
        val refused = mutableListOf<String>()
        var stoppedEarly = false

        ZipInputStream(input.buffered()).use { zip ->
            // A zip that is cut short throws in the middle of the loop, after
            // earlier files were already replaced. Report what was done.
            try {
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val rawName = entry.name

                    if (entry.isDirectory || rawName == MANIFEST_NAME) {
                        zip.closeEntry()
                        continue
                    }

                    val safePath = runCatching { RelativePaths.normalise(rawName) }.getOrNull()
                    val topFolder = safePath?.substringBefore('/')

                    if (safePath == null || safePath.isEmpty() || topFolder !in folders) {
                        refused += rawName
                        zip.closeEntry()
                        continue
                    }

                    if (!overwrite && store.exists(safePath)) {
                        refused += rawName
                        zip.closeEntry()
                        continue
                    }

                    // The stream must not be closed here: the zip stream carries
                    // every entry, so closing it would end the whole restore.
                    val written = store.writeFrom(safePath, NonClosingInputStream(zip))
                    if (written) {
                        fileCount++
                        byteCount += store.sizeOf(safePath)
                    } else {
                        refused += rawName
                    }
                    zip.closeEntry()
                }
            } catch (problem: IOException) {
                AppLog.e(TAG, "The backup zip ended early", problem)
                stoppedEarly = true
            }
        }

        return RestoreResult(fileCount, byteCount, refused, stoppedEarly)
    }

    /** Reads a zip entry without letting the reader close the archive. */
    private class NonClosingInputStream(private val wrapped: InputStream) : InputStream() {
        override fun read(): Int = wrapped.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = wrapped.read(b, off, len)
        override fun available(): Int = wrapped.available()
        override fun close() = Unit
    }
}
