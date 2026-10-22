package io.github.foxesrcool1.margin.core.eink

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import io.github.foxesrcool1.margin.core.log.AppLog

private const val TAG = "ScreenRefresh"

/**
 * A full refresh that the panel cannot miss: the whole window turns black for
 * a moment, then comes back.
 *
 * The first version only set the vendor picture mode to the full refresh mode
 * and back. On the tablet the owner pressed the button and nothing happened.
 * A picture mode only says how the next change is painted, and a press on a
 * settings row changes almost nothing, so there was nothing to paint.
 *
 * Black over the whole window changes every pixel, and taking it away changes
 * every pixel again. That clears the grey marks on any e-ink panel, with or
 * without vendor control. When the vendor mode can be read, both changes are
 * painted in the full refresh mode, and the old mode comes back after. When it
 * cannot be read, the mode is left alone, because nobody could put it back.
 *
 * One instant change to black and one back. No fade: e-ink rule 1 holds.
 */
object ScreenRefresh {

    /** How long the black stays. A full e-ink update takes 300 to 500 ms. */
    const val BLACK_MILLIS = 450L

    /** How long after the black goes the old picture mode comes back. */
    const val RESTORE_MILLIS = 700L

    internal const val COVER_TAG = "eink-refresh-cover"

    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Call on the main thread. A second call while the black is up does nothing. */
    fun run(activity: Activity) = run(activity, EinkDevices.get(activity))

    /** [device] is a parameter for the tests. */
    internal fun run(activity: Activity, device: EinkDevice) {
        if (activity.isFinishing || activity.isDestroyed) return
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (root.findViewWithTag<View>(COVER_TAG) != null) return

        val cover = View(activity).apply {
            tag = COVER_TAG
            setBackgroundColor(Color.BLACK)
            // A tap during the black lands nowhere.
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(cover, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // The vendor calls run on the vendor thread, one after the other, so
        // the restore below always comes after this. They write fullMode and
        // restoreTo; the restore reads them.
        var fullMode = false
        var restoreTo: RefreshMode? = null
        EinkThread.run {
            val before = if (device.hasVendorControl) device.refreshMode() else null
            // A panel found in the full mode already is one a refresh left behind,
            // after the app died in the middle of one or a call to put it back
            // failed. It goes to the reading mode after this one, and heals.
            restoreTo = if (before == RefreshMode.Full) RefreshMode.Reading else before
            fullMode = before != null &&
                (before == RefreshMode.Full || device.setRefreshMode(RefreshMode.Full).ok)
            AppLog.i(TAG, "Force refresh. Vendor full mode: ${if (fullMode) "on, was ${before?.name}" else "not used"}")
        }

        // The main handler, not the view: a view that is gone runs nothing it
        // was given, and the old picture mode must come back in every case.
        main.postDelayed({
            (cover.parent as? ViewGroup)?.removeView(cover)
            main.postDelayed({
                EinkThread.run {
                    val back = restoreTo
                    if (fullMode && back != null) device.setRefreshMode(back)
                }
            }, RESTORE_MILLIS)
        }, BLACK_MILLIS)
    }
}
