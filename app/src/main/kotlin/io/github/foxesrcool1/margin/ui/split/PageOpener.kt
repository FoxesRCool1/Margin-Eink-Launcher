package io.github.foxesrcool1.margin.ui.split

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.github.foxesrcool1.margin.core.books.LibraryBook
import io.github.foxesrcool1.margin.core.ink.PageTemplate
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.ui.apps.AppsRepository
import io.github.foxesrcool1.margin.ui.apps.LauncherEntry
import io.github.foxesrcool1.margin.ui.ink.InkNoteActivity
import io.github.foxesrcool1.margin.ui.reading.epub.EpubReaderActivity
import io.github.foxesrcool1.margin.ui.reading.pdf.PdfReaderActivity
import io.github.foxesrcool1.margin.ui.writing.NoteEditorActivity

private const val TAG = "PageOpener"

/**
 * How a page opens a note, a book or an app.
 *
 * On a screen of its own, each opens a new screen, as it always did. In the
 * second half of the split screen, a note opens in that same half, and an app
 * is asked to open beside this one. The pages do not know which case they are
 * in: they ask [rememberPageOpener], and the split screen puts its own opener
 * in the composition for the half it owns.
 */
interface PageOpener {
    fun openTypedNote(path: String)
    fun openInkNote(path: String, title: String, template: PageTemplate = PageTemplate.Blank)
    fun openBook(book: LibraryBook)

    /** False when the app could not be started. */
    fun openApp(entry: LauncherEntry): Boolean
}

/** Null on a plain screen, where [rememberPageOpener] makes the usual one. */
val LocalPageOpener = staticCompositionLocalOf<PageOpener?> { null }

@Composable
fun rememberPageOpener(): PageOpener {
    val context = LocalContext.current
    return LocalPageOpener.current ?: remember(context) { ScreenPageOpener(context) }
}

/**
 * Opens each thing in a screen of its own.
 *
 * [carry] is the split screen to take along, when there is one: a book or a
 * note opened from Home while a page stands beside it opens with that page
 * still beside it. [afterCarry] runs once a screen took the split screen with
 * it, so the screen it came from can let go of its own copy.
 */
class ScreenPageOpener(
    private val context: Context,
    private val carry: () -> SplitCarry? = { null },
    private val afterCarry: () -> Unit = {},
) : PageOpener {

    override fun openTypedNote(path: String) {
        start("the note $path") { NoteEditorActivity.intent(context, path) }
    }

    override fun openInkNote(path: String, title: String, template: PageTemplate) {
        start("the ink note $path") { InkNoteActivity.intent(context, path, title, template) }
    }

    override fun openBook(book: LibraryBook) {
        start(book.path) { bookIntent(context, book) }
    }

    override fun openApp(entry: LauncherEntry): Boolean = AppsRepository(context).launch(entry)

    private fun start(what: String, make: () -> Intent) {
        // A second tap on the same row, before the first screen is up, would
        // open the note twice, and the two copies would save over each other.
        // E-ink gives no sign that the first tap landed, so this happens.
        val now = SystemClock.uptimeMillis()
        if (what == lastOpened && now - lastOpenedAt < DOUBLE_TAP_MILLIS) {
            AppLog.i(TAG, "Ignored a second tap on $what")
            return
        }
        lastOpened = what
        lastOpenedAt = now

        val taken = carry()
        runCatching {
            context.startActivity(make().withSplit(taken))
            if (taken != null) afterCarry()
            forgetWhenBack()
        }.onFailure {
            lastOpened = null
            AppLog.e(TAG, "Could not open $what", it)
        }
    }

    /**
     * The page that was opened is gone when this screen comes back, so the
     * next tap on the same row is a new one and must not be swallowed.
     */
    private fun forgetWhenBack() {
        val owner = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<LifecycleOwner>().firstOrNull() ?: return
        owner.lifecycle.addObserver(object : LifecycleEventObserver {
            // A new observer is told the state the screen is in now, which is
            // "resumed". Only a resume that follows a pause counts.
            private var paused = false

            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> paused = true
                    Lifecycle.Event.ON_RESUME -> if (paused) {
                        lastOpened = null
                        source.lifecycle.removeObserver(this)
                    }
                    Lifecycle.Event.ON_DESTROY -> source.lifecycle.removeObserver(this)
                    else -> Unit
                }
            }
        })
    }

    private companion object {
        const val DOUBLE_TAP_MILLIS = 800L
        var lastOpened: String? = null
        var lastOpenedAt = 0L
    }
}

fun bookIntent(context: Context, book: LibraryBook): Intent =
    if (book.isPdf) {
        PdfReaderActivity.intent(context, book.path, book.bookId, book.metadata.title)
    } else {
        EpubReaderActivity.intent(context, book.path, book.bookId, book.metadata.title)
    }

/** Puts the split screen into an intent, for the screen it starts. Nothing when [carry] is null. */
fun Intent.withSplit(carry: SplitCarry?): Intent {
    if (carry != null) PanePageCodec.write(carry).forEach { (key, value) -> putExtra(key, value) }
    return this
}

/** The split screen a screen was started with, or null. */
fun Intent.splitCarry(): SplitCarry? = PanePageCodec.read { key -> getStringExtra(key) }
