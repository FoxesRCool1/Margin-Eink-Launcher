package io.github.foxesrcool1.margin.core.storage

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** A file or a folder inside the data root. */
data class StoredEntry(
    val relativePath: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
) {
    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "")
}

/**
 * Everything the app is allowed to do with the data folder.
 *
 * There is one implementation today, [LocalFileStore], which uses plain
 * `java.io.File`. A Storage Access Framework implementation fits behind the
 * same interface, so the choice between them is one line in the app and not a
 * rewrite. See `docs/decisions/0005-storage-layer.md`.
 *
 * Every path is relative to the data root and uses `/` as the separator. A
 * path that tries to leave the root is refused, not repaired.
 */
interface FileStore {

    /** Something to show the user, so they can find the folder themselves. */
    val displayPath: String

    fun exists(relativePath: String): Boolean

    fun isDirectory(relativePath: String): Boolean

    fun sizeOf(relativePath: String): Long

    fun lastModified(relativePath: String): Long

    /** One level only, folders first, then files, both by name. */
    fun list(relativePath: String): List<StoredEntry>

    /** Every file below [relativePath], folders left out. */
    fun listFilesRecursively(relativePath: String): List<StoredEntry>

    fun createDirectories(relativePath: String): Boolean

    fun readBytes(relativePath: String): ByteArray?

    fun readText(relativePath: String): String?

    /**
     * Like [readBytes], but a file that is there and cannot be read throws
     * an [IOException]. Null means the file is not there. A caller that reads
     * a file, changes it and writes it back must use this one: with
     * [readBytes] it cannot tell "missing" from "broken" and would write over
     * the real file.
     */
    @Throws(IOException::class)
    fun readBytesOrThrow(relativePath: String): ByteArray? =
        readBytes(relativePath) ?: if (exists(relativePath)) throw IOException("Could not read $relativePath") else null

    /** The text version of [readBytesOrThrow]. */
    @Throws(IOException::class)
    fun readTextOrThrow(relativePath: String): String? =
        readText(relativePath) ?: if (exists(relativePath)) throw IOException("Could not read $relativePath") else null

    /**
     * Writes the whole file, or leaves the old one alone.
     *
     * The write goes to a temporary file next to the target and is renamed
     * over it. A tablet that loses power in the middle then still has the
     * last good version, instead of half a note.
     */
    fun write(relativePath: String, bytes: ByteArray): Boolean

    fun writeText(relativePath: String, text: String): Boolean

    /** The same promise as [write], for content that is too big to hold in memory. */
    fun writeFrom(relativePath: String, input: InputStream): Boolean

    fun openInput(relativePath: String): InputStream?

    /** Deletes a file, or a folder and everything in it. */
    fun delete(relativePath: String): Boolean

    /** Renames or moves. Creates the target folder when it is missing. */
    fun move(fromRelativePath: String, toRelativePath: String): Boolean

    /** Copies a file inside the store. */
    fun copy(fromRelativePath: String, toRelativePath: String): Boolean

    /** Streams a file out, for a backup or an export. */
    fun copyTo(relativePath: String, output: OutputStream): Boolean
}

/** Thrown when a path would leave the data root. */
class UnsafePathException(path: String) :
    IllegalArgumentException("Path leaves the data folder: $path")
