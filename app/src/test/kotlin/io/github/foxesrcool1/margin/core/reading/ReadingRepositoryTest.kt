package io.github.foxesrcool1.margin.core.reading

import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.LocalFileStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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
}
