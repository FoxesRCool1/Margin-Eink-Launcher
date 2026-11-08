package io.github.foxesrcool1.margin.core.log

import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pure formatting helpers. No Android classes here, so the unit tests can
 * check them without Robolectric.
 */
object LogFormat {

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** The zone used for file names and time stamps. Kept in one place for the tests. */
    var zone: ZoneId = ZoneId.systemDefault()

    fun timestamp(millis: Long): String =
        TIME.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun dayStamp(millis: Long): String =
        DAY.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** The log file name for a moment in time. One file per day. */
    fun fileNameFor(millis: Long): String = "log-${dayStamp(millis)}.txt"

    /** True when [fileName] is a log file that is older than [keepDays] days. */
    fun isExpiredLogFile(fileName: String, nowMillis: Long, keepDays: Int): Boolean {
        val day = dayOf(fileName) ?: return false
        val oldest = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().minusDays(keepDays.toLong() - 1)
        return day.isBefore(oldest)
    }

    /**
     * The crash files that are older than the newest [keep], oldest first.
     * The names carry the time, so the newest are the last in name order.
     */
    fun crashFilesToDelete(names: List<String>, keep: Int = 5): List<String> =
        names.filter { it.startsWith("crash-") && it.endsWith(".txt") }
            .sorted()
            .dropLast(keep)

    private fun dayOf(fileName: String): LocalDate? {
        if (!fileName.startsWith("log-") || !fileName.endsWith(".txt")) return null
        val text = fileName.removePrefix("log-").removeSuffix(".txt")
        return runCatching { LocalDate.parse(text, DAY) }.getOrNull()
    }

    fun stackTrace(error: Throwable): String {
        val writer = StringWriter()
        PrintWriter(writer).use { error.printStackTrace(it) }
        return writer.toString()
    }
}
