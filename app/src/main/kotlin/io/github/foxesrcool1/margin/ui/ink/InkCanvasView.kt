package io.github.foxesrcool1.margin.ui.ink

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import io.github.foxesrcool1.margin.core.ink.EraserGeometry
import io.github.foxesrcool1.margin.core.ink.InkNote
import io.github.foxesrcool1.margin.core.ink.InkPageEditor
import io.github.foxesrcool1.margin.core.ink.InkStroke
import io.github.foxesrcool1.margin.core.ink.InkStrokeBuilder
import io.github.foxesrcool1.margin.core.ink.InkTool
import io.github.foxesrcool1.margin.core.ink.PageTemplate
import io.github.foxesrcool1.margin.core.log.AppLog
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val TAG = "InkCanvas"

/** What the pen does right now. */
enum class InkMode { Pen, Highlighter, Eraser }

/**
 * The handwriting surface. Writing, Journal and the PDF reader all use it.
 *
 * How it keeps the e-ink panel calm:
 *
 * - Finished ink lives in one bitmap. `onDraw` is a single bitmap copy.
 * - While the pen is down, only the newest piece of the line is painted into
 *   that bitmap, and only the small box around it is handed to the panel.
 * - Undo, redo and the eraser repaint only the box around the strokes that
 *   changed.
 *
 * Who paints while the pen is down is a switch, [deviceDrawsLive]:
 *
 * - false: this view paints each piece as it arrives.
 * - true: the tablet paints the line by itself (the ViWoods fast pen). This
 *   view keeps recording the points, stays still, and paints the real strokes
 *   once the pen has been up for [redrawDelayMillis]. Painting sooner shows a
 *   double line, because the fast overlay is still on the glass.
 *
 * Only a stylus draws. A finger never does. A finger swipe turns the page,
 * unless the pen is near: a hand that rests on the glass while writing must
 * not turn pages.
 */
@SuppressLint("ViewConstructor")
class InkCanvasView(context: Context) : View(context) {

    var mode: InkMode = InkMode.Pen

    /** Pen width at full pressure, in page units. */
    var penWidth: Float = PenWidths.MEDIUM

    var highlighterWidth: Float = 44f

    /** In page units. */
    var eraserRadius: Float = 18f

    /**
     * How many page units one unit of pen width stands for. A handwritten
     * page is 1440 units wide and this is 1. A PDF page is about 600 points
     * wide, so the same pen has to be narrower in page units to look the same
     * on the glass. The host sets this to `pageWidth / 1440`.
     */
    var unitScale: Float = 1f

    var deviceDrawsLive: Boolean = false

    var redrawDelayMillis: Long = 900L

    /** Called after every change to the ink, on the main thread. */
    var onInkChanged: (() -> Unit)? = null

    /** A finger swipe: -1 for the previous page, +1 for the next. */
    var onPageSwipe: ((direction: Int) -> Unit)? = null

    /** A finger tap, in view pixels. The PDF reader uses it for tap zones. */
    var onFingerTap: ((x: Float, y: Float) -> Unit)? = null

    /** The stylus changed between tip and eraser end, or the mode changed. For the fast pen tool type. */
    var onErasingChanged: ((erasing: Boolean) -> Unit)? = null

    /** Called when the view gets a size, so a new note can take the shape of the screen it is made on. */
    var onSized: ((width: Int, height: Int) -> Unit)? = null

    var editor: InkPageEditor = InkPageEditor()
        private set

    private var pageWidth = InkNote.DEFAULT_PAGE_WIDTH
    private var pageHeight = InkNote.DEFAULT_PAGE_HEIGHT
    private var template = PageTemplate.Blank

    /** A picture under the ink, for example a PDF page, and the part of the page it shows. */
    private var backdrop: Bitmap? = null
    private var backdropBox: RectF? = null

    /** The part of the page on screen, in page units. Null shows the whole page. */
    private var viewport: RectF? = null

    private val renderer = InkRenderer()
    private var cache: Bitmap? = null
    private var cacheCanvas: Canvas? = null
    private val whitePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val bitmapPaint = Paint().apply { isFilterBitmap = true }

