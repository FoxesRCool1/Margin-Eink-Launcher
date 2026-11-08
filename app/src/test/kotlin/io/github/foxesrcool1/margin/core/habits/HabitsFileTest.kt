package io.github.foxesrcool1.margin.core.habits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitsFileTest {

    private val document = HabitsDocument(
        habits = listOf(
            Habit("read", "Read 30 minutes", LocalDate.of(2026, 1, 1)),
            Habit("walk", "Walk outside", LocalDate.of(2026, 2, 3), archived = true),
        ),
        dayBoundaryHour = 4,
    )

    @Test
    fun `a document survives a round trip`() {
        assertEquals(document, HabitsFile.parse(HabitsFile.serialise(document)))
    }

    @Test
    fun `the file is readable text with the names in it`() {
        val text = HabitsFile.serialise(document)
        assertTrue(text.contains("Read 30 minutes"))
        assertTrue(text.contains("\"version\""))
        assertTrue(text.contains("2026-01-01"))
    }

    @Test
    fun `archived habits are kept apart from active ones`() {
        assertEquals(listOf("read"), document.active().map { it.id })
    }

    @Test
    fun `text that is not this file reads as null`() {
        assertNull(HabitsFile.parse(""))
        assertNull(HabitsFile.parse("not json"))
        assertNull(HabitsFile.parse("[]"))
        assertNull(HabitsFile.parse("""{"habits": []}"""))
    }

    @Test
    fun `a missing boundary hour falls back to the default`() {
        val parsed = HabitsFile.parse("""{"version": 1, "habits": []}""")
        assertEquals(DayBoundary.DEFAULT_HOUR, parsed!!.dayBoundaryHour)
    }

    @Test
    fun `a boundary hour that is not an hour falls back to the default`() {
        val parsed = HabitsFile.parse("""{"version": 1, "dayBoundaryHour": 99, "habits": []}""")
        assertEquals(DayBoundary.DEFAULT_HOUR, parsed!!.dayBoundaryHour)
    }

    @Test
    fun `one broken habit is dropped and the rest are kept`() {
        val text = """
            {
              "version": 1,
              "habits": [
                {"id": "good", "name": "Good", "createdOn": "2026-01-01"},
                {"id": "", "name": "No id", "createdOn": "2026-01-01"},
                {"id": "no-name", "createdOn": "2026-01-01"},
                {"id": "bad-date", "name": "Bad date", "createdOn": "yesterday"},
                {"id": "also-good", "name": "Also good", "createdOn": "2026-02-02"}
              ]
            }
        """.trimIndent()

        val parsed = HabitsFile.parse(text)
        assertEquals(listOf("good", "also-good"), parsed!!.habits.map { it.id })
    }

    @Test
    fun `two habits with the same id keep only the first`() {
        val text = """
            {"version": 1, "habits": [
              {"id": "read", "name": "First", "createdOn": "2026-01-01"},
              {"id": "read", "name": "Second", "createdOn": "2026-01-02"}
            ]}
        """.trimIndent()

        val parsed = HabitsFile.parse(text)
        assertEquals(1, parsed!!.habits.size)
        assertEquals("First", parsed.habits.single().name)
    }

    @Test
    fun `an id is made from the name`() {
        assertEquals("read-30-minutes", HabitsFile.idFor("Read 30 minutes", emptySet()))
        assertEquals("walk", HabitsFile.idFor("  Walk!  ", emptySet()))
        assertEquals("habit", HabitsFile.idFor("...", emptySet()))
    }

    @Test
    fun `an id that is taken gets a number`() {
        assertEquals("read-2", HabitsFile.idFor("Read", setOf("read")))
        assertEquals("read-3", HabitsFile.idFor("Read", setOf("read", "read-2")))
    }

    @Test
    fun `a habit name with a quote in it survives`() {
        val awkward = HabitsDocument(
            habits = listOf(Habit("q", "Say \"hello\" and\nsmile", LocalDate.of(2026, 1, 1))),
            dayBoundaryHour = 4,
        )
        assertEquals(awkward, HabitsFile.parse(HabitsFile.serialise(awkward)))
    }

    @Test
    fun `the number of habits left out is counted`() {
        val text = """
            {"version": 1, "habits": [
              {"id": "good", "name": "Good", "createdOn": "2026-01-01"},
              {"id": "bad", "name": "Bad", "createdOn": "yesterday"}
            ]}
        """.trimIndent()

        val (parsed, dropped) = HabitsFile.parseCounting(text)!!
        assertEquals(listOf("good"), parsed.habits.map { it.id })
        assertEquals(1, dropped)
    }
}
