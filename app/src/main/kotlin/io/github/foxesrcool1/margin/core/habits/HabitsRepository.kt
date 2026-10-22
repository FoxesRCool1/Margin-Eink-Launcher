package io.github.foxesrcool1.margin.core.habits

import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import java.io.IOException
import java.time.LocalDate

private const val TAG = "HabitsRepository"

/**
 * Habits on top of the data folder.
 *
 * Two files, both meant to be read by a person: `habits/habits.json` holds the
 * list, `habits/log.csv` holds one line per day done. Splitting them keeps the
 * log append friendly and keeps a year of ticks out of the file the user is
 * most likely to edit by hand.
 *
 * Step 9 builds the screens on top of this.
 */
class HabitsRepository(private val data: DataRepository) {

    /**
     * Every change here reads a whole file, changes it, and writes it back,
     * and each tap runs on a thread of its own. Two quick taps on two habits
     * could both read the old file, and the second write then wiped the first
     * tick. So the changes take turns. One lock for every copy of this class,
     * because the Journal and Today each make their own.
     */
    private companion object {
        val LOCK = Any()
    }

    fun load(): HabitsDocument = read() ?: HabitsDocument(emptyList(), DayBoundary.DEFAULT_HOUR)

    /**
     * The document, or null when the file is there and could not be read.
     * A change must stop on null: writing the empty list that [load] shows
     * would wipe every habit the user has.
     */
    private fun read(): HabitsDocument? {
        val text = try {
            data.store.readTextOrThrow(StorageLayout.habitsPath())
        } catch (problem: IOException) {
            AppLog.e(TAG, "habits.json could not be read. Nothing was changed.", problem)
            return null
        }
        if (text == null) return HabitsDocument(emptyList(), DayBoundary.DEFAULT_HOUR)

        val parsed = HabitsFile.parse(text)
        if (parsed == null) {
            AppLog.w(TAG, "habits.json could not be read. Starting from an empty list.")
            // The next "Add habit" writes a new file over this one. Keep what
            // the user had, typo and all.
            data.keepUnreadable(StorageLayout.habitsPath(), "${StorageLayout.HABITS}/habits.unreadable.json")
            return HabitsDocument(emptyList(), DayBoundary.DEFAULT_HOUR)
        }
        return parsed
    }

    fun save(document: HabitsDocument): Boolean =
        data.store.writeText(StorageLayout.habitsPath(), HabitsFile.serialise(document))

    fun add(name: String, today: LocalDate): HabitsDocument = synchronized(LOCK) {
        val current = read() ?: return@synchronized load()

        // The Journal has no list of archived habits. Adding one again by its
        // name is the way back, and it comes back with the days it was done,
        // not as a second habit that starts from nothing.
        val archived = current.habits.firstOrNull { it.archived && it.name.equals(name.trim(), ignoreCase = true) }
        if (archived != null) {
            val next = current.copy(
                habits = current.habits.map { if (it.id == archived.id) it.copy(archived = false) else it },
            )
            save(next)
            AppLog.i(TAG, "Brought back the archived habit ${archived.id}")
            return@synchronized next
        }

        val id = HabitsFile.idFor(name, current.habits.map { it.id }.toSet())
        val next = current.copy(habits = current.habits + Habit(id, name.trim(), today))
        save(next)
        AppLog.i(TAG, "Added habit $id")
        next
    }

    fun rename(habitId: String, newName: String): HabitsDocument = synchronized(LOCK) {
        val current = read() ?: return@synchronized load()
        val next = current.copy(
            habits = current.habits.map { habit ->
                if (habit.id == habitId) habit.copy(name = newName.trim()) else habit
            },
        )
        save(next)
        next
    }

    /**
     * Archives rather than deletes.
     *
     * The log keeps the days that were done. Deleting the habit would leave
     * lines in the log that point at nothing, and the user would lose the
     * record of a year of work to one tap.
     */
    fun setArchived(habitId: String, archived: Boolean): HabitsDocument = synchronized(LOCK) {
        val current = read() ?: return@synchronized load()
        val next = current.copy(
            habits = current.habits.map { habit ->
                if (habit.id == habitId) habit.copy(archived = archived) else habit
            },
        )
        save(next)
        AppLog.i(TAG, "Habit $habitId archived: $archived")
        next
    }

    fun setDayBoundaryHour(hour: Int): HabitsDocument = synchronized(LOCK) {
        require(hour in 0..23) { "The day boundary must be an hour of the day" }
        val next = (read() ?: return@synchronized load()).copy(dayBoundaryHour = hour)
        save(next)
        next
    }

    fun loadMarks(): List<HabitMark> = readMarks() ?: emptyList()

    /** The marks, or null when the log is there and could not be read. See [read]. */
    private fun readMarks(): List<HabitMark>? {
        val text = try {
            data.store.readTextOrThrow(StorageLayout.habitLogPath())
        } catch (problem: IOException) {
            AppLog.e(TAG, "log.csv could not be read. Nothing was changed.", problem)
            return null
        } ?: return emptyList()
        return HabitLogFile.parse(text)
    }

    fun saveMarks(marks: Collection<HabitMark>): Boolean =
        data.store.writeText(StorageLayout.habitLogPath(), HabitLogFile.serialise(marks))

    /** Marks a day done, or takes the mark off again. Returns the new state. */
    fun toggle(habitId: String, date: LocalDate): Boolean = synchronized(LOCK) {
        // Unreadable log: change nothing, and say the day is not done.
        val marks = (readMarks() ?: return@synchronized false).toMutableList()
        val mark = HabitMark(habitId, date)

        val nowDone = if (marks.remove(mark)) {
            false
        } else {
            marks += mark
            true
        }

        saveMarks(marks)
        AppLog.i(TAG, "Habit $habitId on $date is now ${if (nowDone) "done" else "not done"}")
        nowDone
    }

    fun datesFor(habitId: String): Set<LocalDate> = HabitLogFile.datesFor(loadMarks(), habitId)

    /** Everything one row of the Journal tab needs, worked out in one read. */
    fun summaries(today: LocalDate): List<HabitSummary> {
        val document = load()
        val marks = loadMarks()

        return document.active().map { habit ->
            val dates = HabitLogFile.datesFor(marks, habit.id)
            HabitSummary(
                habit = habit,
                doneToday = dates.contains(today),
                currentStreak = HabitStreaks.currentStreak(dates, today),
                longestStreak = HabitStreaks.longestStreak(dates),
                lastDays = HabitStreaks.lastDays(dates, today),
            )
        }
    }
}

/** One habit as a Journal row sees it. */
data class HabitSummary(
    val habit: Habit,
    val doneToday: Boolean,
    val currentStreak: Int,
    val longestStreak: Int,
    val lastDays: List<Boolean>,
)
