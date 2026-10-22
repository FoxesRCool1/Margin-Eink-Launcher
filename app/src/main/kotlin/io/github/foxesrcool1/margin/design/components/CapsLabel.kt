package io.github.foxesrcool1.margin.design.components

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import io.github.foxesrcool1.margin.design.EinkType
import java.util.Locale

/**
 * A small label in capitals with wide tracking.
 *
 * Used for dates, status lines, section names and control text. The caller
 * passes normal words; this turns them into capitals, so the strings stay
 * readable in code and in the translation files.
 */
@Composable
fun CapsLabel(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = EinkType.caps,
    maxLines: Int = 1,
    autoSize: TextAutoSize? = null,
) {
    EinkText(
        text = text.uppercase(Locale.ROOT),
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        autoSize = autoSize,
    )
}

/**
 * One size for labels that sit side by side, each under an icon in a cell
 * [width] wide. With the tablet's text size turned up, or a long label in a
 * small cell, the type gets smaller before any label gets dots, and all of
 * them stay the same size.
 */
@Composable
fun rememberFittedCaps(labels: List<String>, width: Dp, style: TextStyle = EinkType.capsSmall): TextStyle {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(labels, width, style, density) {
        val room = with(density) { width.roundToPx() }
        var fitted = style
        while (fitted.fontSize > 6.sp &&
            labels.any { measurer.measure(it.uppercase(Locale.ROOT), fitted, maxLines = 1).size.width > room }
        ) {
            fitted = fitted.copy(fontSize = fitted.fontSize * 0.9f)
        }
        fitted
    }
}
