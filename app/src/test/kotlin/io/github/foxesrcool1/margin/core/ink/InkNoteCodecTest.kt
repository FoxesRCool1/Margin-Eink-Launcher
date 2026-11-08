package io.github.foxesrcool1.margin.core.ink

import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.LocalFileStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class InkNoteCodecTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private val twoPages = InkNote(
        template = PageTemplate.DotGrid,
        pages = listOf(
            InkPageData(listOf(InkTestData.squiggle(200f, 200f)), preview = byteArrayOf(1, 2, 3)),
            InkPageData(listOf(InkTestData.dot(5f, 5f), InkTestData.dot(6f, 6f))),
        ),
    )

    @Test
    fun `a note survives a round trip`() {
        val back = InkNoteCodec.decode(InkNoteCodec.encode(twoPages))
        assertEquals(PageTemplate.DotGrid, back.template)
        assertEquals(1440f, back.pageWidth, 0f)
        assertEquals(2, back.pages.size)
        // Pressure is rounded to one part in 255 on the way out, so compare the path.
        assertArrayEquals(twoPages.pages[0].strokes[0].xs, back.pages[0].strokes[0].xs, 0f)
        assertArrayEquals(twoPages.pages[0].strokes[0].ys, back.pages[0].strokes[0].ys, 0f)
        assertEquals(2, back.pages[1].strokes.size)
        assertArrayEquals(byteArrayOf(1, 2, 3), back.pages[0].preview)
        assertNull(back.pages[1].preview)
    }

    @Test
    fun `the zip holds the documented names`() {
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(InkNoteCodec.encode(twoPages))).use { zip ->
            while (true) names += (zip.nextEntry ?: break).name
        }
        assertEquals(listOf("meta.json", "pages/0001.strokes", "pages/0001.png", "pages/0002.strokes"), names)
    }

    @Test
    fun `a newer note format is refused`() {
        val bytes = zipOf("meta.json" to """{"formatVersion": 9}""".toByteArray())
        try {
            InkNoteCodec.decode(bytes)
            fail("should have been refused")
        } catch (expected: StrokesFormatException) {
            assertTrue(expected.message!!.contains("newer"))
        }
    }

    @Test
    fun `a format version below one or not a whole number is refused`() {
        for (version in listOf("0", "-1", "1.5")) {
            expectRefused(zipOf("meta.json" to """{"formatVersion": $version}""".toByteArray()))
        }
    }

    @Test
    fun `a gap in the page numbers refuses the note`() {
        val strokes = StrokesCodec.encode(emptyList())
        expectRefused(
            zipOf(
                "meta.json" to """{"formatVersion": 1}""".toByteArray(),
                "pages/0001.strokes" to strokes,
                "pages/0003.strokes" to strokes,
            ),
        )
    }

    @Test
    fun `an empty note file is damaged and not missing`() {
        val store = LocalFileStore(temporary.newFolder("Margin"))
        val notes = InkNotesRepository(DataRepository(store).also { it.ensureFolders() })
        store.write("notes/empty.inknote", ByteArray(0))
        assertTrue(notes.load("notes/empty.inknote") is InkNoteLoad.Damaged)
    }

    @Test
    fun `something that is not a zip is refused`() {
        try {
            InkNoteCodec.decode("hello".toByteArray())
            fail("should have been refused")
        } catch (expected: StrokesFormatException) {
            assertNotNull(expected.message)
        }
    }

    @Test
    fun `a note with no page files opens as one empty page`() {
        val back = InkNoteCodec.decode(zipOf("meta.json" to """{"formatVersion": 1, "template": "lined"}""".toByteArray()))
        assertEquals(1, back.pages.size)
        assertEquals(PageTemplate.Lined, back.template)
    }

    @Test
    fun `an unknown template falls back to blank`() {
        assertEquals(PageTemplate.Blank, PageTemplate.fromId("hexagons"))
        assertEquals(PageTemplate.Blank, PageTemplate.fromId(null))
    }

    @Test
    fun `the repository tells missing from damaged`() {
        val store = LocalFileStore(temporary.newFolder("Margin"))
        val data = DataRepository(store).also { it.ensureFolders() }
        val notes = InkNotesRepository(data)

        assertTrue(notes.load("notes/nothing.inknote") is InkNoteLoad.Missing)

        store.write("notes/broken.inknote", "rubbish".toByteArray())
        assertTrue(notes.load("notes/broken.inknote") is InkNoteLoad.Damaged)

        val path = notes.create("notes", "Sketch: one", PageTemplate.Lined)
        assertEquals("notes/Sketch- one.inknote", path)
        val loaded = notes.load(path!!) as InkNoteLoad.Loaded
        assertEquals(PageTemplate.Lined, loaded.note.template)

        assertEquals("notes/Sketch- one 2.inknote", notes.create("notes", "Sketch: one", PageTemplate.Blank))
    }

    private fun expectRefused(bytes: ByteArray) {
        try {
            InkNoteCodec.decode(bytes)
            fail("should have been refused")
        } catch (expected: StrokesFormatException) {
            assertNotNull(expected.message)
        }
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
