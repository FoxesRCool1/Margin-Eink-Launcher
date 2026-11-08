package io.github.foxesrcool1.margin.ui.journal

import io.github.foxesrcool1.margin.core.habits.Habit
import io.github.foxesrcool1.margin.core.habits.HabitsDocument
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ArchivedHabitsTest {

    @Test
    fun archivedOfKeepsOnlyTheArchivedHabits() {
        val day = LocalDate.of(2026, 1, 1)
        val doc = HabitsDocument(
            habits = listOf(
                Habit("a", "Walk", day),
                Habit("b", "Read", day, archived = true),
            ),
            dayBoundaryHour = 4,
        )

        assertEquals(listOf("b"), archivedOf(doc).map { it.id })
    }
}
