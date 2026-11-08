package io.github.foxesrcool1.margin.core.ink

import io.github.foxesrcool1.margin.core.json.Json
import io.github.foxesrcool1.margin.core.json.JsonObject
import io.github.foxesrcool1.margin.core.json.jsonOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Reads and writes an `.inknote` file.
 *
 * It is a zip, so any computer can open it:
 *
 * ```
 * meta.json            format version, page size, template, page count
 * pages/0001.strokes   the ink of page 1, see [StrokesCodec]
 * pages/0001.png       a picture of page 1, for other programs and the file list
 * ```
 *
 * The picture is only ever a copy. The strokes are the truth.
 */
object InkNoteCodec {

    const val VERSION = 1

    private const val META = "meta.json"
    /** The most pages a note keeps. More would be lost on the next open. */
    const val MAX_PAGES = 2_000
    private const val MAX_ENTRY_BYTES = 64L * 1024L * 1024L

    fun encode(note: InkNote): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry(META))
            zip.write(
                Json.write(
                    JsonObject.of(
                        "formatVersion" to jsonOf(VERSION),
                        "pageWidth" to jsonOf(note.pageWidth.toInt()),
                        "pageHeight" to jsonOf(note.pageHeight.toInt()),
                        "template" to jsonOf(note.template.id),
                        "pageCount" to jsonOf(note.pages.size),
                    ),
                ).toByteArray(Charsets.UTF_8),
            )
            zip.closeEntry()

            note.pages.forEachIndexed { index, page ->
                val number = pageNumber(index)
                zip.putNextEntry(ZipEntry("pages/$number.strokes"))
                zip.write(StrokesCodec.encode(page.strokes))
                zip.closeEntry()
                page.preview?.let { png ->
                    zip.putNextEntry(ZipEntry("pages/$number.png"))
                    zip.write(png)
                    zip.closeEntry()
                }
            }
        }
        return bytes.toByteArray()
    }

    @Throws(StrokesFormatException::class)
    fun decode(bytes: ByteArray): InkNote {
        val entries = HashMap<String, ByteArray>()
        runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entries.size < MAX_PAGES * 2 + 1) {
                        entries[entry.name] = readBounded(zip)
                    }
                    zip.closeEntry()
                }
            }
        }.onFailure { throw StrokesFormatException("This is not an ink note: ${it.message}") }

        val meta = entries[META]
            ?.let { Json.parseOrNull(String(it, Charsets.UTF_8)) as? JsonObject }
            ?: throw StrokesFormatException("The ink note has no meta.json")

        val version = meta.number("formatVersion")?.takeIf { it % 1.0 == 0.0 && it >= 1.0 }?.toInt()
            ?: throw StrokesFormatException("meta.json has no usable formatVersion")
        if (version > VERSION) {
            throw StrokesFormatException("Ink note format $version is newer than this app knows ($VERSION)")
        }

        val width = meta.number("pageWidth")?.toFloat()?.takeIf { it in 1f..100_000f }
            ?: InkNote.DEFAULT_PAGE_WIDTH
        val height = meta.number("pageHeight")?.toFloat()?.takeIf { it in 1f..100_000f }
            ?: InkNote.DEFAULT_PAGE_HEIGHT

        // The page files are counted, not the number in meta.json. Someone
        // may have taken a page out of the zip by hand.
        val pages = ArrayList<InkPageData>()
        var index = 0
        while (index < MAX_PAGES) {
            val number = pageNumber(index)
            val strokeBytes = entries["pages/$number.strokes"] ?: break
            pages += InkPageData(StrokesCodec.decode(strokeBytes), entries["pages/$number.png"])
            index++
        }
        // A page after a gap would be lost on the next save. Refuse the file instead.
        val pageFiles = entries.keys.count { it.startsWith("pages/") && it.endsWith(".strokes") }
        if (pageFiles != pages.size) {
            throw StrokesFormatException("The ink note has $pageFiles page files but only ${pages.size} in a row from page 1")
        }
        if (pages.isEmpty()) pages += InkPageData(emptyList())

        return InkNote(width, height, PageTemplate.fromId(meta.string("template")), pages)
    }

    private fun pageNumber(index: Int): String = (index + 1).toString().padStart(4, '0')

    private fun readBounded(zip: ZipInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            total += read
            // A zip can promise a small entry and unpack to gigabytes.
            if (total > MAX_ENTRY_BYTES) throw StrokesFormatException("An entry in the ink note is too large")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
