package io.github.foxesrcool1.margin.ui.reading

import android.os.Bundle
import android.os.Looper
import io.github.foxesrcool1.margin.core.json.JsonObject
import io.github.foxesrcool1.margin.core.settings.ReaderSettings
import io.github.foxesrcool1.margin.core.storage.DataRoot
import io.github.foxesrcool1.margin.ui.reading.epub.EpubOpener
import io.github.foxesrcool1.margin.ui.reading.epub.EpubReaderActivity
import io.github.foxesrcool1.margin.ui.reading.epub.toEpubPreferences
import io.github.foxesrcool1.margin.ui.reading.epub.toJsonObject
import io.github.foxesrcool1.margin.ui.reading.epub.toLocator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.preferences.TextAlign
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The reader cannot be seen from here, but a lot can still go wrong before
 * anything is drawn: the book does not open, the theme does not fit the
 * fragment, the font declaration is refused. This catches those.
 */
@RunWith(RobolectricTestRunner::class)
class EpubReaderSmokeTest {

    private fun writeEpub(target: File) {
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream()).use { zip ->
            // The mimetype entry comes first and is stored, not deflated.
            val mimetype = "application/epub+zip".toByteArray()
            zip.putNextEntry(
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimetype.size.toLong()
                    compressedSize = mimetype.size.toLong()
                    crc = CRC32().apply { update(mimetype) }.value
                },
            )
            zip.write(mimetype)
            zip.closeEntry()

            fun add(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.trimIndent().toByteArray())
                zip.closeEntry()
            }
            add(
                "META-INF/container.xml",
                """<?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>""",
            )
            add(
                "OEBPS/content.opf",
                """<?xml version="1.0"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="id">urn:uuid:test-book</dc:identifier>
                    <dc:title>A Test Book</dc:title>
                    <dc:creator>Nobody</dc:creator>
                    <dc:language>en</dc:language>
                    <meta property="dcterms:modified">2026-09-20T00:00:00Z</meta>
                  </metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="c2.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="c1"/><itemref idref="c2"/></spine>
                </package>""",
            )
            add(
                "OEBPS/nav.xhtml",
                """<?xml version="1.0"?>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head>
                <body><nav epub:type="toc"><ol>
                  <li><a href="c1.xhtml">One</a><ol><li><a href="c1.xhtml#part">One, part two</a></li></ol></li>
                  <li><a href="c2.xhtml">Two</a></li>
                </ol></nav></body></html>""",
            )
            listOf("c1" to "One", "c2" to "Two").forEach { (file, title) ->
                add(
                    "OEBPS/$file.xhtml",
                    """<?xml version="1.0"?>
                    <html xmlns="http://www.w3.org/1999/xhtml"><head><title>$title</title></head>
                    <body><h1>$title</h1><p id="part">Public domain words only.</p></body></html>""",
                )
            }
        }
    }

    @Test
    fun `readium opens a book and reads its contents`() {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.cacheDir, "test.epub").also(::writeEpub)
        val publication = runBlocking { EpubOpener.open(context, file) }
        assertNotNull(publication)
        assertEquals("A Test Book", publication!!.metadata.title)
        assertEquals(2, publication.readingOrder.size)
        assertEquals(2, publication.tableOfContents.size)
        assertEquals(1, publication.tableOfContents[0].children.size)
        publication.close()
    }

    @Test
    fun `a file that is not a book opens as null and does not throw`() {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.cacheDir, "broken.epub").apply { writeText("not a zip") }
        assertNull(runBlocking { EpubOpener.open(context, file) })
    }

    @Test
    fun `a locator survives the trip through the app's own json`() {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.cacheDir, "locator.epub").also(::writeEpub)
        val publication = runBlocking { EpubOpener.open(context, file) }!!
        val locator = publication.locatorFromLink(publication.readingOrder[1])!!
        val json: JsonObject = locator.toJsonObject()!!
        val back = json.toLocator()!!
        assertEquals(locator.href.toString(), back.href.toString())
        assertEquals(locator.mediaType, back.mediaType)
        publication.close()
    }

    @Test
    fun `the e-ink rules are in the preferences whatever the user picks`() {
        val preferences = ReaderSettings(justify = false, lineHeightPercent = 160).toEpubPreferences()
        assertEquals(false, preferences.scroll)
        assertEquals(android.graphics.Color.WHITE, preferences.backgroundColor?.int)
        assertEquals(android.graphics.Color.BLACK, preferences.textColor?.int)
        assertEquals(TextAlign.START, preferences.textAlign)
        assertEquals(1.6, preferences.lineHeight!!, 0.0001)
        assertEquals(false, preferences.publisherStyles)

        val publisher = ReaderSettings(font = ReaderSettings.FONT_PUBLISHER).toEpubPreferences()
        assertEquals(true, publisher.publisherStyles)
        assertNull(publisher.fontFamily)
        assertNull(publisher.lineHeight)
    }

    @Test
    fun `the reader activity starts, opens the book and shuts down cleanly`() {
        val context = RuntimeEnvironment.getApplication()
        writeEpub(File(DataRoot.folder(context), "books/test.epub"))

        val controller = Robolectric.buildActivity(
            EpubReaderActivity::class.java,
            EpubReaderActivity.intent(context, "books/test.epub", "test-1", "A Test Book"),
        ).setup()

        // The book opens on a background thread. Give it a moment, and keep
        // the main looper turning so the result can land.
        val deadline = System.currentTimeMillis() + 15_000
        var fragments = 0
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            fragments = controller.get().supportFragmentManager.fragments.size
            if (fragments > 0) break
            Thread.sleep(50)
        }
        assertTrue("the book view was never added", fragments > 0)
        assertFalse(controller.get().isFinishing)

        controller.pause().stop().destroy()
        // Through the repository, not the folder: the repository is cached for
        // the life of the process, and an earlier test class may have made it.
        assertTrue(DataRoot.repository(context).store.exists("annotations/test-1.json"))
    }

    @Test
    fun `a book that finishes opening after Home was pressed waits and does not crash`() {
        val context = RuntimeEnvironment.getApplication()
        writeEpub(File(DataRoot.folder(context), "books/test.epub"))

        val controller = Robolectric.buildActivity(
            EpubReaderActivity::class.java,
            EpubReaderActivity.intent(context, "books/test.epub", "test-2", "A Test Book"),
        ).setup()

        // Home at once, while the book is still opening on its own thread. When
        // the open ends, the fragment manager has saved its state and would
        // refuse the commit of the book view.
        controller.pause().stop().saveInstanceState(Bundle())
        val manager = controller.get().supportFragmentManager
        val quiet = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < quiet) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        assertEquals("the book view waits for the screen to come back", 0, manager.fragments.size)

        controller.start().resume()
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline && manager.fragments.isEmpty()) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        assertTrue("the book view was never added", manager.fragments.isNotEmpty())
        controller.pause().stop().destroy()
    }
}
