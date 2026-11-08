package io.github.foxesrcool1.margin.core.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalFileStoreTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private lateinit var root: File
    private lateinit var store: LocalFileStore

    @Before
    fun setUp() {
        root = temporary.newFolder("Margin")
        store = LocalFileStore(root)
    }

    @Test
    fun `a write can be read back`() {
        assertTrue(store.writeText("notes/plan.md", "hello"))
        assertEquals("hello", store.readText("notes/plan.md"))
        assertTrue(store.exists("notes/plan.md"))
        assertEquals(5L, store.sizeOf("notes/plan.md"))
    }

    @Test
    fun `a write makes the folders it needs`() {
        assertTrue(store.writeText("notes/deep/deeper/plan.md", "x"))
        assertTrue(store.isDirectory("notes/deep/deeper"))
    }

    @Test
    fun `a write over an existing file replaces it and leaves no part file`() {
        store.writeText("notes/plan.md", "first")
        store.writeText("notes/plan.md", "second")
        assertEquals("second", store.readText("notes/plan.md"))

        val leftovers = File(root, "notes").listFiles()!!.filter { it.name.contains(".part-") }
        assertTrue("a temporary file was left behind: $leftovers", leftovers.isEmpty())
    }

    @Test
    fun `binary content survives a round trip`() {
        val bytes = ByteArray(1024) { (it % 256).toByte() }
        assertTrue(store.write("books/sample.bin", bytes))
        assertArrayEquals(bytes, store.readBytes("books/sample.bin"))
    }

    @Test
    fun `reading something that is not there gives null, not an error`() {
        assertNull(store.readText("notes/missing.md"))
        assertNull(store.readBytes("notes/missing.md"))
        assertNull(store.openInput("notes/missing.md"))
    }

    @Test
    fun `reading a folder as a file gives null`() {
        store.createDirectories("notes/ideas")
        assertNull(store.readText("notes/ideas"))
    }

    @Test
    fun `a path that leaves the folder is refused, and nothing is written`() {
        assertFalse(store.writeText("../escape.md", "no"))
        assertFalse(store.exists("../escape.md"))
        assertFalse(File(root.parentFile, "escape.md").exists())

        assertFalse(store.writeText("/etc/passwd", "no"))
        assertNull(store.readText("../escape.md"))
        assertFalse(store.delete("../"))
    }

    @Test
    fun `deleting the root itself is refused`() {
        store.writeText("notes/plan.md", "x")
        assertFalse(store.delete(""))
        assertTrue(store.exists("notes/plan.md"))
        assertTrue(root.exists())
    }

    @Test
    fun `listing gives folders first and then files, both by name`() {
        store.writeText("notes/b.md", "b")
        store.writeText("notes/a.md", "a")
        store.createDirectories("notes/zebra")
        store.createDirectories("notes/alpha")

        val names = store.list("notes").map { it.name }
        assertEquals(listOf("alpha", "zebra", "a.md", "b.md"), names)
    }

    @Test
    fun `listing something that is not there gives an empty list`() {
        assertEquals(emptyList<StoredEntry>(), store.list("nowhere"))
        assertEquals(emptyList<StoredEntry>(), store.listFilesRecursively("nowhere"))
    }

    @Test
    fun `a recursive listing finds every file and no folder`() {
        store.writeText("notes/a.md", "a")
        store.writeText("notes/one/b.md", "b")
        store.writeText("notes/one/two/c.md", "c")
        store.createDirectories("notes/empty")

        val found = store.listFilesRecursively("notes")
        assertEquals(
            listOf("notes/a.md", "notes/one/b.md", "notes/one/two/c.md"),
            found.map { it.relativePath },
        )
        assertTrue(found.none { it.isDirectory })
    }

    @Test
    fun `deleting a folder removes everything in it`() {
        store.writeText("notes/one/two/c.md", "c")
        assertTrue(store.delete("notes/one"))
        assertFalse(store.exists("notes/one"))
        assertTrue(store.exists("notes"))
    }

    @Test
    fun `deleting something that is not there counts as done`() {
        assertTrue(store.delete("notes/never-existed.md"))
    }

    @Test
    fun `a move renames and leaves nothing behind`() {
        store.writeText("notes/a.md", "a")
        assertTrue(store.move("notes/a.md", "notes/done/b.md"))
        assertFalse(store.exists("notes/a.md"))
        assertEquals("a", store.readText("notes/done/b.md"))
    }

    @Test
    fun `a move onto an existing file is refused, so nothing is lost`() {
        store.writeText("notes/a.md", "a")
        store.writeText("notes/b.md", "b")
        assertFalse(store.move("notes/a.md", "notes/b.md"))
        assertEquals("a", store.readText("notes/a.md"))
        assertEquals("b", store.readText("notes/b.md"))
    }

    @Test
    fun `a copy leaves the first file where it was`() {
        store.writeText("notes/a.md", "a")
        assertTrue(store.copy("notes/a.md", "notes/copy.md"))
        assertEquals("a", store.readText("notes/a.md"))
        assertEquals("a", store.readText("notes/copy.md"))
    }

    @Test
    fun `writing from a stream gives the same bytes`() {
        val bytes = ByteArray(5000) { (it % 97).toByte() }
        assertTrue(store.writeFrom("books/big.bin", bytes.inputStream()))
        assertArrayEquals(bytes, store.readBytes("books/big.bin"))
    }

    @Test
    fun `copyTo writes the file into a stream`() {
        store.writeText("notes/a.md", "hello")
        val output = java.io.ByteArrayOutputStream()
        assertTrue(store.copyTo("notes/a.md", output))
        assertEquals("hello", output.toString("UTF-8"))
        assertFalse(store.copyTo("notes/missing.md", output))
    }

    @Test
    fun `writing onto a folder is refused, and the folder is left as it is`() {
        store.writeText("notes/inside.md", "keep me")
        assertFalse(store.writeText("notes", "x"))
        assertTrue(store.isDirectory("notes"))
        assertEquals("keep me", store.readText("notes/inside.md"))

        val siblings = root.listFiles()!!.filter { it.name.startsWith("notes.old-") }
        assertTrue("a folder was moved aside: $siblings", siblings.isEmpty())
    }

    @Test
    fun `listings hide temporary and renaming leftovers but keep unreadable copies`() {
        store.writeText("notes/real.md", "r")
        store.writeText("notes/x.md.part-123", "p")
        store.writeText("notes/y.md.old-456", "o")
        store.writeText("notes/z.renaming", "z")
        store.writeText("notes/keep.md.unreadable", "u")

        val names = store.list("notes").map { it.name }
        assertEquals(listOf("keep.md.unreadable", "real.md"), names)

        val recursive = store.listFilesRecursively("notes").map { it.name }
        assertEquals(listOf("keep.md.unreadable", "real.md"), recursive)
    }

    @Test
    fun `sweeping removes only old part files`() {
        store.writeText("notes/sub/old.md.part-1", "a")
        store.writeText("notes/sub/fresh.md.part-2", "b")
        store.writeText("notes/sub/plain.md", "c")
        val twoDaysAgo = System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000
        assertTrue(File(root, "notes/sub/old.md.part-1").setLastModified(twoDaysAgo))

        val deleted = store.sweepLeftovers(24L * 60 * 60 * 1000)

        assertEquals(1, deleted)
        assertFalse(store.exists("notes/sub/old.md.part-1"))
        assertTrue(store.exists("notes/sub/fresh.md.part-2"))
        assertTrue(store.exists("notes/sub/plain.md"))
    }
}
