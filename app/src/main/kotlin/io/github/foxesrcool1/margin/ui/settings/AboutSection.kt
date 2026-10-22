package io.github.foxesrcool1.margin.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.EinkText
import io.github.foxesrcool1.margin.design.components.HairlineDivider
import io.github.foxesrcool1.margin.design.components.PagedList

/** A name, three lines of detail in a half of the screen, and the rule. */
private val CreditRowHeight = 104.dp

/** One line of the credits: what it is, and under which licence it is used. */
data class Credit(val name: String, val detail: String)

/**
 * Who and what this app stands on. Kept next to `ASSETS.md` and
 * `licenses/DEPENDENCIES.md`: a new dependency or asset gets a line in all
 * three, in the same change.
 */
object Credits {
    val all: List<Credit> = listOf(
        Credit("Margin", "Free and open source. Apache License 2.0."),
        Credit("Idea", "\"Prose: The distraction-free, e-ink laptop that should exist\" by Micah Daigle. CC BY-SA 4.0."),
        Credit("ViWoods facts", "Notes from jdkruzr/ViwoodsAppDev. No code copied."),
        Credit("Readium Toolkit", "BSD 3-Clause. Copyright Readium Foundation."),
        Credit("AndroidX, Compose, Kotlin", "Apache License 2.0. Google and JetBrains."),
        Credit("Guava, Okio, Timber", "And koi, JSpecify. Apache License 2.0."),
        Credit("jsoup", "MIT License. Jonathan Hedley."),
        Credit("Fonts", "Bodoni Moda, Jost and Literata. SIL Open Font License 1.1."),
        Credit("Lucide", "ISC License, Lucide Icons and Contributors. Some icons from Feather: MIT License, Cole Bemis."),
    )
}

@Composable
fun AboutSection(modifier: Modifier = Modifier) {
    EinkText(text = "No data is collected. Online only to check for updates.", style = EinkType.body)
    Spacer(modifier = Modifier.height(10.dp))
    // Every credit gets the same room, and the room is enough for the longest
    // one in full: a credit that is cut off in the middle is not a credit.
    PagedList(items = Credits.all, pageSize = 2, rowHeight = CreditRowHeight, modifier = modifier) { _, credit ->
        EinkText(text = credit.name, style = EinkType.rowTitle, maxLines = 1)
        EinkText(text = credit.detail, style = EinkType.help, maxLines = 3, modifier = Modifier.padding(bottom = 6.dp))
        HairlineDivider(color = EinkColors.Faded)
    }
}
