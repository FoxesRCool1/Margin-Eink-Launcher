package io.github.foxesrcool1.margin.core.journal

/**
 * The daily word goal of the journal. Plain numbers, so the rules for them
 * can be tested without the rest of the app.
 */
object JournalGoal {

    /** Words per day. Zero is Off. */
    val STEPS = listOf(0, 250, 500, 750, 1000)

    /** Keeps a step as it is, and turns anything else into 0. */
    fun clean(words: Int): Int = if (words in STEPS) words else 0

    /** The step after [words] in [STEPS], wrapping from 1000 back to 0. A value that is not in [STEPS] gives 0. */
    fun next(words: Int): Int =
        STEPS[(STEPS.indexOf(words) + 1).mod(STEPS.size)]

    /** True when the day has reached the goal. A goal of 0 is never reached. */
    fun reached(words: Int, goal: Int): Boolean = goal > 0 && words >= goal

    /** "212 / 750 words" while a goal is set, "212 words" when it is not. */
    fun countText(words: Int, goal: Int): String {
        if (goal > 0) return "$words / $goal words"
        return if (words == 1) "1 word" else "$words words"
    }

    /** For a tight place: "212 / 750" while a goal is set, "212 words" when it is not. */
    fun shortCountText(words: Int, goal: Int): String =
        if (goal > 0) "$words / $goal" else countText(words, goal)

    /** "Off" when the goal is 0, otherwise "750 words". */
    fun goalLabel(goal: Int): String = if (goal <= 0) "Off" else "$goal words"
}
