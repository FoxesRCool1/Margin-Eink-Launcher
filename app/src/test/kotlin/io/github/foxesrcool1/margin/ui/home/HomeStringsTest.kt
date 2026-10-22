package io.github.foxesrcool1.margin.ui.home

import io.github.foxesrcool1.margin.core.routine.PartOfDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class HomeStringsTest {

    private val morning = LocalDateTime.of(2026, 9, 19, 8, 4)
    private val evening = LocalDateTime.of(2026, 9, 19, 21, 30)

    @Test
    fun `the date reads as a day and a month`() {
        assertEquals("Saturday 19 September", HomeStrings.date(morning))
    }

    @Test
    fun `24 hour time keeps the leading zero`() {
        assertEquals("08:04", HomeStrings.time(morning, use24Hour = true))
        assertEquals("21:30", HomeStrings.time(evening, use24Hour = true))
    }

    @Test
    fun `12 hour time drops the leading zero and adds AM or PM`() {
        assertEquals("8:04 AM", HomeStrings.time(morning, use24Hour = false))
        assertEquals("9:30 PM", HomeStrings.time(evening, use24Hour = false))
    }

    @Test
    fun `the status line shows what is known`() {
        assertEquals("Wi-Fi on  .  Battery 82%", HomeStrings.status(82, onWifi = true))
        assertEquals("Wi-Fi off  .  Battery 5%", HomeStrings.status(5, onWifi = false))
    }

    @Test
    fun `a battery that does not report is left out, not guessed`() {
        assertEquals("Wi-Fi on", HomeStrings.status(null, onWifi = true))
    }

    @Test
    fun `24 hour digits keep the leading zero`() {
        assertEquals("08:04", HomeStrings.digits(morning, use24Hour = true))
        assertEquals("21:30", HomeStrings.digits(evening, use24Hour = true))
    }

    @Test
    fun `12 hour digits drop the leading zero and keep midnight as 12`() {
        assertEquals("8:04", HomeStrings.digits(morning, use24Hour = false))
        assertEquals("9:30", HomeStrings.digits(evening, use24Hour = false))
        assertEquals("12:05", HomeStrings.digits(LocalDateTime.of(2026, 9, 19, 0, 5), use24Hour = false))
    }

    @Test
    fun `24 hour digits keep midnight as zero o'clock`() {
        assertEquals("00:05", HomeStrings.digits(LocalDateTime.of(2026, 9, 19, 0, 5), use24Hour = true))
    }

    @Test
    fun `the meridiem says AM or PM`() {
        assertEquals("AM", HomeStrings.meridiem(morning))
        assertEquals("PM", HomeStrings.meridiem(evening))
        assertEquals("AM", HomeStrings.meridiem(LocalDateTime.of(2026, 9, 19, 0, 5)))
        assertEquals("PM", HomeStrings.meridiem(LocalDateTime.of(2026, 9, 19, 12, 0)))
    }

    @Test
    fun `the overline says what comes next`() {
        assertEquals("Next", HomeStrings.nextOverline(PartOfDay.AnyTime))
        assertEquals("This Morning", HomeStrings.nextOverline(PartOfDay.Morning))
        assertEquals("This Afternoon", HomeStrings.nextOverline(PartOfDay.Afternoon))
        assertEquals("This Evening", HomeStrings.nextOverline(PartOfDay.Evening))
    }
}
