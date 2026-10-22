package io.github.foxesrcool1.margin.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkShapes
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.einkClickable
import io.github.foxesrcool1.margin.design.icons.LucideIcon
import java.util.Locale

/**
 * A control with a word on it.
 *
 * Most controls in the app are an [IconPressButton] now. This one is for the
 * places where a word is safer than a picture: the answer to "Delete this?",
 * and a choice between values that have no picture.
 *
 * E-ink rule 5: the pressed state is an instant colour invert. There is no
 * ripple, no shadow and no fade, because each of those is an animation or a
 * grey wash, and both look bad on the panel.
 *
 * A control that is [selected] stays inverted, which is how this design shows
 * the chosen item of a small group.
 */
@Composable
fun InvertPressButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    bordered: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    /** Smaller text for a toolbar with many controls. The touch target does not shrink. */
    compact: Boolean = false,
    /** Drawn in front of the word. */
    icon: LucideIcon? = null,
    /** For a control in a very narrow place: a smaller icon, and less space after it. */
    iconSize: Dp = 20.dp,
    iconGap: Dp = 10.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val inverted = (pressed || selected) && enabled

    val background = if (inverted) EinkColors.Ink else EinkColors.Paper
    val foreground = when {
        !enabled -> EinkColors.Faded
        inverted -> EinkColors.Paper
        else -> EinkColors.Ink
    }

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = EinkDimens.touchTarget)
            .clip(EinkShapes.control)
            .background(background)
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
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(iconGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) EinkIcon(icon = icon, size = iconSize, color = foreground)
        EinkText(
            text = text.uppercase(Locale.ROOT),
            style = (if (compact) EinkType.buttonCompact else EinkType.button).copy(color = foreground),
            maxLines = 1,
        )
    }
}
