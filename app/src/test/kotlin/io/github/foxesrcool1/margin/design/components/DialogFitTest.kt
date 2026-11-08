package io.github.foxesrcool1.margin.design.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import io.github.foxesrcool1.margin.design.EinkTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "sw480dp-w800dp-h360dp-land-xxhdpi")
class DialogFitTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the buttons of a long confirm dialog stay on screen at a large font in a short window`() {
        val longTitle = "a".repeat(200)
        val longMessage = "word ".repeat(120)

        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 1.5f)) {
                EinkTheme {
                    ConfirmDialogContent(
                        title = longTitle,
                        message = longMessage,
                        onConfirm = {},
                        onDismiss = {},
                        modifier = Modifier.fillMaxWidth(0.86f),
                        confirmText = "Confirm",
                        cancelText = "Cancel",
                    )
                }
            }
        }
        compose.waitForIdle()

        val button = compose.onNodeWithText("Confirm", ignoreCase = true, useUnmergedTree = true).fetchSemanticsNode()
        val window = compose.onRoot().fetchSemanticsNode()
        assertTrue(
            "button bottom ${button.boundsInRoot.bottom} must be within window bottom ${window.boundsInRoot.bottom}",
            button.boundsInRoot.bottom <= window.boundsInRoot.bottom,
        )
    }
}
