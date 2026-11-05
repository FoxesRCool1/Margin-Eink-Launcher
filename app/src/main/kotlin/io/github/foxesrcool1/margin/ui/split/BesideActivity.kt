package io.github.foxesrcool1.margin.ui.split

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import io.github.foxesrcool1.margin.core.books.LibraryBook
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.window.ScreenWindow
import io.github.foxesrcool1.margin.design.EinkTheme
import io.github.foxesrcool1.margin.ui.ink.InkCanvasView

private const val TAG = "BesideActivity"

/**
 * The task that stands beside another app.
 *
 * Android's split screen takes two tasks, and never the task of the home
 * screen. Everything this app opens from Home lives in that task. So when an
 * app is chosen for the other half, the page that should stay on screen opens
 * again in this task, and asks for the app beside it from there.
 *
 * There is only ever one such task. The manifest gives this activity a task
 * affinity of its own, and each new request clears the old task first, so a
 * day of requests does not leave books and notes open in the background.
 *
 * A page of the launcher, a tab or the choice of pages, shows here, as the
 * second half of a split screen would, with the way back. A book or a note
 * has a screen of its own: this activity starts that screen in the same task
 * and steps out of the way.
 */
class BesideActivity : ComponentActivity() {

    private lateinit var paneInk: PaneInk
    private val split = SplitState()

    private val host = object : PaneHost {
        override fun openBook(book: LibraryBook, highlightId: String?) {
            // In this task, so the book stays on this side of Android's split.
            runCatching { startActivity(bookIntent(this@BesideActivity, book, highlightId)) }
                .onFailure { AppLog.e(TAG, "Could not open ${book.path}", it) }
        }

        override fun paneInk(canvas: InkCanvasView?) = paneInk.serve(canvas)

        // Already in a task of its own. An app goes beside this one directly.
        override fun keeper(): Intent? = null

        override fun leave() = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScreenWindow.attach(this)

        @Suppress("DEPRECATION")
        val then = intent.getParcelableExtra<Intent>(EXTRA_THEN)
        if (then != null) {
            // A book or a note: its own screen, in this task. The request for
            // the app goes along with it, and that screen asks for the app.
            AdjacentApps.moveRequest(from = intent, to = then)
            AppLog.i(TAG, "Opening ${then.component?.shortClassName} in the task beside, task $taskId")
            runCatching { startActivity(then) }.onFailure { AppLog.e(TAG, "Could not open the screen", it) }
            finish()
            return
        }

        paneInk = PaneInk(this, lifecycleScope)
        val page = intent.splitCarry()?.page ?: PanePage.Choose
        AppLog.i(TAG, "Showing $page in a task of its own, task $taskId")
        split.open(page)
        watchAndroidSplit(split, closeOnSplit = false)

        setContent {
            EinkTheme {
                // Back walks back through the pages, and leaves from the first.
                BackHandler(enabled = true) {
                    if (split.page == page || split.page == PanePage.Choose) finish() else split.back()
                }
                SplitPane(split, host)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::paneInk.isInitialized) return
        paneInk.resume()
        AdjacentApps.takeRequest(this)
    }

    override fun onPause() {
        if (::paneInk.isInitialized) paneInk.pause()
        super.onPause()
    }

    companion object {
        private const val EXTRA_THEN = "beside_then"

        /** A page of the launcher, beside an app. */
        fun intent(context: Context, page: PanePage): Intent =
            Intent(context, BesideActivity::class.java).withSplit(SplitCarry(page, swapped = false))

        /** A screen of its own, a book or a note, beside an app. Its split screen stays behind. */
        fun then(context: Context, screen: Intent): Intent =
            Intent(context, BesideActivity::class.java)
                .putExtra(EXTRA_THEN, Intent(screen).apply { PanePageCodec.keys.forEach(::removeExtra) })
    }
}
