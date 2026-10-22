package io.github.foxesrcool1.margin.design.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkType
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A text field with a cursor that does not blink.
 *
 * The platform cursor blinks twice a second. On e-ink each blink is a repaint
 * of a small box, for ever, for as long as the field has the focus. That is an
 * animation, and it also wears a grey mark into the panel. So the real cursor
 * is made invisible, and a plain black line is drawn where it stands. The line
 * only moves when the cursor does.
 *
 * Every text field in the app goes through this. Do not call `BasicTextField`
 * directly in a user screen.
 */
@Composable
fun EinkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = EinkType.body,
    singleLine: Boolean = false,
    enabled: Boolean = true,
    /**
     * What the Enter key does in a field of one line: the key on a Bluetooth
     * keyboard, and the Done key on the screen keyboard. Null leaves Enter
     * doing nothing, as before.
     */
    onSubmit: (() -> Unit)? = null,
    /**
     * Turns a field of many lines into pages. See [TextPages]. A field of one
     * line has no pages. Without this the field still rests on whole pages,
     * it only does not tell anyone.
     */
    pages: TextPages? = null,
    /**
     * Selects all of the starting text, so the first key typed replaces it.
     * For a field that opens with a value in it, like the page number in
     * "Go to page". Without this the cursor starts at the end.
     */
    selectAllOnOpen: Boolean = false,
) {
    val state = rememberTextFieldState(
        initialText = value,
        initialSelection = TextRange(if (selectAllOnOpen) 0 else value.length, value.length),
    )
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestOnSubmit by rememberUpdatedState(onSubmit)
    val reported = remember { ReportedValues() }

    // From outside in: a note that finished loading, or a field that was reset.
    LaunchedEffect(value) {
        // A value this field reported itself is only the caller catching up.
        // The field may be a letter or two ahead of it by now, and pushing the
        // older text back in would eat those letters.
        val echo = reported.isEcho(value)
        if (echo || state.text.toString() == value) return@LaunchedEffect
        reported.clear()
        state.setTextAndPlaceCursorAtEnd(value)
    }
    // From inside out: what the user typed.
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { typed ->
            if (typed != latestValue) {
                reported.record(typed)
                latestOnChange(typed)
            }
        }
    }

    val scroll: ScrollState = rememberScrollState()
    var focused by remember { mutableStateOf(false) }
    var layout by remember { mutableStateOf<(() -> TextLayoutResult?)?>(null) }
    val cursorWidth = 2.dp

    // Rule 2: a field of many lines does not rest between pages.
    val padLines = remember { mutableIntStateOf(0) }
    // How far down the text area the whole lines of this page reach. What is
    // under it is a part of a line, and it is not painted. Infinity: no clip.
    var wholeLines by remember { mutableStateOf(Float.POSITIVE_INFINITY) }
    val padding = remember {
        OutputTransformation { if (padLines.intValue > 0) append("\n".repeat(padLines.intValue)) }
    }
    if (!singleLine) {
        val book = pages ?: remember { TextPages() }
        LaunchedEffect(scroll, book) {
            book.turn = { page -> book.grid?.let { scroll.dispatchRawDelta((it.offset(page) - scroll.value).toFloat()) } }
            snapshotFlow { PageView(scroll.value, scroll.viewportSize, layout?.invoke()) }.collect { view ->
                val result = view.layout ?: return@collect
                if (view.viewport <= 0) return@collect
                // The first line is a little shorter than the rest: it has no space above it.
                val second = if (result.lineCount > 1) 1 else 0
                val line = result.getLineBottom(second) - result.getLineTop(second)
                if (line <= 0f) return@collect
                val perPage = (view.viewport / line).toInt()
                val textLines = (result.lineCount - padLines.intValue).coerceAtLeast(1)
                val fits = result.getLineBottom((textLines - 1).coerceAtMost(result.lineCount - 1)) <= view.viewport + 1
                val grid = PageGrid(line, perPage, textLines, fits) { i ->
                    if (i < result.lineCount) result.getLineTop(i) else result.getLineBottom(result.lineCount - 1) + (i - result.lineCount) * line
                }
                book.grid = grid
                // Room under the text, so that the last page can start at the
                // cursor and not at the bottom of the box.
                padLines.intValue = if (fits) 0 else perPage

                val here = view.value
                book.at = here
                if (grid.isPage(here)) {
                    book.index = grid.pageAt(here)
                    wholeLines = if (fits) Float.POSITIVE_INFINITY else grid.shown(book.index)
                    return@collect
                }
                val selection = state.selection
                val box = result.getCursorRect(selection.end.coerceIn(0, result.layoutInput.text.length))
                val follows = selection.collapsed &&
                    (abs(box.bottom - (here + view.viewport)) < line / 2 || abs(box.top - here) < line / 2)
                val page = if (follows) {
                    grid.pageOfLine(result.getLineForOffset(selection.end.coerceAtMost(result.layoutInput.text.length)))
                } else {
                    grid.nearest(here)
                }
                book.index = page
                scroll.dispatchRawDelta((grid.offset(page) - here).toFloat())
            }
        }
    }

    BasicTextField(
        state = state,
        enabled = enabled,
        textStyle = textStyle,
        lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.Default,
        keyboardOptions = if (singleLine && onSubmit != null) {
            KeyboardOptions(imeAction = ImeAction.Done)
        } else {
            KeyboardOptions.Default
        },
        onKeyboardAction = if (onSubmit != null) KeyboardActionHandler { latestOnSubmit?.invoke() } else null,
        cursorBrush = SolidColor(Color.Transparent),
        scrollState = scroll,
        outputTransformation = if (singleLine) null else padding,
        onTextLayout = { getResult -> layout = getResult },
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .drawWithContent {
                clipRect(bottom = wholeLines.coerceAtMost(size.height)) { this@drawWithContent.drawContent() }
                val selection = state.selection
                val result = layout?.invoke()
                if (focused && enabled && selection.collapsed && result != null) {
                    val offset = selection.start.coerceIn(0, result.layoutInput.text.length)
                    val box = result.getCursorRect(offset)
                    // The field scrolls its text when it is longer than the
                    // box. The line has to move with the text.
                    val dx = if (singleLine) scroll.value.toFloat() else 0f
                    val dy = if (singleLine) 0f else scroll.value.toFloat()
                    val x = box.left - dx
                    val top = box.top - dy
                    val bottom = box.bottom - dy
                    if (bottom > 0f && top < size.height && x >= -1f && x <= size.width + 1f) {
                        drawLine(
                            color = EinkColors.Ink,
                            start = Offset(x, top),
                            end = Offset(x, bottom),
                            strokeWidth = cursorWidth.toPx(),
                        )
                    }
                }
            },
    )
}

