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
import java.time.LocalDate

class DataRepositoryTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private lateinit var store: LocalFileStore
    private lateinit var repository: DataRepository

    @Before
    fun setUp() {
        store = LocalFileStore(temporary.newFolder("Margin"))
        repository = DataRepository(store)
    }

    @Test
    fun `the folders are made on first run`() {
        assertTrue(repository.ensureFolders())
        StorageLayout.topLevelFolders.forEach { folder ->
            assertTrue("missing folder: $folder", store.isDirectory(folder))
        }
    }

    @Test
    fun `making the folders twice is not a failure`() {
        assertTrue(repository.ensureFolders())
        assertTrue(repository.ensureFolders())
    }

    @Test
    fun `an imported book is copied into the books folder`() {
        val bytes = ByteArray(500) { it.toByte() }
        val result = repository.importBook(bytes.inputStream(), "War and Peace.epub")

        assertTrue(result is ImportResult.Imported)
        val imported = result as ImportResult.Imported
        assertEquals("books/War and Peace.epub", imported.relativePath)
        assertEquals("war-and-peace-500", imported.bookId)
        assertEquals(500L, store.sizeOf(imported.relativePath))
    }

    @Test
    fun `a book name that a file system would refuse is cleaned up`() {
        val result = repository.importBook(
            ByteArray(4).inputStream(),
            "Book: part 1/2 *draft*.epub",
        )
        val imported = result as ImportResult.Imported
        assertTrue(RelativePaths.isSafe(imported.relativePath))
        assertEquals("books/Book- part 1-2 -draft-.epub", imported.relativePath)
    }

    @Test
    fun `importing the same book again says so instead of copying it twice`() {
        repository.importBook(ByteArray(10).inputStream(), "book.epub")
        val second = repository.importBook(ByteArray(10).inputStream(), "book.epub")
        assertTrue(second is ImportResult.AlreadyThere)
        assertEquals(1, repository.listBooks().size)
    }

    @Test
    fun `a file that is not a book is refused`() {
        val result = repository.importBook(ByteArray(10).inputStream(), "notes.txt")
        assertTrue(result is ImportResult.Failed)
        assertTrue(repository.listBooks().isEmpty())
    }

    @Test
    fun `listing books leaves other files alone`() {
        repository.importBook(ByteArray(1).inputStream(), "a.epub")
        repository.importBook(ByteArray(2).inputStream(), "b.pdf")
        store.writeText("books/stray.txt", "x")

        assertEquals(
            listOf("books/a.epub", "books/b.pdf"),
            repository.listBooks().map { it.relativePath },
        )
    }

    @Test
    fun `a free path adds a number instead of overwriting`() {
        store.writeText("notes/plan.md", "first")
        assertEquals("notes/plan 2.md", repository.freePath("notes/plan.md"))

        store.writeText("notes/plan 2.md", "second")
        assertEquals("notes/plan 3.md", repository.freePath("notes/plan.md"))
    }

    @Test
    fun `a free path leaves a name that is not taken alone`() {
        assertEquals("notes/new.md", repository.freePath("notes/new.md"))
    }

    @Test
    fun `a new note path is built from the folder and the title`() {
        assertEquals("notes/ideas/My Plan.md", repository.newNotePath("ideas", "My Plan"))
        assertEquals("notes/Untitled.md", repository.newNotePath("", "Untitled"))
        assertTrue(RelativePaths.isSafe(repository.newNotePath("ideas", "a/b:c")))
    }

    @Test
    fun `a journal entry is written and read back by date`() {
        val date = LocalDate.of(2026, 9, 19)
        assertNull(repository.journalEntry(date))
        assertTrue(repository.writeJournalEntry(date, "It rained."))
        assertEquals("It rained.", repository.journalEntry(date))
    }

    @Test
    fun `the days with an entry in a year are listed in order`() {
        repository.writeJournalEntry(LocalDate.of(2026, 9, 19), "c")
        repository.writeJournalEntry(LocalDate.of(2026, 1, 2), "a")
        repository.writeJournalEntry(LocalDate.of(2026, 5, 6), "b")
        repository.writeJournalEntry(LocalDate.of(2025, 12, 31), "last year")
        store.writeText("journal/2026/notes.txt", "not a day")

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 5, 6),
                LocalDate.of(2026, 9, 19),
            ),
            repository.journalDatesIn(2026),
        )
        assertEquals(listOf(LocalDate.of(2025, 12, 31)), repository.journalDatesIn(2025))
        assertTrue(repository.journalDatesIn(2024).isEmpty())
    }

    @Test
    fun `a backup and a restore carry the data folder across`() {
        repository.ensureFolders()
        repository.importBook(ByteArray(40).inputStream(), "book.epub")
        repository.writeNote("notes/plan.md", "the plan")
        repository.writeJournalEntry(LocalDate.of(2026, 9, 19), "today")

        val zipped = ByteArrayOutputStream()
        val backup = repository.backupTo(zipped)
        assertEquals(3, backup.fileCount)

        val fresh = DataRepository(LocalFileStore(temporary.newFolder("restored")))
        val restore = fresh.restoreFrom(ByteArrayInputStream(zipped.toByteArray()))

        assertEquals(3, restore.fileCount)
        assertEquals("the plan", fresh.readNote("notes/plan.md"))
        assertEquals("today", fresh.journalEntry(LocalDate.of(2026, 9, 19)))
        assertEquals(1, fresh.listBooks().size)
    }

    @Test
    fun `deleting a book removes it`() {
        val imported = repository.importBook(
            ByteArray(10).inputStream(),
            "book.epub",
        ) as ImportResult.Imported

        assertTrue(repository.deleteBook(imported.relativePath))
        assertFalse(store.exists(imported.relativePath))
        assertTrue(repository.listBooks().isEmpty())
    }

    @Test
    fun `the index sees what the repository wrote`() {
        repository.importBook(ByteArray(10).inputStream(), "book.epub")
        repository.writeNote("notes/plan.md", "the plan")

        val snapshot = repository.buildIndex(nowMillis = 5L)
        assertEquals(1, snapshot.of(IndexKind.Book).size)
        assertEquals(1, snapshot.of(IndexKind.TypedNote).size)
    }

    @Test
    fun `a handwritten journal page counts as an entry for its day`() {
        val store = LocalFileStore(temporary.newFolder("MarginInk"))
        val data = DataRepository(store).also { it.ensureFolders() }
        val day = java.time.LocalDate.of(2026, 9, 20)

        assertFalse(data.hasInkJournalEntry(day))
        store.write(StorageLayout.journalPath(day, handwritten = true), byteArrayOf(1, 2, 3))
        data.writeJournalEntry(day, "typed as well")

        assertTrue(data.hasInkJournalEntry(day))
        // One day, even with two files in it.
        assertEquals(listOf(day), data.journalDatesIn(2026))
    }

    @Test
    fun `a different book with the same name and size is kept, not dropped`() {
        repository.importBook(ByteArray(10) { 1 }.inputStream(), "scan.pdf", sizeHintBytes = 10)
        val second = repository.importBook(ByteArray(10) { 2 }.inputStream(), "scan.pdf", sizeHintBytes = 10)
        assertTrue(second is ImportResult.Imported)
        assertEquals(2, repository.listBooks().size)
    }

    @Test
    fun `the same book with a size hint is still found already there`() {
        repository.importBook(ByteArray(10) { 1 }.inputStream(), "scan.pdf", sizeHintBytes = 10)
        val second = repository.importBook(ByteArray(10) { 1 }.inputStream(), "scan.pdf", sizeHintBytes = 10)
        assertTrue(second is ImportResult.AlreadyThere)
        assertEquals(1, repository.listBooks().size)
    }

    @Test
    fun `one broken file is rescued once, however often it is loaded`() {
        store.writeText("habits/habits.json", "{ broken one")
        repeat(3) { repository.keepUnreadable("habits/habits.json", "habits/habits.unreadable.json") }
        store.writeText("habits/habits.json", "{ broken two!")
        repeat(3) { repository.keepUnreadable("habits/habits.json", "habits/habits.unreadable.json") }

        val copies = store.list("habits").filter { it.name.startsWith("habits.unreadable") }
        assertEquals(2, copies.size)
    }
}
