package io.github.foxesrcool1.margin.core.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class StrokesCodecTest {

    @Test
    fun `an empty page survives a round trip`() {
        assertEquals(emptyList<InkStroke>(), StrokesCodec.decode(StrokesCodec.encode(emptyList())))
    }

    @Test
    fun `strokes come back with tool, width and points`() {
        val strokes = listOf(
            InkTestData.line(10f, 20f, 300.5f, 400.25f, width = 3f),
            InkTestData.line(0f, 0f, 1440f, 1920f, width = 44f, tool = InkTool.Highlighter),
            InkTestData.dot(7f, 9f),
        )
        val back = StrokesCodec.decode(StrokesCodec.encode(strokes))
        assertEquals(3, back.size)
        assertEquals(InkTool.Highlighter, back[1].tool)
        assertEquals(44f, back[1].width, 0f)
        assertEquals(300.5f, back[0].xs[1], 0f)
        assertEquals(400.25f, back[0].ys[1], 0f)
        assertEquals(1, back[2].pointCount)
    }

    @Test
    fun `pressure keeps one part in 255`() {
        val stroke = InkTestData.squiggle(100f, 100f)
        val back = StrokesCodec.decode(StrokesCodec.encode(listOf(stroke))).single()
        for (index in 0 until stroke.pointCount) {
            assertEquals(stroke.pressures[index], back.pressures[index], 1f / 255f)
        }
    }

    @Test
    fun `saving twice gives the same bytes`() {
        val once = StrokesCodec.encode(InkTestData.fullPage(50))
        val twice = StrokesCodec.encode(StrokesCodec.decode(once))
        assertTrue(once.contentEquals(twice))
    }

    @Test
    fun `something that is not a strokes file is refused`() {
        expectRefused("not ink at all".toByteArray())
        expectRefused(ByteArray(0))
        expectRefused(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `a newer format is refused and not guessed at`() {
        val bytes = StrokesCodec.encode(listOf(InkTestData.dot(1f, 1f)))
        bytes[5] = 99
        expectRefused(bytes)
    }

    @Test
    fun `a file cut short is refused`() {
        val bytes = StrokesCodec.encode(InkTestData.fullPage(10))
        expectRefused(bytes.copyOf(bytes.size - 5))
        expectRefused(bytes.copyOf(12))
    }

    @Test
    fun `a header that lies about its size costs no memory`() {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            write("EINK".toByteArray())
            writeShort(1)
            writeInt(1)
            writeByte(1)
            writeFloat(5f)
            writeInt(900_000) // and then no points at all
        }
        expectRefused(out.toByteArray())

        val manyStrokes = ByteArrayOutputStream()
        DataOutputStream(manyStrokes).apply {
            write("EINK".toByteArray())
            writeShort(1)
            writeInt(Int.MAX_VALUE)
        }
        expectRefused(manyStrokes.toByteArray())
    }

    @Test
    fun `a stroke from an unknown tool refuses the file so a save cannot delete it`() {
        val bytes = StrokesCodec.encode(listOf(InkTestData.dot(1f, 1f), InkTestData.dot(2f, 2f)))
        bytes[10] = 77 // the tool byte of the first stroke
        expectRefused(bytes)
    }

    @Test
    fun `a full page of 2000 strokes reads in well under a second`() {
        val page = InkTestData.fullPage(2_000)
        val bytes = StrokesCodec.encode(page)
        StrokesCodec.decode(bytes) // warm up
        val started = System.nanoTime()
        val back = StrokesCodec.decode(bytes)
        val millis = (System.nanoTime() - started) / 1_000_000
        assertEquals(2_000, back.size)
        // The tablet is several times slower than a desktop. 150 ms here
        // leaves room for that inside the one second the plan allows.
        assertTrue("decode took $millis ms", millis < 150)
        assertTrue("a page is ${bytes.size} bytes", bytes.size < 1_200_000)
    }

    private fun expectRefused(bytes: ByteArray) {
        try {
            StrokesCodec.decode(bytes)
            fail("should have been refused")
        } catch (expected: StrokesFormatException) {
            assertTrue(expected.message!!.isNotBlank())
        }
    }
}
