package io.github.foxesrcool1.margin.core.ink

import kotlin.math.max
import kotlin.math.min

/** What made a stroke. The eraser is not here: it removes strokes and leaves none. */
enum class InkTool(val code: Int) {
    Pen(1),
    Highlighter(2),
    ;

    companion object {
        fun fromCode(code: Int): InkTool? = entries.firstOrNull { it.code == code }
    }
}

/**
 * One stroke of the pen.
 *
 * The numbers are in page units, not screen pixels. A handwritten note uses a
 * page of 1440 by 1920 units. A PDF page uses PDF points. Either way a stroke
 * stays in the right place when the view is a different size, which is what
 * Step 8 needs for zoom and crop.
 *
 * A stroke never changes after it is made. Undo, redo and the eraser add and
 * remove whole strokes.
 *
 * [xs], [ys] and [pressures] all have the same length. They are arrays and
 * not a list of points because a page can hold 100 000 points, and that many
 * small objects make the tablet stop to collect garbage in the middle of a
 * line.
 */
class InkStroke(
    val tool: InkTool,
    /** The width at full pressure, in page units. */
    val width: Float,
    val xs: FloatArray,
    val ys: FloatArray,
    /** 0 to 1. */
    val pressures: FloatArray,
) {
    init {
        require(xs.size == ys.size && xs.size == pressures.size) { "point arrays differ in length" }
    }

    val pointCount: Int get() = xs.size

    val minX: Float
    val minY: Float
    val maxX: Float
    val maxY: Float

    init {
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (index in xs.indices) {
            left = min(left, xs[index])
            right = max(right, xs[index])
            top = min(top, ys[index])
            bottom = max(bottom, ys[index])
        }
        if (xs.isEmpty()) {
            left = 0f; top = 0f; right = 0f; bottom = 0f
        }
        minX = left
        minY = top
        maxX = right
        maxY = bottom
    }

    /** True when the box around this stroke, grown by [margin], touches the given box. */
    fun boundsTouch(left: Float, top: Float, right: Float, bottom: Float, margin: Float = 0f): Boolean {
        val reach = margin + width / 2f
        return minX - reach <= right && maxX + reach >= left &&
            minY - reach <= bottom && maxY + reach >= top
    }

    override fun equals(other: Any?): Boolean =
        other is InkStroke &&
            tool == other.tool &&
            width == other.width &&
            xs.contentEquals(other.xs) &&
            ys.contentEquals(other.ys) &&
            pressures.contentEquals(other.pressures)

    override fun hashCode(): Int =
        31 * (31 * tool.hashCode() + width.hashCode()) + xs.contentHashCode()
}

/** Collects the points of a stroke while the pen is down. */
class InkStrokeBuilder(val tool: InkTool, val width: Float) {
    /** Only the first [size] values count. Read them, never write them. */
    var xs = FloatArray(64)
        private set
    var ys = FloatArray(64)
        private set
    var pressures = FloatArray(64)
        private set

    var size: Int = 0
        private set

    fun add(x: Float, y: Float, pressure: Float) {
        // The digitiser repeats a point when the pen rests. Those add nothing.
        if (size > 0 && xs[size - 1] == x && ys[size - 1] == y) return
        if (size == xs.size) {
            xs = xs.copyOf(size * 2)
            ys = ys.copyOf(size * 2)
            pressures = pressures.copyOf(size * 2)
        }
        xs[size] = x
        ys[size] = y
        // The pressure the digitiser reports jitters from point to point, and
        // the width of the line would jitter with it. Each point moves only
        // part of the way to the new reading.
        val reading = pressure.coerceIn(0f, 1f)
        pressures[size] = if (size == 0) reading else pressures[size - 1] + (reading - pressures[size - 1]) * PRESSURE_FOLLOW
        size++
    }

    /** Null when the pen never really touched down. */
    fun build(): InkStroke? =
        if (size == 0) null
        else InkStroke(tool, width, xs.copyOf(size), ys.copyOf(size), pressures.copyOf(size))

    private companion object {
        /** How far a point moves from the last pressure to the new reading. 1 would follow every jitter. */
        const val PRESSURE_FOLLOW = 0.5f
    }
}

/** What is printed on the page under the ink. */
enum class PageTemplate(val id: String, val label: String) {
    Blank("blank", "Blank"),
    Lined("lined", "Lined"),
    DotGrid("dots", "Dot grid"),
    ;

    companion object {
        fun fromId(id: String?): PageTemplate = entries.firstOrNull { it.id == id } ?: Blank
    }
}

/** One page: its ink, and the small picture of it, if one was made. */
class InkPageData(
    val strokes: List<InkStroke>,
    /** A PNG. The codec carries it and never looks inside. */
    val preview: ByteArray? = null,
)

/** A whole handwritten note. */
class InkNote(
    val pageWidth: Float = DEFAULT_PAGE_WIDTH,
    val pageHeight: Float = DEFAULT_PAGE_HEIGHT,
    val template: PageTemplate = PageTemplate.Blank,
    val pages: List<InkPageData> = listOf(InkPageData(emptyList())),
) {
    fun withPages(pages: List<InkPageData>): InkNote = InkNote(pageWidth, pageHeight, template, pages)

    fun withTemplate(template: PageTemplate): InkNote = InkNote(pageWidth, pageHeight, template, pages)

    companion object {
        /** The panel of the AiPaper Mini, so one unit is one pixel there. */
        const val DEFAULT_PAGE_WIDTH = 1440f
        const val DEFAULT_PAGE_HEIGHT = 1920f
    }
}
