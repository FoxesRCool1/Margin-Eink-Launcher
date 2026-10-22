package io.github.foxesrcool1.margin.ui.split

import io.github.foxesrcool1.margin.core.ink.PageTemplate
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import io.github.foxesrcool1.margin.ui.home.LauncherRoute

/**
 * What the second half of the split screen shows.
 *
 * Any page of this app can stand there except a book. A book is always the
 * main half of its own screen, because the book engines hold a whole activity
 * each. A book opened from the second half therefore opens a new screen, and
 * the page that stood beside it goes along into that screen's second half.
 * See [SplitCarry].
 */
sealed interface PanePage {

    /** The start of the second half: a choice of what goes there. */
    data object Choose : PanePage

    /** One of the launcher's own pages: Read, Write, Journal, Apps, Settings, Log, Updates. */
    data class Tab(val route: LauncherRoute) : PanePage

    data class TypedNote(val path: String) : PanePage

    data class InkNote(
        val path: String,
        val title: String,
        /** The template a note that does not exist yet starts with. */
        val template: PageTemplate = PageTemplate.Blank,
    ) : PanePage

    /** The typed and the handwritten note that belong to one book. */
    data class BookNotes(val bookTitle: String) : PanePage
}

/**
 * The things a page may write to. Two halves that hold the same thing would
 * each keep a copy in memory, and whichever saved last would win, so the split
 * screen refuses to show one page twice.
 */
internal fun PanePage.holds(): Set<String> = when (this) {
    PanePage.Choose -> emptySet()
    is PanePage.Tab -> if (route == LauncherRoute.Home) emptySet() else setOf("tab:${route.name}")
    is PanePage.TypedNote -> setOf("file:$path")
    is PanePage.InkNote -> setOf("file:$path")
    is PanePage.BookNotes -> setOf(
        "file:" + StorageLayout.readingNotePath(bookTitle.ifBlank { UNTITLED_BOOK }, handwritten = false),
        "file:" + StorageLayout.readingNotePath(bookTitle.ifBlank { UNTITLED_BOOK }, handwritten = true),
    )
}

/** True when the page is a note that is [path] or lies inside the folder [path]. */
internal fun PanePage?.isNoteAtOrInside(path: String): Boolean =
    this?.notePath()?.let { it == path || it.startsWith("$path/") } == true

/** True when the two pages would show, and write, the same thing. */
fun PanePage.clashesWith(other: PanePage?): Boolean =
    other != null && holds().any { it in other.holds() }

/** The file of a note page, or null for any other page. */
private fun PanePage.notePath(): String? = when (this) {
    is PanePage.TypedNote -> path
    is PanePage.InkNote -> path
    else -> null
}

internal const val UNTITLED_BOOK = "Untitled book"

/**
 * A page and the side it stands on, as plain strings, so it can travel in an
 * intent from one screen to the next. The keys are the extras of the intent.
 */
object PanePageCodec {

    private const val KIND = "split_kind"
    private const val ROUTE = "split_route"
    private const val PATH = "split_path"
    private const val TITLE = "split_title"
    private const val TEMPLATE = "split_template"
    private const val SWAPPED = "split_swapped"

    /** Every key [write] may set. */
    val keys: List<String> = listOf(KIND, ROUTE, PATH, TITLE, TEMPLATE, SWAPPED)

    fun write(carry: SplitCarry): Map<String, String> {
        val fields = mutableMapOf(SWAPPED to carry.swapped.toString())
        when (val page = carry.page) {
            PanePage.Choose -> fields[KIND] = "choose"
            is PanePage.Tab -> {
                fields[KIND] = "tab"
                fields[ROUTE] = page.route.name
            }
            is PanePage.TypedNote -> {
                fields[KIND] = "typed"
                fields[PATH] = page.path
            }
            is PanePage.InkNote -> {
                fields[KIND] = "ink"
                fields[PATH] = page.path
                fields[TITLE] = page.title
                fields[TEMPLATE] = page.template.id
            }
            is PanePage.BookNotes -> {
                fields[KIND] = "book"
                fields[TITLE] = page.bookTitle
            }
        }
        return fields
    }

    /** Null when there is no page, or the fields do not make one. A bad extra never opens a wrong file. */
    fun read(field: (String) -> String?): SplitCarry? {
        val page = when (field(KIND)) {
            "choose" -> PanePage.Choose
            "tab" -> LauncherRoute.entries.firstOrNull { it.name == field(ROUTE) }?.let(PanePage::Tab)
            "typed" -> field(PATH)?.takeIf { it.isNotBlank() }?.let(PanePage::TypedNote)
            "ink" -> field(PATH)?.takeIf { it.isNotBlank() }?.let { path ->
                PanePage.InkNote(path, field(TITLE).orEmpty(), PageTemplate.fromId(field(TEMPLATE)))
            }
            "book" -> field(TITLE)?.let(PanePage::BookNotes)
            else -> null
        } ?: return null
        return SplitCarry(page, swapped = field(SWAPPED) == "true")
    }
}

/**
 * What a new screen takes with it when something opens while the split screen
 * is on: the page for its second half, and which side that half is on.
 */
data class SplitCarry(val page: PanePage, val swapped: Boolean)
