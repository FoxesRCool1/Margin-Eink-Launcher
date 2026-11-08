package io.github.foxesrcool1.margin.ui.common

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.foxesrcool1.margin.design.EinkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "sw480dp-w480dp-h640dp-port-xxhdpi")
class ScreenScaffoldTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the title of a screen is a heading`() {
        compose.setContent {
            EinkTheme {
                ScreenScaffold(title = "Journal") {}
            }
        }

        compose.onNode(isHeading()).assertTextEquals("Journal")
    }
}
