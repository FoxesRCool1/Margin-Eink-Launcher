package io.github.foxesrcool1.margin.core.reading

import io.github.foxesrcool1.margin.core.books.BooksRepository
import io.github.foxesrcool1.margin.core.json.JsonObject
import io.github.foxesrcool1.margin.core.json.jsonOf
import io.github.foxesrcool1.margin.core.settings.ReaderSettings
import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.LocalFileStore
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AnnotationsFileTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private fun locator(href: String) = JsonObject.of("href" to jsonOf(href), "type" to jsonOf("application/xhtml+xml"))

    private val early = Highlight("h1", locator("ch1.xhtml"), "I went to the woods", progression = 0.1, chapter = "Economy")
    private val late = Highlight("h2", locator("ch9.xhtml"), "to live deliberately", note = "Why I read this", progression = 0.8, chapter = "Conclusion")

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
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1"><dc:title>$title</dc:title></metadata>
                  </package>""".toByteArray(),
            )
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    @Test
    fun `annotations survive a round trip`() {
        val before = BookAnnotations(
            bookPath = "books/walden.epub",
            title = "Walden",
            position = locator("ch3.xhtml"),
            progress = 0.42,
            highlights = listOf(early, late),
        )
        val after = AnnotationsFile.parse(AnnotationsFile.serialise(before))
        assertEquals(before, after)
    }

    @Test
    fun `highlights are kept in reading order whatever order they were made in`() {
        val annotations = BookAnnotations().withHighlight(late).withHighlight(early)
        assertEquals(listOf("h1", "h2"), annotations.highlights.map { it.id })
    }

    @Test
    fun `saving a highlight again replaces it`() {
        val annotations = BookAnnotations().withHighlight(early).withHighlight(early.copy(note = "new"))
        assertEquals(1, annotations.highlights.size)
        assertEquals("new", annotations.highlight("h1")?.note)
        assertTrue(annotations.without("h1").highlights.isEmpty())
    }

    @Test
    fun `one broken highlight is dropped and the rest are kept`() {
        val text = """
            {"version": 1, "title": "Walden", "highlights": [
              {"id": "ok", "text": "fine", "locator": {"href": "a"}},
              {"id": "no-locator", "text": "lost"},
              {"text": "no id", "locator": {"href": "b"}},
              "not even an object"
            ]}
        """.trimIndent()
        val parsed = AnnotationsFile.parse(text)
        assertEquals(listOf("ok"), parsed?.highlights?.map { it.id })
    }

    @Test
    fun `a file from a newer app or not this file at all reads as null`() {
        assertNull(AnnotationsFile.parse("""{"version": 2}"""))
        assertNull(AnnotationsFile.parse("""{"title": "no version"}"""))
        assertNull(AnnotationsFile.parse("rubbish"))
    }

    @Test
    fun `numbers out of range are pulled back in`() {
        val parsed = AnnotationsFile.parse("""{"version": 1, "progress": 7}""")
        assertEquals(1.0, parsed!!.progress, 0.0)
    }

    @Test
    fun `the markdown export groups by chapter and quotes the text`() {
        val markdown = AnnotationsFile.toMarkdown(BookAnnotations(title = "Walden", highlights = listOf(early, late)))
        assertTrue(markdown.startsWith("# Notes on Walden"))
        assertTrue(markdown.contains("## Economy"))
        assertTrue(markdown.contains("> I went to the woods"))
        assertTrue(markdown.contains("Why I read this"))
        assertFalse(markdown.contains("—"))
    }

    @Test
    fun `an unreadable annotations file is kept and not written over`() {
        val store = LocalFileStore(temporary.newFolder("Margin"))
        val data = DataRepository(store).also { it.ensureFolders() }
        val reading = ReadingRepository(data)
        store.writeText("annotations/walden-1.json", "{ broken")

        assertEquals(BookAnnotations(), reading.load("walden-1"))
        assertNotNull(store.readText("annotations/walden-1.unreadable.json"))

        reading.save("walden-1", BookAnnotations(title = "Walden", progress = 0.5))
        assertEquals(0.5, reading.progressOf("walden-1"), 0.0)
        assertEquals("{ broken", store.readText("annotations/walden-1.unreadable.json"))
    }

    @Test
    fun `the last opened stamp survives a round trip`() {
        val before = BookAnnotations(title = "Walden", progress = 0.42, lastOpened = 1_700_000_000_000L)
        val after = AnnotationsFile.parse(AnnotationsFile.serialise(before))
        assertEquals(1_700_000_000_000L, after?.lastOpened)
    }

    @Test
    fun `a file without the stamp reads as unknown, not as the epoch`() {
        val parsed = AnnotationsFile.parse("""{"version": 1, "title": "Walden"}""")
        assertEquals(0L, parsed?.lastOpened)
    }

    @Test
    fun `saving a book stamps the time it was last opened`() {
        val store = LocalFileStore(temporary.newFolder("Margin3"))
        val reading = ReadingRepository(DataRepository(store).also { it.ensureFolders() })
        assertTrue(reading.save("walden-1", BookAnnotations(title = "Walden", progress = 0.5)))
        val saved = AnnotationsFile.parse(store.readText("annotations/walden-1.json")!!)
        assertTrue(saved!!.lastOpened > 0L)
    }

    @Test
    fun `the book read last comes first, not the file restored last`() {
        val root = temporary.newFolder("Margin4")
        val store = LocalFileStore(root)
        val books = BooksRepository(DataRepository(store))
        store.write("books/older.epub", epub("Read first"))
        store.write("books/younger.epub", epub("Read second"))
        val older = books.list().single { it.metadata.title == "Read first" }
        val younger = books.list().single { it.metadata.title == "Read second" }
        // The reader saved the place in each book, and the stamp says which
        // came last.
        store.writeText(
            StorageLayout.annotationsPath(older.bookId),
            AnnotationsFile.serialise(BookAnnotations(title = "Read first", progress = 0.3, lastOpened = 1_000_000_000_000L)),
        )
        store.writeText(
            StorageLayout.annotationsPath(younger.bookId),
            AnnotationsFile.serialise(BookAnnotations(title = "Read second", progress = 0.7, lastOpened = 2_000_000_000_000L)),
        )
        // A restore gave every file the same new date, and the newer stamp
        // sits in the older file. The stamp must win.
        File(root, StorageLayout.annotationsPath(older.bookId)).setLastModified(1_000_000_300_000L)
        File(root, StorageLayout.annotationsPath(younger.bookId)).setLastModified(1_000_000_300_000L)

        assertEquals(
            listOf("Read second", "Read first"),
            books.recentlyRead(2).map { it.first.metadata.title },
        )
    }

    @Test
    fun `reading time adds up per day`() {
        val store = LocalFileStore(temporary.newFolder("Margin2"))
        val reading = ReadingRepository(DataRepository(store).also { it.ensureFolders() })
        val day = LocalDate.of(2026, 9, 20)
        reading.addReadingTime("walden-1", day, 600)
        reading.addReadingTime("other-2", day, 300)
        reading.addReadingTime("walden-1", day.plusDays(1), 60)
        reading.addReadingTime("walden-1", day, 0)
        assertEquals(900, reading.secondsReadOn(day))
        assertEquals(60, reading.secondsReadOn(day.plusDays(1)))
        assertTrue(store.readText("annotations/reading-log.csv")!!.startsWith("date,book,seconds\n"))
    }

    @Test
    fun `a damaged log line is skipped`() {
        val entries = ReadingLog.parse("date,book,seconds\n2026-09-20,a,100\nnot a line\n2026-13-99,a,5\n2026-09-20,b,-4\n2026-09-20,b,50\n")
        assertEquals(150, ReadingLog.secondsOn(entries, LocalDate.of(2026, 9, 20)))
    }

    @Test
    fun `the goal line runs from empty to full and no further`() {
        assertEquals(0f, ReadingLog.goalFraction(0, 30), 0f)
        assertEquals(0.5f, ReadingLog.goalFraction(900, 30), 0.001f)
        assertEquals(1f, ReadingLog.goalFraction(9_000, 30), 0f)
        assertEquals(0f, ReadingLog.goalFraction(900, 0), 0f)
    }

    @Test
    fun `the timer counts reading and not a tablet left on the table`() {
        val timer = ReadingTimer(idleLimitSeconds = 300)
        timer.resume(1_000)
        timer.activity(1_100)
        timer.activity(1_200)
        timer.pause(1_250)
        assertEquals(250, timer.take())
        assertEquals(0, timer.take())

        // Left open for an hour with no page turn: five minutes count, not sixty.
        timer.resume(2_000)
        timer.pause(5_600)
        assertEquals(300, timer.take())

        // A break in the middle: the break itself is not counted.
        timer.resume(10_000)
        timer.activity(10_100)
        timer.activity(12_000)
        timer.pause(12_050)
        assertEquals(100 + 300 + 50, timer.take())
    }

    @Test
    fun `reader settings stay inside their limits and cycle`() {
        var settings = ReaderSettings()
        repeat(50) { settings = settings.larger() }
        assertEquals(300, settings.fontSizePercent)
        repeat(50) { settings = settings.smaller() }
        assertEquals(60, settings.fontSizePercent)
        repeat(50) { settings = settings.narrowerMargins() }
        assertEquals(0, settings.marginPercent)

        assertEquals(ReaderSettings.FONT_LITERATA, ReaderSettings(font = "comic sans").clamped().font)
        assertEquals(ReaderSettings.FONT_LITERATA, ReaderSettings(font = ReaderSettings.FONT_PUBLISHER).nextFont().font)
        assertEquals(5, ReaderSettings(refreshEveryPages = 0).nextRefresh().refreshEveryPages)
        assertEquals(0, ReaderSettings(refreshEveryPages = 20).nextRefresh().refreshEveryPages)
    }
}
