package io.github.foxesrcool1.margin.ui.split

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.foxesrcool1.margin.DemoActivity
import io.github.foxesrcool1.margin.core.books.LibraryBook
import io.github.foxesrcool1.margin.core.ink.PageTemplate
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.settings.SettingsStore
import io.github.foxesrcool1.margin.core.window.findActivity
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkShapes
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.CapsLabel
import io.github.foxesrcool1.margin.design.components.EinkIcon
import io.github.foxesrcool1.margin.design.components.HairlineDivider
import io.github.foxesrcool1.margin.design.components.IconPressButton
import io.github.foxesrcool1.margin.design.einkClickable
import io.github.foxesrcool1.margin.design.icons.Lucide
import io.github.foxesrcool1.margin.design.icons.LucideIcon
import io.github.foxesrcool1.margin.ui.apps.AppsScreen
import io.github.foxesrcool1.margin.ui.apps.LauncherEntry
import io.github.foxesrcool1.margin.ui.home.LauncherRoute
import io.github.foxesrcool1.margin.ui.home.icon
import io.github.foxesrcool1.margin.ui.ink.InkCanvasView
import io.github.foxesrcool1.margin.ui.journal.JournalScreen
import io.github.foxesrcool1.margin.ui.log.LogViewerScreen
import io.github.foxesrcool1.margin.ui.reading.ReadingScreen
import io.github.foxesrcool1.margin.ui.settings.SettingsScreen
import io.github.foxesrcool1.margin.ui.update.UpdateScreen
import io.github.foxesrcool1.margin.ui.writing.NoteEditorScreen
import io.github.foxesrcool1.margin.ui.writing.WritingScreen

private const val TAG = "SplitPane"

/** What the second half needs from the screen around it. */
interface PaneHost {
    /** The book in the main half, for the choice "notes of this book". Null when the main half is no book. */
    val bookTitle: String? get() = null

    /** A book was chosen in the second half. See [openBookBeside]. */
    fun openBook(book: LibraryBook)

    /** The handwriting canvas the second half shows, or null, so the screen can point the fast pen at it. */
    fun paneInk(canvas: InkCanvasView?)

    /**
     * The main half again, as a screen of its own, for a task of its own.
     * Android puts another app beside that, and never beside the task of the
     * home screen. See [AdjacentApps]. Null where there is nothing to move.
     */
    fun keeper(): Intent? = null

    /** Lets go of the main half after a new screen took it over: Home goes back to Home, any other screen closes. */
    fun leave() {}
}

/**
 * The second half of the split screen: whatever page [split] says.
 *
 * Every page here is the same page as on a screen of its own. Only three
 * things differ. Its way back leads to the choice of pages and not to Home.
 * The controls of the split screen stand where the icon that turns the screen
 * would be. And a note opens right here, through [PaneOpener].
 */
