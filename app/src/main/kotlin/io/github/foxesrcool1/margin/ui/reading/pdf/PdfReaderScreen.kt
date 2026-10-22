package io.github.foxesrcool1.margin.ui.reading.pdf

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import io.github.foxesrcool1.margin.core.window.findActivity
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.CapsLabel
import io.github.foxesrcool1.margin.design.components.DialogOption
import io.github.foxesrcool1.margin.design.components.EinkDialog
import io.github.foxesrcool1.margin.design.components.EinkRow
import io.github.foxesrcool1.margin.design.components.EinkText
import io.github.foxesrcool1.margin.design.components.HairlineDivider
import io.github.foxesrcool1.margin.design.components.IconPressButton
import io.github.foxesrcool1.margin.design.components.InvertPressButton
import io.github.foxesrcool1.margin.design.components.OptionsDialog
import io.github.foxesrcool1.margin.design.components.PagedList
import io.github.foxesrcool1.margin.design.components.TextPromptDialog
import io.github.foxesrcool1.margin.design.icons.Lucide
import io.github.foxesrcool1.margin.ui.common.RotateButton
import io.github.foxesrcool1.margin.ui.ink.InkCanvasView
import io.github.foxesrcool1.margin.ui.ink.InkModeButtons
import io.github.foxesrcool1.margin.ui.ink.VerticalRule
import io.github.foxesrcool1.margin.ui.split.SplitButton
import io.github.foxesrcool1.margin.ui.ink.PenWidths

/**
 * The PDF reader: the tools, one screen of a page, and a status line.
 *
 * Upright, the tools are a row above the page. On its side the tablet has no
 * height to give away, so the tools stand in a rail down the left edge.
 *
 * In one half of the split screen the reader takes the shape that fits the
 * half: a row where the half is wide enough for one, a rail where it is tall
 * enough for one. The second half itself belongs to the activity around this.
 */
@Composable
fun PdfReaderScreen(state: PdfUiState, actions: PdfActions) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(EinkColors.Paper)
            .systemBarsPadding(),
    ) {
        // Seven tools of 56 dp stand in a rail of 400 dp, or in a row of 464
        // dp with the icon that turns the screen. Whichever the shape of the
        // room asks for, unless it does not fit and the other one does.
        val railFits = maxHeight >= 400.dp
        val rowFits = maxWidth >= 464.dp
        val rail = if (maxWidth > maxHeight) railFits || !rowFits else !rowFits && railFits

        val tools: @Composable () -> Unit = {
            IconPressButton(icon = Lucide.X, label = "Close the book", onClick = actions::close)
            InkModeButtons(mode = state.mode, onPick = actions::pick)
            IconPressButton(icon = Lucide.Undo2, label = "Undo", onClick = actions::undo)
            IconPressButton(icon = Lucide.ZoomIn, label = "Zoom: ${state.zoom.label}", onClick = actions::nextZoom)
            IconPressButton(icon = Lucide.Ellipsis, label = "More", onClick = { actions.show(PdfPanel.More) })
        }
        val status: @Composable (Modifier) -> Unit = { statusModifier ->
            CapsLabel(
                text = when {
                    state.loading -> "Opening"
                    state.notice != null -> state.notice.orEmpty()
                    else -> buildString {
                        append("Page ${state.pageIndex + 1} of ${state.pageCount}")
                        if (state.screenCount > 1) append("  .  part ${state.screenIndex + 1} of ${state.screenCount}")
                        append("  .  ${state.zoom.label}")
                        if (state.cropMargins) append("  .  cropped")
                    }
                },
                style = EinkType.capsSmall,
                modifier = statusModifier,
            )
        }
        val page: @Composable (Modifier) -> Unit = { pageModifier ->
            when {
                state.failure != null -> Message(state.failure.orEmpty(), pageModifier)
                else -> AndroidView(
                    modifier = pageModifier,
                    factory = { context -> InkCanvasView(context).also(actions::attach) },
                )
            }
        }

        if (rail) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxHeight().padding(4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) { tools() }
                VerticalRule()
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // The split button is on the top line, which is 56 dp
                    // tall anyway because of the button that turns the screen.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        status(Modifier.weight(1f))
                        SplitButton()
                        RotateButton()
                    }
                    HairlineDivider()
                    page(Modifier.fillMaxWidth().weight(1f))
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tools()
                    RotateButton()
                }
                HairlineDivider()
                page(Modifier.fillMaxWidth().weight(1f))
                HairlineDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    status(Modifier.weight(1f))
                    SplitButton()
                }
            }
        }
    }

    // The keyboard of "Go to page" must not shrink the window under the dialog.
    // The window getting shorter would turn the reader into its wide shape and
    // fit the page again while a number is typed, and on e-ink that is a full
    // repaint for nothing. The dialog is a window of its own and keeps clear of
    // the keyboard itself. Every other time the window resizes as before, for
    // the note beside the book.
    val window = LocalContext.current.findActivity()?.window
    val typing = state.panel == PdfPanel.GoTo
    DisposableEffect(window, typing) {
        val before = window?.attributes?.softInputMode
        if (typing && window != null && before != null) {
            val keepState = before and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()
            window.setSoftInputMode(keepState or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            onDispose { window.setSoftInputMode(before) }
        } else {
            onDispose { }
        }
    }

    when (state.panel) {
        PdfPanel.None -> Unit

        PdfPanel.More -> OptionsDialog(
            title = "Page ${state.pageIndex + 1} of ${state.pageCount}",
            onDismiss = { actions.show(PdfPanel.None) },
            options = listOf(
                DialogOption("Go to page", icon = Lucide.Search) { actions.show(PdfPanel.GoTo) },
                DialogOption(if (state.cropMargins) "Show the whole page" else "Crop the margins", icon = Lucide.Crop) { actions.toggleCrop() },
                DialogOption("Pen width: ${PenWidths.label(state.penWidth)}", icon = Lucide.PenLine) { actions.nextPenWidth() },
                DialogOption("Redo", icon = Lucide.Redo2) { actions.redo(); actions.show(PdfPanel.None) },
                DialogOption("Pages with handwriting", icon = Lucide.Signature) { actions.show(PdfPanel.InkPages) },
                DialogOption("Export this page as a picture", icon = Lucide.Image) { actions.exportPage() },
            ) + if (io.github.foxesrcool1.margin.core.eink.DevEnvironment.fingerDraws) {
                // Emulator only, where the mouse is both the finger and the pen.
                listOf(
                    DialogOption(
                        label = if (state.mouseDraws) "Mouse: turn pages" else "Mouse: draw",
                        icon = Lucide.MousePointer,
                    ) {
                        actions.toggleMouse()
                        actions.show(PdfPanel.None)
                    },
                )
            } else {
                emptyList()
            },
        )

        PdfPanel.GoTo -> TextPromptDialog(
            title = "Go to page, 1 to ${state.pageCount}",
            initialValue = (state.pageIndex + 1).toString(),
            confirmText = "Go",
            onConfirm = { typed -> actions.goToPage(typed.trim().toIntOrNull() ?: (state.pageIndex + 1)) },
            onDismiss = { actions.show(PdfPanel.None) },
        )

        PdfPanel.InkPages -> InkPagesDialog(state, actions)
    }
}

