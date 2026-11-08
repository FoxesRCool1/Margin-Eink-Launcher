package io.github.foxesrcool1.margin.core.pdf

import io.github.foxesrcool1.margin.core.ink.InkStroke
import io.github.foxesrcool1.margin.core.ink.InkTool
import io.github.foxesrcool1.margin.core.ink.StrokesCodec
import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.LocalFileStore
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfInkRepositoryTest {

    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `a page zero file and a non numeric name are never listed`() {
        val store = LocalFileStore(temporary.newFolder("Margin"))
        val bookId = "book-1"
        val folder = "${StorageLayout.ANNOTATIONS}/${StorageLayout.safeName(bookId)}"
        val ink = StrokesCodec.encode(listOf(InkStroke(InkTool.Pen, 2f, floatArrayOf(1f), floatArrayOf(1f), floatArrayOf(1f))))
        store.write("$folder/page-0001.strokes", ink)
        store.write("$folder/page-0003.strokes", ink)
        store.write("$folder/page-0000.strokes", ink)
        store.write("$folder/page-xx.strokes", ink)

        val pages = PdfInkRepository(DataRepository(store), bookId).annotatedPages()

        assertEquals(listOf(1, 3), pages.map { it.pageNumber })
    }
}
