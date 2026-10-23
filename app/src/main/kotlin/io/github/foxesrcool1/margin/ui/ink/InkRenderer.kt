package io.github.foxesrcool1.margin.ui.ink

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import io.github.foxesrcool1.margin.core.ink.InkStroke
import io.github.foxesrcool1.margin.core.ink.InkStrokeBuilder
import io.github.foxesrcool1.margin.core.ink.InkTool
import io.github.foxesrcool1.margin.core.ink.PageTemplate
import io.github.foxesrcool1.margin.core.ink.TemplateGeometry
import kotlin.math.abs

/**
 * Paints strokes and page templates.
 *
 * Everything is drawn in page units. The caller scales the canvas first, so
 * the screen, the preview picture, the PNG export and the PDF export all come
 * from this one piece of code and cannot drift apart.
 *
 * The pen is black and its width follows the pressure. The highlighter is a
 * flat grey, painted in DARKEN mode: grey over white gives grey, grey over
 * grey gives the same grey, and grey over black leaves the black. So it never
 * hides pen strokes or the text of a PDF, the order of painting does not
 * matter, and a line that crosses itself does not get darker.
 */
class InkRenderer {

    private val penPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.BLACK
    }

    private val dotPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.BLACK
    }

    private val highlighterPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.ROUND
        color = HIGHLIGHTER_GREY
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
    }

    private val templatePaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = TEMPLATE_GREY
    }

    private val templateDotPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = TEMPLATE_DOT_GREY
    }

    private val path = Path()

    fun drawTemplate(canvas: Canvas, template: PageTemplate, pageWidth: Float, pageHeight: Float) {
        when (template) {
            PageTemplate.Blank -> Unit

            PageTemplate.Lined -> TemplateGeometry.lineYs(pageHeight).forEach { y ->
                canvas.drawLine(
                    TemplateGeometry.SIDE_MARGIN, y,
                    pageWidth - TemplateGeometry.SIDE_MARGIN, y,
                    templatePaint,
                )
            }

            PageTemplate.DotGrid -> {
                val rows = TemplateGeometry.dotRows(pageHeight)
                TemplateGeometry.dotColumns(pageWidth).forEach { x ->
                    rows.forEach { y -> canvas.drawCircle(x, y, 2.2f, templateDotPaint) }
                }
            }
        }
    }

    fun drawStrokes(canvas: Canvas, strokes: List<InkStroke>) {
        strokes.forEach { drawStroke(canvas, it) }
    }

    fun drawStroke(canvas: Canvas, stroke: InkStroke) {
        drawPoints(canvas, stroke.tool, stroke.width, stroke.xs, stroke.ys, stroke.pressures, stroke.pointCount)
    }

    /** Paints a stroke that is still being written, exactly as [drawStroke] will paint it once it is done. */
    fun drawLive(canvas: Canvas, builder: InkStrokeBuilder) {
        drawPoints(canvas, builder.tool, builder.width, builder.xs, builder.ys, builder.pressures, builder.size)
    }

    /**
     * The line does not go straight from point to point. It curves through the
     * middle of each pair of points, with the point itself as the bend. A pen
     * that moves fast leaves points far apart, and straight pieces between
     * them turn a round letter into a polygon.
     *
     * Piece j belongs to point j: it runs from the middle before that point to
     * the middle after it. The first piece starts at the first point and the
     * last piece ends at the last point, so the line ends where the pen did.
     */
    private fun drawPoints(
        canvas: Canvas,
        tool: InkTool,
        width: Float,
        xs: FloatArray,
        ys: FloatArray,
        pressures: FloatArray,
        count: Int,
    ) {
        if (count == 0) return

        if (count == 1) {
            // A tap of the pen: a dot.
            if (tool == InkTool.Pen) {
                canvas.drawCircle(xs[0], ys[0], penWidth(width, pressures[0]) / 2f, dotPaint)
            } else {
                highlighterPaint.strokeWidth = width
                canvas.drawPoint(xs[0], ys[0], highlighterPaint)
            }
            return
        }

        if (tool == InkTool.Highlighter) {
            path.rewind()
            path.moveTo(xs[0], ys[0])
            for (index in 0 until count) addPiece(xs, ys, count, index)
            highlighterPaint.strokeWidth = width
            canvas.drawPath(path, highlighterPaint)
            return
        }

        // The width changes along a pen stroke. One draw call per piece is
        // slow on a page of 100 000 points, so neighbours of nearly the same
        // width go into one path.
        var runWidth = -1f
        path.rewind()
        for (index in 0 until count) {
            val pieceWidth = quantise(penWidth(width, pressures[index]))
            if (abs(pieceWidth - runWidth) > 0.001f) {
                if (runWidth > 0f) {
                    penPaint.strokeWidth = runWidth
                    canvas.drawPath(path, penPaint)
                }
                path.rewind()
                if (index == 0) {
                    path.moveTo(xs[0], ys[0])
                } else {
                    path.moveTo((xs[index - 1] + xs[index]) / 2f, (ys[index - 1] + ys[index]) / 2f)
                }
                runWidth = pieceWidth
            }
            addPiece(xs, ys, count, index)
        }
        if (runWidth > 0f) {
            penPaint.strokeWidth = runWidth
            canvas.drawPath(path, penPaint)
        }
    }

    /** Adds piece [index] to [path], which already stands where that piece starts. */
    private fun addPiece(xs: FloatArray, ys: FloatArray, count: Int, index: Int) {
        when (index) {
            0 -> path.lineTo((xs[0] + xs[1]) / 2f, (ys[0] + ys[1]) / 2f)
            count - 1 -> path.lineTo(xs[index], ys[index])
            else -> path.quadTo(
                xs[index], ys[index],
                (xs[index] + xs[index + 1]) / 2f, (ys[index] + ys[index + 1]) / 2f,
            )
        }
    }

    companion object {
        const val HIGHLIGHTER_GREY: Int = 0xFFC4C4C4.toInt()
        private const val TEMPLATE_GREY: Int = 0xFF9A9A9A.toInt()
        private const val TEMPLATE_DOT_GREY: Int = 0xFF6E6E6E.toInt()

        /** A light touch still leaves a line that an e-ink panel can show. */
        fun penWidth(fullWidth: Float, pressure: Float): Float =
            fullWidth * (0.4f + 0.6f * pressure.coerceIn(0f, 1f))

        private fun quantise(width: Float): Float = (width * 2f).toInt().coerceAtLeast(1) / 2f
    }
}
