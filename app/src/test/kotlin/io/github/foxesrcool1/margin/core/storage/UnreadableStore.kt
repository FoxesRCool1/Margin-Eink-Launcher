package io.github.foxesrcool1.margin.core.storage

import java.io.IOException
import java.io.InputStream

/**
 * A store where some files are there but cannot be read, like a card that
 * fails in one place. Writes and everything else go to [real].
 */
class UnreadableStore(private val real: FileStore, private vararg val broken: String) : FileStore by real {
    override fun readBytes(relativePath: String): ByteArray? =
        if (relativePath in broken) null else real.readBytes(relativePath)

    override fun readText(relativePath: String): String? =
        if (relativePath in broken) null else real.readText(relativePath)

    override fun openInput(relativePath: String): InputStream? =
        if (relativePath in broken) null else real.openInput(relativePath)

    // A delegate hands the defaults of the interface to [real] too, so these two say it again.
    override fun readBytesOrThrow(relativePath: String): ByteArray? =
        if (relativePath in broken) throw IOException("broken") else real.readBytesOrThrow(relativePath)

    override fun readTextOrThrow(relativePath: String): String? =
        if (relativePath in broken) throw IOException("broken") else real.readTextOrThrow(relativePath)
}
