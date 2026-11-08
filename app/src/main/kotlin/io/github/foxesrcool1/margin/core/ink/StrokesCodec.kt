package io.github.foxesrcool1.margin.core.ink

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

/** Thrown for a `.strokes` file this app cannot read. The message is fit to show in the log. */
class StrokesFormatException(message: String) : Exception(message)

/**
 * Reads and writes a `.strokes` file: the ink of one page.
 *
 * The layout, all big endian:
 *
 * ```
 * 4 bytes   "EINK"
 * u16       format version, 1 today
 * i32       number of strokes
 * per stroke:
 *   u8      tool: 1 pen, 2 highlighter
 *   f32     width at full pressure, in page units
 *   i32     number of points
 *   per point:
 *     f32   x in page units
 *     f32   y in page units
 *     u8    pressure, 0 to 255
 * ```
 *
 * It is this plain on purpose. The files sit in a folder the user owns, and
 * someone else should be able to read them in an afternoon with no library.
 * `docs/decisions/0008-ink-engine.md` says why this is not the Jetpack Ink
 * storage format the plan first named.
 *
 * A reader refuses a newer version than it knows, rather than guess.
 */
object StrokesCodec {

    const val VERSION = 1

    private val MAGIC = byteArrayOf('E'.code.toByte(), 'I'.code.toByte(), 'N'.code.toByte(), 'K'.code.toByte())

    // A crafted or damaged file must not make the app ask for gigabytes.
    private const val MAX_STROKES = 200_000
    private const val MAX_POINTS_PER_STROKE = 1_000_000

    fun encode(strokes: List<InkStroke>): ByteArray {
        val bytes = ByteArrayOutputStream(16 + strokes.sumOf { 9 + it.pointCount * 9 })
        DataOutputStream(bytes).use { out ->
            out.write(MAGIC)
            out.writeShort(VERSION)
            out.writeInt(strokes.size)
            strokes.forEach { stroke ->
                out.writeByte(stroke.tool.code)
                out.writeFloat(stroke.width)
                out.writeInt(stroke.pointCount)
                for (index in 0 until stroke.pointCount) {
                    out.writeFloat(stroke.xs[index])
                    out.writeFloat(stroke.ys[index])
                    out.writeByte((stroke.pressures[index].coerceIn(0f, 1f) * 255f + 0.5f).toInt())
                }
            }
        }
        return bytes.toByteArray()
    }

    @Throws(StrokesFormatException::class)
    fun decode(bytes: ByteArray): List<InkStroke> {
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val magic = ByteArray(4)
                input.readFully(magic)
                if (!magic.contentEquals(MAGIC)) throw StrokesFormatException("This is not a strokes file")

                val version = input.readUnsignedShort()
                if (version < 1 || version > VERSION) {
                    throw StrokesFormatException("Strokes format $version is newer than this app knows ($VERSION)")
                }

                val strokeCount = input.readInt()
                if (strokeCount < 0 || strokeCount > MAX_STROKES) {
                    throw StrokesFormatException("Stroke count $strokeCount is not believable")
                }

                val strokes = ArrayList<InkStroke>(strokeCount)
                repeat(strokeCount) {
                    val toolCode = input.readUnsignedByte()
                    val width = input.readFloat()
                    val pointCount = input.readInt()
                    if (pointCount < 0 || pointCount > MAX_POINTS_PER_STROKE) {
                        throw StrokesFormatException("Point count $pointCount is not believable")
                    }
                    // Nine bytes a point. Checking before the arrays are made
                    // keeps a lying header from costing memory.
                    if (pointCount.toLong() * 9L > input.available().toLong()) {
                        throw StrokesFormatException("The file ends in the middle of a stroke")
                    }
                    val xs = FloatArray(pointCount)
                    val ys = FloatArray(pointCount)
                    val pressures = FloatArray(pointCount)
                    for (index in 0 until pointCount) {
                        xs[index] = input.readFloat()
                        ys[index] = input.readFloat()
                        pressures[index] = input.readUnsignedByte() / 255f
                    }
                    val tool = InkTool.fromCode(toolCode)
                    val sane = width.isFinite() && width > 0f &&
                        xs.all { it.isFinite() } && ys.all { it.isFinite() }
                    // A tool this version does not know means a newer app wrote
                    // the file. Refuse it: skipping the stroke would delete it
                    // on the next save.
                    if (tool == null) throw StrokesFormatException("Unknown pen tool $toolCode")
                    // Numbers that cannot be drawn are left out. The rest of
                    // the page is still worth having.
                    if (sane && pointCount > 0) {
                        strokes += InkStroke(tool, width, xs, ys, pressures)
                    }
                }
                return strokes
            }
        } catch (end: EOFException) {
            throw StrokesFormatException("The file ends too early")
        }
    }
}