    // Page to view: view = page * scale + offset.
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    private var builder: InkStrokeBuilder? = null
    private var paintedUpTo = 0
    private var erasingNow = false
    private var lastReportedErasing: Boolean? = null
    private var lastPageX = 0f
    private var lastPageY = 0f

    /** Strokes the tablet painted and this view still owes, and boxes to clean after a device erase. */
    private val owedStrokes = ArrayList<InkStroke>()
    private val owedBox = RectF()
    private val payOwed = Runnable { paintOwed() }

    // Not View.postDelayed: that one holds its work back until the view is
    // attached to a window, and a wait that never ends would lose the paint.
    private val timer = android.os.Handler(android.os.Looper.getMainLooper())

    // Far in the past, so the first finger swipe after start-up counts.
    private var lastPenMillis = -PALM_QUIET_MILLIS
    private var fingerDownX = 0f
    private var fingerDownY = 0f
    private var fingerIsSwipe = false

    // -- Content --------------------------------------------------------------

    /** Shows a page. The undo history starts again. */
    fun setPage(
        strokes: List<InkStroke>,
        pageWidth: Float,
        pageHeight: Float,
        template: PageTemplate,
        backdrop: Bitmap? = null,
    ) {
        timer.removeCallbacks(payOwed)
        owedStrokes.clear()
        owedBox.setEmpty()
        builder = null
        this.editor = InkPageEditor(strokes)
        this.pageWidth = pageWidth
        this.pageHeight = pageHeight
        this.template = template
        this.backdrop = backdrop
        this.backdropBox = null
        this.viewport = null
        computeTransform()
        rebuildAll()
    }

    /**
     * Shows one part of the page, with a picture of exactly that part under
     * the ink. The ink and its undo history stay. This is how the PDF reader
     * changes zoom, crop and screen without the strokes moving on the page.
     */
    fun showPart(pageBox: RectF, picture: Bitmap?) {
        settle()
        viewport = RectF(pageBox)
        backdrop = picture
        backdropBox = RectF(pageBox)
        computeTransform()
        rebuildAll()
    }

    fun setTemplate(template: PageTemplate) {
        if (this.template == template) return
        this.template = template
        rebuildAll()
    }

    /** Swaps the picture under the ink and keeps the ink and its undo history. */
    fun setBackdrop(backdrop: Bitmap?) {
        this.backdrop = backdrop
        this.backdropBox = null
        rebuildAll()
    }

    fun undo() = afterHistoryChange(editor.undo())

    fun redo() = afterHistoryChange(editor.redo())

    fun clearPage() = afterHistoryChange(editor.clear())

    private fun afterHistoryChange(changed: List<InkStroke>) {
        if (changed.isEmpty()) return
        // A stroke still owed that this change takes away must not be painted back later.
        forgetOwed(changed.filter { stroke -> owedStrokes.any { it === stroke } })
        rebuildAround(changed)
        onInkChanged?.invoke()
    }

    /** The page box in view pixels, for a host that places things around it. */
    fun pageBoxInView(): RectF =
        RectF(offsetX, offsetY, offsetX + pageWidth * scale, offsetY + pageHeight * scale)

