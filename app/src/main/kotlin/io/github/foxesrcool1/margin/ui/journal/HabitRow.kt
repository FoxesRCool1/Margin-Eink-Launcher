package io.github.foxesrcool1.margin.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.foxesrcool1.margin.core.habits.HabitSummary
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.CapsLabel
import io.github.foxesrcool1.margin.design.components.EinkIcon
import io.github.foxesrcool1.margin.design.components.EinkRow
import io.github.foxesrcool1.margin.design.components.EinkText
import io.github.foxesrcool1.margin.design.components.HairlineDivider
import io.github.foxesrcool1.margin.design.icons.Lucide

/**
 * One habit: its name, its streak, and a row of dots for the last 14 days.
 *
 * A tap marks the day done or undone. A hold opens the options. The dots are
 * filled or hollow circles rather than colours, because the panel has no
 * colour and a grey dot would dither.
 *
 * A row that is not [enabled], on a day that has not come yet, is faded and a
 * tap does nothing. The hold still works: a habit can be renamed any day.
 */
@Composable
fun HabitRow(
    summary: HabitSummary,
    onToggle: () -> Unit,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    EinkRow(onClick = if (enabled) onToggle else ({}), onLongClick = onOptions, modifier = modifier) { pressed ->
        val foreground = if (pressed) EinkColors.Paper else if (enabled) EinkColors.Ink else EinkColors.Faded
        val faded = if (pressed) EinkColors.Paper else EinkColors.Faded

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EinkIcon(
                icon = if (summary.doneToday) Lucide.CircleCheck else Lucide.Circle,
                size = 22.dp,
                color = foreground,
            )
            Spacer(modifier = Modifier.width(14.dp))
            EinkText(
                text = summary.habit.name,
                style = EinkType.rowTitle.copy(color = foreground),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            val streak = JournalStrings.streakLabel(summary.currentStreak)
            if (streak.isNotEmpty()) {
                CapsLabel(text = streak, style = EinkType.capsSmall.copy(color = faded))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(36.dp))
            summary.lastDays.forEach { done ->
                Dot(filled = done, colour = if (done) foreground else faded, diameter = 8.dp)
            }
        }
    }
    HairlineDivider(color = EinkColors.Faded)
}

/** A dot. Filled when the day was done, an outline when it was not. */
@Composable
private fun Dot(
    filled: Boolean,
    colour: Color,
    diameter: Dp,
) {
    Box(
        modifier = Modifier
            .size(diameter)
            .then(
                if (filled) {
                    Modifier.background(colour, CircleShape)
                } else {
                    Modifier.border(EinkDimens.hairline, colour, CircleShape)
                },
            ),
    )
}
