package io.github.foxesrcool1.margin.ui.journal

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.foxesrcool1.margin.core.habits.Habit
import io.github.foxesrcool1.margin.core.habits.HabitStreaks
import io.github.foxesrcool1.margin.core.habits.HabitSummary
import io.github.foxesrcool1.margin.design.EinkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "sw480dp-w480dp-h640dp-port-xxhdpi")
class RowSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 9, 19)

    private fun summary(doneToday: Boolean): HabitSummary {
        val dates = if (doneToday) setOf(today) else emptySet()
        return HabitSummary(
            habit = Habit("read", "Read 30 minutes", LocalDate.of(2026, 1, 1)),
            doneToday = doneToday,
            currentStreak = HabitStreaks.currentStreak(dates, today),
            longestStreak = HabitStreaks.longestStreak(dates),
            lastDays = HabitStreaks.lastDays(dates, today),
        )
    }

    @Test
    fun `a done habit says Done to the screen reader`() {
        compose.setContent {
            EinkTheme {
                HabitRow(summary = summary(true), onToggle = {}, onOptions = {}, modifier = Modifier)
            }
        }
        compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Done"),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `a habit not done yet says Not done to the screen reader`() {
        compose.setContent {
            EinkTheme {
                HabitRow(summary = summary(false), onToggle = {}, onOptions = {}, modifier = Modifier)
            }
        }
        compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not done"),
            useUnmergedTree = true,
        ).assertExists()
    }
}