@Composable
private fun Message(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(EinkDimens.screenMargin)) {
        EinkText(text = text, style = EinkType.body)
    }
}

/** The annotation list of a PDF: every page that has ink, with a jump to it. */
@Composable
private fun InkPagesDialog(state: PdfUiState, actions: PdfActions) {
    EinkDialog(
        onDismissRequest = { actions.show(PdfPanel.None) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(EinkColors.Paper)
                .padding(EinkDimens.screenMargin),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EinkText(text = "Handwriting", style = EinkType.title, maxLines = 1, modifier = Modifier.weight(1f))
                IconPressButton(icon = Lucide.X, label = "Back to the page", onClick = { actions.show(PdfPanel.None) })
            }
            HairlineDivider()
            state.notice?.let {
                Spacer(modifier = Modifier.height(8.dp))
                CapsLabel(text = it, style = EinkType.capsSmall, maxLines = 2)
            }
            PagedList(
                items = state.inkPages,
                pageSize = 6,
                rowHeight = EinkDimens.rowTwoLines,
                emptyText = "No handwriting in this PDF yet",
                modifier = Modifier.weight(1f),
            ) { _, page ->
                EinkRow(onClick = { actions.goToPage(page.pageNumber) }) { pressed ->
                    val colour = if (pressed) EinkColors.Paper else EinkColors.Ink
                    EinkText(text = "Page ${page.pageNumber}", style = EinkType.rowTitle.copy(color = colour), maxLines = 1)
                    CapsLabel(
                        text = "${page.strokeCount} pen strokes",
                        style = EinkType.capsSmall.copy(color = if (pressed) EinkColors.Paper else EinkColors.Faded),
                    )
                }
                HairlineDivider(color = EinkColors.Faded)
            }
            Spacer(modifier = Modifier.height(EinkDimens.targetGap))
            InvertPressButton(
                text = "Export as Markdown",
                icon = Lucide.Share,
                enabled = state.inkPages.isNotEmpty(),
                onClick = actions::exportNotes,
            )
        }
    }
}
