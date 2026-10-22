package io.github.foxesrcool1.margin.design.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.icons.Lucide

/** Keeps the page index of a [PagedList] across recomposition and rotation. */
@Stable
class PagedListState(initialPage: Int = 0) {
    var page by mutableIntStateOf(initialPage)
        internal set

    fun goTo(page: Int, itemCount: Int, pageSize: Int) {
        this.page = Pagination.clampPage(page, itemCount, pageSize)
    }

    // Both step from the page that is showing, which is not always [page].
    // When the list gets shorter, say the last book of the last page is
    // deleted, [page] still holds the old number and the screen shows the
    // clamped one. Stepping from the old number made the first press of
    // Previous land on the page already showing, so it did nothing.
    fun next(itemCount: Int, pageSize: Int) =
        goTo(Pagination.clampPage(page, itemCount, pageSize) + 1, itemCount, pageSize)

    fun previous(itemCount: Int, pageSize: Int) =
        goTo(Pagination.clampPage(page, itemCount, pageSize) - 1, itemCount, pageSize)
}

@Composable
fun rememberPagedListState(initialPage: Int = 0): PagedListState =
    rememberSaveable(saver = PagedListStateSaver) { PagedListState(initialPage) }

private val PagedListStateSaver =
    androidx.compose.runtime.saveable.Saver<PagedListState, Int>(
        save = { it.page },
        restore = { PagedListState(it) },
    )

/**
 * One page of rows, with a way to the page before and the page after below.
 *
 * E-ink rule 2: paginate, do not scroll. A scroll repaints the whole panel on
 * every frame and leaves a grey smear. A page turn repaints once. A swipe left
 * or right turns exactly one page.
 *
 * Give [rowHeight] and the list works out by itself how many rows fit the
 * room it has, and every row is made exactly that tall. That is what a screen
 * wants: the same list has to fit the tablet upright, on its side, and in half
 * of a split screen, and a fixed count fits only one of those. A fixed
 * [pageSize] did overflow once: eight apps were asked for where six fit, and
 * the last two of every page could not be seen. [pageSize] alone is for rows
 * whose height is not known, and the caller then has to make sure they fit.
 *
 * A list that fits on one page shows no page controls at all.
 */
@Composable
fun <T> PagedList(
    items: List<T>,
    pageSize: Int,
    modifier: Modifier = Modifier,
    state: PagedListState = rememberPagedListState(),
    emptyText: String = "Nothing here yet",
    rowHeight: Dp? = null,
    itemContent: @Composable (index: Int, item: T) -> Unit,
) {
    // A row height is worked out for the normal text size. With a larger
    // text size from the tablet settings, the text in a row of that height
    // was cut off, so the rows grow with the text and fewer fit on a page.
    val fontScale = LocalDensity.current.fontScale
    val rowHeight = rowHeight?.let { if (fontScale > 1f) it * fontScale else it }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val size = if (rowHeight != null && constraints.hasBoundedHeight) {
            val fitsAlone = (maxHeight / rowHeight).toInt().coerceAtLeast(1)
            if (items.size <= fitsAlone) {
                fitsAlone
            } else {
                ((maxHeight - PagerHeight) / rowHeight).toInt().coerceAtLeast(1)
            }
        } else {
            pageSize
        }
        PagedListPage(items, size, state, emptyText, rowHeight, itemContent)
    }
}

/** The page controls: a 56 dp target and the gap above it. */
private val PagerHeight = EinkDimens.touchTarget + EinkDimens.targetGap

@Composable
private fun <T> PagedListPage(
    items: List<T>,
    pageSize: Int,
    state: PagedListState,
    emptyText: String,
    rowHeight: Dp?,
    itemContent: @Composable (index: Int, item: T) -> Unit,
) {
    val page = Pagination.clampPage(state.page, items.size, pageSize)
    val pageCount = Pagination.pageCount(items.size, pageSize)
    val firstIndex = Pagination.firstIndexOnPage(page, items.size, pageSize)
    val visible = Pagination.pageItems(items, page, pageSize)

    // One page turn per swipe. The threshold keeps a small drag from counting.
    val swipeThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    var dragTotal by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(items.size, pageSize) {
                detectHorizontalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = {
                        if (dragTotal <= -swipeThresholdPx) {
                            state.next(items.size, pageSize)
                        } else if (dragTotal >= swipeThresholdPx) {
                            state.previous(items.size, pageSize)
                        }
                        dragTotal = 0f
                    },
                    onDragCancel = { dragTotal = 0f },
                    onHorizontalDrag = { _, amount -> dragTotal += amount },
                )
            },
    ) {
        Column(modifier = Modifier.weight(1f, fill = true)) {
            if (visible.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CapsLabel(
                        text = emptyText,
                        style = EinkType.caps.copy(color = EinkColors.Faded, textAlign = TextAlign.Center),
                        maxLines = 3,
                    )
                }
            } else {
                visible.forEachIndexed { offset, item ->
                    if (rowHeight != null) {
                        Column(
                            modifier = Modifier.fillMaxWidth().height(rowHeight),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            itemContent(firstIndex + offset, item)
                        }
                    } else {
                        itemContent(firstIndex + offset, item)
                    }
                }
            }
        }

        if (pageCount > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = EinkDimens.targetGap),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconPressButton(
                    icon = Lucide.ChevronLeft,
                    label = "Previous page",
                    onClick = { state.previous(items.size, pageSize) },
                    enabled = Pagination.hasPrevious(page),
                    bordered = true,
                )
                CapsLabel(
                    text = "${page + 1} of $pageCount",
                    style = EinkType.capsSmall,
                )
                IconPressButton(
                    icon = Lucide.ChevronRight,
                    label = "Next page",
                    onClick = { state.next(items.size, pageSize) },
                    enabled = Pagination.hasNext(page, items.size, pageSize),
                    bordered = true,
                )
            }
        }
    }
}