    // -- Layout ---------------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        cache?.recycle()
        cache = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        cacheCanvas = Canvas(cache!!)
        computeTransform()
        rebuildAll()
        onSized?.invoke(w, h)
    }

    private fun computeTransform() {
        if (width <= 0 || height <= 0) return
        val part = viewport
        if (part == null || part.isEmpty) {
            scale = min(width / pageWidth, height / pageHeight)
            offsetX = (width - pageWidth * scale) / 2f
            offsetY = (height - pageHeight * scale) / 2f
        } else {
            scale = min(width / part.width(), height / part.height())
            offsetX = (width - part.width() * scale) / 2f - part.left * scale
            offsetY = (height - part.height() * scale) / 2f - part.top * scale
        }
    }

    override fun onDraw(canvas: Canvas) {
        cache?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    // -- Painting into the cache -----------------------------------------------

    private fun rebuildAll() {
        val target = cacheCanvas ?: return
        val started = SystemClock.elapsedRealtime()
        target.drawColor(Color.WHITE)
        paintPage(target, null)
        invalidate()
        val took = SystemClock.elapsedRealtime() - started
        if (editor.strokeCount > 200 || took > 100) {
            AppLog.i(TAG, "Painted ${editor.strokeCount} strokes in $took ms")
        }
    }

    /** Repaints the box around these strokes and nothing else. E-ink rule 7. */
    private fun rebuildAround(strokes: List<InkStroke>) {
        if (strokes.isEmpty()) return
        val box = RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        strokes.forEach { stroke ->
            val reach = stroke.width / 2f + 2f
            box.left = min(box.left, stroke.minX - reach)
            box.top = min(box.top, stroke.minY - reach)
            box.right = max(box.right, stroke.maxX + reach)
            box.bottom = max(box.bottom, stroke.maxY + reach)
        }
        rebuildPageBox(box)
    }

    private fun rebuildPageBox(pageBox: RectF) {
        val target = cacheCanvas ?: return
        val left = floor(pageBox.left * scale + offsetX).toInt() - 2
        val top = floor(pageBox.top * scale + offsetY).toInt() - 2
        val right = ceil(pageBox.right * scale + offsetX).toInt() + 2
        val bottom = ceil(pageBox.bottom * scale + offsetY).toInt() + 2

        target.save()
        target.clipRect(left, top, right, bottom)
        target.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), whitePaint)
        paintPage(target, pageBox)
        target.restore()
        invalidate(left, top, right, bottom)
    }

    private fun paintPage(target: Canvas, onlyTouching: RectF?) {
        target.save()
        target.translate(offsetX, offsetY)
        target.scale(scale, scale)
        // With only a part of the page on screen, ink from the rest of the
        // page must not show up in the white around that part.
        viewport?.let { target.clipRect(it) }
        backdrop?.takeIf { !it.isRecycled }?.let {
            target.drawBitmap(it, null, backdropBox ?: RectF(0f, 0f, pageWidth, pageHeight), bitmapPaint)
        }
        renderer.drawTemplate(target, template, pageWidth, pageHeight)
        val strokes = editor.strokes
        if (onlyTouching == null) {
            renderer.drawStrokes(target, strokes)
        } else {
            strokes.forEach { stroke ->
                if (stroke.boundsTouch(onlyTouching.left, onlyTouching.top, onlyTouching.right, onlyTouching.bottom)) {
                    renderer.drawStroke(target, stroke)
                }
            }
        }
        target.restore()
    }

    // -- Input ------------------------------------------------------------------

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (isStylus(event)) lastPenMillis = SystemClock.uptimeMillis()
        return super.onHoverEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean =
        if (isStylus(event)) onPenEvent(event) else onFingerEvent(event)

    private fun isStylus(event: MotionEvent): Boolean {
        val tool = event.getToolType(0)
        return tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER ||
            // The emulator on the dev machine has a mouse and no pen. See DevEnvironment.
            fingerDraws
    }

    /** Debug builds on an emulator only. On a real device this is always false. */
    var fingerDraws: Boolean = io.github.foxesrcool1.margin.core.eink.DevEnvironment.fingerDraws

    private fun onPenEvent(event: MotionEvent): Boolean {
        lastPenMillis = SystemClock.uptimeMillis()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // The system batches touch events to the frame rate. A pen
                // wants every point as soon as it exists.
                requestUnbufferedDispatch(event)
                parent?.requestDisallowInterceptTouchEvent(true)
                timer.removeCallbacks(payOwed)

                erasingNow = mode == InkMode.Eraser ||
                    event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER ||
                    (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
                if (lastReportedErasing != erasingNow) {
                    lastReportedErasing = erasingNow
                    onErasingChanged?.invoke(erasingNow)
                }

                lastPageX = toPageX(event.x)
                lastPageY = toPageY(event.y)
                if (erasingNow) {
                    builder = null
                    eraseAlong(lastPageX, lastPageY, lastPageX, lastPageY)
                } else {
                    val tool = if (mode == InkMode.Highlighter) InkTool.Highlighter else InkTool.Pen
                    val width = (if (tool == InkTool.Highlighter) highlighterWidth else penWidth) * unitScale
                    builder = InkStrokeBuilder(tool, width).also {
                        it.add(lastPageX, lastPageY, event.pressure)
                    }
                    paintedUpTo = 0
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.historySize) {
                    penMovedTo(event.getHistoricalX(index), event.getHistoricalY(index), event.getHistoricalPressure(index))
                }
                penMovedTo(event.x, event.y, event.pressure)
                paintNewPieces()
            }

            MotionEvent.ACTION_UP -> {
                penMovedTo(event.x, event.y, event.pressure)
                paintNewPieces()
                finishStroke()
            }

            MotionEvent.ACTION_CANCEL -> {
                // The system took the gesture away. What was painted so far
                // has to come off the glass again.
                val abandoned = builder?.build()
                builder = null
                if (abandoned != null && !deviceDrawsLive) rebuildAround(listOf(abandoned))
                if (deviceDrawsLive) schedulePayOwed()
            }
        }
        return true
    }

    private fun penMovedTo(viewX: Float, viewY: Float, pressure: Float) {
        val x = toPageX(viewX)
        val y = toPageY(viewY)
        if (erasingNow) {
            eraseAlong(lastPageX, lastPageY, x, y)
        } else {
            builder?.add(x, y, pressure)
        }
        lastPageX = x
        lastPageY = y
    }

    private fun paintNewPieces() {
        val current = builder ?: return
        if (deviceDrawsLive) return
        val last = current.size - 1
        if (last <= paintedUpTo) return
        val target = cacheCanvas ?: return

        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (index in paintedUpTo..last) {
            left = min(left, current.x(index))
            right = max(right, current.x(index))
            top = min(top, current.y(index))
            bottom = max(bottom, current.y(index))
        }

        target.save()
        target.translate(offsetX, offsetY)
        target.scale(scale, scale)
        renderer.drawLive(target, current, paintedUpTo, last)
        target.restore()
        paintedUpTo = last

        val reach = current.width / 2f + 2f
        invalidate(
            floor((left - reach) * scale + offsetX).toInt() - 1,
            floor((top - reach) * scale + offsetY).toInt() - 1,
            ceil((right + reach) * scale + offsetX).toInt() + 1,
            ceil((bottom + reach) * scale + offsetY).toInt() + 1,
        )
    }

    private fun finishStroke() {
        val current = builder
        builder = null
        if (erasingNow) {
            if (deviceDrawsLive) schedulePayOwed()
            return
        }
        val stroke = current?.build() ?: return
        editor.add(stroke)
        when {
            deviceDrawsLive -> {
                owedStrokes += stroke
                schedulePayOwed()
            }
            // A dot was never painted live, because one point makes no piece.
            stroke.pointCount == 1 -> rebuildAround(listOf(stroke))
        }
        onInkChanged?.invoke()
    }

    private fun eraseAlong(x0: Float, y0: Float, x1: Float, y1: Float) {
        val hit = EraserGeometry.hits(editor.strokes, x0, y0, x1, y1, eraserRadius * unitScale)
        if (hit.isEmpty()) return
        val removed = editor.remove(hit)
        if (removed.isEmpty()) return
        if (deviceDrawsLive) {
            // The fast overlay is on the glass. Repaint under it once it is gone.
            forgetOwed(removed)
        } else {
            rebuildAround(removed)
        }
        onInkChanged?.invoke()
    }

    /**
     * With the fast pen the tablet's own line may still be on the glass for strokes
     * that left the page. Their place is cleaned when the wait is over, and an owed
     * stroke is dropped so it cannot be painted back.
     */
    private fun forgetOwed(gone: List<InkStroke>) {
        gone.forEach { stroke ->
            owedBox.union(stroke.minX - stroke.width, stroke.minY - stroke.width, stroke.maxX + stroke.width, stroke.maxY + stroke.width)
        }
        owedStrokes.removeAll { owed -> gone.any { it === owed } }
    }

    private fun schedulePayOwed() {
        timer.removeCallbacks(payOwed)
        timer.postDelayed(payOwed, redrawDelayMillis)
    }

    /** The pen has been up long enough. Paint what the tablet painted for us, for real. */
    private fun paintOwed() {
        if (builder != null) return
        val target = cacheCanvas ?: return
        if (!owedBox.isEmpty) {
            rebuildPageBox(RectF(owedBox))
            owedBox.setEmpty()
        }
        if (owedStrokes.isNotEmpty()) {
            target.save()
            target.translate(offsetX, offsetY)
            target.scale(scale, scale)
            renderer.drawStrokes(target, owedStrokes)
            target.restore()
            val box = RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            owedStrokes.forEach { stroke ->
                val reach = stroke.width / 2f + 2f
                box.left = min(box.left, stroke.minX - reach)
                box.top = min(box.top, stroke.minY - reach)
                box.right = max(box.right, stroke.maxX + reach)
                box.bottom = max(box.bottom, stroke.maxY + reach)
            }
            owedStrokes.clear()
            invalidate(
                floor(box.left * scale + offsetX).toInt() - 1,
                floor(box.top * scale + offsetY).toInt() - 1,
                ceil(box.right * scale + offsetX).toInt() + 1,
                ceil(box.bottom * scale + offsetY).toInt() + 1,
            )
        }
    }

    /** Paints anything still owed right now. Call before a page turn or a save of the preview. */
    fun settle() {
        timer.removeCallbacks(payOwed)
        paintOwed()
    }

    private fun onFingerEvent(event: MotionEvent): Boolean {
        if (onPageSwipe == null && onFingerTap == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Palm rejection: a finger that lands while the pen is on or
                // near the glass is a resting hand.
                val penIsNear = SystemClock.uptimeMillis() - lastPenMillis < PALM_QUIET_MILLIS
                fingerIsSwipe = !penIsNear && builder == null
                fingerDownX = event.x
                fingerDownY = event.y
            }

            MotionEvent.ACTION_POINTER_DOWN -> fingerIsSwipe = false

            MotionEvent.ACTION_UP -> {
                if (!fingerIsSwipe) return true
                val dx = event.x - fingerDownX
                val dy = event.y - fingerDownY
                val threshold = SWIPE_DP * resources.displayMetrics.density
                val tapSlop = TAP_DP * resources.displayMetrics.density
                if (abs(dx) > threshold && abs(dx) > abs(dy) * 2f) {
                    onPageSwipe?.invoke(if (dx < 0) 1 else -1)
                } else if (abs(dx) < tapSlop && abs(dy) < tapSlop) {
                    onFingerTap?.invoke(event.x, event.y)
                }
            }
        }
        return true
    }

    private fun toPageX(viewX: Float): Float = (viewX - offsetX) / scale

    private fun toPageY(viewY: Float): Float = (viewY - offsetY) / scale

    override fun onDetachedFromWindow() {
        timer.removeCallbacks(payOwed)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val PALM_QUIET_MILLIS = 700L
        const val SWIPE_DP = 72f
        const val TAP_DP = 12f
    }
}

/** The three pen widths, in page units, and the vendor width range that looks closest. */
object PenWidths {
    const val FINE = 3f
    const val MEDIUM = 5f
    const val BOLD = 8f

    val all: List<Float> = listOf(FINE, MEDIUM, BOLD)

    fun label(width: Float): String = when (width) {
        FINE -> "Fine"
        BOLD -> "Bold"
        else -> "Medium"
    }

    /**
     * The fast pen has its own idea of width. These pairs are a first guess
     * and have to be matched by eye on the tablet, so that the line does not
     * jump when the real stroke replaces the fast one.
     */
    fun vendorRange(width: Float): Pair<Int, Int> = when (width) {
        FINE -> 1 to 2
        BOLD -> 2 to 5
        else -> 1 to 3
    }
}
