package io.github.foxesrcool1.margin.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkShapes

/**
 * A row in a list. It inverts while pressed and it can hold a long press.
 *
 * It uses `detectTapGestures` rather than `clickable` or `combinedClickable`,
 * so there is no ripple and no indication node at all, and the long press
 * comes free. The caller draws the content and gets the pressed state, so a
 * row can invert its own text.
 */
@Composable
fun EinkRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    minHeight: Dp = EinkDimens.touchTarget,
    role: Role = Role.Button,
    content: @Composable ColumnScope.(pressed: Boolean) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }

    // A new lambda arrives on every recomposition. If those lambdas were the
    // pointerInput keys, the gesture detector would be torn down and rebuilt
    // every time, and a press in flight would be lost. Key on Unit and read
    // the newest lambdas through these instead.
    val currentClick by rememberUpdatedState(onClick)
    val currentLongClick by rememberUpdatedState(onLongClick)
    val hasLongClick = onLongClick != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight)
            .clip(EinkShapes.field)
            .background(if (pressed) EinkColors.Ink else EinkColors.Paper)
            .semantics(mergeDescendants = true) {
                this.role = role
                this.onClick(action = {
                    currentClick()
                    true
                })
                if (hasLongClick) {
                    this.onLongClick(label = "Options", action = {
                        currentLongClick?.invoke()
                        true
                    })
                }
            }
            .pointerInput(hasLongClick) {
                // detectTapGestures wants ((Offset) -> Unit)?, so the parameter
                // type has to be written out. Passing null when there is no
                // long press keeps the normal tap fast: with a long press
                // handler the detector has to wait before it reports a tap.
                val longPress: ((Offset) -> Unit)? =
                    if (hasLongClick) {
                        { _: Offset -> currentLongClick?.invoke() }
                    } else {
                        null
                    }

                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { currentClick() },
                    onLongPress = longPress,
                )
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        content(pressed)
    }
}
