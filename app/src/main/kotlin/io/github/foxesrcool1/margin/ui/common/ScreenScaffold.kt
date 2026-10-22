package io.github.foxesrcool1.margin.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import io.github.foxesrcool1.margin.core.window.ScreenWindow
import io.github.foxesrcool1.margin.core.window.findActivity
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.CapsLabel
import io.github.foxesrcool1.margin.design.components.EinkText
import io.github.foxesrcool1.margin.design.components.HairlineDivider
import io.github.foxesrcool1.margin.design.components.IconPressButton
import io.github.foxesrcool1.margin.design.components.PlantArt
import io.github.foxesrcool1.margin.design.icons.Lucide
import io.github.foxesrcool1.margin.design.icons.LucideIcon
import io.github.foxesrcool1.margin.ui.split.LocalPane
import io.github.foxesrcool1.margin.ui.split.LocalSplit
import io.github.foxesrcool1.margin.ui.split.PaneControls
import io.github.foxesrcool1.margin.ui.split.SplitButton

/**
 * True when the space a screen has is wider than it is tall: the tablet on
 * its side. A screen reads this to lay itself out, and not the orientation of
 * the device, because half of a split screen is tall even on a wide tablet.
 */
val LocalWideScreen = staticCompositionLocalOf { false }

/**
 * The shape every screen shares.
 *
 * One line at the top: the way back, the title, and the small icon that turns
 * the screen. Then a rule, then the content. Nothing moves and nothing
 * scrolls.
 *
 * [actions] are the controls of the screen. Upright, they get a line of their
 * own under the rule. On its side the tablet has width to spare and no height,
 * so they move up beside the title.
 *
 * In the second half of the split screen the same screen has less room. Its
 * margins shrink, the plant goes, the way back leads to the choice of pages,
 * and the controls of the split screen take the place of the icons that turn
 * and split the screen.
 */
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    plant: LucideIcon? = null,
    onBack: (() -> Unit)? = null,
    backIcon: LucideIcon = Lucide.House,
    backLabel: String = "Home",
    /** False on the device test, which is locked upright so a turn cannot wipe its results. */
    showRotate: Boolean = true,
    /** A shorter title for half a screen, where [title] would end in three dots. */
    shortTitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inPane = LocalPane.current != null
    val halfScreen = inPane || LocalSplit.current?.isOpen == true

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        val roomForPlant = maxWidth >= 440.dp
        // A narrow half takes the short title, where there is one. A wide
        // half keeps the long one, made smaller if it has to be.
        val shownTitle = if (halfScreen && shortTitle != null && maxWidth < 600.dp) shortTitle else title

        CompositionLocalProvider(LocalWideScreen provides wide) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(
                        horizontal = if (inPane) 12.dp else EinkDimens.screenMargin,
                        vertical = when {
                            inPane -> 8.dp
                            wide -> 16.dp
                            else -> EinkDimens.screenMargin
                        },
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        // Home is no place to go back to from a half of the
                        // screen. The same button leads to the choice of pages.
                        if (inPane && backIcon == Lucide.House) {
                            IconPressButton(icon = Lucide.ArrowLeft, label = "Back", onClick = onBack)
                        } else {
                            IconPressButton(icon = backIcon, label = backLabel, onClick = onBack)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    // A long title, like a date in the Journal, gets the smaller
                    // size and the room of the plant. A title that ends in three
                    // dots tells the user nothing.
                    val longTitle = shownTitle.length > LONG_TITLE
                    Column(modifier = Modifier.weight(1f)) {
                        if (overline != null) CapsLabel(text = overline, style = EinkType.capsSmall)
                        val titleStyle = if (wide || longTitle) {
                            EinkType.title.copy(fontSize = 26.sp, lineHeight = 36.sp)
                        } else {
                            EinkType.title
                        }
                        EinkText(
                            text = shownTitle,
                            style = titleStyle,
                            maxLines = 1,
                            // The top line holds more controls than it did,
                            // and half a screen has less room again. The type
                            // gets smaller before the title gets dots. A title
                            // that fits keeps its full size.
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 16.sp,
                                maxFontSize = titleStyle.fontSize,
                                stepSize = 2.sp,
                            ),
                        )
                    }
                    if (wide && actions != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            content = actions,
                        )
                    }
                    // A narrow half has no room for the plant beside the title.
                    if (!wide && !longTitle && plant != null && !inPane && roomForPlant) {
                        PlantArt(plant = plant, size = 56.dp)
                    }
                    when {
                        inPane -> PaneControls()
                        showRotate -> {
                            SplitButton()
                            RotateButton()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (wide) 6.dp else EinkDimens.targetGap))
                HairlineDivider()

                if (!wide && actions != null) {
                    Spacer(modifier = Modifier.height(EinkDimens.targetGap))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(EinkDimens.targetGap),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions,
                    )
                }

                Spacer(modifier = Modifier.height(if (wide) 8.dp else EinkDimens.targetGap))
                content()
            }
        }
    }
}

/** A title with more letters than this does not fit beside the plant at the full size. */
private const val LONG_TITLE = 12

/**
 * Turns the screen on its side, and back. Every screen has one at the top.
 * The owner asked for it to be small and easy to overlook, so it is grey, and
 * smaller than the other icons. The target is still 56 dp.
 */
@Composable
fun RotateButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    IconPressButton(
        icon = Lucide.RotateCwSquare,
        label = "Turn the screen",
        quiet = true,
        modifier = modifier,
        onClick = { context.findActivity()?.let(ScreenWindow::toggleLandscape) },
    )
}
