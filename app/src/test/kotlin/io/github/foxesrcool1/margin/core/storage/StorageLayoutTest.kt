package io.github.foxesrcool1.margin.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StorageLayoutTest {

    @Test
    fun `a safe name keeps letters digits and simple punctuation`() {
        assertEquals("War and Peace", StorageLayout.safeName("War and Peace"))
        assertEquals("notes_2026-09-19.md", StorageLayout.safeName("notes_2026-09-19.md"))
    }

    @Test
    fun `characters a file system refuses become a dash`() {
        assertEquals("a-b", StorageLayout.safeName("a/b"))
        assertEquals("a-b", StorageLayout.safeName("a:b"))
        assertEquals("a-b", StorageLayout.safeName("a?b"))
        assertEquals("a-b", StorageLayout.safeName("a*b"))
    }

    @Test
    fun `a run of refused characters becomes one dash`() {
        assertEquals("a-b", StorageLayout.safeName("a///b"))
    }

    @Test
    fun `a name never ends with a dot or a space, because Windows refuses those`() {
        assertEquals("notes", StorageLayout.safeName("notes."))
        assertEquals("notes", StorageLayout.safeName("notes   "))
        assertEquals("notes", StorageLayout.safeName("  notes  "))
    }

    @Test
    fun `a name is never empty`() {
        assertEquals("untitled", StorageLayout.safeName(""))
        assertEquals("untitled", StorageLayout.safeName("   "))
        assertEquals("untitled", StorageLayout.safeName("///"))
        assertEquals("untitled", StorageLayout.safeName("..."))
    }

    @Test
    fun `a very long name is cut short`() {
        val long = "x".repeat(500)
        assertTrue(StorageLayout.safeName(long).toByteArray(Charsets.UTF_8).size <= 200)
    }

    @Test
    fun `a long latin book name keeps its extension and stays a book`() {
        val name = "x".repeat(150) + ".epub"
        val path = StorageLayout.bookPath(name)
        assertTrue(path.endsWith(".epub"))
        assertTrue(path.substringAfterLast('/').toByteArray(Charsets.UTF_8).size <= 200)
        assertTrue(StorageLayout.isBook(name))
    }

    @Test
    fun `a long CJK book name keeps its extension within 200 bytes`() {
        val path = StorageLayout.bookPath("漢".repeat(100) + ".pdf")
        assertTrue(path.endsWith(".pdf"))
        assertTrue(path.substringAfterLast('/').toByteArray(Charsets.UTF_8).size <= 200)
    }

    @Test
    fun `safeName never splits a surrogate pair`() {
        val result = StorageLayout.safeName("a".repeat(198) + "😀", 200)
        assertEquals("a".repeat(198), result)
        assertFalse(result.any { it.isSurrogate() })
    }

    @Test
    fun `a long book id keeps its size suffix and is a safe name`() {
        val id = StorageLayout.bookIdFor("w".repeat(300) + ".epub", 12345)
        assertTrue(id.endsWith("-12345"))
        assertTrue(id.toByteArray(Charsets.UTF_8).size <= 200)
        assertEquals(id, StorageLayout.safeName(id))
    }

    @Test
    fun `a short name stays unchanged`() {
        assertEquals("notes", StorageLayout.safeName("notes"))
        assertEquals("books/a.epub", StorageLayout.bookPath("a.epub"))
    }

    @Test
    fun `a book id uses the name and the size`() {
        assertEquals("war-and-peace-12345", StorageLayout.bookIdFor("War and Peace.epub", 12345))
    }

    @Test
    fun `two files with the same name but different sizes get different ids`() {
        val one = StorageLayout.bookIdFor("book.epub", 100)
        val two = StorageLayout.bookIdFor("book.epub", 200)
        assertFalse(one == two)
    }

    @Test
    fun `a journal entry sits in a folder for its year`() {
        val date = LocalDate.of(2026, 9, 19)
        assertEquals("journal/2026/2026-09-19.md", StorageLayout.journalPath(date))
        assertEquals(
            "journal/2026/2026-09-19.inknote",
            StorageLayout.journalPath(date, handwritten = true),
        )
    }

    @Test
    fun `stroke files are padded so they sort in page order`() {
        assertEquals("annotations/book-1/page-0001.strokes", StorageLayout.strokesPath("book-1", 1))
        assertEquals("annotations/book-1/page-0042.strokes", StorageLayout.strokesPath("book-1", 42))
        val pages = listOf(10, 2, 1).map { StorageLayout.strokesPath("b", it) }.sorted()
        assertEquals(
            listOf(
                "annotations/b/page-0001.strokes",
                "annotations/b/page-0002.strokes",
                "annotations/b/page-0010.strokes",
            ),
            pages,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `page zero is refused, because pages start at one`() {
        StorageLayout.strokesPath("book", 0)
    }

    @Test
    fun `file kinds are told apart by extension, whatever the case`() {
        assertTrue(StorageLayout.isBook("a.epub"))
        assertTrue(StorageLayout.isBook("a.PDF"))
        assertFalse(StorageLayout.isBook("a.txt"))
        assertTrue(StorageLayout.isTypedNote("a.md"))
        assertTrue(StorageLayout.isInkNote("a.inknote"))
        assertFalse(StorageLayout.isInkNote("a.md"))
    }

    @Test
    fun `every path the layout builds is safe`() {
        val date = LocalDate.of(2026, 1, 2)
        val paths = listOf(
            StorageLayout.bookPath("../../etc/passwd"),
            StorageLayout.annotationsPath("../escape"),
            StorageLayout.strokesPath("../escape", 3),
            StorageLayout.journalPath(date),
            StorageLayout.habitsPath(),
            StorageLayout.habitLogPath(),
        )
        paths.forEach { path ->
            assertTrue("unsafe path built: $path", RelativePaths.isSafe(path))
        }
    }
}
