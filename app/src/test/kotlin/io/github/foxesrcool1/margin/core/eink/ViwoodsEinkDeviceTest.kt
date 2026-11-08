package io.github.foxesrcool1.margin.core.eink

import android.content.Context
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Has the shape of the vendor class, as far as the public notes describe it. */
class FakeENoteSetting {
    val calls = mutableListOf<String>()
    var mode = 3
    var failInitWriting = false

    fun getPictureMode(): Int = mode

    fun setPictureMode(value: Int): Boolean {
        calls += "setPictureMode($value)"
        mode = value
        return true
    }

    fun getWaveVersion(): String = "FAKE516"

    fun setApplicationContext(context: Context) {
        calls += "setApplicationContext"
    }

    fun initWriting() {
        calls += "initWriting"
        if (failInitWriting) throw IllegalStateException("lock error:-22")
    }

    fun exitWriting() {
        calls += "exitWriting"
    }

    fun setAutoDrawToolType(type: Int) {
        calls += "setAutoDrawToolType($type)"
    }

    fun setAutoDrawPenWidthRange(min: Int, max: Int) {
        calls += "setAutoDrawPenWidthRange($min,$max)"
    }

    companion object {
        var current = FakeENoteSetting()

        @JvmStatic
        fun getInstance(): FakeENoteSetting = current
    }
}

@RunWith(RobolectricTestRunner::class)
class ViwoodsEinkDeviceTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val later = mutableListOf<() -> Unit>()
    private lateinit var fake: FakeENoteSetting

    @Before
    fun setUp() {
        fake = FakeENoteSetting()
        FakeENoteSetting.current = fake
    }

    private fun device(className: String = FakeENoteSetting::class.java.name) = ViwoodsEinkDevice(
        guard = FastPenGuard(temp.root),
        settingClassName = className,
        binderInterfaceName = "no.such.Interface",
        serviceName = "NoSuchService",
        allowShell = false,
        runLater = { _, action -> later += action },
    )

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun aMissingVendorClassNeverThrows() {
        val device = device(className = "no.such.Class")
        assertFalse(device.hasVendorControl)
        assertNull(device.refreshMode())
        assertFalse(device.setRefreshMode(RefreshMode.Fast).ok)
        assertFalse(device.fullRefresh().ok)
        assertFalse(device.startFastPen(context, FastPenPath.Writing, Rect(0, 0, 10, 10)).ok)
        assertFalse(device.setPenTool(PenTool.Eraser).ok)
        assertTrue(device.deviceInfo().isNotEmpty())
    }

    @Test
    fun theModeGoesThroughTheWrapper() {
        val device = device()
        assertTrue(device.setRefreshMode(RefreshMode.Browse).ok)
        assertEquals(2, fake.mode)
        assertEquals(RefreshMode.Browse, device.refreshMode())
    }

    @Test
    fun aFullRefreshGoesBackToTheOldMode() {
        val device = device()
        fake.mode = 3
        assertTrue(device.fullRefresh().ok)
        assertEquals(17, fake.mode)
        later.forEach { it() }
        assertEquals(3, fake.mode)
    }

    @Test
    fun pathAMakesTheTwoCallsInOrder() {
        val device = device()
        val result = device.startFastPen(context, FastPenPath.Writing, Rect(0, 100, 1440, 1920))
        assertTrue(result.detail, result.ok)
        assertEquals(listOf("setPictureMode(4)", "setApplicationContext", "initWriting"), fake.calls)
        assertEquals(FastPenPath.Writing, device.activeFastPen)

        device.stopFastPen()
        assertTrue(fake.calls.contains("exitWriting"))
        assertEquals("the old mode comes back", 3, fake.mode)
        assertNull(device.activeFastPen)
    }

    @Test
    fun aJavaFailureInsideTheVendorCodeIsAnAnswerNotACrash() {
        fake.failInitWriting = true
        val result = device().startFastPen(context, FastPenPath.Writing, Rect(0, 0, 10, 10))
        assertFalse(result.ok)
        assertTrue(result.detail, result.detail.contains("lock error"))
        // It came back, so the guard must not call it a native crash.
        assertTrue(FastPenGuard(temp.root).mayTry(FastPenPath.Writing))
    }

    @Test
    fun aStartThatHalfWorkedPutsTheModeBack() {
        fake.failInitWriting = true
        val device = device()
        assertFalse(device.startFastPen(context, FastPenPath.Writing, Rect(0, 0, 10, 10)).ok)
        assertNull(device.activeFastPen)
        assertEquals("the old mode comes back", 3, fake.mode)
        assertTrue(fake.calls.contains("exitWriting"))
    }

    @Test
    fun pathBWithoutABinderFailsCleanly() {
        val result = device().startFastPen(context, FastPenPath.AutoDraw, Rect(0, 0, 10, 10))
        assertFalse(result.ok)
    }

    @Test
    fun aPathThatCrashedBeforeIsRefused() {
        val guard = FastPenGuard(temp.root)
        repeat(2) {
            guard.markTrying(FastPenPath.Writing)
            guard.settleAfterStart()
        }
        val result = device().startFastPen(context, FastPenPath.Writing, Rect(0, 0, 10, 10))
        assertFalse(result.ok)
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun toolAndWidthUseTheDocumentedNumbers() {
        val device = device()
        device.setPenTool(PenTool.Pen)
        device.setPenTool(PenTool.Eraser)
        device.setPenWidthRange(1, 3)
        assertEquals(
            listOf("setAutoDrawToolType(2)", "setAutoDrawToolType(4)", "setAutoDrawPenWidthRange(1,3)"),
            fake.calls,
        )
    }

    @Test
    fun theGenericDeviceSaysNoToEverything() {
        val device = GenericEinkDevice()
        assertFalse(device.hasVendorControl)
        assertFalse(device.fullRefresh().ok)
        assertFalse(device.startFastPen(context, FastPenPath.AutoDraw, Rect()).ok)
    }
}
