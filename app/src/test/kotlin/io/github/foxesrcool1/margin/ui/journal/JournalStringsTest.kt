package io.github.foxesrcool1.margin.ui.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JournalStringsTest {

    private val today = LocalDate.of(2026, 9, 19)

    @Test
    fun `a day reads as a weekday and a date`() {
        assertEquals("Saturday 19 September", JournalStrings.dayTitle(today))
        assertEquals("19 Sep", JournalStrings.shortDayTitle(today))
    }

    @Test
    fun `a month reads as a name and a year`() {
        assertEquals("September 2026", JournalStrings.monthTitle(today))
    }

    @Test
    fun `the overline says where the user is`() {
        assertEquals("Today", JournalStrings.dayOverline(today, today))
        assertEquals("Yesterday", JournalStrings.dayOverline(today.minusDays(1), today))
        assertEquals("Tomorrow", JournalStrings.dayOverline(today.plusDays(1), today))
        assertEquals("2026", JournalStrings.dayOverline(today.minusDays(9), today))
    }

    @Test
    fun `a streak reads in plain words`() {
        assertEquals("No streak", JournalStrings.streakLabel(0))
        assertEquals("1 day", JournalStrings.streakLabel(1))
        assertEquals("12 days", JournalStrings.streakLabel(12))
    }

    @Test
    fun `the week starts on Monday`() {
        val initials = JournalStrings.weekdayInitials()
        assertEquals(7, initials.size)
        assertEquals("M", initials.first())
        assertEquals("S", initials.last())
    }

    @Test
    fun `a month grid is whole weeks`() {
        val grid = JournalStrings.monthGrid(today)
        assertTrue(grid.all { it.size == 7 })
        assertEquals(30, grid.flatten().count { it != null })
    }

    @Test
    fun `the first of the month lands under the right weekday`() {
        // 1 September 2026 is a Tuesday, so it is the second cell.
        val grid = JournalStrings.monthGrid(LocalDate.of(2026, 9, 1))
        assertEquals(null, grid[0][0])
        assertEquals(LocalDate.of(2026, 9, 1), grid[0][1])
    }

    @Test
    fun `a month that starts on a Monday has no blanks at the front`() {
        // 1 June 2026 is a Monday.
        val grid = JournalStrings.monthGrid(LocalDate.of(2026, 6, 15))
        assertEquals(LocalDate.of(2026, 6, 1), grid[0][0])
    }

    @Test
    fun `February in a leap year has 29 days`() {
        val grid = JournalStrings.monthGrid(LocalDate.of(2028, 2, 10))
        assertEquals(29, grid.flatten().count { it != null })
    }

    @Test
    fun `every day of the month appears exactly once and in order`() {
        val days = JournalStrings.monthGrid(today).flatten().filterNotNull()
        assertEquals((1..30).toList(), days.map { it.dayOfMonth })
    }

    @Test
    fun `the grid is the same whichever day of the month is asked about`() {
        assertEquals(
            JournalStrings.monthGrid(LocalDate.of(2026, 9, 1)),
            JournalStrings.monthGrid(LocalDate.of(2026, 9, 30)),
        )
    }

    @Test
    fun `stepping a short month and back keeps the anchor day`() {
        val back = JournalStrings.stepMonth(LocalDate.of(2026, 3, 31), 31, -1)
        assertEquals(LocalDate.of(2026, 2, 28), back)
        assertEquals(LocalDate.of(2026, 3, 31), JournalStrings.stepMonth(back, 31, 1))
    }

    @Test
    fun `stepping months keeps the anchor day`() {
        assertEquals(
            LocalDate.of(2026, 10, 15),
            JournalStrings.stepMonth(LocalDate.of(2026, 9, 15), 15, 1),
        )
        assertEquals(
            LocalDate.of(2025, 12, 15),
            JournalStrings.stepMonth(LocalDate.of(2026, 9, 15), 15, -9),
        )
    }

    @Test
    fun `stepping into a leap February clamps to the 29th`() {
        assertEquals(
            LocalDate.of(2024, 2, 29),
            JournalStrings.stepMonth(LocalDate.of(2024, 1, 30), 30, 1),
        )
    }
}
