package io.github.foxesrcool1.margin.core.habits

import java.time.LocalDate

/**
 * Reads and writes `habits/log.csv`.
 *
 * One line per day per habit, as plain comma separated text, so the owner can
 * open a year of tracking in a spreadsheet without this app. The header line
 * is part of the format.
 */
object HabitLogFile {

    const val HEADER = "date,habit"

    /** The byte order mark some editors put at the start of a saved file. */
    private val BYTE_ORDER_MARK = Char(0xFEFF).toString()

    fun serialise(marks: Collection<HabitMark>): String = buildString {
        append(HEADER).append('\n')
        marks
            .distinct()
            .sortedWith(compareBy({ it.date }, { it.habitId }))
            .forEach { mark ->
                append(mark.date).append(',').append(escape(mark.habitId)).append('\n')
            }
    }

    /**
     * Reads the file back. A line that makes no sense is dropped, not fatal:
     * one bad line must not cost the user the whole history.
     */
    fun parse(text: String): List<HabitMark> = parseCounting(text).first

    /**
     * Like [parse], and also says how many lines were left out. A byte order
     * mark at the start of the text is skipped, so the header still counts as one.
     */
    fun parseCounting(text: String): Pair<List<HabitMark>, Int> {
        val marks = mutableListOf<HabitMark>()
        var dropped = 0

        var headerChecked = false
        text.removePrefix(BYTE_ORDER_MARK).lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            val isFirstLine = !headerChecked
            headerChecked = true
            if (isFirstLine && line.equals(HEADER, ignoreCase = true)) return@forEach

            val fields = splitLine(line)
            if (fields.size < 2) {
                dropped++
                return@forEach
            }

            val date = runCatching { LocalDate.parse(fields[0]) }.getOrNull()
            if (date == null) {
                dropped++
                return@forEach
            }
            val habitId = fields[1].takeIf { it.isNotBlank() }
            if (habitId == null) {
                dropped++
                return@forEach
            }

            marks += HabitMark(habitId, date)
        }

        return marks.distinct() to dropped
    }

    /** The days on which one habit was done. */
    fun datesFor(marks: Collection<HabitMark>, habitId: String): Set<LocalDate> =
        marks.filter { it.habitId == habitId }.map { it.date }.toSet()

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    /** Splits one line, understanding quoted fields with commas inside. */
    private fun splitLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val character = line[index]
            when {
                inQuotes && character == '"' && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                character == '"' -> inQuotes = !inQuotes
                character == ',' && !inQuotes -> {
                    fields += current.toString()
                    current.setLength(0)
                }
                else -> current.append(character)
            }
            index++
        }
        fields += current.toString()
        return fields.map { it.trim() }
    }
}
