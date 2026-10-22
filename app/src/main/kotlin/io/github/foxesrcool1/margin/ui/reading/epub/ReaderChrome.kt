package io.github.foxesrcool1.margin.ui.reading.epub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import io.github.foxesrcool1.margin.design.components.EinkTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.foxesrcool1.margin.core.reading.Highlight
import io.github.foxesrcool1.margin.core.reading.ReadingLog
import io.github.foxesrcool1.margin.core.reading.ReadingProgress
import io.github.foxesrcool1.margin.core.settings.ReaderSettings
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.CapsLabel
import io.github.foxesrcool1.margin.design.components.ConfirmDialog
import io.github.foxesrcool1.margin.design.components.EinkRow
import io.github.foxesrcool1.margin.design.components.EinkText
import io.github.foxesrcool1.margin.design.components.HairlineDivider
import io.github.foxesrcool1.margin.design.components.IconPressButton
import io.github.foxesrcool1.margin.design.components.einkFieldBorder
import io.github.foxesrcool1.margin.design.icons.Lucide
import io.github.foxesrcool1.margin.ui.common.RotateButton
import io.github.foxesrcool1.margin.design.components.PagedList

/**
 * Everything the reader draws on top of the page.
 *
 * When nothing is open this draws nothing at all, and the book view under it
 * gets every touch. The page is the whole screen.
 */
@Composable
fun ReaderChrome(state: ReaderUiState, actions: ReaderActions) {
    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        when {
            state.loading -> FullPanel(title = state.title.ifBlank { "Opening" }, onClose = actions::close, closeLabel = "Cancel") {
                EinkText(text = "Opening the book.", style = EinkType.body)
            }

            state.failure != null -> FullPanel(title = "Read", onClose = actions::close, closeLabel = "Back") {
                EinkText(text = state.failure.orEmpty(), style = EinkType.body)
            }

            state.selecting -> SelectionBar(actions, Modifier.align(Alignment.BottomCenter))

            else -> when (val panel = state.panel) {
                ReaderPanel.None -> Unit
                ReaderPanel.Menu -> MenuBar(state, actions, Modifier.align(Alignment.TopCenter))
                ReaderPanel.Contents -> ContentsPanel(state, actions)
                ReaderPanel.Notes -> NotesPanel(state, actions)
                ReaderPanel.Text -> TextPanel(state, actions, Modifier.align(Alignment.BottomCenter))
                is ReaderPanel.HighlightOptions -> HighlightPanel(state, panel.highlightId, actions, Modifier.align(Alignment.BottomCenter))
                is ReaderPanel.NoteEditor -> NoteEditorPanel(state, panel.highlightId, actions)
            }
        }
    }
}

@Composable
private fun MenuBar(state: ReaderUiState, actions: ReaderActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().background(EinkColors.Paper)) {
        // Five icons, because that is what fits the book when it has only
        // half of a tablet on its side. A tap on the page closes the menu.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconPressButton(icon = Lucide.X, label = "Close the book", onClick = actions::close)
            IconPressButton(icon = Lucide.TableOfContents, label = "Contents", onClick = { actions.show(ReaderPanel.Contents) })
            IconPressButton(icon = Lucide.MessageSquareText, label = "Notes and highlights", onClick = { actions.show(ReaderPanel.Notes) })
            IconPressButton(icon = Lucide.Type, label = "Text and reading settings", onClick = { actions.show(ReaderPanel.Text) })
            IconPressButton(
                icon = if (io.github.foxesrcool1.margin.ui.split.halvesSideBySide()) {
                    Lucide.SquareSplitHorizontal
                } else {
                    Lucide.SquareSplitVertical
                },
                label = if (state.split) "Close the split screen" else "Split screen",
                selected = state.split,
                onClick = actions::toggleSplit,
            )
        }
        Column(modifier = Modifier.padding(start = 16.dp).padding(bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    EinkText(text = state.title, style = EinkType.rowTitle, maxLines = 1)
                    CapsLabel(text = state.placeLabel, style = EinkType.capsSmall)
                }
                RotateButton()
            }
            if (state.settings.goalMinutes > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                CapsLabel(
                    text = "${state.secondsToday / 60} / ${state.settings.goalMinutes} min today",
                    style = EinkType.capsSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                GoalLine(
                    ReadingLog.goalFraction(state.secondsToday, state.settings.goalMinutes),
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        }
        HairlineDivider(thickness = EinkDimens.rule)
    }
}

/** The daily goal as a thin line: a hairline for the whole goal, a heavy rule for the part done. */
@Composable
fun GoalLine(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(4.dp)) {
        Box(modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth().height(1.dp).background(EinkColors.Ink))
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(EinkColors.Ink),
            )
        }
    }
}

@Composable
private fun SelectionBar(actions: ReaderActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().background(EinkColors.Paper)) {
        HairlineDivider(thickness = EinkDimens.rule)
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(EinkDimens.targetGap),
        ) {
            IconPressButton(icon = Lucide.Highlighter, label = "Highlight", bordered = true, onClick = actions::highlight)
            IconPressButton(icon = Lucide.MessageSquarePlus, label = "Highlight and add a note", bordered = true, onClick = actions::highlightWithNote)
            Spacer(modifier = Modifier.weight(1f))
            IconPressButton(icon = Lucide.X, label = "Cancel", onClick = actions::cancelSelection)
        }
    }
}

