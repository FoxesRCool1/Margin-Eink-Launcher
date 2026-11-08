package io.github.foxesrcool1.margin.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkShapes
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.CapsLabel
import io.github.foxesrcool1.margin.design.components.EinkText
import io.github.foxesrcool1.margin.design.components.HairlineDivider
import io.github.foxesrcool1.margin.design.components.IconPressButton
import io.github.foxesrcool1.margin.design.einkClickable
import io.github.foxesrcool1.margin.design.icons.Lucide
import java.time.LocalDate

/**
 * A month, as whole weeks starting on Monday.
 *
 * A day with an entry carries a rule under its number. Every number is pure
 * black: small grey text is hard to read on the panel. Today has a thin ring and
 * the chosen day a thick one. There is no colour and no fill, because a filled
 * cell on e-ink is a large black block that ghosts.
 *
 * Every day cell is at least 56 dp tall. When all the weeks fit the height they
 * are shown together. When they do not, the month is shown in pages of weeks
 * with Previous and Next below. There is no scrolling.
 */
@Composable
fun MonthView(
    month: LocalDate,
    daysWithEntries: Set<LocalDate>,
    today: LocalDate,
    selected: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val weeks = JournalStrings.monthGrid(month)
        val rowGap = EinkDimens.targetGap
        // The columns are narrow on a half screen, so their gap is small.
        val columnGap = 4.dp
        // The height holds the 56 dp rule. The width is what the half allows:
        // it is never above 56 dp and may be less in a narrow half.
        val cellWidth = minOf(EinkDimens.touchTarget, (maxWidth - columnGap * 6f) / 7f)

        // How many week rows fit in a given height.
        fun rowsWithin(room: Dp): Int =
            ((room + rowGap) / (EinkDimens.touchTarget + rowGap)).toInt()

        // All weeks on one page when they fit, otherwise pages that leave room
        // for the Previous and Next controls.
        val pages = if (!constraints.hasBoundedHeight || rowsWithin(maxHeight - HeaderHeight) >= weeks.size) {
            listOf(0 until weeks.size)
        } else {
            weekPages(weeks.size, rowsWithin(maxHeight - HeaderHeight - PageControlsHeight))
        }

        // Open on the page with the selected day, or today when the selected
        // day is not in this month. Reset when the month changes.
        val focusDay = if (weeks.any { it.contains(selected) }) selected else today
        val focusWeek = weeks.indexOfFirst { it.contains(focusDay) }.coerceAtLeast(0)
        var page by remember(month) {
            mutableIntStateOf(pages.indexOfFirst { focusWeek in it }.coerceAtLeast(0))
        }
        val shownPage = page.coerceIn(0, pages.lastIndex)

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(columnGap, Alignment.CenterHorizontally),
            ) {
                JournalStrings.weekdayInitials().forEach { initial ->
                    CapsLabel(
                        text = initial,
                        style = EinkType.capsSmall.copy(textAlign = TextAlign.Center),
                        modifier = Modifier.width(cellWidth),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HairlineDivider(color = EinkColors.Faded)
            Spacer(modifier = Modifier.height(4.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(rowGap),
            ) {
                pages[shownPage].forEach { weekIndex ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(columnGap, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        weeks[weekIndex].forEach { day ->
                            DayCell(
                                day = day,
                                hasEntry = day != null && daysWithEntries.contains(day),
                                isToday = day == today,
                                isSelected = day == selected,
                                onSelect = { if (day != null) onSelectDay(day) },
                                height = EinkDimens.touchTarget,
                                modifier = Modifier.width(cellWidth),
                            )
                        }
                    }
                }
            }

            if (pages.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = EinkDimens.targetGap),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconPressButton(
                        icon = Lucide.ChevronLeft,
                        label = "Previous weeks",
                        onClick = { page = shownPage - 1 },
                        enabled = shownPage > 0,
                        bordered = true,
                    )
                    CapsLabel(
                        text = "${shownPage + 1} of ${pages.size}",
                        style = EinkType.capsSmall,
                    )
                    IconPressButton(
                        icon = Lucide.ChevronRight,
                        label = "Next weeks",
                        onClick = { page = shownPage + 1 },
                        enabled = shownPage < pages.lastIndex,
                        bordered = true,
                    )
                }
            }
        }
    }
}

/**
 * Splits the week rows of a month into pages. When every week fits in
 * [fitRows], there is one page. Otherwise each page holds three weeks, or
 * fewer when there is less room, and never less than one row.
 */
internal fun weekPages(weeks: Int, fitRows: Int): List<IntRange> {
    if (weeks <= fitRows) return listOf(0 until weeks)
    val size = minOf(3, maxOf(1, fitRows))
    return (0 until weeks step size).map { start -> start until minOf(start + size, weeks) }
}

/** The weekday letters, the rule under them and the gaps. */
private val HeaderHeight = 28.dp

/** The Previous and Next row: a 56 dp target and the gap above it. */
private val PageControlsHeight = EinkDimens.touchTarget + EinkDimens.targetGap

@Composable
private fun DayCell(
    day: LocalDate?,
    hasEntry: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    if (day == null) {
        Box(modifier = modifier.height(height))
        return
    }

    Column(
        modifier = modifier
            .height(height)
            .then(
                when {
                    isSelected -> Modifier.border(EinkDimens.rule, EinkColors.Ink, EinkShapes.field)
                    // Today is told by a thin ring, not by grey on the other days.
                    isToday -> Modifier.border(EinkDimens.hairline, EinkColors.Ink, EinkShapes.field)
                    else -> Modifier
                },
            )
            .einkClickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EinkText(
            text = day.dayOfMonth.toString(),
            style = EinkType.rowTitle.copy(textAlign = TextAlign.Center),
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .height(EinkDimens.rule)
                .then(if (hasEntry) Modifier.fillMaxWidth(0.5f) else Modifier)
                .background(if (hasEntry) EinkColors.Ink else EinkColors.Paper),
        )
    }
}
