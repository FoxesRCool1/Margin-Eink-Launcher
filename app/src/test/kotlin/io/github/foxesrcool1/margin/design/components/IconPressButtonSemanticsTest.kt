package io.github.foxesrcool1.margin.design.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import io.github.foxesrcool1.margin.design.EinkTheme
import io.github.foxesrcool1.margin.design.icons.Lucide
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "sw480dp-w480dp-h640dp-port-xxhdpi")
class IconPressButtonSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a selected icon button is published as selected and an ordinary one is not`() {
        compose.setContent {
            EinkTheme {
                IconPressButton(icon = Lucide.Eraser, label = "Eraser", selected = true, onClick = {})
                IconPressButton(icon = Lucide.PenLine, label = "Pen", selected = false, onClick = {})
            }
        }
        compose.onNodeWithContentDescription("Eraser").assertIsSelected()
        // A button that is not selected publishes no Selected key at all, so a
        // plain button is not announced as "not selected".
        compose.onNodeWithContentDescription("Pen")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }
}
