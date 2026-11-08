package io.github.foxesrcool1.margin.core.storage

import io.github.foxesrcool1.margin.core.log.AppLog
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * A [FileStore] over plain `java.io.File`.
 *
 * It holds no Android class, so every rule in it is checked by a plain JUnit
 * test against a temporary folder. The only thing the app has to decide is
 * which folder to hand it.
 */
class LocalFileStore(private val root: File) : FileStore {

    init {
        root.mkdirs()
    }

    override val displayPath: String get() = root.absolutePath

    /** Turns a relative path into a real file, and refuses anything outside the root. */
    private fun resolve(relativePath: String): File {
        val clean = RelativePaths.normalise(relativePath)
        if (clean.isEmpty()) return root
        val target = File(root, clean)

        // A second check, in case a symbolic link points out of the folder.
        val rootPath = root.canonicalFile.path
        val targetPath = target.canonicalFile.path
        if (targetPath != rootPath && !targetPath.startsWith(rootPath + File.separator)) {
            throw UnsafePathException(relativePath)
        }
        return target
    }

    private fun relativeOf(file: File): String =
        file.canonicalFile.path
            .removePrefix(root.canonicalFile.path)
            .removePrefix(File.separator)
            .replace(File.separatorChar, '/')

    private fun entryOf(file: File): StoredEntry = StoredEntry(
        relativePath = relativeOf(file),
        name = file.name,
        isDirectory = file.isDirectory,
        sizeBytes = if (file.isDirectory) 0L else file.length(),
        lastModified = file.lastModified(),
    )

    override fun exists(relativePath: String): Boolean =
        runCatching { resolve(relativePath).exists() }.getOrDefault(false)

    override fun isDirectory(relativePath: String): Boolean =
        runCatching { resolve(relativePath).isDirectory }.getOrDefault(false)

    override fun sizeOf(relativePath: String): Long =
        runCatching { resolve(relativePath).let { if (it.isFile) it.length() else 0L } }
            .getOrDefault(0L)

    override fun lastModified(relativePath: String): Long =
        runCatching { resolve(relativePath).lastModified() }.getOrDefault(0L)

    /** Names left behind by an interrupted or Windows write, which are not user content. */
    private val leftoverPattern = Regex("""\.(part|old)-\d+$""")
    private val partPattern = Regex("""\.part-\d+$""")

    private fun isLeftover(name: String): Boolean =
        leftoverPattern.containsMatchIn(name) || name.endsWith(".renaming")

