package io.github.foxesrcool1.margin.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import io.github.foxesrcool1.margin.core.json.JsonObject
import io.github.foxesrcool1.margin.core.reading.BookAnnotations
import io.github.foxesrcool1.margin.core.reading.Highlight
import io.github.foxesrcool1.margin.core.settings.ReaderSettings
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkTheme
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.EinkText
import io.github.foxesrcool1.margin.support.captureTo
import io.github.foxesrcool1.margin.ui.reading.epub.ReaderActions
import io.github.foxesrcool1.margin.ui.reading.epub.ReaderChrome
import io.github.foxesrcool1.margin.ui.reading.epub.ReaderPanel
import io.github.foxesrcool1.margin.ui.reading.epub.ReaderUiState
import io.github.foxesrcool1.margin.ui.reading.epub.TocRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Public domain words only: the opening of Walden, by Henry David Thoreau, 1854. */
private const val WALDEN =
    "When I wrote the following pages, or rather the bulk of them, I lived alone, in the woods, " +
        "a mile from any neighbor, in a house which I had built myself, on the shore of Walden Pond, " +
        "in Concord, Massachusetts, and earned my living by the labor of my hands only. I lived there " +
        "two years and two months. At present I am a sojourner in civilized life again."

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "sw480dp-w480dp-h640dp-port-xxhdpi")
class ReaderChromeScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val nothing = object : ReaderActions {
        override fun toggleSplit() = Unit
        override fun close() = Unit
        override fun show(panel: ReaderPanel) = Unit
        override fun goTo(row: TocRow) = Unit
        override fun goTo(highlight: Highlight) = Unit
        override fun highlight() = Unit
        override fun highlightWithNote() = Unit
        override fun cancelSelection() = Unit
        override fun saveNote(highlightId: String, note: String) = Unit
        override fun handwrite(highlightId: String) = Unit
        override fun remove(highlightId: String) = Unit
        override fun change(settings: ReaderSettings) = Unit
        override fun exportNotes() = Unit
    }

    private fun state(panel: ReaderPanel) = ReaderUiState().apply {
        loading = false
        title = "Walden"
        placeLabel = "12%  .  position 31  .  Economy"
        secondsToday = 18 * 60
        this.panel = panel
        annotations = BookAnnotations(
            title = "Walden",
            highlights = listOf(
                Highlight("h1", JsonObject(emptyMap()), "I lived alone, in the woods, a mile from any neighbor", note = "Start here", progression = 0.01, chapter = "Economy"),
                Highlight("h2", JsonObject(emptyMap()), "earned my living by the labor of my hands only", progression = 0.012, chapter = "Economy"),
            ),
        )
    }

    private fun shoot(name: String, state: ReaderUiState) {
        compose.setContent {
            EinkTheme(botanicalArt = false) {
                Box(modifier = Modifier.fillMaxSize().background(EinkColors.Paper)) {
                    EinkText(text = "$WALDEN\n\n$WALDEN\n\n$WALDEN", style = EinkType.reading, modifier = Modifier.padding(28.dp))
                    ReaderChrome(state = state, actions = nothing)
                }
            }
        }
        compose.onRoot().captureTo(name)
    }

    @Test
    fun menu() = shoot("reader_menu", state(ReaderPanel.Menu))

    @Test
    fun textPanel() = shoot("reader_text_panel", state(ReaderPanel.Text))

    @Test
    fun notes() = shoot("reader_notes", state(ReaderPanel.Notes))

    @Test
    fun selection() = shoot("reader_selection", state(ReaderPanel.None).apply { selecting = true })
}
