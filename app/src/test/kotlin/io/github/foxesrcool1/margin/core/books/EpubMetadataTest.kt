package io.github.foxesrcool1.margin.core.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubMetadataTest {

    private fun epub(
        containerXml: String? = CONTAINER,
        packagePath: String = "OEBPS/content.opf",
        packageXml: String? = PACKAGE,
        packageBeforeContainer: Boolean = false,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()

            fun container() {
                zip.putNextEntry(ZipEntry("META-INF/container.xml"))
                zip.write(containerXml!!.toByteArray())
                zip.closeEntry()
            }

            fun packageDocument() {
                zip.putNextEntry(ZipEntry(packagePath))
                zip.write(packageXml!!.toByteArray())
                zip.closeEntry()
            }

            if (packageBeforeContainer && packageXml != null) packageDocument()
            if (containerXml != null) container()
            if (!packageBeforeContainer && packageXml != null) packageDocument()
        }
        return out.toByteArray()
    }

    private fun read(bytes: ByteArray): BookMetadata? =
        EpubMetadata.read { ByteArrayInputStream(bytes) }

    @Test
    fun `the title and the author are read out of an epub`() {
        val metadata = read(epub())
        assertEquals("War and Peace", metadata!!.title)
        assertEquals("Leo Tolstoy", metadata.author)
    }

    @Test
    fun `a package document without a namespace prefix still works`() {
        val plain = """
            <?xml version="1.0"?>
            <package><metadata>
              <title>Plain Title</title>
              <creator>Plain Author</creator>
            </metadata></package>
        """.trimIndent()

        val metadata = read(epub(packageXml = plain))
        assertEquals("Plain Title", metadata!!.title)
        assertEquals("Plain Author", metadata.author)
    }

    @Test
    fun `a book with no author still reads`() {
        val noAuthor = """
            <?xml version="1.0"?>
            <package><metadata><dc:title>Anonymous Work</dc:title></metadata></package>
        """.trimIndent()

        val metadata = read(epub(packageXml = noAuthor))
        assertEquals("Anonymous Work", metadata!!.title)
        assertNull(metadata.author)
    }

    @Test
    fun `the package document is found wherever the container says it is`() {
        val container = """
            <?xml version="1.0"?>
            <container><rootfiles>
              <rootfile full-path="somewhere/else/book.opf" media-type="application/oebps-package+xml"/>
            </rootfiles></container>
        """.trimIndent()

        val metadata = read(epub(containerXml = container, packagePath = "somewhere/else/book.opf"))
        assertEquals("War and Peace", metadata!!.title)
    }

    @Test
    fun `a file that is not a zip reads as null`() {
        assertNull(read("this is not a zip".toByteArray()))
    }

    @Test
    fun `an epub with no container reads as null`() {
        assertNull(read(epub(containerXml = null)))
    }

    @Test
    fun `an epub whose container points at nothing reads as null`() {
        assertNull(read(epub(packagePath = "not/where/the/container/said.opf")))
    }

    @Test
    fun `an epub with no title reads as null`() {
        val noTitle = """<?xml version="1.0"?><package><metadata/></package>"""
        assertNull(read(epub(packageXml = noTitle)))
    }

    @Test
    fun `broken xml reads as null instead of throwing`() {
        assertNull(read(epub(packageXml = "<package><metadata><dc:title>Unclosed")))
        assertNull(read(epub(containerXml = "not xml at all")))
    }

    @Test
    fun `a document that names an external entity is refused`() {
        // This is the XXE attack. A book comes from somewhere else, and a
        // parser that followed this would read a file off the device.
        val hostile = """
            <?xml version="1.0"?>
            <!DOCTYPE package [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
            <package><metadata><dc:title>&xxe;</dc:title></metadata></package>
        """.trimIndent()

        assertNull(read(epub(packageXml = hostile)))
    }

    @Test
    fun `a stream that cannot be opened reads as null`() {
        assertNull(EpubMetadata.read { null })
    }

    @Test
    fun `an epub is told apart by its extension, whatever the case`() {
        assertTrue(EpubMetadata.looksLikeEpub("book.epub"))
        assertTrue(EpubMetadata.looksLikeEpub("book.EPUB"))
        assertFalse(EpubMetadata.looksLikeEpub("book.pdf"))
        assertFalse(EpubMetadata.looksLikeEpub("book"))
    }

    @Test
    fun `a file name is enough to make metadata without opening anything`() {
        val metadata = BookMetadata.fromFileName("War and Peace.epub")
        assertEquals("War and Peace", metadata.title)
        assertNull(metadata.author)
    }

    @Test
    fun `a doctype inside a comment does not cost the book its title`() {
        val commented = """
            <?xml version="1.0"?>
            <!-- An old generator left this behind: <!DOCTYPE package> -->
            <package><metadata><dc:title>Commented Title</dc:title></metadata></package>
        """.trimIndent()

        val metadata = read(epub(packageXml = commented))
        assertEquals("Commented Title", metadata!!.title)
    }

    @Test
    fun `a real doctype is still refused`() {
        val doctyped = """
            <?xml version="1.0"?>
            <!DOCTYPE package>
            <package><metadata><dc:title>Doctyped Title</dc:title></metadata></package>
        """.trimIndent()

        assertNull(read(epub(packageXml = doctyped)))
    }

    @Test
    fun `a percent escaped full path finds the package document`() {
        val container = """
            <?xml version="1.0"?>
            <container><rootfiles>
              <rootfile full-path="OEBPS/my%20book.opf" media-type="application/oebps-package+xml"/>
            </rootfiles></container>
        """.trimIndent()

        val metadata = read(epub(containerXml = container, packagePath = "OEBPS/my book.opf"))
        assertEquals("War and Peace", metadata!!.title)
    }

    @Test
    fun `an over cap package document is unreadable and reads as null`() {
        val big = "x".repeat(2 * 1024 * 1024 + 16)
        val oversized = """
            <?xml version="1.0"?>
            <package><metadata><dc:title>$big</dc:title></metadata></package>
        """.trimIndent()

        assertNull(read(epub(packageXml = oversized)))
    }

    @Test
    fun `a doctype hidden behind a processing instruction and a comment is still refused`() {
        // The parser sees the doctype, so it must be refused, even though a
        // regex that only strips comments would miss it behind the
        // processing instruction.
        val hostile = """
            <?pi <!-- ?><!DOCTYPE package [<!ENTITY x "y">]><package><metadata><dc:title>Hidden Title</dc:title></metadata></package>
        """.trimIndent()

        assertNull(read(epub(packageXml = hostile)))
    }

    @Test
    fun `a package document stored before the container still reads`() {
        val metadata = read(epub(packageBeforeContainer = true))
        assertEquals("War and Peace", metadata!!.title)
        assertEquals("Leo Tolstoy", metadata.author)
    }

    private companion object {
        val CONTAINER = """
            <?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent()

        val PACKAGE = """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>War and Peace</dc:title>
                <dc:creator>Leo Tolstoy</dc:creator>
              </metadata>
            </package>
        """.trimIndent()
    }
}
