package io.github.foxesrcool1.margin.core.notes

import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.LocalFileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NotesRepositoryTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private lateinit var store: LocalFileStore
    private lateinit var notes: NotesRepository

    @Before
    fun setUp() {
        store = LocalFileStore(temporary.newFolder("Margin"))
        val data = DataRepository(store)
        data.ensureFolders()
        notes = NotesRepository(data)
    }

    @Test
    fun `a new note starts with its title as a heading`() {
        val path = notes.createNote(notes.rootPath, "My Plan")
        assertNotNull(path)
        assertEquals("notes/My Plan.md", path)
        assertTrue(notes.read(path!!).startsWith("# My Plan"))
    }

    @Test
    fun `two notes with the same title do not overwrite each other`() {
        val first = notes.createNote(notes.rootPath, "Plan")
        val second = notes.createNote(notes.rootPath, "Plan")
        assertEquals("notes/Plan.md", first)
        assertEquals("notes/Plan 2.md", second)
        assertEquals(2, notes.list(notes.rootPath).size)
    }

    @Test
    fun `a note that got a number in its name has the number in its heading`() {
        val first = notes.createNote(notes.rootPath, "groceries")!!
        val second = notes.createNote(notes.rootPath, "groceries")!!
        val third = notes.createNote(notes.rootPath, "groceries")!!
        assertEquals("notes/groceries 2.md", second)
        assertEquals("# groceries\n\n", notes.read(first))
        assertEquals("# groceries 2\n\n", notes.read(second))
        assertEquals("# groceries 3\n\n", notes.read(third))
        assertEquals(
            listOf("groceries", "groceries 2", "groceries 3"),
            notes.list(notes.rootPath).map { it.title }.sorted(),
        )
    }

    @Test
    fun `a title a file system would refuse is cleaned up`() {
        val path = notes.createNote(notes.rootPath, "a/b: c*d")
        assertEquals("notes/a-b- c-d.md", path)
    }

    @Test
    fun `a folder is made and notes go into it`() {
        assertTrue(notes.createFolder(notes.rootPath, "Ideas"))
        val path = notes.createNote("notes/Ideas", "Sketch")
        assertEquals("notes/Ideas/Sketch.md", path)

        val rows = notes.list(notes.rootPath)
        assertEquals(listOf("Ideas"), rows.map { it.name })
        assertTrue(rows.single().isFolder)
    }

    @Test
    fun `a row takes its title from the heading inside the note`() {
        val path = notes.createNote(notes.rootPath, "note-1")!!
        notes.write(path, "# The Real Title\n\nSome text")

        assertEquals("The Real Title", notes.list(notes.rootPath).single().title)
    }

    @Test
    fun `a row has the start of the note under its title, from the same read`() {
        val path = notes.createNote(notes.rootPath, "Shopping")!!
        notes.write(path, "# Shopping\n\nBread, milk")
        store.createDirectories("notes/Ideas")

        val rows = notes.list(notes.rootPath)

        assertEquals("Bread, milk", rows.single { it.name == "Shopping.md" }.preview)
        assertEquals("", rows.single { it.isFolder }.preview)
    }

    @Test
    fun `folders come before files`() {
        notes.createNote(notes.rootPath, "aaa")
        notes.createFolder(notes.rootPath, "zzz")

        assertEquals(listOf("zzz", "aaa.md"), notes.list(notes.rootPath).map { it.name })
    }

    @Test
    fun `renaming keeps the extension`() {
        val path = notes.createNote(notes.rootPath, "Old")!!
        val renamed = notes.rename(path, "New")

        assertEquals("notes/New.md", renamed)
        assertFalse(store.exists(path))
        assertTrue(store.exists(renamed!!))
    }

    @Test
    fun `renaming onto a name that is taken gets a number`() {
        notes.createNote(notes.rootPath, "Taken")
        val path = notes.createNote(notes.rootPath, "Other")!!

        assertEquals("notes/Taken 2.md", notes.rename(path, "Taken"))
    }

    @Test
    fun `a note moves into a folder`() {
        notes.createFolder(notes.rootPath, "Ideas")
        val path = notes.createNote(notes.rootPath, "Sketch")!!

        assertTrue(notes.move(path, "notes/Ideas"))
        assertFalse(store.exists(path))
        assertTrue(store.exists("notes/Ideas/Sketch.md"))
    }

    @Test
    fun `moving into the folder it is already in changes nothing`() {
        val path = notes.createNote(notes.rootPath, "Sketch")!!
        assertTrue(notes.move(path, notes.rootPath))
        assertTrue(store.exists(path))
    }

    @Test
    fun `a folder cannot be moved into itself`() {
        notes.createFolder(notes.rootPath, "Outer")
        notes.createFolder("notes/Outer", "Inner")

        assertFalse(notes.move("notes/Outer", "notes/Outer"))
        assertFalse(notes.move("notes/Outer", "notes/Outer/Inner"))
        assertTrue(store.exists("notes/Outer/Inner"))
    }

    @Test
    fun `deleting a folder removes what is in it`() {
        notes.createFolder(notes.rootPath, "Ideas")
        notes.createNote("notes/Ideas", "Sketch")

        assertTrue(notes.delete("notes/Ideas"))
        assertFalse(store.exists("notes/Ideas"))
        assertTrue(notes.list(notes.rootPath).isEmpty())
    }

    @Test
    fun `the root knows it is the root`() {
        assertTrue(notes.isRoot("notes"))
        assertFalse(notes.isRoot("notes/Ideas"))
        assertEquals("notes", notes.parentOf("notes/Ideas"))
        assertEquals("notes", notes.parentOf("notes"))
    }

    @Test
    fun `a quick note lands in its own folder`() {
        val path = notes.createNote(notes.quickNotePath(), "2026-09-19T08-04-00")
        assertEquals("notes/quick/2026-09-19T08-04-00.md", path)
    }

    @Test
    fun `a handwritten note is listed under its file name without the extension`() {
        store.write("notes/Garden plan.inknote", byteArrayOf(1))
        val entry = notes.list(notes.rootPath).single { it.name == "Garden plan.inknote" }
        assertEquals("Garden plan", entry.title)
    }

    @Test
    fun `renaming a typed note changes the heading the row shows`() {
        val path = notes.createNote(notes.rootPath, "Shopping")!!
        notes.write(path, "# Shopping\n\nBread, milk")

        val renamed = notes.rename(path, "Groceries")!!

        assertEquals("notes/Groceries.md", renamed)
        assertEquals("# Groceries\n\nBread, milk", notes.read(renamed))
        assertEquals("Groceries", notes.list(notes.rootPath).single().title)
    }

    @Test
    fun `a note that starts with plain text keeps its text when renamed`() {
        store.writeText("notes/list.md", "Bread, milk\neggs")

        val renamed = notes.rename("notes/list.md", "Groceries")!!

        assertEquals("notes/Groceries.md", renamed)
        assertEquals("Bread, milk\neggs", notes.read(renamed))
    }

    @Test
    fun `renaming to the same name keeps the name and gets no number`() {
        val path = notes.createNote(notes.rootPath, "Plan")!!

        assertEquals(path, notes.rename(path, "Plan"))
        assertEquals(listOf("Plan.md"), notes.list(notes.rootPath).map { it.name })
    }

    @Test
    fun `renaming that only changes the capitals gets no number`() {
        val path = notes.createNote(notes.rootPath, "plan")!!

        assertEquals("notes/Plan.md", notes.rename(path, "Plan"))
        assertEquals(listOf("Plan.md"), notes.list(notes.rootPath).map { it.name })
        assertEquals("Plan", notes.list(notes.rootPath).single().title)
    }

    @Test
    fun `a folder with a dot in its name is renamed without keeping a part of it`() {
        notes.createFolder(notes.rootPath, "Drafts v1.2")

        assertEquals("notes/Drafts", notes.rename("notes/Drafts v1.2", "Drafts"))
    }

    @Test
    fun `a handwritten note is renamed and its ink is left alone`() {
        store.write("notes/Garden.inknote", byteArrayOf(1, 2, 3))

        assertEquals("notes/Garden plan.inknote", notes.rename("notes/Garden.inknote", "Garden plan"))
        assertEquals(listOf<Byte>(1, 2, 3), store.readBytes("notes/Garden plan.inknote")!!.toList())
    }

    @Test
    fun `a list read after the editor saved shows the typed text as the preview`() {
        val path = notes.createNote(notes.rootPath, "Plan")!!
        assertEquals("", notes.list(notes.rootPath).single().preview)

        // What the editor writes when the user types under the heading.
        assertTrue(notes.write(path, "# Plan\n\nBuy milk and bread"))
        val row = notes.list(notes.rootPath).single()
        assertEquals("Plan", row.title)
        assertEquals("Buy milk and bread", row.preview)
    }

    @Test
    fun `a quick note nothing was typed into is discarded`() {
        val path = notes.createNote("notes/quick", "2026-10-22T09-00-00")!!
        val made = notes.read(path)

        assertTrue(notes.discardUntouched(path, made, typed = made))
        assertFalse(notes.exists(path))
    }

    @Test
    fun `a quick note with its text cleared is discarded`() {
        val path = notes.createNote("notes/quick", "2026-10-22T09-00-00")!!
        assertTrue(notes.discardUntouched(path, notes.read(path), typed = "  \n"))
        assertFalse(notes.exists(path))
    }

    @Test
    fun `a note with typing in it is never discarded`() {
        val path = notes.createNote("notes/quick", "2026-10-22T09-00-00")!!
        val made = notes.read(path)

        // Typed in the editor, not saved yet.
        assertFalse(notes.discardUntouched(path, made, typed = made + "hello"))
        assertTrue(notes.exists(path))

        // Saved, and the editor was opened again with the same made-with text.
        assertTrue(notes.write(path, made + "hello"))
        assertFalse(notes.discardUntouched(path, made, typed = made))
        assertEquals(made + "hello", notes.read(path))
    }

    @Test
    fun `only a typed note file can be discarded`() {
        notes.createFolder(notes.rootPath, "Drafts.md")
        assertFalse(notes.discardUntouched("notes/Drafts.md", "", typed = ""))
        assertTrue(notes.exists("notes/Drafts.md"))

        store.writeText("notes/photo.png", "")
        assertFalse(notes.discardUntouched("notes/photo.png", "", typed = ""))
        assertFalse(notes.discardUntouched("notes/missing.md", "", typed = ""))
        assertTrue(notes.exists("notes/photo.png"))
    }
}
