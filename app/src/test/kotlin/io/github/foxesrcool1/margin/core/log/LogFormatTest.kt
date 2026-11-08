package io.github.foxesrcool1.margin.core.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class LogFormatTest {

    private fun millisOf(text: String): Long =
        LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Before
    fun useOneZone() {
        LogFormat.zone = ZoneId.of("UTC")
    }

    @Test
    fun `one log file per day`() {
        assertEquals("log-2026-09-19.txt", LogFormat.fileNameFor(millisOf("2026-09-19T23:59:59")))
        assertEquals("log-2026-09-20.txt", LogFormat.fileNameFor(millisOf("2026-09-20T00:00:00")))
    }

    @Test
    fun `a time stamp has milliseconds`() {
        assertEquals(
            "2026-09-19 08:04:05.000",
            LogFormat.timestamp(millisOf("2026-09-19T08:04:05")),
        )
    }

    @Test
    fun `old log files expire and new ones stay`() {
        val now = millisOf("2026-09-19T12:00:00")
        assertTrue(LogFormat.isExpiredLogFile("log-2026-09-01.txt", now, keepDays = 14))
        assertFalse(LogFormat.isExpiredLogFile("log-2026-09-06.txt", now, keepDays = 14))
        assertFalse(LogFormat.isExpiredLogFile("log-2026-09-19.txt", now, keepDays = 14))
    }

    @Test
    fun `a file that is not a log file is left alone`() {
        val now = millisOf("2026-09-19T12:00:00")
        assertFalse(LogFormat.isExpiredLogFile("crash-2020-01-01-1.txt", now, keepDays = 14))
        assertFalse(LogFormat.isExpiredLogFile("notes.md", now, keepDays = 14))
        assertFalse(LogFormat.isExpiredLogFile("log-not-a-date.txt", now, keepDays = 14))
    }

    @Test
    fun `only the newest five crash files are kept`() {
        val names = (1..8).map { "crash-2026-11-0$it-170000000000$it.txt" }
        assertEquals(names.take(3), LogFormat.crashFilesToDelete(names))
    }

    @Test
    fun `three crash files are all kept`() {
        val names = (1..3).map { "crash-2026-11-0$it-170000000000$it.txt" }
        assertEquals(emptyList<String>(), LogFormat.crashFilesToDelete(names))
    }

    @Test
    fun `a log line keeps its level tag and message`() {
        val line = LogLine(millisOf("2026-09-19T08:04:05"), LogLevel.WARN, "Ink", "pen lost")
        assertEquals("2026-09-19 08:04:05.000 W Ink: pen lost", line.format())
    }

    @Test
    fun `a stack trace becomes text`() {
        val trace = LogFormat.stackTrace(IllegalStateException("boom"))
        assertTrue(trace.contains("IllegalStateException"))
        assertTrue(trace.contains("boom"))
    }
}
