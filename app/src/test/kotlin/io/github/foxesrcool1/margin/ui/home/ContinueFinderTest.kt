package io.github.foxesrcool1.margin.ui.home

import io.github.foxesrcool1.margin.core.ink.InkNotesRepository
import io.github.foxesrcool1.margin.core.ink.PageTemplate
import io.github.foxesrcool1.margin.core.notes.NotesRepository
import io.github.foxesrcool1.margin.core.reading.BookAnnotations
import io.github.foxesrcool1.margin.core.reading.ReadingRepository
import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.LocalFileStore
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ContinueFinderTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private lateinit var root: File
    private lateinit var store: LocalFileStore
    private lateinit var data: DataRepository
    private lateinit var finder: ContinueFinder

    private val baseMillis = 1_000_000_000_000L

    @Before
    fun setUp() {
        root = temporary.newFolder("Margin")
        store = LocalFileStore(root)
        data = DataRepository(store)
        data.ensureFolders()
        finder = ContinueFinder(data)
    }

    private fun setAge(relativePath: String, offsetMinutes: Long) {
        File(root, relativePath).setLastModified(baseMillis + offsetMinutes * 60_000)
    }

    /** Puts a 100 byte Walden.pdf in the library and returns its id. */
    private fun addBook(): String {
        store.write(StorageLayout.bookPath("Walden.pdf"), ByteArray(100))
        return StorageLayout.bookIdFor("Walden.pdf", 100)
    }

    private fun readBook(bookId: String, progress: Double, offsetMinutes: Long) {
        ReadingRepository(data).save(bookId, BookAnnotations(progress = progress))
        setAge(StorageLayout.annotationsPath(bookId), offsetMinutes)
    }

    // -- Nothing to continue --------------------------------------------------

    @Test
    fun `an empty data folder has nothing to continue`() {
        assertNull(finder.find())
    }

    @Test
    fun `a book that was never opened is not offered`() {
        addBook()

        assertNull(finder.find())
    }

    // -- A book ----------------------------------------------------------------

    @Test
    fun `a book that was read is offered with its progress`() {
        val bookId = addBook()
        readBook(bookId, 0.12, 1)

        val target = finder.find()!!
        assertTrue(target is ContinueTarget.Book)
        assertEquals("Walden", target.title)
        assertEquals("12%", (target as ContinueTarget.Book).mark)
    }

    @Test
    fun `a book with no progress yet is offered without a percent`() {
        val bookId = addBook()
        readBook(bookId, 0.0, 1)

        val target = finder.find()!!
        assertTrue(target is ContinueTarget.Book)
        assertEquals("Walden", target.title)
        assertNull((target as ContinueTarget.Book).mark)
    }

    // -- Notes ------------------------------------------------------------------

    @Test
    fun `a note edited after the book was read is offered`() {
        val bookId = addBook()
        readBook(bookId, 0.12, 1)
        val notes = NotesRepository(data)
        val path = notes.createNote(notes.rootPath, "Garden plan")!!
        setAge(path, 2)

        val target = finder.find()
        assertEquals(ContinueTarget.TypedNote(path, "Garden plan"), target)
    }

    @Test
    fun `a book read after the newest note is offered`() {
        val notes = NotesRepository(data)
        val notePath = notes.createNote(notes.rootPath, "Garden plan")!!
        setAge(notePath, 1)
        val bookId = addBook()
        readBook(bookId, 0.12, 2)

        val target = finder.find()!!
        assertTrue(target is ContinueTarget.Book)
        assertEquals("Walden", target.title)
    }

    @Test
    fun `an ink note that is the newest thing is offered`() {
        val ink = InkNotesRepository(data)
        val path = ink.create(NotesRepository(data).rootPath, "Sketch", PageTemplate.Blank)!!
        setAge(path, 1)

        val target = finder.find()
        assertEquals(ContinueTarget.InkNote(path, "Sketch"), target)
    }

    @Test
    fun `the newest note in a sub folder beats an older note in the root`() {
        val notes = NotesRepository(data)
        val rootNote = notes.createNote(notes.rootPath, "Old idea")!!
        setAge(rootNote, 1)
        notes.createFolder(notes.rootPath, "Essays")
        val subNote = notes.createNote("notes/Essays", "New essay")!!
        setAge(subNote, 2)

        val target = finder.find()
        assertEquals(ContinueTarget.TypedNote(subNote, "New essay"), target)
    }

    @Test
    fun `a file in the notes folder that is not a note is ignored`() {
        store.writeText("notes/readme.txt", "x")
        setAge("notes/readme.txt", 1)

        assertNull(finder.find())
    }
}