/**
 * The texts a field has reported and not yet seen come back.
 *
 * The field tells the caller what was typed, and the caller hands the same
 * text back as the new value a moment later. If the user types in that
 * moment, the text that comes back is already old. This is how the field
 * tells that echo apart from a value the caller really set from outside.
 */
internal class ReportedValues(private val limit: Int = 32) {

    private val waiting = ArrayDeque<String>()

    fun record(text: String) {
        waiting.addLast(text)
        while (waiting.size > limit) waiting.removeFirst()
    }

    /**
     * True when [value] is one this field reported. It is forgotten then, and
     * so is every older one: the caller may skip values, it never goes back.
     */
    fun isEcho(value: String): Boolean {
        val index = waiting.indexOf(value)
        if (index < 0) return false
        repeat(index + 1) { waiting.removeFirst() }
        return true
    }

    fun clear() = waiting.clear()
}


/**
 * The pages of a field of many lines, for a screen that wants to show them.
 *
 * Pass one to [EinkTextField] and read [index] and [count] from it. The field
 * keeps them up to date. [next] and [previous] turn a page and leave the
 * cursor where it is.
 */
@Stable
class TextPages {

    /** The page on show, counted from 0. */
    var index by mutableIntStateOf(0)
        internal set

    /** Where the text rests now, in pixels. For the tests. */
    internal var at by mutableIntStateOf(0)

    internal var grid by mutableStateOf<PageGrid?>(null)

    internal var turn: (Int) -> Unit = {}

    /** How many pages the text fills. 1 when it all fits. */
    val count: Int get() = grid?.count ?: 1

    fun next() = turn((index + 1).coerceAtMost(count - 1))

    fun previous() = turn((index - 1).coerceAtLeast(0))
}

internal class PageView(val value: Int, val viewport: Int, val layout: TextLayoutResult?)

/**
 * Where the pages of a text lie, in whole lines. A page is [perPage] lines
 * tall and the next one starts two lines before the end of it, so the line at
 * the bottom of one page is near the top of the next.
 */
internal class PageGrid(
    private val line: Float,
    private val perPage: Int,
    textLines: Int,
    /** All the text is on show at once. */
    fits: Boolean,
    /** Where line [i] starts, in pixels from the top of the text. */
    private val lineTop: (Int) -> Float,
) {

    /** Lines from the top of one page to the top of the next. */
    private val step = (perPage - 2).coerceAtLeast(1)

    val count: Int = if (fits) 1 else (textLines - 1) / step + 1

    /** Where page [page] starts, in pixels of scroll. */
    fun offset(page: Int): Int = lineTop(page.coerceIn(0, count - 1) * step).roundToInt()

    /** How far below the top of page [page] its last whole line ends. */
    fun shown(page: Int): Float = lineTop(page.coerceIn(0, count - 1) * step + perPage) - offset(page)

    fun pageOfLine(lineIndex: Int): Int = (lineIndex / step).coerceIn(0, count - 1)

    fun nearest(value: Int): Int = (value / (step * line)).roundToInt().coerceIn(0, count - 1)

    fun pageAt(value: Int): Int = nearest(value)

    fun isPage(value: Int): Boolean = offset(nearest(value)) == value
}
