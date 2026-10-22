package io.github.foxesrcool1.margin.ui.home

import io.github.foxesrcool1.margin.core.routine.PartOfDay
import io.github.foxesrcool1.margin.design.icons.Lucide
import io.github.foxesrcool1.margin.design.icons.LucideIcon
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The strings on Home.
 *
 * Kept free of Android so the unit tests can check them at a fixed time.
 */
object HomeStrings {

    private val DATE = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)
    private val TIME_24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val TIME_12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val DIGITS_24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val DIGITS_12 = DateTimeFormatter.ofPattern("h:mm", Locale.ENGLISH)
    private val MERIDIEM = DateTimeFormatter.ofPattern("a", Locale.ENGLISH)

    fun date(now: LocalDateTime): String = DATE.format(now)

    fun time(now: LocalDateTime, use24Hour: Boolean): String =
        if (use24Hour) TIME_24.format(now) else TIME_12.format(now)

    /** The time without AM or PM, which the big clock draws small and beside the digits. */
    fun digits(now: LocalDateTime, use24Hour: Boolean): String =
        if (use24Hour) DIGITS_24.format(now) else DIGITS_12.format(now)

    /** "AM" or "PM". */
    fun meridiem(now: LocalDateTime): String = MERIDIEM.format(now)

    /** The small line over the next routine item. */
    fun nextOverline(part: PartOfDay): String =
        if (part == PartOfDay.AnyTime) "Next" else "This ${part.title}"

    /** "12%" at the end of the Continue row, or null when there is nothing to say. */
    fun percent(percent: Int?): String? = percent?.let { "$it%" }

    /** The battery icon for a charge: full, half, low, and a warning under 15 %. */
    fun batteryIcon(percent: Int): LucideIcon = when {
        percent >= 70 -> Lucide.BatteryFull
        percent >= 35 -> Lucide.BatteryMedium
        percent >= 15 -> Lucide.BatteryLow
        else -> Lucide.BatteryWarning
    }

    /**
     * The status line in words. The screen shows it as icons, and a screen
     * reader speaks this. Parts that are not known are left
     * out rather than shown as a guess.
     */
    fun status(batteryPercent: Int?, onWifi: Boolean): String {
        val parts = mutableListOf<String>()
        parts += if (onWifi) "Wi-Fi on" else "Wi-Fi off"
        if (batteryPercent != null) parts += "Battery $batteryPercent%"
        return parts.joinToString("  .  ")
    }
}
