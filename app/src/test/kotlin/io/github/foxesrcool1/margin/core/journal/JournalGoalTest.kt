package io.github.foxesrcool1.margin.core.journal

import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.LocalFileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class JournalGoalTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private lateinit var store: LocalFileStore
    private lateinit var data: DataRepository

    private val day = LocalDate.of(2026, 9, 19)

    @Before
    fun setUp() {
        store = LocalFileStore(temporary.newFolder("Margin"))
        data = DataRepository(store)
        data.ensureFolders()
    }

    @Test
    fun `cycling goes Off, 250, 500, 750, 1000 and then back to Off`() {
        var goal = 0
        assertEquals(0, goal)
        goal = JournalGoal.next(goal)
        assertEquals(250, goal)
        goal = JournalGoal.next(goal)
        assertEquals(500, goal)
        goal = JournalGoal.next(goal)
        assertEquals(750, goal)
        goal = JournalGoal.next(goal)
        assertEquals(1000, goal)
        goal = JournalGoal.next(goal)
        assertEquals(0, goal)
    }

    @Test
    fun `an unknown value gives Off when cycled`() {
        assertEquals(0, JournalGoal.next(123))
    }

    @Test
    fun `clean keeps a step and turns anything else into 0`() {
        assertEquals(0, JournalGoal.clean(0))
        assertEquals(250, JournalGoal.clean(250))
        assertEquals(500, JournalGoal.clean(500))
        assertEquals(750, JournalGoal.clean(750))
        assertEquals(1000, JournalGoal.clean(1000))
        assertEquals(0, JournalGoal.clean(123))
        assertEquals(0, JournalGoal.clean(-5))
    }

    @Test
    fun `the count text shows the goal when one is set`() {
        assertEquals("212 / 750 words", JournalGoal.countText(212, 750))
        assertEquals("750 / 750 words", JournalGoal.countText(750, 750))
        assertEquals("1 / 250 words", JournalGoal.countText(1, 250))
    }

    @Test
    fun `the count text counts words when no goal is set`() {
        assertEquals("212 words", JournalGoal.countText(212, 0))
        assertEquals("1 word", JournalGoal.countText(1, 0))
        assertEquals("0 words", JournalGoal.countText(0, 0))
    }

    @Test
    fun `the short count text drops the word only when there is a goal`() {
        assertEquals("212 / 750", JournalGoal.shortCountText(212, 750))
        assertEquals("212 words", JournalGoal.shortCountText(212, 0))
        assertEquals("1 word", JournalGoal.shortCountText(1, 0))
    }

    @Test
    fun `reached is true at and past the goal, and never with no goal`() {
        assertFalse(JournalGoal.reached(800, 0))
        assertFalse(JournalGoal.reached(749, 750))
        assertTrue(JournalGoal.reached(750, 750))
        assertTrue(JournalGoal.reached(800, 750))
    }

    @Test
    fun `the goal label is Off for zero and words otherwise`() {
        assertEquals("Off", JournalGoal.goalLabel(0))
        assertEquals("750 words", JournalGoal.goalLabel(750))
    }

    @Test
    fun `saving an entry writes it and emptying it deletes it`() {
        assertTrue(data.saveJournalEntry(day, "It rained."))
        assertTrue(data.journalDatesIn(2026).contains(day))
        assertEquals("It rained.", data.journalEntry(day))

        assertTrue(data.saveJournalEntry(day, "  \n"))
        assertTrue(data.journalDatesIn(2026).isEmpty())
        assertNull(data.journalEntry(day))
    }

    @Test
    fun `saving a blank text for a day that has no file returns true`() {
        assertTrue(data.saveJournalEntry(day, "  "))
    }
}
