package io.github.foxesrcool1.margin.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkType

/** Asks for one line of text. Used to add a habit or rename a note. */
@Composable
fun TextPromptDialog(
    title: String,
    initialValue: String = "",
    confirmText: String = "Save",
    cancelText: String = "Cancel",
    /** True when an empty answer is a fine answer. */
    allowEmpty: Boolean = false,
    /** The starting text is selected, so typing replaces it. See [EinkTextField]. */
    selectAllOnOpen: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    val canConfirm = allowEmpty || value.isNotBlank()
    // The field takes the focus as the dialog opens, so the keyboard comes
    // at once. Without this every prompt cost one extra tap.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    EinkDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = DialogMaxWidth)
                .fillMaxWidth(0.86f)
                .einkPanel()
                .padding(EinkDimens.blockGap),
        ) {
            EinkText(text = title, style = EinkType.title, maxLines = 2)
            Spacer(modifier = Modifier.height(EinkDimens.targetGap))

            EinkTextField(
                value = value,
                onValueChange = { value = it },
                textStyle = EinkType.body,
                singleLine = true,
                selectAllOnOpen = selectAllOnOpen,
                // Enter is the same as the confirm button, for a keyboard user.
                onSubmit = { if (canConfirm) onConfirm(value.trim()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .defaultMinSize(minHeight = EinkDimens.touchTarget)
                    .einkFieldBorder()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )

            Spacer(modifier = Modifier.height(EinkDimens.blockGap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                InvertPressButton(text = cancelText, onClick = onDismiss)
                Spacer(modifier = Modifier.width(EinkDimens.targetGap))
                InvertPressButton(
                    text = confirmText,
                    enabled = canConfirm,
                    onClick = { onConfirm(value.trim()) },
                )
            }
        }
    }
}
