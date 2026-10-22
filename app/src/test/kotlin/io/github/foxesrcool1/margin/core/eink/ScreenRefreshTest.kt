package io.github.foxesrcool1.margin.core.eink

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.util.concurrent.Executor

/** Records the picture modes it is given, like the tablet would take them. */
private class RecordingDevice(
    override val hasVendorControl: Boolean = true,
    var mode: RefreshMode? = RefreshMode.Reading,
) : EinkDevice {
    val modes = mutableListOf<RefreshMode>()
    override val name = "Recording"
    override val activeFastPen: FastPenPath? = null
    override fun deviceInfo(): List<Pair<String, String>> = emptyList()
    override fun refreshMode(): RefreshMode? = mode
    override fun setRefreshMode(mode: RefreshMode): EinkCallResult {
        modes += mode
        this.mode = mode
        return EinkCallResult("setRefreshMode", true, "")
    }
    override fun fullRefresh() = EinkCallResult("fullRefresh", true, "")
    override fun startFastPen(context: Context, path: FastPenPath, drawRegion: Rect, excluded: List<Rect>) =
        EinkCallResult("startFastPen", false, "")
    override fun stopFastPen() = EinkCallResult("stopFastPen", true, "")
    override fun setPenTool(tool: PenTool) = EinkCallResult("setPenTool", true, "")
    override fun setPenWidthRange(min: Int, max: Int) = EinkCallResult("setPenWidthRange", true, "")
}

@RunWith(RobolectricTestRunner::class)
class ScreenRefreshTest {

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val vendorThread = EinkThread.executor

    /** The vendor thread runs its work in place, so the test sees the calls at once. */
    @Before
    fun runVendorCallsInPlace() {
        EinkThread.executor = Executor { it.run() }
    }

    @After
    fun putTheVendorThreadBack() {
        EinkThread.executor = vendorThread
    }

    private fun cover(): View? =
        activity.findViewById<ViewGroup>(android.R.id.content).findViewWithTag(ScreenRefresh.COVER_TAG)

    /** One step per wait: Robolectric moves the clock first, so a task posted by a task runs late. */
    private fun waitFor(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private fun waitForAll() {
        waitFor(ScreenRefresh.BLACK_MILLIS)
        waitFor(ScreenRefresh.RESTORE_MILLIS)
    }

    @Test
    fun `the screen goes black, comes back, and the old mode returns`() {
        val device = RecordingDevice()

        ScreenRefresh.run(activity, device)

        val black = cover()
        assertNotNull("a black cover over the whole window", black)
        assertEquals(Color.BLACK, (black!!.background as ColorDrawable).color)
        assertEquals(listOf(RefreshMode.Full), device.modes)

        waitFor(ScreenRefresh.BLACK_MILLIS)
        assertNull("the cover is gone again", cover())

        waitFor(ScreenRefresh.RESTORE_MILLIS)
        assertEquals(listOf(RefreshMode.Full, RefreshMode.Reading), device.modes)
    }

    @Test
    fun `a second press while the screen is black does nothing`() {
        val device = RecordingDevice()

        ScreenRefresh.run(activity, device)
        ScreenRefresh.run(activity, device)

        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        assertEquals(1, (0 until root.childCount).count { root.getChildAt(it).tag == ScreenRefresh.COVER_TAG })
        waitForAll()
        assertEquals(listOf(RefreshMode.Full, RefreshMode.Reading), device.modes)
    }

    @Test
    fun `a device with no vendor control still flashes`() {
        val device = RecordingDevice(hasVendorControl = false)

        ScreenRefresh.run(activity, device)

        assertNotNull(cover())
        waitForAll()
        assertNull(cover())
        assertEquals(emptyList<RefreshMode>(), device.modes)
    }

    @Test
    fun `a panel left in the full mode goes back to the reading mode`() {
        val device = RecordingDevice(mode = RefreshMode.Full)

        ScreenRefresh.run(activity, device)
        waitForAll()

        assertEquals(listOf(RefreshMode.Reading), device.modes)
    }

    @Test
    fun `a mode that cannot be read is left alone, because nobody could put it back`() {
        val device = RecordingDevice(mode = null)

        ScreenRefresh.run(activity, device)

        assertNotNull(cover())
        waitForAll()
        assertEquals(emptyList<RefreshMode>(), device.modes)
    }
}
