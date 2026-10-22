package io.github.foxesrcool1.margin.core.routine

/**
 * One thing to do today, in order.
 *
 * [target] is a plain string rather than a screen or an intent, so this file
 * and the file it is written to hold no Android and no user interface. The
 * Journal tab turns it into something to open.
 *
 * Known targets:
 * - `""` for an item with nothing to open. The user just ticks it off.
 * - `read`, `write`, `journal`, `apps` for a tab in this app.
 * - `app:<package>/<class>` for another app on the tablet.
 *
 * [part] says when in the day Home starts offering the item.
 */
data class RoutineItem(
    val id: String,
    val label: String,
    val target: String = "",
    val part: PartOfDay = PartOfDay.AnyTime,
) {
    val opensATab: Boolean get() = target in TAB_TARGETS

    val opensAnApp: Boolean get() = target.startsWith(APP_PREFIX)

    /** The component of the app to open, or null. */
    fun appComponent(): Pair<String, String>? {
        if (!opensAnApp) return null
        val key = target.removePrefix(APP_PREFIX)
        val packageName = key.substringBefore('/', "")
        val className = key.substringAfter('/', "")
        return if (packageName.isBlank() || className.isBlank()) {
            null
        } else {
            packageName to className
        }
    }

    companion object {
        const val APP_PREFIX = "app:"
        val TAB_TARGETS = setOf("read", "write", "journal", "apps")
    }
}

/** The whole routine, in the order the user put it in. */
data class RoutineDocument(val items: List<RoutineItem>) {

    fun move(id: String, by: Int): RoutineDocument {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return this
        val target = (index + by).coerceIn(0, items.size - 1)
        if (target == index) return this

        val reordered = items.toMutableList()
        reordered.add(target, reordered.removeAt(index))
        return RoutineDocument(reordered)
    }

    fun without(id: String): RoutineDocument = RoutineDocument(items.filterNot { it.id == id })
}
