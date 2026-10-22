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
import androidx.compose.runtime.Composable
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
import io.github.foxesrcool1.margin.design.einkClickable
import java.time.LocalDate

/**
 * A month, as whole weeks starting on Monday.
 *
 * A day with an entry carries a rule under its number. Every number is pure
 * black: small grey text is hard to read on the panel. Today has a thin ring and
 * the chosen day a thick one. There is no colour and no fill, because a filled
 * cell on e-ink is a large black block that ghosts. The whole month fits on one
 * screen, so there is nothing to scroll.
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
    // A month of six weeks has to fit the tablet on its side as well. There
    // the rows give up a few dp each, rather than the last week being cut off.
    val cellHeight = if (constraints.hasBoundedHeight) {
        minOf(EinkDimens.touchTarget, (maxHeight - HeaderHeight) / weeks.size)
    } else {
        EinkDimens.touchTarget
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            JournalStrings.weekdayInitials().forEach { initial ->
                CapsLabel(
                    text = initial,
                    style = EinkType.capsSmall.copy(textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        HairlineDivider(color = EinkColors.Faded)
        Spacer(modifier = Modifier.height(4.dp))

        weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        hasEntry = day != null && daysWithEntries.contains(day),
                        isToday = day == today,
                        isSelected = day == selected,
                        onSelect = { if (day != null) onSelectDay(day) },
                        height = cellHeight,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    }
}

/** The weekday letters, the rule under them and the gaps. */
private val HeaderHeight = 28.dp

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
