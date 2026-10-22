package io.github.foxesrcool1.margin.core.routine

import java.time.LocalTime

/**
 * The part of the day a routine item belongs to.
 *
 * It only decides when Home starts offering the item. It never takes the item
 * away: a Morning item that is still undone at four in the afternoon is still
 * the next thing to do.
 *
 * [key] is what `routine.json` holds. Any Time holds nothing, so a file from
 * before this field and a file that never sets it read the same.
 */
enum class PartOfDay(val title: String, val key: String, private val startHour: Int?) {
    AnyTime("Any Time", "", null),

    /** Starts with the day itself, at the day boundary hour. */
    Morning("Morning", "morning", null),
    Afternoon("Afternoon", "afternoon", 12),
    Evening("Evening", "evening", 17),
    ;

    /**
     * True once this part of the day has begun at [time]. The day starts at
     * [boundaryHour], not at midnight, so with the usual 4 o'clock the small
     * hours still belong to the evening before.
     */
    fun hasStarted(time: LocalTime, boundaryHour: Int): Boolean {
        val start = startHour ?: return true
        return hoursIntoDay(time.hour, boundaryHour) >= hoursIntoDay(start, boundaryHour)
    }

    companion object {
        /** Anything that is not a known key, and a missing key, is Any Time. */
        fun fromKey(key: String?): PartOfDay =
            entries.firstOrNull { it.key.isNotEmpty() && it.key == key } ?: AnyTime

        private fun hoursIntoDay(hour: Int, boundaryHour: Int): Int = (hour - boundaryHour + 24) % 24
    }
}
