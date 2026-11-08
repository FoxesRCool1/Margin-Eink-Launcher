package io.github.foxesrcool1.margin.core.storage

import io.github.foxesrcool1.margin.core.json.Json
import io.github.foxesrcool1.margin.core.json.JsonObject
import io.github.foxesrcool1.margin.core.json.JsonString
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
    /** The app settings from settings.json at the root of the archive. Empty when there were none. */
    val settings: Map<String, String> = emptyMap(),
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
    const val SETTINGS_NAME = "settings.json"
    const val FORMAT_VERSION = 1

    fun backup(
        store: FileStore,
        output: OutputStream,
        folders: List<String> = StorageLayout.backedUpFolders,
        settings: Map<String, String> = emptyMap(),
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

            if (settings.isNotEmpty()) {
                val text = Json.write(JsonObject(settings.mapValues { JsonString(it.value) }))
                zip.putNextEntry(ZipEntry(SETTINGS_NAME))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            folders.forEach { folder ->
                store.listFilesRecursively(folder).forEach { entry ->
                    val input = runCatching { store.openInput(entry.relativePath) }.getOrNull()
                    if (input == null) {
                        AppLog.w(TAG, "Backup could not open ${entry.relativePath}")
                        skipped += entry.relativePath
                        return@forEach
                    }

                    // Only a failed read skips the file. A failed write belongs to the
                    // zip output and must reach the caller, so the write is not in the try.
                    var readFailed = false
                    input.use { source ->
                        val buffer = ByteArray(8192)
                        fun readChunk(): Int = try {
                            source.read(buffer)
                        } catch (problem: IOException) {
                            AppLog.w(TAG, "Backup could not read ${entry.relativePath}", problem)
                            readFailed = true
                            -1
                        }

                        // The first read happens before the entry exists. If it fails,
                        // no entry is written and the file is listed in skipped.
                        var read = readChunk()
                        if (!readFailed) {
                            zip.putNextEntry(ZipEntry(entry.relativePath))
                            while (read >= 0) {
                                zip.write(buffer, 0, read)
                                read = readChunk()
                            }
                            // A read that fails after the first chunk leaves a short entry,
                            // and the file is still listed in skipped.
                            zip.closeEntry()
                        }
                    }

                    if (readFailed) {
                        skipped += entry.relativePath
                    } else {
                        fileCount++
                        byteCount += entry.sizeBytes
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
        var settings: Map<String, String> = emptyMap()

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

                    if (rawName == SETTINGS_NAME) {
                        settings = readSettings(zip)
                        zip.closeEntry()
                        continue
                    }

                    val safePath = runCatching { RelativePaths.normalise(rawName) }.getOrNull()
                    val topFolder = safePath?.substringBefore('/')

                    // A name with no folder above it would be a file where a folder belongs.
                    // An existing folder is never replaced by a file.
                    if (safePath == null || safePath.isEmpty() || !safePath.contains('/') ||
                        safePath.substringAfterLast('/').isEmpty() || topFolder !in folders ||
                        store.isDirectory(safePath)
                    ) {
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

        return RestoreResult(fileCount, byteCount, refused, stoppedEarly, settings)
    }

    /** Reads settings.json. Only string values are kept. A damaged file gives no settings. */
    private fun readSettings(zip: ZipInputStream): Map<String, String> {
        val parsed = Json.parseOrNull(zip.readBytes().toString(Charsets.UTF_8)) as? JsonObject
        if (parsed == null) {
            AppLog.w(TAG, "settings.json could not be read")
            return emptyMap()
        }
        return parsed.entries
            .mapNotNull { (key, value) -> (value as? JsonString)?.let { key to it.value } }
            .toMap()
    }

    /** Reads a zip entry without letting the reader close the archive. */
    private class NonClosingInputStream(private val wrapped: InputStream) : InputStream() {
        override fun read(): Int = wrapped.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = wrapped.read(b, off, len)
        override fun available(): Int = wrapped.available()
        override fun close() = Unit
    }
}
