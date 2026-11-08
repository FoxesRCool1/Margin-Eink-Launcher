package io.github.foxesrcool1.margin.ui.split

import android.app.Activity
import androidx.lifecycle.LifecycleCoroutineScope
import io.github.foxesrcool1.margin.core.settings.SettingsStore
import io.github.foxesrcool1.margin.ui.ink.FastPenSession
import io.github.foxesrcool1.margin.ui.ink.InkCanvasView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The fast pen of the tablet, for a handwritten note in the second half of a
 * screen that has no ink of its own: Home, the typed note, the EPUB reader.
 *
 * The screen hands over the canvas the second half shows, or null, and calls
 * [pause] and [resume] from its own. The tablet must stop drawing on the glass
 * before another screen shows. With the fast pen off in Settings this does
 * nothing, and the canvas paints for itself.
 */
class PaneInk(private val activity: Activity, private val scope: LifecycleCoroutineScope) {

    private var canvas: InkCanvasView? = null
    private var session: FastPenSession? = null

    /** True between [pause] and [resume]. A layout change in that time must not start the pen again. */
    private var paused = false

    /** The half moved or changed size: a turn, or a swap. The tablet has to draw in the new box. */
    private val refit = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        if (!paused) session?.start()
    }

    fun serve(next: InkCanvasView?) {
        if (next === canvas && session != null) return
        session?.stop()
        session = null
        canvas?.removeOnLayoutChangeListener(refit)
        canvas = next
        next?.addOnLayoutChangeListener(refit)
        if (next != null && !paused) resume()
    }

    fun resume() {
        paused = false
        val view = canvas ?: return
        if (session != null) {
            session?.start()
            return
        }
        scope.launch {
            val settings = SettingsStore(activity)
            val mode = settings.fastPenMode.first()
            if (mode == SettingsStore.FAST_PEN_OFF) return@launch
            val delayMillis = settings.inkRedrawDelayMillis.first()
            // The canvas has no size until it has been laid out once.
            view.post {
                if (paused || view !== canvas || view.width == 0 || session != null) return@post
                session = FastPenSession(activity, view, mode, delayMillis).also { it.start() }
            }
        }
    }

    fun pause() {
        paused = true
        session?.stop()
    }
}
