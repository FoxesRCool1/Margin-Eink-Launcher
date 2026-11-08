package io.github.foxesrcool1.margin.core.habits

import io.github.foxesrcool1.margin.core.json.Json
import io.github.foxesrcool1.margin.core.json.JsonArray
import io.github.foxesrcool1.margin.core.json.JsonObject
import io.github.foxesrcool1.margin.core.json.jsonArrayOf
import io.github.foxesrcool1.margin.core.json.jsonOf
import java.time.LocalDate

/**
 * Reads and writes `habits/habits.json`.
 *
 * The file is meant to be readable and editable by hand. A user who wants to
 * rename a habit with a text editor should be able to, and the app should not
 * mind.
 */
object HabitsFile {

    const val VERSION = 1

    fun serialise(document: HabitsDocument): String {
        val habits = document.habits.map { habit ->
            JsonObject.of(
                "id" to jsonOf(habit.id),
                "name" to jsonOf(habit.name),
                "createdOn" to jsonOf(habit.createdOn.toString()),
                "archived" to jsonOf(habit.archived),
            )
        }

        return Json.write(
            JsonObject.of(
                "version" to jsonOf(VERSION),
                "dayBoundaryHour" to jsonOf(document.dayBoundaryHour),
                "habits" to jsonArrayOf(habits),
            ),
        )
    }

    /**
     * Returns null when the text is not this file at all. A single bad habit
     * is dropped rather than losing every other one, because the alternative
     * is a user who loses a year of tracking to one stray comma.
     */
    fun parse(text: String): HabitsDocument? = parseCounting(text)?.first

    /** Like [parse], and also says how many entries were left out. */
    fun parseCounting(text: String): Pair<HabitsDocument, Int>? {
        val root = Json.parseOrNull(text) as? JsonObject ?: return null
        if (root.int("version") == null) return null

        val hour = root.int("dayBoundaryHour")
            ?.takeIf { it in 0..23 }
            ?: DayBoundary.DEFAULT_HOUR

        val entries = root["habits"] as? JsonArray
        val habits = entries
            ?.objects()
            ?.mapNotNull { entry ->
                val id = entry.string("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = entry.string("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val createdOn = entry.string("createdOn")
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: return@mapNotNull null

                Habit(
                    id = id,
                    name = name,
                    createdOn = createdOn,
                    archived = entry.boolean("archived") ?: false,
                )
            }
            ?.distinctBy { it.id }
            ?: emptyList()

        val dropped = (entries?.size ?: 0) - habits.size
        return HabitsDocument(habits, hour) to dropped
    }

    /** Turns a name into an id that is stable and safe in a file name. */
    fun idFor(name: String, existing: Set<String>): String {
        val base = name
            .lowercase(java.util.Locale.ROOT)
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .replace(Regex("-{2,}"), "-")
            .trim('-')
            .take(40)
            .ifBlank { "habit" }

        if (base !in existing) return base
        var counter = 2
        while ("$base-$counter" in existing) counter++
        return "$base-$counter"
    }
}
