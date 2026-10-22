package io.github.foxesrcool1.margin.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkShapes
import io.github.foxesrcool1.margin.design.einkClickable
import io.github.foxesrcool1.margin.design.icons.LucideIcon

/**
 * The line of an icon, in the icon's own 24-unit grid: 2 units for an icon
 * the size of a control, the lighter 1.5 units for a large one. Lucide itself
 * does this, and the line grows with the icon.
 */
fun defaultStrokeUnits(size: Dp): Float = if (size <= 32.dp) 2f else 1.5f

/**
 * A Lucide icon, drawn the Lucide way.
 *
 * The line is [strokeUnits] wide in the icon's own 24-unit grid, so it grows
 * with the [size], like the icons on lucide.dev. A 24 dp icon has a 2 dp line.
 * A large icon takes the lighter 1.5 units, so a 56 dp icon has a 3.5 dp line
 * and still looks fine. Pass [strokeUnits] to override that. Icons are the
 * one exception to e-ink rule 5 (decision 0026).
 */
@Composable
fun EinkIcon(
    icon: LucideIcon,
    modifier: Modifier = Modifier,
    size: Dp = EinkDimens.icon,
    color: Color = EinkColors.Ink,
    strokeUnits: Float = defaultStrokeUnits(size),
) {
    Canvas(modifier = modifier.size(size)) {
        val factor = this.size.minDimension / LucideIcon.VIEWPORT
        // In grid units, because the scale below multiplies it.
        val stroke = Stroke(width = strokeUnits, cap = StrokeCap.Round, join = StrokeJoin.Round)
        scale(scale = factor, pivot = Offset.Zero) {
            icon.fillPaths.forEach { drawPath(path = it, color = color, style = Fill) }
            icon.strokePaths.forEach { drawPath(path = it, color = color, style = stroke) }
        }
    }
}

/**
 * A control that is an icon and no word.
 *
 * It is a round target of 56 dp. While it is pressed, and for as long as it is
 * [selected], the circle turns black and the icon white: the same instant
 * invert every control in this app has. There is no ripple and no fade.
 *
 * [label] is never shown. A screen reader speaks it, and the tests find the
 * control by it.
 */
@Composable
fun IconPressButton(
    icon: LucideIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    bordered: Boolean = false,
    /** A control that should not draw the eye, like the one that turns the screen. */
    quiet: Boolean = false,
    iconSize: Dp = EinkDimens.icon,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val inverted = (pressed || selected) && enabled

    val foreground = when {
        inverted -> EinkColors.Paper
        !enabled || quiet -> EinkColors.Faded
        else -> EinkColors.Ink
    }

    Box(
        modifier = modifier
            .size(EinkDimens.touchTarget)
            .clip(EinkShapes.control)
            .background(if (inverted) EinkColors.Ink else EinkColors.Paper)
            .then(
                if (bordered) {
                    Modifier.border(
                        width = EinkDimens.hairline,
                        color = if (enabled) EinkColors.Ink else EinkColors.Faded,
                        shape = EinkShapes.control,
                    )
                } else {
                    Modifier
                },
            )
            .einkClickable(enabled = enabled, interactionSource = interactionSource, onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        EinkIcon(icon = icon, size = if (quiet) 20.dp else iconSize, color = foreground)
    }
}
