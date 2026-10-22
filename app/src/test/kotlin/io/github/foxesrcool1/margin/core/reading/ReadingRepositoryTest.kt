package io.github.foxesrcool1.margin.core.reading

import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.LocalFileStore
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReadingRepositoryTest {

    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `the handwritten card of a removed highlight is deleted`() {
        val store = LocalFileStore(temporary.newFolder("Margin"))
        val reading = ReadingRepository(DataRepository(store))
        val card = reading.inkNotePath("book-1", "h1")
        store.write(card, ByteArray(8))

        assertTrue(reading.deleteInkNote(card))
        assertFalse(store.exists(card))
    }

    @Test
    fun `a path from a damaged file reaches nothing outside the cards`() {
        val store = LocalFileStore(temporary.newFolder("Margin"))
        val reading = ReadingRepository(DataRepository(store))
        store.write("notes/Diary.inknote", ByteArray(8))
        store.write("annotations/book-1.json", ByteArray(8))

        assertFalse(reading.deleteInkNote("notes/Diary.inknote"))
        assertFalse(reading.deleteInkNote("annotations/book-1.json"))
        assertFalse(reading.deleteInkNote("annotations/../notes/Diary.inknote"))
        assertTrue(store.exists("notes/Diary.inknote"))
        assertTrue(store.exists("annotations/book-1.json"))
    }

    @Test
    fun `an annotations file that cannot be read is never saved over`() {
        val root = temporary.newFolder("Margin")
        val store = LocalFileStore(root)
        val path = StorageLayout.annotationsPath("book-1")
        store.writeText(path, "the notes")
        val file = File(root, path)
        file.setReadable(false)
        assumeFalse("Running as root: every file can be read", file.canRead())

        val reading = ReadingRepository(DataRepository(store))
        assertTrue(reading.load("book-1").highlights.isEmpty())
        assertTrue(reading.savesBlocked("book-1"))
        assertFalse(reading.save("book-1", BookAnnotations(progress = 0.5)))

        file.setReadable(true)
        assertEquals("the notes", store.readText(path))
        // Once the file reads again, the next open saves as before.
        store.writeText(path, AnnotationsFile.serialise(BookAnnotations()))
        reading.load("book-1")
        assertFalse(reading.savesBlocked("book-1"))
        assertTrue(reading.save("book-1", BookAnnotations(progress = 0.5)))
    }
}