@Composable
private fun FullPanel(
    title: String,
    onClose: () -> Unit,
    closeLabel: String = "Back to the page",
    /** Controls beside the title, in front of the cross. */
    actions: @Composable () -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EinkColors.Paper)
            .padding(horizontal = EinkDimens.screenMargin, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EinkText(text = title, style = EinkType.title, maxLines = 1, modifier = Modifier.weight(1f))
            actions()
            IconPressButton(icon = Lucide.X, label = closeLabel, onClick = onClose)
        }
        HairlineDivider()
        Spacer(modifier = Modifier.height(EinkDimens.targetGap))
        Column(modifier = Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun ContentsPanel(state: ReaderUiState, actions: ReaderActions) {
    FullPanel(title = "Contents", onClose = { actions.show(ReaderPanel.None) }) {
        PagedList(
            items = state.contents,
            pageSize = 9,
            rowHeight = EinkDimens.rowOneLine,
            emptyText = "This book has no table of contents",
        ) { _, row ->
            EinkRow(onClick = { actions.goTo(row) }) { pressed ->
                EinkText(
                    text = row.title,
                    style = (if (row.depth == 0) EinkType.rowTitle else EinkType.body)
                        .copy(color = if (pressed) EinkColors.Paper else EinkColors.Ink),
                    maxLines = 1,
                    modifier = Modifier.padding(start = (row.depth * 20).coerceAtMost(60).dp),
                )
            }
            HairlineDivider(color = EinkColors.Faded)
        }
    }
}

@Composable
private fun NotesPanel(state: ReaderUiState, actions: ReaderActions) {
    FullPanel(
        title = "Notes",
        onClose = { actions.show(ReaderPanel.None) },
        actions = {
            IconPressButton(
                icon = Lucide.Share,
                label = "Export the notes as Markdown",
                enabled = state.annotations.highlights.isNotEmpty(),
                onClick = actions::exportNotes,
            )
        },
    ) {
        state.notice?.let {
            CapsLabel(text = it, style = EinkType.capsSmall, maxLines = 2)
            Spacer(modifier = Modifier.height(8.dp))
        }
        PagedList(
            items = state.annotations.highlights,
            pageSize = 5,
            rowHeight = NoteRowHeight,
            emptyText = "Hold a word, then press the marker",
        ) { _, highlight ->
            EinkRow(
                onClick = { actions.goTo(highlight) },
                onLongClick = { actions.show(ReaderPanel.HighlightOptions(highlight.id)) },
            ) { pressed ->
                val colour = if (pressed) EinkColors.Paper else EinkColors.Ink
                EinkText(text = highlight.text.ifBlank { "(no text)" }, style = EinkType.body.copy(color = colour), maxLines = 2)
                CapsLabel(
                    text = noteSummary(highlight),
                    style = EinkType.capsSmall.copy(color = if (pressed) EinkColors.Paper else EinkColors.Faded),
                )
            }
            HairlineDivider(color = EinkColors.Faded)
        }
    }
}

private fun noteSummary(highlight: Highlight): String = buildString {
    append("${ReadingProgress.percent(highlight.progression)}%")
    if (highlight.chapter.isNotBlank()) append("  .  ").append(highlight.chapter)
    if (highlight.note.isNotBlank()) append("  .  ").append(highlight.note.lineSequence().first())
    if (highlight.inkNotePath.isNotBlank()) append("  .  handwritten note")
}

@Composable
private fun HighlightPanel(state: ReaderUiState, highlightId: String, actions: ReaderActions, modifier: Modifier = Modifier) {
    val highlight = state.annotations.highlight(highlightId)
    var asking by remember(highlightId) { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth().background(EinkColors.Paper)) {
        HairlineDivider(thickness = EinkDimens.rule)
        Column(modifier = Modifier.padding(16.dp)) {
            EinkText(text = highlight?.text.orEmpty(), style = EinkType.body, maxLines = 3)
            if (!highlight?.note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                EinkText(text = "Note: ${highlight.note}", style = EinkType.body, maxLines = 4)
            }
            Spacer(modifier = Modifier.height(EinkDimens.targetGap))
            Row(horizontalArrangement = Arrangement.spacedBy(EinkDimens.targetGap)) {
                IconPressButton(
                    icon = Lucide.Keyboard,
                    label = if (highlight?.note.isNullOrBlank()) "Type a note" else "Edit the note",
                    bordered = true,
                    onClick = { actions.show(ReaderPanel.NoteEditor(highlightId)) },
                )
                IconPressButton(
                    icon = Lucide.Signature,
                    label = if (highlight?.inkNotePath.isNullOrBlank()) "Write a note by hand" else "Open the handwritten note",
                    bordered = true,
                    selected = !highlight?.inkNotePath.isNullOrBlank(),
                    onClick = { actions.handwrite(highlightId) },
                )
                IconPressButton(icon = Lucide.Trash2, label = "Remove the highlight", onClick = { asking = true })
                Spacer(modifier = Modifier.weight(1f))
                IconPressButton(icon = Lucide.X, label = "Close", onClick = { actions.show(ReaderPanel.None) })
            }
        }
    }
    if (asking) {
        val hasNote = !highlight?.note.isNullOrBlank() || !highlight?.inkNotePath.isNullOrBlank()
        ConfirmDialog(
            title = "Remove this highlight?",
            message = if (hasNote) "Its note goes too." else "This cannot be undone.",
            confirmText = "Remove",
            cancelText = "Keep",
            onConfirm = {
                asking = false
                actions.remove(highlightId)
            },
            onDismiss = { asking = false },
        )
    }
}

@Composable
private fun NoteEditorPanel(state: ReaderUiState, highlightId: String, actions: ReaderActions) {
    val highlight = state.annotations.highlight(highlightId)
    var text by remember(highlightId) { mutableStateOf(highlight?.note.orEmpty()) }
    // The activity keeps a draft on Back and on Pause, so it needs to see it.
    DisposableEffect(highlightId) { onDispose { state.noteDraft = null } }

    FullPanel(
        title = "Note",
        onClose = { actions.show(ReaderPanel.None) },
        closeLabel = "Cancel",
        actions = {
            IconPressButton(icon = Lucide.Check, label = "Save", bordered = true, onClick = { actions.saveNote(highlightId, text) })
        },
    ) {
        EinkText(text = highlight?.text.orEmpty(), style = EinkType.body, maxLines = 4)
        Spacer(modifier = Modifier.height(EinkDimens.targetGap))
        EinkTextField(
            value = text,
            onValueChange = {
                text = it
                state.noteDraft = it
            },
            textStyle = EinkType.body,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .einkFieldBorder()
                .padding(14.dp),
        )
    }
}

@Composable
private fun TextPanel(state: ReaderUiState, actions: ReaderActions, modifier: Modifier = Modifier) {
    val settings = state.settings
    // Two short pages and not one tall one, so half of the book page stays in
    // view and the reader can see what a change does.
    var readingPage by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().background(EinkColors.Paper)) {
        HairlineDivider(thickness = EinkDimens.rule)
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!readingPage) {
                SettingRow("Font", settings.fontLabel) { NextValue("Next font") { actions.change(settings.nextFont()) } }
                StepRow("Size", "${settings.fontSizePercent} %", { actions.change(settings.smaller()) }, { actions.change(settings.larger()) })
                StepRow("Margins", "${settings.marginPercent} %", { actions.change(settings.narrowerMargins()) }, { actions.change(settings.widerMargins()) })
                if (settings.font != ReaderSettings.FONT_PUBLISHER) {
                    StepRow("Line spacing", "${settings.lineHeightPercent} %", { actions.change(settings.tighterLines()) }, { actions.change(settings.looserLines()) })
                    SettingRow("Justify", if (settings.justify) "On" else "Off") {
                        NextValue("Justify on or off") { actions.change(settings.copy(justify = !settings.justify)) }
                    }
                }
            } else {
                SettingRow("Highlights", if (settings.underlineHighlights) "Underline" else "Grey block") {
                    NextValue("Next highlight style") { actions.change(settings.copy(underlineHighlights = !settings.underlineHighlights)) }
                }
                SettingRow("Full refresh", if (settings.refreshEveryPages == 0) "Never" else "Every ${settings.refreshEveryPages} pages") {
                    NextValue("Next refresh setting") { actions.change(settings.nextRefresh()) }
                }
                SettingRow("Daily goal", if (settings.goalMinutes == 0) "None" else "${settings.goalMinutes} minutes") {
                    NextValue("Next daily goal") { actions.change(settings.nextGoal()) }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EinkDimens.targetGap)) {
                IconPressButton(icon = Lucide.Type, label = "Type settings", selected = !readingPage, onClick = { readingPage = false })
                IconPressButton(icon = Lucide.BookOpen, label = "Reading settings", selected = readingPage, onClick = { readingPage = true })
                Spacer(modifier = Modifier.weight(1f))
                IconPressButton(icon = Lucide.X, label = "Back to the page", onClick = { actions.show(ReaderPanel.None) })
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String, controls: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            CapsLabel(text = label, style = EinkType.capsSmall)
            EinkText(text = value, style = EinkType.body, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { controls() }
    }
}

@Composable
private fun StepRow(label: String, value: String, onLess: () -> Unit, onMore: () -> Unit) {
    SettingRow(label, value) {
        IconPressButton(icon = Lucide.Minus, label = "Less $label", bordered = true, onClick = onLess)
        IconPressButton(icon = Lucide.Plus, label = "More $label", bordered = true, onClick = onMore)
    }
}

/** A setting with a few values in a ring: one press goes to the next one. */
@Composable
private fun NextValue(label: String, onClick: () -> Unit) {
    IconPressButton(icon = Lucide.ChevronRight, label = label, bordered = true, onClick = onClick)
}

/** A highlight in the notes list: two lines of the text, one small line, and the rule. */
private val NoteRowHeight = 96.dp
