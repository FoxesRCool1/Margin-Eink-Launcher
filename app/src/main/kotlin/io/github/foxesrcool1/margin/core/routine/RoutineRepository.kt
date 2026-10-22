package io.github.foxesrcool1.margin.core.routine

import io.github.foxesrcool1.margin.core.habits.HabitLogFile
import io.github.foxesrcool1.margin.core.habits.HabitMark
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import io.github.foxesrcool1.margin.core.habits.DayBoundary
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime

private const val TAG = "RoutineRepository"

/** One routine item as Today and the Journal tab see it. */
data class RoutineStatus(
    val item: RoutineItem,
    val doneToday: Boolean,
)

/**
 * The routine, on top of the data folder.
 *
 * The list lives in `habits/routine.json`. What was done on which day lives in
 * `habits/routine-log.csv`, in the same shape as the habit log and read by the
 * same code. Two files rather than one, so ticking an item off never rewrites
 * the list itself.
 */
class RoutineRepository(private val data: DataRepository) {

    /** Changes take turns, for the reason given in HabitsRepository. */
    private companion object {
        val LOCK = Any()
    }

    private val listPath = "${StorageLayout.HABITS}/routine.json"
    private val logPath = "${StorageLayout.HABITS}/routine-log.csv"

    fun load(): RoutineDocument = read() ?: RoutineDocument(emptyList())

    /**
     * The document, or null when the file is there and could not be read.
     * A change must stop on null: writing the empty list that [load] shows
     * would wipe every routine item the user has.
     */
    private fun read(): RoutineDocument? {
        val text = try {
            data.store.readTextOrThrow(listPath)
        } catch (problem: IOException) {
            AppLog.e(TAG, "routine.json could not be read. Nothing was changed.", problem)
            return null
        }
        if (text == null) return RoutineDocument(emptyList())

        val parsed = RoutineFile.parse(text)
        if (parsed == null) {
            AppLog.w(TAG, "routine.json could not be read. Starting from an empty list.")
            // The next "Add item" writes a new file over this one. Keep what
            // the user had, typo and all.
            data.keepUnreadable(listPath, "${StorageLayout.HABITS}/routine.unreadable.json")
            return RoutineDocument(emptyList())
        }
        return parsed
    }

    fun save(document: RoutineDocument): Boolean =
        data.store.writeText(listPath, RoutineFile.serialise(document))

    fun add(label: String, target: String = ""): RoutineDocument = synchronized(LOCK) {
        val current = read() ?: return@synchronized load()
        // A removed item leaves its ticks, and a new item with the same label
        // must not pick them up.
        val taken = current.items.map { it.id }.toSet() + readMarks().orEmpty().map { it.habitId }.toSet()
        val id = RoutineFile.idFor(label, taken)
        val next = RoutineDocument(current.items + RoutineItem(id, label.trim(), target))
        save(next)
        AppLog.i(TAG, "Added routine item $id")
        next
    }

    /** Sets the part of the day of one item. An id that is not there changes nothing. */
    fun setPart(id: String, part: PartOfDay): RoutineDocument = synchronized(LOCK) {
        val current = read() ?: return@synchronized load()
        val next = RoutineDocument(current.items.map { if (it.id == id) it.copy(part = part) else it })
        if (next != current) save(next)
        next
    }

    fun remove(id: String): RoutineDocument = synchronized(LOCK) {
        val current = read() ?: return@synchronized load()
        current.without(id).also { save(it) }
    }

    fun move(id: String, by: Int): RoutineDocument = synchronized(LOCK) {
        val current = read() ?: return@synchronized load()
        current.move(id, by).also { save(it) }
    }

    /** The marks, or null when the log is there and could not be read. See [read]. */
    private fun readMarks(): List<HabitMark>? {
        val text = try {
            data.store.readTextOrThrow(logPath)
        } catch (problem: IOException) {
            AppLog.e(TAG, "routine-log.csv could not be read. Nothing was changed.", problem)
            return null
        } ?: return emptyList()
        return HabitLogFile.parse(text)
    }

    /** Ticks an item off for [date], or takes the tick away. Returns the new state. */
    fun toggle(id: String, date: LocalDate): Boolean = synchronized(LOCK) {
        // Unreadable log: change nothing, and say the item is not done.
        val current = (readMarks() ?: return@synchronized false).toMutableList()
        val mark = HabitMark(id, date)

        val nowDone = if (current.remove(mark)) {
            false
        } else {
            current += mark
            true
        }

        data.store.writeText(logPath, HabitLogFile.serialise(current))
        nowDone
    }

    fun statuses(date: LocalDate): List<RoutineStatus> {
        val done = readMarks().orEmpty().filter { it.date == date }.map { it.habitId }.toSet()
        return load().items.map { item -> RoutineStatus(item, done.contains(item.id)) }
    }

    /**
     * The next thing to do today, or null when there is nothing on offer.
     *
     * "Next" is the first item in the user's own order that is not ticked off
     * and whose part of the day has begun at [now]. Not the nearest in time,
     * and not the one the app thinks is important. The order is the user's
     * plan for the day. An item of an earlier part of the day that is still
     * undone stays on offer; one of a later part waits for its time.
     */
    fun next(date: LocalDate, now: LocalTime, boundaryHour: Int = DayBoundary.DEFAULT_HOUR): RoutineStatus? =
        statuses(date).firstOrNull { !it.doneToday && it.item.part.hasStarted(now, boundaryHour) }
}
