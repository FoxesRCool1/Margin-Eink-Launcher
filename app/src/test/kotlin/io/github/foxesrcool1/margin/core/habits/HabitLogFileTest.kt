package io.github.foxesrcool1.margin.core.habits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitLogFileTest {

    private val marks = listOf(
        HabitMark("read", LocalDate.of(2026, 9, 19)),
        HabitMark("walk", LocalDate.of(2026, 9, 18)),
        HabitMark("read", LocalDate.of(2026, 9, 18)),
    )

    @Test
    fun `the log survives a round trip`() {
        assertEquals(marks.toSet(), HabitLogFile.parse(HabitLogFile.serialise(marks)).toSet())
    }

    @Test
    fun `the file starts with a header and sorts by date`() {
        val lines = HabitLogFile.serialise(marks).trim().lines()
        assertEquals(HabitLogFile.HEADER, lines.first())
        assertEquals("2026-09-18,read", lines[1])
        assertEquals("2026-09-18,walk", lines[2])
        assertEquals("2026-09-19,read", lines[3])
    }

    @Test
    fun `the same mark twice is written once`() {
        val doubled = marks + marks
        assertEquals(4, HabitLogFile.serialise(doubled).trim().lines().size)
    }

    @Test
    fun `an empty log is still a valid file`() {
        val text = HabitLogFile.serialise(emptyList())
        assertEquals(HabitLogFile.HEADER, text.trim())
        assertTrue(HabitLogFile.parse(text).isEmpty())
    }

    @Test
    fun `a broken line is dropped and the rest is kept`() {
        val text = """
            date,habit
            2026-09-18,read
            not-a-date,read
            2026-09-19
            2026-09-19,
            2026-09-20,walk
        """.trimIndent()

        assertEquals(
            listOf(
                HabitMark("read", LocalDate.of(2026, 9, 18)),
                HabitMark("walk", LocalDate.of(2026, 9, 20)),
            ),
            HabitLogFile.parse(text),
        )
    }

    @Test
    fun `a file with no header still reads`() {
        assertEquals(
            listOf(HabitMark("read", LocalDate.of(2026, 9, 18))),
            HabitLogFile.parse("2026-09-18,read\n"),
        )
    }

    @Test
    fun `blank lines are ignored`() {
        assertEquals(
            listOf(HabitMark("read", LocalDate.of(2026, 9, 18))),
            HabitLogFile.parse("date,habit\n\n2026-09-18,read\n\n\n"),
        )
    }

    @Test
    fun `a habit id with a comma in it survives`() {
        val awkward = listOf(HabitMark("read, then walk", LocalDate.of(2026, 9, 18)))
        val text = HabitLogFile.serialise(awkward)
        assertTrue(text.contains("\"read, then walk\""))
        assertEquals(awkward, HabitLogFile.parse(text))
    }

    @Test
    fun `a habit id with a quote in it survives`() {
        val awkward = listOf(HabitMark("say \"hello\"", LocalDate.of(2026, 9, 18)))
        assertEquals(awkward, HabitLogFile.parse(HabitLogFile.serialise(awkward)))
    }

    @Test
    fun `the days for one habit are picked out`() {
        assertEquals(
            setOf(LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 19)),
            HabitLogFile.datesFor(marks, "read"),
        )
        assertEquals(setOf(LocalDate.of(2026, 9, 18)), HabitLogFile.datesFor(marks, "walk"))
        assertTrue(HabitLogFile.datesFor(marks, "nothing").isEmpty())
    }

    @Test
    fun `a byte order mark at the start does not cost the first row`() {
        val text = HabitLogFile.serialise(marks)
        assertEquals(HabitLogFile.parse(text), HabitLogFile.parse(Char(0xFEFF).toString() + text))
    }

    @Test
    fun `the number of lines left out is counted`() {
        val text = "date,habit\n2026/11/8,run\n2026-09-18,read\n2026-09-19\n"
        assertEquals(
            Pair(listOf(HabitMark("read", LocalDate.of(2026, 9, 18))), 2),
            HabitLogFile.parseCounting(text),
        )
    }

    @Test
    fun `a blank line before the header is not counted as dropped`() {
        assertEquals(
            Pair(listOf(HabitMark("read", LocalDate.of(2026, 9, 18))), 0),
            HabitLogFile.parseCounting("\ndate,habit\n2026-09-18,read\n"),
        )
    }
}
