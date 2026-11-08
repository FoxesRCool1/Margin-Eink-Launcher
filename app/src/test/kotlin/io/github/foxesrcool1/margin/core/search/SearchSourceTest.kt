package io.github.foxesrcool1.margin.core.search

import io.github.foxesrcool1.margin.core.reading.AnnotationsFile
import io.github.foxesrcool1.margin.core.reading.BookAnnotations
import io.github.foxesrcool1.margin.core.reading.Highlight
import io.github.foxesrcool1.margin.core.json.jsonOf
import io.github.foxesrcool1.margin.core.json.JsonObject
import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.LocalFileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SearchSourceTest {

    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `a highlight whose book is gone is skipped`() {
        val store = LocalFileStore(temporary.newFolder("Margin"))
        val data = DataRepository(store)

        // An annotations file for a book that is not in the data folder.
        val annotations = BookAnnotations(
            bookPath = "books/gone.epub",
            title = "A Book",
            highlights = listOf(
                Highlight(
                    id = "h1",
                    locator = JsonObject.of("type" to jsonOf("text")),
                    text = "the marked words",
                ),
            ),
        )
        store.writeText("annotations/gone.json", AnnotationsFile.serialise(annotations))

        val docs = SearchSource(data).documents()
        assertTrue(docs.none { it.kind == SearchKind.Highlight })
    }

    @Test
    fun `a highlight whose book is there is kept`() {
        val store = LocalFileStore(temporary.newFolder("Margin"))
        val data = DataRepository(store)

        // A book in the data folder, and an annotations file for it.
        store.writeText("books/here.epub", "book bytes")
        val annotations = BookAnnotations(
            bookPath = "books/here.epub",
            title = "A Book",
            highlights = listOf(
                Highlight(
                    id = "h1",
                    locator = JsonObject.of("type" to jsonOf("text")),
                    text = "the marked words",
                ),
            ),
        )
        store.writeText("annotations/here.json", AnnotationsFile.serialise(annotations))

        val docs = SearchSource(data).documents()
        val highlights = docs.filter { it.kind == SearchKind.Highlight }
        assertEquals(1, highlights.size)
        assertEquals("A Book", highlights.single().title)
    }
}
