package io.github.foxesrcool1.margin.ui.ink

import io.github.foxesrcool1.margin.core.ink.InkNote
import io.github.foxesrcool1.margin.core.ink.InkNoteLoad
import io.github.foxesrcool1.margin.core.ink.InkNotesRepository
import io.github.foxesrcool1.margin.core.ink.InkPageData
import io.github.foxesrcool1.margin.core.ink.InkTestData
import io.github.foxesrcool1.margin.core.ink.PageTemplate
import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.LocalFileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InkNoteControllerTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private fun repository() = InkNotesRepository(DataRepository(LocalFileStore(temporary.newFolder("Margin"))))

    @Test
    fun `a blank quick page leaves no file, even with a page added and a template picked`() {
        val notes = repository()
        val path = notes.freshPath("notes/quick", "Handwritten")
        val controller = InkNoteController(notes, path, InkNote(), mayWrite = true)
        controller.addPage()
        controller.changeTemplate(PageTemplate.Lined)
        controller.saveAndWait()
        assertFalse(notes.exists(path))
    }

    @Test
    fun `a new page with ink is written`() {
        val notes = repository()
        val path = notes.freshPath("notes/quick", "Handwritten")
        val note = InkNote(pages = listOf(InkPageData(InkTestData.fullPage(3))))
        val controller = InkNoteController(notes, path, note, mayWrite = true)
        controller.addPage()
        controller.saveAndWait()
        assertTrue(notes.exists(path))
    }

    @Test
    fun `a note on the disk that is emptied is still written`() {
        val notes = repository()
        val path = notes.create("notes", "Sketch", PageTemplate.Blank)!!
        assertTrue(notes.save(path, InkNote(pages = listOf(InkPageData(InkTestData.fullPage(3))))))
        val loaded = (notes.load(path) as InkNoteLoad.Loaded).note
        val controller = InkNoteController(notes, path, loaded, mayWrite = true)
        controller.deletePage()
        controller.saveAndWait()
        val after = (notes.load(path) as InkNoteLoad.Loaded).note
        assertEquals(0, after.pages.single().strokes.size)
    }
}
