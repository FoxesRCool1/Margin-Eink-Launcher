package io.github.foxesrcool1.margin.ui.journal

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The words on the Journal tab. Pure, so the tests can pin them down. */
object JournalStrings {

    private val DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)
    private val SHORT_DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    fun dayTitle(date: LocalDate): String = DAY.format(date)

    /** "19 Sep": the day in half of a split screen, where the long one ends in three dots. */
    fun shortDayTitle(date: LocalDate): String = SHORT_DAY.format(date)

    fun monthTitle(date: LocalDate): String = MONTH.format(date)

    /** "Today", "Yesterday", or the date, so the user knows where they are. */
    fun dayOverline(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> date.year.toString()
    }

    fun streakLabel(currentStreak: Int): String = when (currentStreak) {
        0 -> "No streak"
        1 -> "1 day"
        else -> "$currentStreak days"
    }

    /** One letter per weekday for the month view header, starting on Monday. */
    fun weekdayInitials(): List<String> =
        (1..7).map { day ->
            java.time.DayOfWeek.of(day)
                .getDisplayName(TextStyle.NARROW, Locale.ENGLISH)
        }

    /**
     * The grid of a month, as whole weeks starting on Monday.
     *
     * Days outside the month are null, so the calendar keeps its shape and the
     * first of the month lands under the right weekday.
     */
    fun monthGrid(anyDayInMonth: LocalDate): List<List<LocalDate?>> {
        val first = anyDayInMonth.withDayOfMonth(1)
        val length = anyDayInMonth.lengthOfMonth()
        val blanksBefore = first.dayOfWeek.value - 1

        val cells = buildList<LocalDate?> {
            repeat(blanksBefore) { add(null) }
            (1..length).forEach { day -> add(first.withDayOfMonth(day)) }
            while (size % 7 != 0) add(null)
        }
        return cells.chunked(7)
    }

    /**
     * The day in the month [by] months away. It takes the day the user chose,
     * [anchorDay], and not the day on show, so Mar 31 to February to March
     * comes back to the 31st and not the 28th.
     */
    fun stepMonth(from: LocalDate, anchorDay: Int, by: Long): LocalDate {
        val month = YearMonth.from(from).plusMonths(by)
        return month.atDay(minOf(anchorDay, month.lengthOfMonth()))
    }
}
