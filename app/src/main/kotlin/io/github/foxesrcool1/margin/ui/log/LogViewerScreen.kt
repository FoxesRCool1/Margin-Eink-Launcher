package io.github.foxesrcool1.margin.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.log.LogLine
import io.github.foxesrcool1.margin.core.threads.AppDispatchers
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.CapsLabel
import io.github.foxesrcool1.margin.design.components.EinkText
import io.github.foxesrcool1.margin.design.components.HairlineDivider
import io.github.foxesrcool1.margin.design.components.IconPressButton
import io.github.foxesrcool1.margin.design.icons.Lucide
import io.github.foxesrcool1.margin.design.components.PagedList
import io.github.foxesrcool1.margin.design.components.rememberPagedListState
import io.github.foxesrcool1.margin.ui.common.ScreenScaffold
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The in-app log viewer.
 *
 * There is no logcat on this tablet, so this screen is the only way the owner
 * can see what the app did. "Copy to Download" writes every log file into
 * `Download/Margin/`, where a file manager can reach it.
 */
@Composable
fun LogViewerScreen(
    modifier: Modifier = Modifier,
    linesProvider: () -> List<LogLine> = { AppLog.recentLines() },
    onCopyToDownloads: (() -> Int)? = null,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshToken by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var copying by remember { mutableStateOf(false) }

    val lines = remember(refreshToken) { linesProvider().asReversed() }
    val state = rememberPagedListState()

    ScreenScaffold(
        title = "Log",
        overline = "Newest first",
        modifier = modifier,
        onBack = onBack,
        backIcon = Lucide.ArrowLeft,
        backLabel = "Back",
        actions = {
            IconPressButton(icon = Lucide.RefreshCw, label = "Read the log again", onClick = { refreshToken++ })
            IconPressButton(
                icon = Lucide.Download,
                label = "Copy the log files to the Download folder",
                bordered = true,
                enabled = !copying,
                onClick = {
                    // The copy waits for the log writer and then writes files,
                    // so it runs off the main thread. It froze the screen for
                    // up to two seconds.
                    copying = true
                    status = "Copying"
                    scope.launch {
                        val copied = withContext(AppDispatchers.io) {
                            onCopyToDownloads?.invoke() ?: AppLog.copyAllToDownloads(context)
                        }
                        status = when (copied) {
                            0 -> "Nothing copied"
                            else -> "Copied to Download/Margin"
                        }
                        copying = false
                        refreshToken++
                    }
                },
            )
        },
    ) {
        if (status != null) {
            CapsLabel(text = status.orEmpty(), style = EinkType.capsSmall)
            Spacer(modifier = Modifier.height(EinkDimens.targetGap))
        }

        PagedList(
            items = lines,
            pageSize = 5,
            rowHeight = LogRowHeight,
            state = state,
            emptyText = "No log lines",
            modifier = Modifier.weight(1f),
        ) { _, line ->
            LogRow(line)
        }
    }
}

/** The level and the tag, two lines of the message, and the rule. */
private val LogRowHeight = 70.dp

@Composable
private fun LogRow(line: LogLine) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CapsLabel(
            text = "${line.level.short}  ${line.tag}",
            style = EinkType.capsSmall.copy(
                color = if (line.level.short == 'E') EinkColors.Ink else EinkColors.Faded,
            ),
        )
        EinkText(
            text = line.message,
            style = EinkType.help,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        HairlineDivider(color = EinkColors.Faded)
    }
}
