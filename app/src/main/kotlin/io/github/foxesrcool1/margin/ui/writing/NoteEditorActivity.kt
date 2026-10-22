package io.github.foxesrcool1.margin.ui.writing

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import io.github.foxesrcool1.margin.core.books.LibraryBook
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.design.EinkTheme
import io.github.foxesrcool1.margin.ui.ink.InkCanvasView
import io.github.foxesrcool1.margin.ui.split.BesideActivity
import io.github.foxesrcool1.margin.ui.split.PaneHost
import io.github.foxesrcool1.margin.ui.split.PaneInk
import io.github.foxesrcool1.margin.ui.split.PanePage
import io.github.foxesrcool1.margin.ui.split.SplitLayout
import io.github.foxesrcool1.margin.ui.split.SplitPane
import io.github.foxesrcool1.margin.ui.split.SplitState
import io.github.foxesrcool1.margin.ui.split.handSplitBack
import io.github.foxesrcool1.margin.ui.split.openBookBeside
import io.github.foxesrcool1.margin.ui.split.splitCarry
import io.github.foxesrcool1.margin.ui.split.watchAndroidSplit

private const val TAG = "NoteEditorActivity"

/**
 * The typed note editor.
 *
 * Its own activity, as the plan says. If it lived inside the launcher task,
 * the Home key would close the note the user was in the middle of writing.
 *
 * A Bluetooth keyboard can press Escape to leave and Ctrl+S to save. Those are
 * handled here rather than in the composition, because an activity key handler
 * catches them wherever the focus happens to be.
 */
class NoteEditorActivity : ComponentActivity() {

    private val saveRequests = androidx.compose.runtime.mutableIntStateOf(0)
    private var notePath = ""
    private val split = SplitState(mainPage = { PanePage.TypedNote(notePath) })
    private lateinit var paneInk: PaneInk

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        io.github.foxesrcool1.margin.core.window.ScreenWindow.attach(this)

        val path = intent.getStringExtra(EXTRA_NOTE_PATH)
        if (path.isNullOrBlank()) {
            AppLog.e(TAG, "Opened with no note path")
            finish()
            return
        }

        AppLog.i(TAG, "Editing $path")
        notePath = path
        val createdWith = intent.getStringExtra(EXTRA_CREATED_WITH)
        paneInk = PaneInk(this, lifecycleScope)
        watchAndroidSplit(split)
        intent.splitCarry()?.let { split.open(it.page, it.swapped) }

        val paneHost = object : PaneHost {
            override fun openBook(book: LibraryBook) = openBookBeside(this@NoteEditorActivity, split, book, this)

            override fun paneInk(canvas: InkCanvasView?) = paneInk.serve(canvas)

            override fun keeper(): Intent = BesideActivity.then(this@NoteEditorActivity, NoteEditorActivity.intent(this@NoteEditorActivity, path))

            override fun leave() = finish()
        }

        setContent {
            EinkTheme {
                SplitLayout(
                    split = split,
                    main = {
                        NoteEditorScreen(
                            notePath = path,
                            onClose = { finish() },
                            saveRequests = saveRequests.intValue,
                            createdWith = createdWith,
                        )
                    },
                    pane = { SplitPane(split, paneHost) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::paneInk.isInitialized) paneInk.resume()
        io.github.foxesrcool1.margin.ui.split.AdjacentApps.takeRequest(this)
    }

    override fun onPause() {
        handSplitBack(split)
        if (::paneInk.isInitialized) paneInk.pause()
        super.onPause()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when {
            keyCode == KeyEvent.KEYCODE_ESCAPE -> {
                finish()
                true
            }

            keyCode == KeyEvent.KEYCODE_S && event?.isCtrlPressed == true -> {
                saveRequests.intValue++
                true
            }

            else -> super.onKeyUp(keyCode, event)
        }
    }

    companion object {
        const val EXTRA_NOTE_PATH = "note_path"

        /** The text a just made note starts with. Present only for the Home quick note. */
        const val EXTRA_CREATED_WITH = "created_with"

        fun intent(context: Context, notePath: String, createdWith: String? = null): Intent =
            Intent(context, NoteEditorActivity::class.java)
                .putExtra(EXTRA_NOTE_PATH, notePath)
                .also { if (createdWith != null) it.putExtra(EXTRA_CREATED_WITH, createdWith) }
    }
}
