package io.github.foxesrcool1.margin.ui.split

import androidx.compose.ui.unit.dp
import io.github.foxesrcool1.margin.core.ink.PageTemplate
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import io.github.foxesrcool1.margin.ui.home.LauncherRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitStateTest {

    private val journal = PanePage.Tab(LauncherRoute.Journal)
    private val writing = PanePage.Tab(LauncherRoute.Writing)
    private val note = PanePage.TypedNote("notes/Letter.md")

    @Test
    fun `the split opens on the first page and closes to nothing`() {
        val split = SplitState(firstPage = { PanePage.BookNotes("Walden") })
        assertFalse(split.isOpen)
        split.toggle()
        assertTrue(split.isOpen)
        assertEquals(PanePage.BookNotes("Walden"), split.page)
        split.toggle()
        assertFalse(split.isOpen)
        assertNull(split.carry())
    }

    @Test
    fun `back walks the trail and then lands on the choice`() {
        val split = SplitState().apply { open() }
        split.show(writing)
        split.show(note)
        split.back()
        assertEquals(writing, split.page)
        split.back()
        assertEquals(PanePage.Choose, split.page)
        split.back()
        assertEquals(PanePage.Choose, split.page)
    }

    @Test
    fun `back on Home steps back to the choice and then closes the split`() {
        val split = SplitState().apply { open() }
        split.show(writing)
        split.show(note)
        split.backAtHome()
        assertTrue(split.isOpen)
        assertEquals(PanePage.Choose, split.page)
        split.backAtHome()
        assertFalse(split.isOpen)
    }

    @Test
    fun `a deleted note sends the second half back to its choice`() {
        val split = SplitState().apply { open() }
        split.show(note)
        assertFalse(split.noteDeleted("notes/Other.md"))
        assertEquals(note, split.page)
        // A folder takes the notes in it along, but not a folder that only starts the same.
        split.noteDeleted("notes/Let")
        assertEquals(note, split.page)
        split.noteDeleted("notes")
        assertEquals(PanePage.Choose, split.page)

        split.show(PanePage.InkNote("notes/Sketch.inknote", "Sketch"))
        split.noteDeleted("notes/Sketch.inknote")
        assertEquals(PanePage.Choose, split.page)
        assertTrue(split.isOpen)
    }

    @Test
    fun `a deleted note that is the main half is reported to its screen`() {
        val split = SplitState(mainPage = { note }).apply { open(writing) }
        assertFalse(split.noteDeleted("notes/Other.md"))
        assertTrue(split.noteDeleted("notes/Letter.md"))
        // The second half was not that note, so it stays.
        assertEquals(writing, split.page)
    }

    @Test
    fun `the page the main half shows is refused, with a notice`() {
        var main: PanePage? = note
        val split = SplitState(mainPage = { main }).apply { open() }
        assertFalse(split.show(note))
        assertEquals(PanePage.Choose, split.page)
        assertEquals(SplitStrings.ALREADY_OPEN, split.notice)

        // The next move clears the notice.
        assertTrue(split.show(journal))
        assertNull(split.notice)

        // The main half moves on to the same page: the second half gives way.
        main = journal
        split.mainChanged()
        assertEquals(PanePage.Choose, split.page)
        assertEquals(SplitStrings.ALREADY_OPEN, split.notice)
    }

    @Test
    fun `a split that is carried over never opens on the page of the new main half`() {
        val split = SplitState(mainPage = { note })
        split.open(note, swapped = true)
        assertEquals(PanePage.Choose, split.page)
        assertTrue(split.swapped)
    }

    @Test
    fun `the notes of a book clash with the same files opened as notes`() {
        val book = PanePage.BookNotes("Walden")
        val typed = PanePage.TypedNote(StorageLayout.readingNotePath("Walden", handwritten = false))
        val ink = PanePage.InkNote(StorageLayout.readingNotePath("Walden", handwritten = true), "Walden")
        assertTrue(book.clashesWith(typed))
        assertTrue(ink.clashesWith(book))
        assertFalse(book.clashesWith(PanePage.BookNotes("Middlemarch")))
        assertFalse(PanePage.Choose.clashesWith(PanePage.Choose))
        assertFalse(PanePage.Tab(LauncherRoute.Home).clashesWith(PanePage.Tab(LauncherRoute.Home)))
        assertTrue(journal.clashesWith(PanePage.Tab(LauncherRoute.Journal)))
    }

    @Test
    fun `swap and carry keep the side`() {
        val split = SplitState().apply { open(journal) }
        split.swap()
        assertEquals(SplitCarry(journal, swapped = true), split.carry())
    }

    @Test
    fun `the trail does not grow without end`() {
        val split = SplitState().apply { open() }
        repeat(40) { split.show(PanePage.TypedNote("notes/$it.md")) }
        repeat(12) { split.back() }
        assertEquals(PanePage.Choose, split.page)
    }

    @Test
    fun `every page survives the trip through an intent`() {
        val pages = listOf(
            PanePage.Choose,
            journal,
            note,
            PanePage.InkNote("journal/2026/2026-09-22.inknote", "Tuesday", PageTemplate.Lined),
            PanePage.BookNotes("Walden"),
            PanePage.BookNotes(""),
        )
        pages.forEach { page ->
            listOf(false, true).forEach { swapped ->
                val fields = PanePageCodec.write(SplitCarry(page, swapped))
                assertTrue(fields.keys.all { it in PanePageCodec.keys })
                assertEquals(SplitCarry(page, swapped), PanePageCodec.read(fields::get))
            }
        }
    }

    @Test
    fun `a broken intent opens nothing`() {
        assertNull(PanePageCodec.read { null })
        assertNull(PanePageCodec.read(mapOf("split_kind" to "typed")::get))
        assertNull(PanePageCodec.read(mapOf("split_kind" to "typed", "split_path" to " ")::get))
        assertNull(PanePageCodec.read(mapOf("split_kind" to "tab", "split_route" to "Nowhere")::get))
        assertNull(PanePageCodec.read(mapOf("split_kind" to "something new")::get))
    }

    @Test
    fun `the choice grid uses the room it has`() {
        // Upright, the second half is wide and short: two rows of three
        // give larger icons than one row of five.
        assertEquals(3, ChoiceGrid.best(5, 456.dp, 230.dp, 12.dp).columns)
        // On its side it is narrow and tall: two columns.
        assertEquals(2, ChoiceGrid.best(5, 295.dp, 400.dp, 12.dp).columns)
        // Never smaller than a touch target, never larger than a tab on Home.
        assertEquals(56.dp, ChoiceGrid.best(6, 100.dp, 100.dp, 12.dp).cell)
        assertEquals(132.dp, ChoiceGrid.best(1, 900.dp, 900.dp, 12.dp).cell)
    }
}