@Composable
fun SplitPane(split: SplitState, host: PaneHost, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val opener = remember(split, host, context) { PaneOpener(split, host, context) }

    CompositionLocalProvider(LocalPageOpener provides opener) {
        Column(modifier = modifier.fillMaxSize().background(EinkColors.Paper)) {
            split.notice?.let {
                CapsLabel(
                    text = it,
                    style = EinkType.capsSmall,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
                HairlineDivider(color = EinkColors.Faded)
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // A page of its own each time, so nothing one page held can
                // leak into the next one: two notes one after the other are
                // two editors, and the first one saves as it goes.
                key(split.page) { PageInPane(split.page, split, host) }
            }
        }
    }
}

@Composable
private fun PageInPane(page: PanePage, split: SplitState, host: PaneHost) {
    val context = LocalContext.current
    when (page) {
        PanePage.Choose -> ChoosePage(split, host.bookTitle)

        is PanePage.BookNotes -> NotePane(bookTitle = page.bookTitle, onInkCanvas = host::paneInk, onBack = split::back)

        is PanePage.TypedNote -> NoteEditorScreen(notePath = page.path, onClose = split::back)

        is PanePage.InkNote -> Column(modifier = Modifier.fillMaxSize()) {
            InkPane(
                path = page.path,
                templateForNew = page.template,
                leading = { BackButton(split::back) },
                onInkCanvas = host::paneInk,
            )
        }

        is PanePage.Tab -> when (page.route) {
            LauncherRoute.Home -> ChoosePage(split, host.bookTitle)
            LauncherRoute.Reading -> ReadingScreen(onBack = split::home)
            LauncherRoute.Writing -> WritingScreen(
                onBack = split::home,
                onOpenNote = { split.show(PanePage.TypedNote(it)) },
                onOpenInkNote = { path, title -> split.show(PanePage.InkNote(path, title)) },
            )
            LauncherRoute.Journal -> JournalScreen(onBack = split::home)
            LauncherRoute.Apps -> AppsScreen(onBack = split::home)
            LauncherRoute.Settings -> SettingsScreen(
                onBack = split::home,
                onOpenLog = { split.show(PanePage.Tab(LauncherRoute.Log)) },
                onOpenDemo = {
                    runCatching { context.startActivity(Intent(context, DemoActivity::class.java)) }
                        .onFailure { AppLog.e(TAG, "Could not open the demo screen", it) }
                },
                onOpenUpdates = { split.show(PanePage.Tab(LauncherRoute.Update)) },
            )
            LauncherRoute.Log -> LogViewerScreen(onBack = split::back)
            LauncherRoute.Update -> UpdateScreen(onBack = split::back)
        }
    }
}

/** A note opens in the second half itself, a book in a screen of its own, an app beside this one. */
private class PaneOpener(
    private val split: SplitState,
    private val host: PaneHost,
    private val context: android.content.Context,
) : PageOpener {
    // [createdWith] only matters for a screen of its own: the quick note is made on Home.
    override fun openTypedNote(path: String, createdWith: String?) {
        split.show(PanePage.TypedNote(path))
    }

    override fun openInkNote(path: String, title: String, template: PageTemplate) {
        split.show(PanePage.InkNote(path, title, template))
    }

    override fun openBook(book: LibraryBook) = host.openBook(book)

    override fun openApp(entry: LauncherEntry): Boolean = AdjacentApps.open(context.findActivity(), entry, host)
}

/**
 * Opens a book chosen in the second half.
 *
 * A book always gets a screen of its own, so this starts one, and the page
 * that stood beside the second half goes into the second half of the new
 * screen. The book lands where the user tapped it: the sides swap. When the
 * main half held a book, or the Home screen, which cannot stand in a half,
 * the new book takes the main half instead and the second half keeps what it
 * showed, which is the library the book was chosen from.
 *
 * [PaneHost.leave] runs after the new screen was asked for. A screen that
 * handed its main page over finishes there, so it is not open twice.
 */
fun openBookBeside(
    activity: android.app.Activity,
    split: SplitState,
    book: LibraryBook,
    host: PaneHost,
) {
    val main = split.main()
    val carry = if (main != null) SplitCarry(main, swapped = !split.swapped) else split.carry()
    AppLog.i(TAG, "Opening ${book.path} from the second half, taking $carry along")
    runCatching {
        activity.startActivity(bookIntent(activity, book).withSplit(carry))
        host.leave()
    }.onFailure { AppLog.e(TAG, "Could not open ${book.path}", it) }
}

/**
 * The start of the second half: the pages that can stand there, as large
 * icons, like the tabs on Home. The names under them follow the same setting
 * as on Home.
 */
@Composable
internal fun ChoosePage(split: SplitState, bookTitle: String?) {
    val context = LocalContext.current
    val showLabels by remember(context) { SettingsStore(context).homeLabels }
        .collectAsStateWithLifecycle(initialValue = false)

    val choices = buildList {
        if (bookTitle != null) add(Choice(PanePage.BookNotes(bookTitle), Lucide.NotebookPen, "Notes on this book"))
        LauncherRoute.tabs.forEach { add(Choice(PanePage.Tab(it), it.icon(), it.title)) }
        add(Choice(PanePage.Tab(LauncherRoute.Settings), Lucide.Settings, LauncherRoute.Settings.title))
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CapsLabel(
                text = if (LocalPane.current != null) "Beside this" else "Pages",
                style = EinkType.capsSmall,
                modifier = Modifier.weight(1f),
            )
            PaneControls()
        }
        HairlineDivider()
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val gap = 12.dp
            val grid = ChoiceGrid.best(choices.size, maxWidth, maxHeight, gap)
            Column(
                verticalArrangement = Arrangement.spacedBy(gap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                choices.chunked(grid.columns).forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        line.forEach { choice ->
                            ChoiceCell(choice, grid.cell, showLabels) { split.show(choice.page) }
                        }
                    }
                }
            }
        }
    }
}

private class Choice(val page: PanePage, val icon: LucideIcon, val label: String)

/** How many columns of icons fit a box best, and how large each one can be. */
internal data class ChoiceGrid(val columns: Int, val cell: Dp) {
    companion object {
        /** No cell is smaller than a touch target, and none larger than a tab on Home. */
        private val SMALLEST = 56.dp
        private val LARGEST = 132.dp

        fun best(count: Int, width: Dp, height: Dp, gap: Dp): ChoiceGrid {
            var best = ChoiceGrid(1, SMALLEST)
            for (columns in 1..count.coerceAtLeast(1)) {
                val rows = (count + columns - 1) / columns
                val side = minOf(
                    (width - gap * (columns - 1)) / columns,
                    (height - gap * (rows - 1)) / rows,
                    LARGEST,
                )
                if (side > best.cell) best = ChoiceGrid(columns, side)
            }
            return best.copy(cell = maxOf(best.cell, SMALLEST))
        }
    }
}

/** One choice: an icon in a round box. The whole box is the target, and the whole box inverts. */
@Composable
private fun ChoiceCell(choice: Choice, side: Dp, showLabel: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val foreground = if (pressed) EinkColors.Paper else EinkColors.Ink

    Column(
        modifier = Modifier
            .size(side)
            .clip(EinkShapes.panel)
            .background(if (pressed) EinkColors.Ink else EinkColors.Paper)
            .einkClickable(interactionSource = interactionSource, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = choice.label
                role = Role.Button
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EinkIcon(icon = choice.icon, size = if (side < 96.dp) 32.dp else 44.dp, color = foreground)
        if (showLabel && side >= 96.dp) {
            Spacer(modifier = Modifier.height(8.dp))
            CapsLabel(
                text = choice.label,
                style = EinkType.capsSmall.copy(color = foreground),
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}
