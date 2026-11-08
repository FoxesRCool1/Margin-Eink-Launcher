package io.github.foxesrcool1.margin.ui.reading.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.pdf.CropDetector
import io.github.foxesrcool1.margin.core.pdf.PageBox
import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt

private const val TAG = "PdfPages"

/**
 * Renders PDF pages with the platform `PdfRenderer`.
 *
 * See `docs/decisions/0010-pdf-renderer.md` for why this one. What matters
 * for the code:
 *
 * - `PdfRenderer` is not thread safe and lets only one page be open at a
 *   time, so every method here is synchronized and the caller uses one
 *   background thread.
 * - Memory. The tablet has 4 GB and a scanned book has pages of 30 million
 *   pixels. A page is never rendered whole at zoom. Only the part on screen
 *   is rendered, straight into a bitmap the size of the view, whatever the
 *   zoom step. That is about 11 MB a screen on this panel, always.
 */
class PdfPages private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    val pageCount: Int = renderer.pageCount

    private val sizes = HashMap<Int, PageBox?>()
    private val contentBoxes = HashMap<Int, PageBox?>()

    /** The whole page, in PDF points, origin top left. Null when the page is damaged. */
    @Synchronized
    fun pageBox(index: Int): PageBox? = sizes.getOrPut(index) {
        runCatching {
            renderer.openPage(index).use { page -> PageBox(0f, 0f, page.width.toFloat(), page.height.toFloat()) }
        }.onFailure { AppLog.e(TAG, "Could not open page ${index + 1}", it) }.getOrNull()
    }

    /** The box around what is printed on the page, for "crop margins". Found once per page. Null when the page is damaged. */
    @Synchronized
    fun contentBox(index: Int): PageBox? {
        val whole = pageBox(index)
        if (whole == null) return null
        return contentBoxes.getOrPut(index) {
            runCatching {
                val width = CROP_PROBE_WIDTH
                val height = (width * whole.height / whole.width).roundToInt().coerceIn(1, width * 4)
                val probe = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                probe.eraseColor(Color.WHITE)
                renderer.openPage(index).use { page ->
                    page.render(probe, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
                val pixels = IntArray(width * height)
                probe.getPixels(pixels, 0, width, 0, 0, width, height)
                probe.recycle()
                CropDetector.contentBox(pixels, width, height, whole.width, whole.height)
            }.getOrElse {
                AppLog.w(TAG, "Could not find the margins of page ${index + 1}", it)
                whole
            }
        }
    }

    /**
     * Renders [part] of a page into a new bitmap that fits inside
     * [maxWidth] by [maxHeight] and keeps the shape of [part].
     */
    @Synchronized
    fun render(index: Int, part: PageBox, maxWidth: Int, maxHeight: Int): Bitmap? = runCatching {
        val scale = minOf(maxWidth / part.width, maxHeight / part.height)
        val width = (part.width * scale).roundToInt().coerceAtLeast(1)
        val height = (part.height * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // A PDF page has no paper of its own. Without this the page is see-through.
        bitmap.eraseColor(Color.WHITE)
        val matrix = Matrix().apply {
            postTranslate(-part.left, -part.top)
            postScale(scale, scale)
        }
        renderer.openPage(index).use { page ->
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
        bitmap
    }.onFailure { AppLog.e(TAG, "Could not render page ${index + 1}", it) }.getOrNull()

    @Synchronized
    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        private const val CROP_PROBE_WIDTH = 160

        /** Null when the file is not a PDF the platform can read, for example one with a password. */
        fun open(file: File): PdfPages? = runCatching {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                PdfPages(descriptor, PdfRenderer(descriptor))
            } catch (problem: Throwable) {
                descriptor.close()
                throw problem
            }
        }.onFailure { AppLog.e(TAG, "Could not open ${file.name}", it) }.getOrNull()
    }
}
