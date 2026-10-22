package io.github.foxesrcool1.margin.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/** The text style a [io.github.foxesrcool1.margin.design.components.EinkText] uses when it gets none. */
val LocalEinkTextStyle = staticCompositionLocalOf { EinkType.body }

/** Section 5: the corner line art can be turned off by the user. */
val LocalBotanicalArtEnabled = staticCompositionLocalOf { true }

/**
 * Selected text lies on light grey, with black handles. Compose's default is
 * Android blue, which the panel shows as a muddy grey dither.
 */
private val SelectionColors = TextSelectionColors(
    handleColor = EinkColors.Ink,
    backgroundColor = EinkColors.Ink.copy(alpha = 0.25f),
)

/**
 * The root of every screen.
 *
 * It paints the page white and sets the default text style. It sets no
 * animation, no ripple and no elevation, because none of that exists in this
 * design system: there is nothing to turn off.
 */
@Composable
fun EinkTheme(
    botanicalArt: Boolean = true,
    textStyle: TextStyle = EinkType.body,
    /**
     * False for a layer that lies on top of something else, like the menus of
     * the EPUB reader over the book. With the paper painted there, the layer
     * is a white sheet over the whole page and the book cannot be seen. That
     * happened, and it took an emulator to find: no unit test looks through a
     * window.
     */
    paintPaper: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalEinkTextStyle provides textStyle,
        LocalBotanicalArtEnabled provides botanicalArt,
        LocalTextSelectionColors provides SelectionColors,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (paintPaper) Modifier.background(EinkColors.Paper) else Modifier),
        ) {
            content()
        }
    }
}

/**
 * A tap target with no ripple, no highlight and no animation.
 *
 * Compose draws a ripple by default. A ripple is an animation, so e-ink rule 1
 * forbids it. Every clickable surface in this app goes through here.
 */
@Composable
fun Modifier.einkClickable(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit,
): Modifier = this.clickable(
    interactionSource = interactionSource,
    indication = null,
    enabled = enabled,
    onClick = onClick,
)
