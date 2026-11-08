package io.github.foxesrcool1.margin.ui.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthViewPagesTest {

    @Test
    fun fiveWeeksWithRoomForSixRowsIsOnePage() {
        assertEquals(listOf(0..4), weekPages(5, 6))
    }

    @Test
    fun sixWeeksWithRoomForThreeRowsIsTwoPagesOfThree() {
        assertEquals(listOf(0..2, 3..5), weekPages(6, 3))
    }

    @Test
    fun fourWeeksWithRoomForThreeRowsIsPagesOfThreeAndOne() {
        assertEquals(listOf(0..2, 3..3), weekPages(4, 3))
    }

    @Test
    fun noRoomStillGivesPagesOfOneRow() {
        val pages = weekPages(6, 0)
        assertEquals(6, pages.size)
        assertTrue(pages.all { it.count() == 1 })
    }

    @Test
    fun oneRowOfRoomGivesPagesOfOneRow() {
        val pages = weekPages(5, 1)
        assertEquals(5, pages.size)
        assertTrue(pages.all { it.count() == 1 })
    }
}
