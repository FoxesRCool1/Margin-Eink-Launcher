package io.github.foxesrcool1.margin.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkShapes
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.CapsLabel
import io.github.foxesrcool1.margin.design.components.EinkIcon
import io.github.foxesrcool1.margin.design.components.EinkRow
import io.github.foxesrcool1.margin.design.components.EinkText
import io.github.foxesrcool1.margin.design.components.HairlineDivider
import io.github.foxesrcool1.margin.design.components.PagedList
import io.github.foxesrcool1.margin.design.icons.LucideIcon

/**
 * One row of Settings: what it is called and what a tap does. No help line:
 * a name that needs one is the wrong name.
 *
 * [value] is what the setting is set to now, for a setting with more than two
 * states. [on] makes the row a switch. [trailing] is the icon at the end of
 * the row: an arrow for a row that opens something, nothing for a row that
 * just does its job when pressed.
 */
class Setting(
    val title: String,
    val icon: LucideIcon? = null,
    val enabled: Boolean = true,
    val value: String? = null,
    val on: Boolean? = null,
    val trailing: LucideIcon? = null,
    val onClick: () -> Unit,
) {
    companion object {
        /** A row that is on or off. The switch at its end shows which. */
        fun switch(
            title: String,
            on: Boolean,
            onChange: (Boolean) -> Unit,
            icon: LucideIcon? = null,
            enabled: Boolean = true,
        ) = Setting(
            title = title,
            icon = icon,
            enabled = enabled,
            on = on,
            onClick = { onChange(!on) },
        )
    }
}

/**
 * The line of the title, the padding of the row and the rule under it, and a
 * few dp to spare. Text that is even one pixel too tall for its row loses its
 * last line, so the row is never cut to the exact sum.
 */
private val SettingRowHeight = 60.dp

/** One page of settings. It works out by itself how many rows fit. */
@Composable
fun SettingsList(rows: List<Setting>, modifier: Modifier = Modifier) {
    PagedList(
        items = rows,
        pageSize = 5,
        rowHeight = SettingRowHeight,
        modifier = modifier,
    ) { _, row ->
        SettingRow(row)
    }
}

@Composable
private fun SettingRow(row: Setting) {
    EinkRow(onClick = { if (row.enabled) row.onClick() }) { pressed ->
        val strong = when {
            pressed && row.enabled -> EinkColors.Paper
            row.enabled -> EinkColors.Ink
            else -> EinkColors.Faded
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.icon != null) EinkIcon(icon = row.icon, color = strong)
            EinkText(
                text = row.title,
                style = EinkType.rowTitle.copy(color = strong),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (row.value != null) CapsLabel(text = row.value, style = EinkType.capsSmall.copy(color = strong))
            if (row.on != null) {
                SwitchMark(on = row.on, color = strong, knob = if (pressed && row.enabled) EinkColors.Ink else EinkColors.Paper)
            }
            if (row.trailing != null) EinkIcon(icon = row.trailing, size = 22.dp, color = strong)
        }
    }
    HairlineDivider(color = EinkColors.Faded)
}

/**
 * A switch: a black pill with the knob on the right is on, an outline with
 * the knob on the left is off. [knob] is the colour behind the row, so the
 * knob of a pill that is on shows up as a hole.
 */
@Composable
private fun SwitchMark(on: Boolean, color: Color, knob: Color) {
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(EinkShapes.control)
            .background(if (on) color else Color.Transparent)
            .border(width = EinkDimens.rule, color = color, shape = EinkShapes.control)
            .padding(5.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(modifier = Modifier.size(14.dp).clip(EinkShapes.control).background(if (on) knob else color))
    }
}
