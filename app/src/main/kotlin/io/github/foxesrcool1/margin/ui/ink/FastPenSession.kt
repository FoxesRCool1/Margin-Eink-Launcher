package io.github.foxesrcool1.margin.ui.ink

import android.app.Activity
import android.graphics.Rect
import android.view.View
import io.github.foxesrcool1.margin.core.eink.EinkCallResult
import io.github.foxesrcool1.margin.core.eink.EinkDevice
import io.github.foxesrcool1.margin.core.eink.EinkDevices
import io.github.foxesrcool1.margin.core.eink.EinkThread
import io.github.foxesrcool1.margin.core.eink.FastPenPath
import io.github.foxesrcool1.margin.core.eink.PenTool
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.settings.SettingsStore

private const val TAG = "FastPenSession"

/**
 * Ties an [InkCanvasView] to the fast pen of the tablet for as long as one
 * screen is open.
 *
 * The screen calls [start] once the canvas has a size, [stop] when it leaves,
 * and [pause] around anything that must not be drawn on, such as a dialog.
 * When the fast pen cannot start, the canvas simply paints for itself, so the
 * screen never has to know which case it is in.
 */
class FastPenSession(
    private val activity: Activity,
    val canvas: InkCanvasView,
    private val mode: String,
    private val redrawDelayMillis: Long,
) {
    private val device: EinkDevice = EinkDevices.get(activity)

    /** True from the moment a start is queued until the stop. The main thread owns it. */
    private var running = false

    /** Counts the starts, so the answer to an old start is not taken for the answer to a new one. */
    private var round = 0

    /** Where on the screen the tablet draws, while it does. */
    private var box: Rect? = null

    private val path: FastPenPath? = when (mode) {
        SettingsStore.FAST_PEN_WRITING -> FastPenPath.Writing
        SettingsStore.FAST_PEN_AUTODRAW -> FastPenPath.AutoDraw
        else -> null
    }

    /**
     * [keepOut] are views the tablet must not draw on: toolbars and buttons.
     *
     * Call it again after the canvas moved or changed size, after a turn of
     * the screen or when the split screen opens, closes or swaps. The tablet
     * is then told the new box. Called again with nothing changed, it does
     * nothing.
     */
    fun start(keepOut: List<View> = emptyList()) {
        val wanted = path ?: return
        if (canvas.width == 0 || !device.hasVendorControl) return
        val now = screenBox(canvas)
        if (running) {
            if (now == box) return
            AppLog.i(TAG, "The canvas moved from $box to $now, starting the fast pen again there")
            stop()
        }

        // Views are read here, on the main thread. The worker gets plain values.
        val excluded = keepOut.map(::screenBox)
        val range = PenWidths.vendorRange(canvas.penWidth)
        running = true
        box = now
        canvas.redrawDelayMillis = redrawDelayMillis
        val mine = ++round
        // The vendor calls can wait on a binder call, so they never run on the
        // main thread. Until the answer is back, the canvas paints for itself.
        EinkThread.run {
            val result = device.startFastPen(activity, wanted, now, excluded)
            if (result.ok) device.setPenWidthRange(range.first, range.second)
            canvas.post { started(mine, result) }
        }
    }

    private fun started(mine: Int, result: EinkCallResult) {
        // Stopped or moved while the start was under way: this answer is old.
        if (mine != round || !running) return
        if (result.ok) {
            canvas.deviceDrawsLive = true
            canvas.onErasingChanged = { erasing ->
                val tool = if (erasing) PenTool.Eraser else PenTool.Pen
                EinkThread.run { device.setPenTool(tool) }
            }
        } else {
            running = false
            box = null
            AppLog.w(TAG, "Fast pen did not start, this app paints instead: ${result.detail}")
        }
    }

    fun applyWidth(penWidth: Float) {
        if (!running) return
        val (low, high) = PenWidths.vendorRange(penWidth)
        EinkThread.run { device.setPenWidthRange(low, high) }
    }

    /** Stops the tablet from drawing, and pays what the canvas owes right away. */
    fun stop() {
        if (!running) return
        running = false
        round++
        EinkThread.run { device.stopFastPen() }
        canvas.deviceDrawsLive = false
        canvas.onErasingChanged = null
        canvas.settle()
    }

    val isRunning: Boolean get() = running

    private fun screenBox(view: View): Rect {
        val corner = IntArray(2)
        view.getLocationOnScreen(corner)
        return Rect(corner[0], corner[1], corner[0] + view.width, corner[1] + view.height)
    }
}