    override fun list(relativePath: String): List<StoredEntry> {
        val directory = runCatching { resolve(relativePath) }.getOrNull() ?: return emptyList()
        val children = directory.listFiles() ?: return emptyList()
        return children
            .filterNot { isLeftover(it.name) }
            .map(::entryOf)
            .sortedWith(compareByDescending<StoredEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override fun listFilesRecursively(relativePath: String): List<StoredEntry> {
        val start = runCatching { resolve(relativePath) }.getOrNull() ?: return emptyList()
        if (!start.exists()) return emptyList()

        val found = mutableListOf<StoredEntry>()

        // An explicit stack rather than recursion: a deep folder tree that a
        // sync program made should not overflow the stack. The visited set
        // stops a link that points back at a parent from looping for ever.
        val pending = ArrayDeque<File>()
        val visited = mutableSetOf<String>()
        pending.addLast(start)

        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            val key = runCatching { current.canonicalPath }.getOrDefault(current.path)
            if (!visited.add(key)) continue

            val children = current.listFiles() ?: continue
            children.forEach { child ->
                if (child.isDirectory) {
                    pending.addLast(child)
                } else if (!isLeftover(child.name)) {
                    found += entryOf(child)
                }
            }
        }
        return found.sortedBy { it.relativePath.lowercase() }
    }

    override fun sweepLeftovers(olderThanMillis: Long): Int {
        val cutoff = System.currentTimeMillis() - olderThanMillis
        var deleted = 0

        // The same walk as listFilesRecursively, without the leftover filter.
        val pending = ArrayDeque<File>()
        val visited = mutableSetOf<String>()
        pending.addLast(root)

        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            val key = runCatching { current.canonicalPath }.getOrDefault(current.path)
            if (!visited.add(key)) continue

            val children = current.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    pending.addLast(child)
                } else if (partPattern.containsMatchIn(child.name) && child.lastModified() < cutoff && child.delete()) {
                    deleted++
                }
            }
        }
        return deleted
    }

    override fun createDirectories(relativePath: String): Boolean = runCatching {
        val directory = resolve(relativePath)
        directory.isDirectory || directory.mkdirs()
    }.getOrDefault(false)

    override fun readBytes(relativePath: String): ByteArray? = runCatching {
        val file = resolve(relativePath)
        if (file.isFile) file.readBytes() else null
    }.getOrNull()

    override fun readText(relativePath: String): String? = runCatching {
        val file = resolve(relativePath)
        if (file.isFile) file.readText() else null
    }.getOrNull()

    override fun write(relativePath: String, bytes: ByteArray): Boolean =
        writeAtomically(relativePath) { output -> output.write(bytes) }

    override fun writeText(relativePath: String, text: String): Boolean =
        write(relativePath, text.toByteArray())

    override fun writeFrom(relativePath: String, input: InputStream): Boolean =
        writeAtomically(relativePath) { output -> input.copyTo(output) }

    /**
     * Writes through a temporary file in the same folder, then renames it over
     * the target. A rename inside one folder is atomic on every file system
     * this app will meet, so a reader sees either the old file or the new one
     * and never a half written one.
     */
    private fun writeAtomically(relativePath: String, body: (OutputStream) -> Unit): Boolean {
        return runCatching {
            if (RelativePaths.normalise(relativePath).isEmpty()) return@runCatching false

            val target = resolve(relativePath)
            if (target.isDirectory) {
                AppLog.w("LocalFileStore", "Refused to write over the folder $relativePath")
                return@runCatching false
            }
            val parent = target.parentFile ?: return@runCatching false
            parent.mkdirs()

            val temporary = File(parent, "${target.name}.part-${System.nanoTime()}")
            try {
                FileOutputStream(temporary).use { output ->
                    body(output)
                    output.flush()
                    // Push the bytes to the disk before the rename, so a power loss
                    // cannot leave the new name pointing at an empty file.
                    output.fd.sync()
                }

                // On every file system this app will meet except Windows, a
                // rename replaces the file that is there, in one step.
                if (temporary.renameTo(target)) return@runCatching true

                // Windows and some memory cards refuse that. Move the old file
                // aside rather than delete it, so it can be put back if the
                // rename fails again. Deleting first would mean a failure here
                // loses the note.
                val aside = File(parent, "${target.name}.old-${System.nanoTime()}")
                val movedAside = target.exists() && target.renameTo(aside)

                if (temporary.renameTo(target)) {
                    if (movedAside) aside.delete()
                    return@runCatching true
                }

                if (movedAside) aside.renameTo(target)
                temporary.delete()
                false
            } catch (error: Throwable) {
                temporary.delete()
                throw error
            }
        }.getOrDefault(false)
    }

    override fun openInput(relativePath: String): InputStream? = runCatching {
        val file = resolve(relativePath)
        if (file.isFile) file.inputStream() else null
    }.getOrNull()

    override fun delete(relativePath: String): Boolean = runCatching {
        val file = resolve(relativePath)
        if (!file.exists()) return@runCatching true
        // Never let a delete walk out of the data folder.
        if (file.canonicalFile == root.canonicalFile) return@runCatching false
        file.deleteRecursively()
    }.getOrDefault(false)

    override fun move(fromRelativePath: String, toRelativePath: String): Boolean = runCatching {
        val from = resolve(fromRelativePath)
        val to = resolve(toRelativePath)
        if (!from.exists()) return@runCatching false
        if (to.exists()) return@runCatching false
        to.parentFile?.mkdirs()
        if (from.renameTo(to)) return@runCatching true

        // A rename across two file systems fails. Copy, then remove.
        if (from.isDirectory) {
            from.copyRecursively(to, overwrite = false)
            from.deleteRecursively()
        } else {
            from.copyTo(to, overwrite = false)
            from.delete()
        }
        to.exists()
    }.getOrDefault(false)

    override fun copy(fromRelativePath: String, toRelativePath: String): Boolean = runCatching {
        val from = resolve(fromRelativePath)
        if (!from.isFile) return@runCatching false
        from.inputStream().use { input -> writeFrom(toRelativePath, input) }
    }.getOrDefault(false)

    override fun copyTo(relativePath: String, output: OutputStream): Boolean = runCatching {
        val file = resolve(relativePath)
        if (!file.isFile) return@runCatching false
        file.inputStream().use { input -> input.copyTo(output) }
        true
    }.getOrDefault(false)
}
