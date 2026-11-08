package io.github.foxesrcool1.margin.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupArchiveTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private lateinit var source: LocalFileStore
    private lateinit var target: LocalFileStore

    @Before
    fun setUp() {
        source = LocalFileStore(temporary.newFolder("source"))
        target = LocalFileStore(temporary.newFolder("target"))
    }

    @Test
    fun `everything that went in comes back out`() {
        source.writeText("notes/plan.md", "the plan")
        source.writeText("notes/deep/deeper/idea.md", "an idea")
        source.writeText("journal/2026/2026-09-19.md", "today")
        source.write("books/book.epub", ByteArray(300) { it.toByte() })
        source.writeText("habits/log.csv", "date,habit\n")

        val zipped = ByteArrayOutputStream()
        val backup = BackupArchive.backup(source, zipped)
        assertEquals(5, backup.fileCount)
        assertTrue(backup.skipped.isEmpty())

        val restore = BackupArchive.restore(target, ByteArrayInputStream(zipped.toByteArray()))
        assertEquals(5, restore.fileCount)
        assertTrue(restore.refused.isEmpty())

        assertEquals("the plan", target.readText("notes/plan.md"))
        assertEquals("an idea", target.readText("notes/deep/deeper/idea.md"))
        assertEquals("today", target.readText("journal/2026/2026-09-19.md"))
        assertEquals("date,habit\n", target.readText("habits/log.csv"))
        assertEquals(300, target.sizeOf("books/book.epub"))
    }

    @Test
    fun `exports and logs stay out of the backup`() {
        source.writeText("notes/keep.md", "keep")
        source.writeText("exports/page.png", "not really a png")
        source.writeText("logs/log-2026-09-19.txt", "noise")

        val zipped = ByteArrayOutputStream()
        val backup = BackupArchive.backup(source, zipped)
        assertEquals(1, backup.fileCount)

        BackupArchive.restore(target, ByteArrayInputStream(zipped.toByteArray()))
        assertTrue(target.exists("notes/keep.md"))
        assertFalse(target.exists("exports/page.png"))
        assertFalse(target.exists("logs/log-2026-09-19.txt"))
    }

    @Test
    fun `an archive that points outside the data folder is refused`() {
        // This is the zip slip attack. A zip can name an entry anything it
        // likes, and a restore that trusted the name would write over a file
        // outside the data folder.
        val zipped = ByteArrayOutputStream()
        ZipOutputStream(zipped).use { zip ->
            listOf(
                "../escaped.md",
                "notes/../../escaped-too.md",
                "/etc/passwd",
                "..\\windows.md",
                "notes/good.md",
            ).forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write("x".toByteArray())
                zip.closeEntry()
            }
        }

        val result = BackupArchive.restore(target, ByteArrayInputStream(zipped.toByteArray()))

        assertEquals(1, result.fileCount)
        assertEquals(4, result.refused.size)
        assertEquals("x", target.readText("notes/good.md"))

        val outside = File(target.displayPath).parentFile
        assertFalse(File(outside, "escaped.md").exists())
        assertFalse(File(outside, "escaped-too.md").exists())
        assertFalse(File(outside, "windows.md").exists())
    }

    @Test
    fun `an entry that is not in a known folder is refused`() {
        val zipped = ByteArrayOutputStream()
        ZipOutputStream(zipped).use { zip ->
            zip.putNextEntry(ZipEntry("somewhere-else/file.md"))
            zip.write("x".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("loose-file.md"))
            zip.write("x".toByteArray())
            zip.closeEntry()
        }

        val result = BackupArchive.restore(target, ByteArrayInputStream(zipped.toByteArray()))
        assertEquals(0, result.fileCount)
        assertEquals(2, result.refused.size)
        assertNull(target.readText("loose-file.md"))
    }

    @Test
    fun `restore without overwrite leaves the file that is already there`() {
        source.writeText("notes/plan.md", "new")
        val zipped = ByteArrayOutputStream()
        BackupArchive.backup(source, zipped)

        target.writeText("notes/plan.md", "old")
        val result = BackupArchive.restore(
            target,
            ByteArrayInputStream(zipped.toByteArray()),
            overwrite = false,
        )

        assertEquals(0, result.fileCount)
        assertEquals("old", target.readText("notes/plan.md"))
    }

    @Test
    fun `an empty data folder makes an archive that restores to nothing`() {
        val zipped = ByteArrayOutputStream()
        val backup = BackupArchive.backup(source, zipped)
        assertEquals(0, backup.fileCount)

        val restore = BackupArchive.restore(target, ByteArrayInputStream(zipped.toByteArray()))
        assertEquals(0, restore.fileCount)
        assertTrue(restore.refused.isEmpty())
    }

    @Test
    fun `a file with an awkward name survives the round trip`() {
        source.writeText("notes/a note with spaces.md", "ok")
        source.writeText("notes/folder with spaces/another.md", "ok too")

        val zipped = ByteArrayOutputStream()
        BackupArchive.backup(source, zipped)
        BackupArchive.restore(target, ByteArrayInputStream(zipped.toByteArray()))

        assertEquals("ok", target.readText("notes/a note with spaces.md"))
        assertEquals("ok too", target.readText("notes/folder with spaces/another.md"))
    }

    @Test
    fun `a file that cannot be read is skipped and leaves no empty entry`() {
        source.writeText("notes/good.md", "good")
        source.writeText("notes/bad.md", "bad")

        val zipped = ByteArrayOutputStream()
        val backup = BackupArchive.backup(UnreadableStore(source, "notes/bad.md"), zipped)
        assertEquals(1, backup.fileCount)
        assertEquals(listOf("notes/bad.md"), backup.skipped)

        target.writeText("notes/bad.md", "the real one")
        BackupArchive.restore(target, ByteArrayInputStream(zipped.toByteArray()))
        assertEquals("the real one", target.readText("notes/bad.md"))
        assertEquals("good", target.readText("notes/good.md"))
    }

    @Test
    fun `a file whose first read fails has no entry in the zip and is skipped`() {
        source.writeText("notes/good.md", "good")
        source.writeText("notes/bad.md", "bad")

        val zipped = ByteArrayOutputStream()
        val backup = BackupArchive.backup(ReadFailingStore(source, "notes/bad.md"), zipped)
        assertEquals(listOf("notes/bad.md"), backup.skipped)

        val names = ZipInputStream(ByteArrayInputStream(zipped.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }
        assertFalse(names.contains("notes/bad.md"))
        assertTrue(names.contains("notes/good.md"))
    }

    @Test
    fun `a zip that ends early reports what it restored`() {
        source.writeText("notes/a.md", "first")
        source.write("notes/b.bin", java.util.Random(1).let { r -> ByteArray(5000).also(r::nextBytes) })
        val zipped = ByteArrayOutputStream()
        BackupArchive.backup(source, zipped)
        val bytes = zipped.toByteArray()

        val result = BackupArchive.restore(target, ByteArrayInputStream(bytes.copyOf(bytes.size - 2000)))

        assertTrue(result.stoppedEarly)
        assertEquals(1, result.fileCount)
        assertEquals("first", target.readText("notes/a.md"))
    }

    @Test
    fun `an entry named like a top folder is refused and the folder is left alone`() {
        target.writeText("notes/plan.md", "the plan")
        val zipped = ByteArrayOutputStream()
        ZipOutputStream(zipped).use { zip ->
            zip.putNextEntry(ZipEntry("notes"))
            zip.write("x".toByteArray())
            zip.closeEntry()
        }

        val result = BackupArchive.restore(target, ByteArrayInputStream(zipped.toByteArray()))

        assertEquals(0, result.fileCount)
        assertEquals(listOf("notes"), result.refused)
        assertTrue(target.isDirectory("notes"))
        assertEquals("the plan", target.readText("notes/plan.md"))
    }

    @Test
    fun `an entry named like an existing folder is refused`() {
        target.writeText("notes/sub/inside.md", "inside")
        val zipped = ByteArrayOutputStream()
        ZipOutputStream(zipped).use { zip ->
            zip.putNextEntry(ZipEntry("notes/sub"))
            zip.write("x".toByteArray())
            zip.closeEntry()
        }

        val result = BackupArchive.restore(target, ByteArrayInputStream(zipped.toByteArray()))

        assertEquals(0, result.fileCount)
        assertEquals(listOf("notes/sub"), result.refused)
        assertTrue(target.isDirectory("notes/sub"))
        assertEquals("inside", target.readText("notes/sub/inside.md"))
    }

    @Test
    fun `settings come back out of a restore as the same map`() {
        source.writeText("notes/plan.md", "the plan")
        val settings = mapOf(
            "theme" to "dark",
            "quote" to "say \"hi\"",
            "city" to "Zürich ✓",
        )
        val zipped = ByteArrayOutputStream()
        BackupArchive.backup(source, zipped, settings = settings)

        val result = BackupArchive.restore(target, ByteArrayInputStream(zipped.toByteArray()))

        assertEquals(settings, result.settings)
        assertTrue(result.refused.isEmpty())
        assertFalse(target.exists("settings.json"))
    }

    @Test
    fun `a backup without settings writes no settings file`() {
        source.writeText("notes/plan.md", "the plan")
        val zipped = ByteArrayOutputStream()
        BackupArchive.backup(source, zipped, settings = emptyMap())

        val names = ZipInputStream(ByteArrayInputStream(zipped.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }
        assertFalse(names.contains("settings.json"))

        val result = BackupArchive.restore(target, ByteArrayInputStream(zipped.toByteArray()))
        assertTrue(result.settings.isEmpty())
        assertTrue(result.refused.isEmpty())
    }

    @Test
    fun `a file that fails while it is read is skipped and the rest still goes in`() {
        source.writeText("notes/good.md", "good")
        source.writeText("notes/bad.md", "bad")

        val zipped = ByteArrayOutputStream()
        val backup = BackupArchive.backup(ReadFailingStore(source, "notes/bad.md"), zipped)
        assertEquals(1, backup.fileCount)
        assertEquals(listOf("notes/bad.md"), backup.skipped)

        BackupArchive.restore(target, ByteArrayInputStream(zipped.toByteArray()))
        assertEquals("good", target.readText("notes/good.md"))
    }

    @Test(expected = IOException::class)
    fun `a failed write to the output stops the backup instead of counting files as skipped`() {
        source.writeText("notes/a.md", "a")
        source.writeText("notes/b.md", "b")
        BackupArchive.backup(source, FullDiskOutput())
    }
}

/** A store where one file opens but its bytes cannot be read, like a card that fails half way. */
private class ReadFailingStore(private val real: FileStore, private vararg val broken: String) : FileStore by real {
    override fun openInput(relativePath: String): InputStream? =
        if (relativePath in broken) FailingInput() else real.openInput(relativePath)

    private class FailingInput : InputStream() {
        override fun read(): Int {
            throw IOException("the card failed")
        }
    }
}

/** An output that fails on every write, like a full disk. */
private class FullDiskOutput : OutputStream() {
    override fun write(b: Int) {
        throw IOException("disk full")
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        throw IOException("disk full")
    }
}
