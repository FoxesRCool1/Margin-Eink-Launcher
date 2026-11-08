package io.github.foxesrcool1.margin.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkType

/**
 * Asks before something cannot be undone.
 *
 * A black line around a white panel with round corners. No shadow, no dim behind it, no fade in.
 * The panel changes as few pixels as it can, which is e-ink rule 7.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "Yes",
    cancelText: String = "No",
) {
    EinkDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        ConfirmDialogContent(
            title = title,
            message = message,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            confirmText = confirmText,
            cancelText = cancelText,
            modifier = Modifier.widthIn(max = DialogMaxWidth).fillMaxWidth(0.86f),
        )
    }
}

/**
 * The panel inside the dialog window. Kept apart so a screenshot test can draw
 * it without a second window.
 */
@Composable
fun ConfirmDialogContent(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "Yes",
    cancelText: String = "No",
) {
    // The room the window really gives (not the screen size), so insets count too.
    BoxWithConstraints(modifier = modifier) {
        // A short window (landscape tablet, large font) gets shorter text, so
        // the buttons never fall off the bottom.
        val compact = maxHeight < 420.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight - 24.dp)
                .einkPanel()
                .padding(EinkDimens.blockGap),
        ) {
            // The text takes what the buttons leave and is cut there. The buttons
            // are measured first, so they are always whole and on screen.
            Column(modifier = Modifier.weight(1f, fill = false).clipToBounds()) {
                EinkText(
                    text = title,
                    style = EinkType.title,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(EinkDimens.targetGap))
                EinkText(
                    text = message,
                    style = EinkType.body,
                    maxLines = if (compact) 3 else 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(EinkDimens.blockGap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                InvertPressButton(text = cancelText, onClick = onDismiss)
                Spacer(modifier = Modifier.width(EinkDimens.targetGap))
                InvertPressButton(text = confirmText, onClick = onConfirm)
            }
        }
    }
}
