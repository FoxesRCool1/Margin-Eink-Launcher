package io.github.foxesrcool1.margin.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.icons.Lucide
import io.github.foxesrcool1.margin.design.icons.LucideIcon

/** One line in an [OptionsDialog]. */
data class DialogOption(
    val label: String,
    val enabled: Boolean = true,
    val icon: LucideIcon? = null,
    val onSelect: () -> Unit,
)

/** The widest a dialog gets. In landscape 86 % of the screen is far too wide to read. */
internal val DialogMaxWidth = 440.dp

/**
 * A short list of actions, shown after a long press, or the values of a
 * setting. [message] is a few plain lines above the list, for a choice that
 * needs them.
 */
@Composable
fun OptionsDialog(
    title: String,
    options: List<DialogOption>,
    onDismiss: () -> Unit,
    message: String? = null,
) {
    EinkDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        OptionsDialogContent(
            title = title,
            options = options,
            onDismiss = onDismiss,
            message = message,
            modifier = Modifier.widthIn(max = DialogMaxWidth).fillMaxWidth(0.86f),
        )
    }
}

@Composable
fun OptionsDialogContent(
    title: String,
    options: List<DialogOption>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    val panelMaxHeight = LocalConfiguration.current.screenHeightDp.dp - 24.dp
    Column(
        modifier = modifier
            .heightIn(max = panelMaxHeight)
            .einkPanel()
            .padding(EinkDimens.blockGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EinkText(text = title, style = EinkType.title, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconPressButton(icon = Lucide.X, label = "Close", onClick = onDismiss)
        }
        Spacer(modifier = Modifier.height(EinkDimens.targetGap))
        if (message != null) {
            EinkText(text = message, style = EinkType.body)
            Spacer(modifier = Modifier.height(EinkDimens.targetGap))
        }

        Column(modifier = Modifier.weight(1f, fill = false).clipToBounds()) {
            options.forEach { option ->
                EinkRow(onClick = { if (option.enabled) option.onSelect() }) { pressed ->
                    val colour = when {
                        !option.enabled -> EinkColors.Faded
                        pressed -> EinkColors.Paper
                        else -> EinkColors.Ink
                    }
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (option.icon != null) EinkIcon(icon = option.icon, color = colour)
                        EinkText(text = option.label, style = EinkType.rowTitle.copy(color = colour), maxLines = 1)
                    }
                }
            }
        }
    }
}
