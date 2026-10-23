package io.github.foxesrcool1.margin.ui.ink

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import io.github.foxesrcool1.margin.core.ink.InkNote
import io.github.foxesrcool1.margin.core.ink.InkTestData
import io.github.foxesrcool1.margin.core.ink.InkTool
import io.github.foxesrcool1.margin.core.ink.PageTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w480dp-h640dp-xxhdpi")
class InkCanvasViewTest {

    private lateinit var view: InkCanvasView
    private var changes = 0

    @Before
    fun setUp() {
        view = InkCanvasView(RuntimeEnvironment.getApplication())
        view.measure(
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 720, 960)
        view.setPage(emptyList(), InkNote.DEFAULT_PAGE_WIDTH, InkNote.DEFAULT_PAGE_HEIGHT, PageTemplate.Blank)
        view.onInkChanged = { changes++ }
    }

    private fun event(action: Int, x: Float, y: Float, tool: Int, pressure: Float = 0.6f): MotionEvent {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = tool }
        val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; this.pressure = pressure; size = 1f }
        val now = SystemClock.uptimeMillis()
        return MotionEvent.obtain(now, now, action, 1, arrayOf(properties), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun drag(tool: Int, vararg points: Pair<Float, Float>) {
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, points.first().first, points.first().second, tool))
        points.drop(1).forEach { view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, it.first, it.second, tool)) }
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, points.last().first, points.last().second, tool))
    }

    private fun pixel(x: Int, y: Int): Int {
        val bitmap = Bitmap.createBitmap(720, 960, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        return bitmap.getPixel(x, y)
    }

    @Test
    fun `the pen makes a stroke in page units and paints it`() {
        drag(MotionEvent.TOOL_TYPE_STYLUS, 100f to 100f, 200f to 100f, 300f to 100f)
        assertEquals(1, view.editor.strokeCount)
        val stroke = view.editor.strokes.single()
        // The view is half the page size, so 100 px on the glass is 200 units.
        assertEquals(200f, stroke.xs.first(), 0.5f)
        assertEquals(600f, stroke.xs.last(), 0.5f)
        assertEquals(InkTool.Pen, stroke.tool)
        assertEquals(1, changes)
        assertEquals(Color.BLACK, pixel(200, 100))
        assertEquals(Color.WHITE, pixel(200, 300))
    }

    @Test
    fun `the line shows while the pen is down, and a cancel takes it off`() {
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 100f, 100f, MotionEvent.TOOL_TYPE_STYLUS))
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 200f, 100f, MotionEvent.TOOL_TYPE_STYLUS))
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 300f, 100f, MotionEvent.TOOL_TYPE_STYLUS))
        assertEquals("drawn on top before the pen lifts", Color.BLACK, pixel(200, 100))
        assertEquals(0, view.editor.strokeCount)

        view.dispatchTouchEvent(event(MotionEvent.ACTION_CANCEL, 300f, 100f, MotionEvent.TOOL_TYPE_STYLUS))
        assertEquals(0, view.editor.strokeCount)
        assertEquals(Color.WHITE, pixel(200, 100))
    }

    @Test
    fun `a curve passes through the middle of its corners, not the corners`() {
        // Three points make a sharp corner at (200, 100). The line bends
        // through it on a curve, so the corner pixel itself stays white.
        drag(MotionEvent.TOOL_TYPE_STYLUS, 100f to 200f, 200f to 100f, 300f to 200f)
        assertEquals(Color.WHITE, pixel(200, 100))
        // The bend is half way between the corner and the middle of its sides.
        assertNotEquals(Color.WHITE, pixel(200, 125))
        assertNotEquals("the line still ends where the pen lifted", Color.WHITE, pixel(299, 199))
    }

    @Test
    fun `a finger never draws`() {
        drag(MotionEvent.TOOL_TYPE_FINGER, 100f to 100f, 300f to 100f)
        assertEquals(0, view.editor.strokeCount)
        assertEquals(Color.WHITE, pixel(200, 100))
    }

    @Test
    fun `a finger swipe turns the page, but not just after the pen`() {
        var turned = 0
        view.onPageSwipe = { turned += it }
        drag(MotionEvent.TOOL_TYPE_FINGER, 600f to 400f, 300f to 410f)
        assertEquals(1, turned)

        drag(MotionEvent.TOOL_TYPE_STYLUS, 100f to 100f, 120f to 100f)
        drag(MotionEvent.TOOL_TYPE_FINGER, 600f to 400f, 300f to 410f)
        assertEquals("a resting hand must not turn the page", 1, turned)
    }

    @Test
    fun `the eraser end of the pen removes a stroke and its pixels`() {
        drag(MotionEvent.TOOL_TYPE_STYLUS, 100f to 100f, 300f to 100f)
        drag(MotionEvent.TOOL_TYPE_ERASER, 200f to 50f, 200f to 150f)
        assertEquals(0, view.editor.strokeCount)
        assertEquals(Color.WHITE, pixel(150, 100))
    }

    @Test
    fun `eraser mode works with the pen tip and undo brings the stroke back`() {
        drag(MotionEvent.TOOL_TYPE_STYLUS, 100f to 100f, 300f to 100f)
        view.mode = InkMode.Eraser
        drag(MotionEvent.TOOL_TYPE_STYLUS, 200f to 50f, 200f to 150f)
        assertEquals(0, view.editor.strokeCount)
        view.undo()
        assertEquals(1, view.editor.strokeCount)
        assertEquals(Color.BLACK, pixel(150, 100))
        view.undo()
        assertEquals(0, view.editor.strokeCount)
        assertEquals(Color.WHITE, pixel(150, 100))
    }

    @Test
    fun `the highlighter is grey and leaves pen ink black`() {
        drag(MotionEvent.TOOL_TYPE_STYLUS, 100f to 200f, 300f to 200f)
        view.mode = InkMode.Highlighter
        drag(MotionEvent.TOOL_TYPE_STYLUS, 100f to 200f, 300f to 200f)
        assertEquals(2, view.editor.strokeCount)
        assertEquals(Color.BLACK, pixel(200, 200))
        assertEquals(InkRenderer.HIGHLIGHTER_GREY, pixel(200, 208))
    }

    @Test
    fun `with the fast pen on the view stays still until the wait is over`() {
        view.deviceDrawsLive = true
        view.redrawDelayMillis = 900
        drag(MotionEvent.TOOL_TYPE_STYLUS, 100f to 100f, 300f to 100f)
        assertEquals("the stroke is kept at once", 1, view.editor.strokeCount)
        assertEquals("but not painted yet", Color.WHITE, pixel(200, 100))

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals(Color.WHITE, pixel(200, 100))

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals(Color.BLACK, pixel(200, 100))
    }

    @Test
    fun `a second stroke inside the wait moves the wait`() {
        view.deviceDrawsLive = true
        drag(MotionEvent.TOOL_TYPE_STYLUS, 100f to 100f, 300f to 100f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        drag(MotionEvent.TOOL_TYPE_STYLUS, 100f to 300f, 300f to 300f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals("still inside the wait of the second stroke", Color.WHITE, pixel(200, 100))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertEquals(Color.BLACK, pixel(200, 100))
        assertEquals(Color.BLACK, pixel(200, 300))
    }

    @Test
    fun `settle pays what is owed at once`() {
        view.deviceDrawsLive = true
        drag(MotionEvent.TOOL_TYPE_STYLUS, 100f to 100f, 300f to 100f)
        view.settle()
        assertEquals(Color.BLACK, pixel(200, 100))
    }

    @Test
    fun `undo inside the wait does not paint the stroke back`() {
        view.deviceDrawsLive = true
        drag(MotionEvent.TOOL_TYPE_STYLUS, 100f to 100f, 300f to 100f)
        view.undo()
        assertEquals(0, view.editor.strokeCount)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
        assertEquals(Color.WHITE, pixel(200, 100))
    }

    @Test
    fun `clearing the page inside the wait does not paint the strokes back`() {
        view.deviceDrawsLive = true
        drag(MotionEvent.TOOL_TYPE_STYLUS, 100f to 100f, 300f to 100f)
        drag(MotionEvent.TOOL_TYPE_STYLUS, 100f to 300f, 300f to 300f)
        view.clearPage()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
        assertEquals(0, view.editor.strokeCount)
        assertEquals(Color.WHITE, pixel(200, 100))
        assertEquals(Color.WHITE, pixel(200, 300))
    }

    @Test
    fun `redo inside the wait paints the stroke once`() {
        view.deviceDrawsLive = true
        drag(MotionEvent.TOOL_TYPE_STYLUS, 100f to 100f, 300f to 100f)
        view.undo()
        view.redo()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
        assertEquals(1, view.editor.strokeCount)
        assertEquals(Color.BLACK, pixel(200, 100))
    }

    @Test
    fun `a loaded page is painted and a page change starts a new history`() {
        view.setPage(listOf(InkTestData.line(200f, 200f, 600f, 200f, width = 8f)), 1440f, 1920f, PageTemplate.Lined)
        assertNotEquals(Color.WHITE, pixel(200, 100))
        assertTrue(!view.editor.canUndo)
    }

    @Test
    fun `a full page paints in reasonable time and exports`() {
        val page = InkTestData.fullPage(2_000)
        val started = System.currentTimeMillis()
        view.setPage(page, 1440f, 1920f, PageTemplate.DotGrid)
        val took = System.currentTimeMillis() - started
        assertTrue("painting took $took ms", took < 3_000)

        val note = InkNote(template = PageTemplate.DotGrid, pages = listOf(io.github.foxesrcool1.margin.core.ink.InkPageData(page)))
        val png = InkExport.pagePng(note, note.pages[0], InkExport.PREVIEW_SCALE)
        assertTrue(png.size > 1_000)
        java.io.File(io.github.foxesrcool1.margin.support.Screenshots.pathFor("ink_full_page_preview")).apply {
            parentFile?.mkdirs()
            writeBytes(png)
        }
        // The PDF export is not checked here. The test sandbox has no PDF
        // writer, so that one is on the device test list.
    }

    @Test
    fun `with a part of a pdf page on screen the ink still lands in page points`() {
        // A 600 by 800 point page. The view is 720 by 960, so the whole page fits at 1.2.
        view.unitScale = 600f / 1440f
        view.setPage(emptyList(), 600f, 800f, PageTemplate.Blank)

        // Zoom to the bottom right quarter of the page.
        view.showPart(android.graphics.RectF(300f, 400f, 600f, 800f), null)
        drag(MotionEvent.TOOL_TYPE_STYLUS, 0f to 0f, 720f to 960f)
        val zoomed = view.editor.strokes.single()
        assertEquals(300f, zoomed.xs.first(), 0.5f)
        assertEquals(400f, zoomed.ys.first(), 0.5f)
        assertEquals(600f, zoomed.xs.last(), 0.5f)
        assertEquals(800f, zoomed.ys.last(), 0.5f)
        // The pen is as wide on the glass as on a note page, so narrower in points.
        assertEquals(PenWidths.MEDIUM * 600f / 1440f, zoomed.width, 0.001f)

        // Back to the whole page: the same stroke now runs from the middle to the corner.
        view.showPart(android.graphics.RectF(0f, 0f, 600f, 800f), null)
        assertEquals(Color.BLACK, pixel(540, 720))
        assertEquals(Color.WHITE, pixel(180, 240))
    }
}
