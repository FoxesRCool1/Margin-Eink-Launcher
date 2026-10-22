package io.github.foxesrcool1.margin.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkShapes
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.CapsLabel
import io.github.foxesrcool1.margin.design.components.EinkIcon
import io.github.foxesrcool1.margin.design.einkClickable
import io.github.foxesrcool1.margin.design.icons.Lucide
import io.github.foxesrcool1.margin.design.icons.LucideIcon

/** The tabs of Settings. The index of a tab is what `initialPage` of [SettingsScreen] means. */
enum class SettingsTab(val title: String, val icon: LucideIcon) {
    Screen("Screen", Lucide.Monitor),
    Pen("Pen", Lucide.PenLine),
    Files("Files", Lucide.Archive),
    About("About", Lucide.Info),
}

/**
 * The strip of tabs under the header: an icon with a small word under it. The
 * open tab is inverted, the others are plain. A tab is not a level, so Back
 * does not walk through them. Four of them fit in a half of the screen 320 dp
 * wide, and each is taller than the 56 dp of a touch target.
 *
 * [vertical] stands the tabs in a column, for a tablet on its side: the
 * strip would take a row of height it has none to spare, and a column takes
 * width, which it has.
 */
@Composable
fun SettingsTabs(
    tabs: List<SettingsTab>,
    selected: SettingsTab,
    onSelect: (SettingsTab) -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
) {
    if (vertical) {
        Column(modifier = modifier.width(88.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEach { tab ->
                TabButton(tab = tab, selected = tab == selected, onClick = { onSelect(tab) })
            }
        }
    } else {
        Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEach { tab ->
                TabButton(tab = tab, selected = tab == selected, onClick = { onSelect(tab) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TabButton(
    tab: SettingsTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val inverted = pressed || selected
    val foreground = if (inverted) EinkColors.Paper else EinkColors.Ink

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(EinkShapes.field)
            .background(if (inverted) EinkColors.Ink else EinkColors.Paper)
            .einkClickable(interactionSource = interactionSource, onClick = onClick)
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EinkIcon(icon = tab.icon, color = foreground)
        CapsLabel(text = tab.title, style = EinkType.capsSmall.copy(color = foreground))
    }
}
