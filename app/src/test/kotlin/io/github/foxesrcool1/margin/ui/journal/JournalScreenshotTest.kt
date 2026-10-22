package io.github.foxesrcool1.margin.ui.journal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.foxesrcool1.margin.core.habits.Habit
import io.github.foxesrcool1.margin.core.habits.HabitStreaks
import io.github.foxesrcool1.margin.core.habits.HabitSummary
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkTheme
import io.github.foxesrcool1.margin.support.captureTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Screenshots of the Journal parts that take their data as arguments.
 *
 * The whole screen is left out on purpose: it loads from the disk in the
 * background, and a screenshot that waits on a background job is a screenshot
 * that fails once a month for no reason anyone can find.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "sw480dp-w480dp-h640dp-port-xxhdpi")
class JournalScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 9, 19)

    private fun summary(
        name: String,
        doneOffsets: List<Long>,
    ): HabitSummary {
        val dates = doneOffsets.map { today.minusDays(it) }.toSet()
        return HabitSummary(
            habit = Habit(name.lowercase(), name, LocalDate.of(2026, 1, 1)),
            doneToday = dates.contains(today),
            currentStreak = HabitStreaks.currentStreak(dates, today),
            longestStreak = HabitStreaks.longestStreak(dates),
            lastDays = HabitStreaks.lastDays(dates, today),
        )
    }

    @Test
    fun habitRows() {
        compose.setContent {
            EinkTheme {
                Column(modifier = Modifier.fillMaxSize().padding(EinkDimens.screenMargin)) {
                    HabitRow(
                        summary = summary("Read 30 minutes", listOf(0, 1, 2, 3, 4)),
                        onToggle = {},
                        onOptions = {},
                    )
                    HabitRow(
                        summary = summary("Walk outside", listOf(1, 2, 5, 9)),
                        onToggle = {},
                        onOptions = {},
                    )
                    HabitRow(
                        summary = summary("Write a page", emptyList()),
                        onToggle = {},
                        onOptions = {},
                    )
                }
            }
        }
        compose.onRoot().captureTo("habit_rows")
    }

    @Test
    fun habitRowWithoutStreak() {
        // Another day than today: the streak counts up to today, so it is left out.
        compose.setContent {
            EinkTheme {
                Column(modifier = Modifier.fillMaxSize().padding(EinkDimens.screenMargin)) {
                    HabitRow(
                        summary = summary("Read 30 minutes", listOf(0, 1, 2, 3, 4)),
                        onToggle = {},
                        onOptions = {},
                        showStreak = false,
                    )
                }
            }
        }
        compose.onRoot().captureTo("habit_row_without_streak")
    }

    @Test
    fun monthView() {
        compose.setContent {
            EinkTheme {
                Column(modifier = Modifier.fillMaxSize().padding(EinkDimens.screenMargin)) {
                    MonthView(
                        month = today,
                        daysWithEntries = setOf(
                            LocalDate.of(2026, 9, 1),
                            LocalDate.of(2026, 9, 2),
                            LocalDate.of(2026, 9, 15),
                            LocalDate.of(2026, 9, 18),
                            LocalDate.of(2026, 9, 19),
                        ),
                        today = today,
                        // Today has the thin ring, the chosen day the thick one.
                        selected = LocalDate.of(2026, 9, 9),
                        onSelectDay = {},
                    )
                }
            }
        }
        compose.onRoot().captureTo("month_view")
    }
}
