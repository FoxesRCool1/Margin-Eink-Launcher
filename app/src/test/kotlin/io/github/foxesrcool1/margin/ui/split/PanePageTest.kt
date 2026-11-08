package io.github.foxesrcool1.margin.ui.split

import io.github.foxesrcool1.margin.ui.home.LauncherRoute
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanePageTest {

    private val day = LocalDate.of(2026, 9, 22)

    private fun pages() = listOf(
        PanePage.TypedNote("notes/Letter.md"),
        PanePage.InkNote("notes/Sketch.inknote", "Sketch"),
        PanePage.BookNotes("Walden"),
        PanePage.JournalDay(day),
        PanePage.Tab(LauncherRoute.Journal),
        PanePage.Tab(LauncherRoute.Settings),
    )

    @Test
    fun `every page clashes with an equal copy of itself`() {
        pages().zip(pages()).forEach { (page, copy) ->
            assertTrue(page.toString(), page.clashesWith(copy))
        }
        assertFalse(PanePage.Choose.clashesWith(PanePage.Choose))
        assertFalse(PanePage.Tab(LauncherRoute.Home).clashesWith(PanePage.Tab(LauncherRoute.Home)))
    }

    @Test
    fun `a day of the journal clashes with the journal tab both ways`() {
        val journalDay = PanePage.JournalDay(day)
        val journalTab = PanePage.Tab(LauncherRoute.Journal)
        assertTrue(journalDay.clashesWith(journalTab))
        assertTrue(journalTab.clashesWith(journalDay))
    }
}
