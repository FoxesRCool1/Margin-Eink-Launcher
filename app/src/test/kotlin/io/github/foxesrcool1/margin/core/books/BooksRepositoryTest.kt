package io.github.foxesrcool1.margin.core.books

import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.FileStore
import io.github.foxesrcool1.margin.core.storage.LocalFileStore
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Counts how often a file is opened, which is what costs the battery here. */
private class CountingStore(private val inner: FileStore) : FileStore by inner {
    var opened = 0

    override fun openInput(relativePath: String): InputStream? {
        opened++
        return inner.openInput(relativePath)
    }
}

class BooksRepositoryTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private fun epub(title: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                  </container>""".toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("content.opf"))
            zip.write(
                """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>$title</dc:title></metadata>
                  </package>""".toByteArray(),
            )
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    @Test
    fun `the library opens a book once, not every time it comes back on screen`() {
        val folder = temporary.newFolder("Margin")
        val store = CountingStore(LocalFileStore(folder))
        val books = BooksRepository(DataRepository(store))
        store.write("books/war.epub", epub("War and Peace"))

        assertEquals("War and Peace", books.list().single().metadata.title)
        // One read of an EPUB opens the zip more than once.
        val oneRead = store.opened

        repeat(3) { assertEquals("War and Peace", books.list().single().metadata.title) }
        assertEquals(oneRead, store.opened)

        // A new repository, the way each screen makes its own, still knows it.
        assertEquals("War and Peace", BooksRepository(DataRepository(store)).list().single().metadata.title)
        assertEquals(oneRead, store.opened)
    }

    @Test
    fun `a book changed by hand is read again`() {
        val folder = temporary.newFolder("Margin")
        val store = CountingStore(LocalFileStore(folder))
        val books = BooksRepository(DataRepository(store))
        store.write("books/war.epub", epub("War and Peace"))
        books.list()
        val oneRead = store.opened

        store.write("books/war.epub", epub("Anna Karenina, the longer title"))
        File(folder, "books/war.epub").setLastModified(1_000_000_000_000L)

        assertEquals("Anna Karenina, the longer title", books.list().single().metadata.title)
        assertEquals(2 * oneRead, store.opened)
    }

    @Test
    fun `the book read last comes first, not the book added last`() {
        val folder = temporary.newFolder("Margin")
        val store = LocalFileStore(folder)
        val books = BooksRepository(DataRepository(store))
        store.write("books/old.epub", epub("Read today"))
        store.write("books/new.epub", epub("Added yesterday"))
        store.write("books/never.epub", epub("Added last week"))
        File(folder, "books/old.epub").setLastModified(1_000_000_000_000L)
        File(folder, "books/new.epub").setLastModified(1_000_000_200_000L)
        File(folder, "books/never.epub").setLastModified(1_000_000_100_000L)

        // The reader saves the place in the book when it closes.
        val read = books.list().single { it.metadata.title == "Read today" }
        val place = "annotations/${read.bookId}.json"
        store.writeText(place, "{}")
        File(folder, place).setLastModified(1_000_000_300_000L)

        assertEquals(
            listOf("Read today", "Added yesterday", "Added last week"),
            books.byRecent().map { it.metadata.title },
        )
    }

    @Test
    fun `deleting a book with a long name takes its ink folder too`() {
        val folder = temporary.newFolder("Margin")
        val store = LocalFileStore(folder)
        val books = BooksRepository(DataRepository(store))
        // A name this long makes the book id longer than the 120 characters a
        // file name may hold, so the id and its safe name are not the same.
        val name = "a".repeat(140) + ".epub"
        store.write("books/$name", epub("A long name"))
        val book = books.list().single()
        assertTrue(book.bookId.length > 120)
        val ink = "${StorageLayout.ANNOTATIONS}/${StorageLayout.safeName(book.bookId)}"
        store.write("$ink/page-0001.strokes", ByteArray(8))

        assertTrue(books.delete(book))
        assertFalse(store.exists(book.path))
        assertFalse(store.exists(StorageLayout.annotationsPath(book.bookId)))
        assertFalse(store.exists(ink))
    }
}
