package io.github.foxesrcool1.margin.core.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InkPageEditorTest {

    private val a = InkTestData.dot(1f, 1f)
    private val b = InkTestData.dot(2f, 2f)
    private val c = InkTestData.dot(3f, 3f)

    @Test
    fun `undo takes the last stroke away and redo brings it back`() {
        val editor = InkPageEditor()
        editor.add(a)
        editor.add(b)
        assertEquals(listOf(b), editor.undo())
        assertEquals(listOf(a), editor.strokes)
        assertEquals(listOf(b), editor.redo())
        assertEquals(listOf(a, b), editor.strokes)
    }

    @Test
    fun `undo with nothing to undo does nothing`() {
        val editor = InkPageEditor(listOf(a))
        assertFalse(editor.canUndo)
        assertEquals(emptyList<InkStroke>(), editor.undo())
        assertEquals(listOf(a), editor.strokes)
    }

    @Test
    fun `a new stroke after an undo forgets the redo`() {
        val editor = InkPageEditor()
        editor.add(a)
        editor.undo()
        editor.add(b)
        assertFalse(editor.canRedo)
        assertEquals(listOf(b), editor.strokes)
    }

    @Test
    fun `an erased stroke comes back in its old place`() {
        val editor = InkPageEditor(listOf(a, b, c))
        editor.remove(listOf(b))
        assertEquals(listOf(a, c), editor.strokes)
        editor.undo()
        assertEquals(listOf(a, b, c), editor.strokes)
        assertSame(b, editor.strokes[1])
    }

    @Test
    fun `several strokes erased at once are one undo step`() {
        val editor = InkPageEditor(listOf(a, b, c))
        editor.remove(listOf(c, a))
        assertEquals(listOf(b), editor.strokes)
        editor.undo()
        assertEquals(listOf(a, b, c), editor.strokes)
        editor.redo()
        assertEquals(listOf(b), editor.strokes)
    }

    @Test
    fun `erasing picks by identity so a twin stroke stays`() {
        val twin = InkTestData.dot(1f, 1f)
        val editor = InkPageEditor(listOf(a, twin))
        assertEquals(a, twin)
        editor.remove(listOf(a))
        assertEquals(1, editor.strokeCount)
        assertSame(twin, editor.strokes[0])
    }

    @Test
    fun `removing a stroke that is not on the page changes nothing`() {
        val editor = InkPageEditor(listOf(a))
        val before = editor.revision
        assertEquals(emptyList<InkStroke>(), editor.remove(listOf(b)))
        assertEquals(before, editor.revision)
        assertFalse(editor.canUndo)
    }

    @Test
    fun `clear is one step and can be undone`() {
        val editor = InkPageEditor(listOf(a, b))
        editor.clear()
        assertEquals(0, editor.strokeCount)
        editor.undo()
        assertEquals(listOf(a, b), editor.strokes)
    }

    @Test
    fun `the revision goes up on every change including undo`() {
        val editor = InkPageEditor()
        val start = editor.revision
        editor.add(a)
        editor.undo()
        editor.redo()
        assertEquals(start + 3, editor.revision)
    }

    @Test
    fun `the undo history has a limit and the ink does not`() {
        val editor = InkPageEditor()
        repeat(InkPageEditor.MAX_UNDO + 50) { editor.add(InkTestData.dot(it.toFloat(), 0f)) }
        var undone = 0
        while (editor.canUndo) {
            editor.undo()
            undone++
        }
        assertEquals(InkPageEditor.MAX_UNDO, undone)
        assertEquals(50, editor.strokeCount)
    }

    @Test
    fun `the stroke list handed out is a copy`() {
        val editor = InkPageEditor(listOf(a))
        val snapshot = editor.strokes
        editor.add(b)
        assertEquals(1, snapshot.size)
        assertTrue(editor.strokes.size == 2)
    }

    @Test
    fun `a builder drops repeated points and builds nothing from nothing`() {
        val builder = InkStrokeBuilder(InkTool.Pen, 5f)
        assertEquals(null, builder.build())
        builder.add(1f, 1f, 0.5f)
        builder.add(1f, 1f, 0.9f)
        builder.add(2f, 1f, 7f)
        val stroke = builder.build()!!
        assertEquals(2, stroke.pointCount)
        // The reading of 7 is held to 1, and the pressure moves half way to it.
        assertEquals(0.75f, stroke.pressures[1], 0f)
    }

    @Test
    fun `pressure follows the pen smoothly, not every jitter`() {
        val builder = InkStrokeBuilder(InkTool.Pen, 5f)
        listOf(0.5f, 0.9f, 0.1f, 0.9f, 0.1f).forEachIndexed { index, reading -> builder.add(index.toFloat(), 0f, reading) }
        val pressures = builder.build()!!.pressures
        val jumps = (1 until pressures.size).map { kotlin.math.abs(pressures[it] - pressures[it - 1]) }
        assertTrue("jumps $jumps", jumps.all { it <= 0.41f })
        assertEquals(0.5f, pressures[0], 0f)
    }

    @Test
    fun `template lines stay on the page`() {
        val ys = TemplateGeometry.lineYs(1920f)
        assertTrue(ys.size in 15..25)
        assertTrue(ys.all { it > 0f && it < 1920f })
        assertTrue(TemplateGeometry.dotColumns(1440f).all { it < 1440f })
    }
}
