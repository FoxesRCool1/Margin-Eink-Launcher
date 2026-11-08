package io.github.foxesrcool1.margin.design.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import io.github.foxesrcool1.margin.design.EinkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "sw480dp-w480dp-h640dp-port-xxhdpi")
class EinkRowSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a row with only a click can be activated by TalkBack`() {
        var clicks = 0

        compose.setContent {
            EinkTheme {
                EinkRow(onClick = { clicks++ }) {
                    BasicText("Open")
                }
            }
        }

        compose.onNodeWithText("Open").assertHasClickAction()
        compose.onNodeWithText("Open").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun `a row with a long click runs it from TalkBack and not the click`() {
        var clicks = 0
        var longClicks = 0

        compose.setContent {
            EinkTheme {
                EinkRow(
                    onClick = { clicks++ },
                    onLongClick = { longClicks++ },
                ) {
                    BasicText("Book")
                }
            }
        }

        compose.onNodeWithText("Book").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.waitForIdle()
        assertEquals(1, longClicks)
        assertEquals(0, clicks)
    }
}
