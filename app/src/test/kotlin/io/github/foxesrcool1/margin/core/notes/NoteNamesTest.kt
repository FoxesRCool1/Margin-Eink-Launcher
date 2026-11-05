package io.github.foxesrcool1.margin.core.notes

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteNamesTest {

    @Test
    fun `the name reads like the journal titles`() {
        val at = LocalDateTime.of(2026, 11, 5, 17, 33, 42)
        assertEquals("5 November 2026 17.33", timestampedNoteName(at))
    }

    @Test
    fun `the name has no colon and no slash, so it is safe in a file name`() {
        val at = LocalDateTime.of(2026, 1, 2, 3, 4, 5)
        val name = timestampedNoteName(at)
        assertEquals("2 January 2026 03.04", name)
        org.junit.Assert.assertFalse(name.contains(':'))
        org.junit.Assert.assertFalse(name.contains('/'))
    }
}
