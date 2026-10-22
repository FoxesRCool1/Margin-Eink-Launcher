package io.github.foxesrcool1.margin.ui.split

import io.github.foxesrcool1.margin.ui.home.LauncherRoute
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SplitReturnTest {

    private val journal = PanePage.Tab(LauncherRoute.Journal)
    private val writing = PanePage.Tab(LauncherRoute.Writing)
    private val note = PanePage.TypedNote("notes/Letter.md")

    @Before
    @After
    fun clean() = SplitReturn.forget()

    @Test
    fun `nothing is waiting when Home gave nothing away`() {
        assertNull(SplitReturn.take())
        SplitReturn.handBack(SplitCarry(journal, swapped = false))
        assertNull(SplitReturn.take())
    }

    @Test
    fun `Home gets nothing back while the screen is still open`() {
        SplitReturn.leftFrom(LauncherRoute.Writing)
        assertNull(SplitReturn.take())
    }

    @Test
    fun `a screen that closes with its split screen open hands the page back, once`() {
        SplitReturn.leftFrom(LauncherRoute.Writing)
        SplitReturn.handBack(SplitCarry(journal, swapped = true))

        assertEquals(SplitRestore(LauncherRoute.Writing, SplitCarry(journal, swapped = true)), SplitReturn.take())
        assertNull(SplitReturn.take())
    }

    @Test
    fun `a split screen closed in the screen stays closed, and Home still gets its tab`() {
        SplitReturn.leftFrom(LauncherRoute.Writing)
        SplitReturn.handBack(null)

        assertEquals(SplitRestore(LauncherRoute.Writing, null), SplitReturn.take())
    }

    @Test
    fun `the Home key forgets it`() {
        SplitReturn.leftFrom(LauncherRoute.Writing)
        SplitReturn.forget()
        SplitReturn.handBack(SplitCarry(journal, swapped = false))

        assertNull(SplitReturn.take())
    }

    @Test
    fun `the last screen to close wins`() {
        SplitReturn.leftFrom(LauncherRoute.Home)
        // A note opens a book and closes itself, then the book closes with its own split screen.
        SplitReturn.handBack(SplitCarry(writing, swapped = false))
        SplitReturn.handBack(SplitCarry(journal, swapped = true))

        assertEquals(SplitCarry(journal, swapped = true), SplitReturn.take()?.carry)
    }

    @Test
    fun `a new trip starts clean`() {
        SplitReturn.leftFrom(LauncherRoute.Writing)
        SplitReturn.handBack(SplitCarry(journal, swapped = false))
        SplitReturn.leftFrom(LauncherRoute.Reading)

        assertNull(SplitReturn.take())
    }

    @Test
    fun `a page that stands in the second half is kept, and so is the tab`() {
        val restore = SplitRestore(LauncherRoute.Writing, SplitCarry(journal, swapped = true))

        assertEquals(restore, restore.resolve { true })
    }

    @Test
    fun `a page the tab also shows stays in the second half and Home goes back to Home`() {
        val restore = SplitRestore(LauncherRoute.Writing, SplitCarry(writing, swapped = true))

        assertEquals(SplitRestore(LauncherRoute.Home, SplitCarry(writing, swapped = true)), restore.resolve { true })
    }

    @Test
    fun `a note that is gone is not restored, the choice stands there instead`() {
        val restore = SplitRestore(LauncherRoute.Writing, SplitCarry(note, swapped = true))

        assertTrue(restore.needsFiles)
        assertEquals(SplitCarry(PanePage.Choose, swapped = true), restore.resolve { false }.carry)
        assertEquals(SplitCarry(note, swapped = true), restore.resolve { it == note }.carry)
    }

    @Test
    fun `a note beside a tab is no clash`() {
        val restore = SplitRestore(LauncherRoute.Writing, SplitCarry(note, swapped = false))

        assertEquals(LauncherRoute.Writing, restore.resolve { true }.route)
    }

    @Test
    fun `only a note needs a look at the files`() {
        assertFalse(SplitRestore(LauncherRoute.Home, SplitCarry(journal, swapped = false)).needsFiles)
        assertFalse(SplitRestore(LauncherRoute.Home, SplitCarry(PanePage.Choose, swapped = false)).needsFiles)
        assertFalse(SplitRestore(LauncherRoute.Home, null).needsFiles)
        assertTrue(SplitRestore(LauncherRoute.Home, SplitCarry(PanePage.InkNote("notes/a.ink", "A"), swapped = false)).needsFiles)
    }
}
