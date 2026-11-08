package io.github.foxesrcool1.margin.core.books

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

/** What the library knows about a file before anything opens it. */
data class BookMetadata(
    val title: String,
    val author: String?,
) {
    companion object {
        fun fromFileName(fileName: String): BookMetadata =
            BookMetadata(title = fileName.substringBeforeLast('.', fileName), author = null)
    }
}

/**
 * Reads the title and the author out of an EPUB.
 *
 * An EPUB is a zip. `META-INF/container.xml` says which file inside it is the
 * package document, and that document holds `dc:title` and `dc:creator`.
 *
 * This is deliberately small. The reader in step 7 uses the Readium toolkit,
 * which reads far more than this, but the library list has to show a real
 * title before any reader exists, and a list of file names is not a library.
 *
 * It holds no Android class, so the tests build real EPUB files in a temporary
 * folder and read them back.
 */
object EpubMetadata {

    private const val CONTAINER_PATH = "META-INF/container.xml"
    private const val MAX_XML_BYTES = 2 * 1024 * 1024

    /** Returns null when the file is not an EPUB this can read. */
    fun read(open: () -> InputStream?): BookMetadata? = runCatching {
        // The caller promises only a stream, so a ZipFile (which needs a file
        // to jump around in) is out. Two walks, each one stops at the entry it
        // wants: first the container, which names the package document, then
        // the package document itself. Nothing else in the zip is read, only
        // skipped, and a big picture in the book can not spoil the read.
        val containerXml = findEntry(open) { it.equals(CONTAINER_PATH, ignoreCase = true) } ?: return null
        val packagePath = packagePathFrom(parse(containerXml) ?: return null) ?: return null
        val wanted = listOf(decodePath(packagePath), packagePath)
        val packageXml = findEntry(open) { name -> wanted.any { name.equals(it, ignoreCase = true) } } ?: return null
        metadataFrom(parse(packageXml) ?: return null)
    }.getOrNull()

    /** Walks the zip and returns the bytes of the first file whose name [matches], or null. */
    private fun findEntry(open: () -> InputStream?, matches: (String) -> Boolean): ByteArray? {
        val stream = open() ?: return null
        ZipInputStream(stream.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && matches(entry.name)) return readBounded(zip)
                entry = zip.nextEntry
            }
        }
        return null
    }

    /**
     * Reads the current zip entry, but never more than the cap. An entry over
     * the cap is unreadable, the same as a missing one, and it is never held
     * in memory whole.
     */
    private fun readBounded(zip: ZipInputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() <= MAX_XML_BYTES) {
            val read = zip.read(buffer, 0, minOf(buffer.size, MAX_XML_BYTES + 1 - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return if (out.size() > MAX_XML_BYTES) null else out.toByteArray()
    }

    /**
     * Parses XML with the door shut.
     *
     * A book is a file from somewhere else. An XML document can name an
     * external entity and make a naive parser read a file off the device or
     * open a network connection. Doctypes are refused outright, which is the
     * simplest way to make that impossible.
     */
    private fun parse(bytes: ByteArray): Document? = runCatching {
        // The doctype is refused here, by looking, and not by asking the
        // parser to refuse it. The parser on Android does not know the
        // feature names the desktop one does, and throws on every one of
        // them. Found on the emulator: the desktop tests passed and every
        // real book lost its title and author.
        if (declaresDoctype(bytes)) return@runCatching null

        val factory = DocumentBuilderFactory.newInstance()
        // Belt and braces where the platform has them. Each one alone, so a
        // parser that lacks one still gets the others.
        listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
        ).forEach { (feature, value) -> runCatching { factory.setFeature(feature, value) } }
        runCatching { factory.isXIncludeAware = false }
        runCatching { factory.isExpandEntityReferences = false }
        factory.isNamespaceAware = false
        factory.newDocumentBuilder().parse(bytes.inputStream())
    }.getOrNull()

    /**
     * True when the document has a doctype, in any of the encodings an EPUB
     * may use. Only the prolog is looked at, because XML allows a doctype
     * only before the root element, and a doctype inside a comment is not a
     * doctype at all.
     */
    private fun declaresDoctype(bytes: ByteArray): Boolean {
        val charsets = listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE)
        return charsets.any { charset -> prologHasDoctype(bytes.toString(charset)) }
    }

    /**
     * Scans the prolog: whitespace, a byte order mark, processing
     * instructions and comments, and stops at the first thing that is none of
     * those. A doctype is only a doctype in that place, and a comment may
     * legally hold the text of one.
     */
    private fun prologHasDoctype(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            when {
                text[i].isWhitespace() || text[i] == Char(0xFEFF) -> i++
                text.startsWith("<?", i) -> {
                    val end = text.indexOf("?>", i + 2)
                    if (end < 0) return false
                    i = end + 2
                }
                text.startsWith("<!--", i) -> {
                    val end = text.indexOf("-->", i + 4)
                    if (end < 0) return false
                    i = end + 3
                }
                text.startsWith("<!DOCTYPE", i, ignoreCase = true) -> return true
                else -> return false
            }
        }
        return false
    }

    private fun packagePathFrom(container: Document): String? {
        val rootFiles = container.getElementsByTagName("rootfile")
        for (index in 0 until rootFiles.length) {
            val element = rootFiles.item(index) as? Element ?: continue
            val path = element.getAttribute("full-path")
            if (path.isNotBlank()) return path.trimStart('/')
        }
        return null
    }

    /**
     * A full-path may be percent escaped ("OEBPS/my%20book.opf") and may
     * start with "./". Decode the escapes and strip the prefix; the reader
     * falls back to the raw name when the decoded one is not in the zip.
     *
     * URLDecoder is not used because it turns "+" into a space, and a file
     * name may contain a real plus. Only %XX sequences are decoded.
     */
    private fun decodePath(path: String): String {
        val decoded = path.replace(HEX_ESCAPE) { match ->
            val bytes = match.value.chunked(3).map { it.substring(1).toInt(16).toByte() }.toByteArray()
            String(bytes, Charsets.UTF_8)
        }
        return decoded.trimStart('/').removePrefix("./")
    }

    /** Runs of percent escapes, so a multi byte character decodes as one. */
    private val HEX_ESCAPE = Regex("(?:%[0-9A-Fa-f]{2})+")

    private fun metadataFrom(packageDocument: Document): BookMetadata? {
        val title = firstText(packageDocument, "dc:title", "title") ?: return null
        val author = firstText(packageDocument, "dc:creator", "creator")
        return BookMetadata(title = title, author = author)
    }

    private fun firstText(document: Document, vararg tagNames: String): String? {
        tagNames.forEach { tag ->
            val nodes = document.getElementsByTagName(tag)
            for (index in 0 until nodes.length) {
                val text = nodes.item(index)?.textContent?.trim()
                if (!text.isNullOrBlank()) return text.take(200)
            }
        }
        return null
    }

    fun looksLikeEpub(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase(Locale.ROOT) == "epub"
}
