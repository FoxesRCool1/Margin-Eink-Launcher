package io.github.foxesrcool1.margin.design.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.github.foxesrcool1.margin.design.EinkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "sw480dp-w480dp-h640dp-port-xxhdpi")
class EmptyStateTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `an empty paged list shows its empty text`() {
        compose.setContent {
            EinkTheme {
                PagedList(
                    items = emptyList<String>(),
                    pageSize = 6,
                    emptyText = "Nothing here yet",
                    modifier = Modifier.fillMaxWidth().height(400.dp),
                    itemContent = { _, _ -> },
                )
            }
        }

        compose.onNodeWithText("Nothing here yet").assertIsDisplayed()
    }
}
