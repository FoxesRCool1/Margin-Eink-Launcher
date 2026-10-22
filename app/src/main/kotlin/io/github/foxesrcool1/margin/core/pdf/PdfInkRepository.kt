package io.github.foxesrcool1.margin.core.pdf

import io.github.foxesrcool1.margin.core.ink.InkStroke
import io.github.foxesrcool1.margin.core.ink.StrokesCodec
import io.github.foxesrcool1.margin.core.ink.StrokesFormatException
import io.github.foxesrcool1.margin.core.json.JsonObject
import io.github.foxesrcool1.margin.core.json.jsonOf
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import java.io.IOException

private const val TAG = "PdfInkRepository"

/** What loading the ink of one PDF page gave. */
sealed interface PageInkLoad {
    class Loaded(val strokes: List<InkStroke>) : PageInkLoad

    /** The file is there and cannot be read. It must not be written over. */
    class Damaged(val reason: String) : PageInkLoad
}

/** A page that has ink on it, for the annotation list. */
data class AnnotatedPage(val pageNumber: Int, val strokeCount: Int)

/**
 * The ink on a PDF, kept beside the book and never inside it.
 *
 * One file per page, `annotations/<book-id>/page-0001.strokes`. The strokes
 * are in PDF points with the origin at the top left of the page, so they stay
 * in place at every zoom step and crop setting. The PDF file itself is never
 * changed.
 */
class PdfInkRepository(private val data: DataRepository, private val bookId: String) {

    fun load(pageNumber: Int): PageInkLoad {
        val bytes = try {
            data.store.readBytesOrThrow(StorageLayout.strokesPath(bookId, pageNumber))
        } catch (problem: IOException) {
            AppLog.e(TAG, "Ink of $bookId page $pageNumber could not be read", problem)
            return PageInkLoad.Damaged("The file could not be read")
        } ?: return PageInkLoad.Loaded(emptyList())
        if (bytes.isEmpty()) return PageInkLoad.Loaded(emptyList())
        return try {
            PageInkLoad.Loaded(StrokesCodec.decode(bytes))
        } catch (problem: StrokesFormatException) {
            AppLog.e(TAG, "Ink of $bookId page $pageNumber could not be read: ${problem.message}")
            PageInkLoad.Damaged(problem.message ?: "The file is damaged")
        }
    }

    /** A page with no ink left has no file, so the folder only ever lists pages worth listing. */
    fun save(pageNumber: Int, strokes: List<InkStroke>): Boolean {
        val path = StorageLayout.strokesPath(bookId, pageNumber)
        return if (strokes.isEmpty()) {
            !data.store.exists(path) || data.store.delete(path)
        } else {
            data.store.write(path, StrokesCodec.encode(strokes))
        }
    }

    fun annotatedPages(): List<AnnotatedPage> =
        data.store.list("${StorageLayout.ANNOTATIONS}/${StorageLayout.safeName(bookId)}")
            .filter { !it.isDirectory && it.name.startsWith("page-") && it.name.endsWith(".strokes") }
            .mapNotNull { entry ->
                val number = entry.name.removePrefix("page-").removeSuffix(".strokes").toIntOrNull()
                    ?: return@mapNotNull null
                val strokes = (load(number) as? PageInkLoad.Loaded)?.strokes ?: return@mapNotNull null
                if (strokes.isEmpty()) null else AnnotatedPage(number, strokes.size)
            }
            .sortedBy { it.pageNumber }

    fun markdown(title: String): String = buildString {
        append("# Notes on ").append(title.ifBlank { "a PDF" }).append("\n\n")
        val pages = annotatedPages()
        if (pages.isEmpty()) {
            append("No handwriting on this PDF yet.\n")
        } else {
            pages.forEach { append("- Page ${it.pageNumber}: ${it.strokeCount} pen strokes\n") }
        }
    }
}

/** Where the reader was in a PDF: page, zoom, crop and screen. Kept in the `position` of the annotations file. */
data class PdfPosition(
    val pageIndex: Int = 0,
    val zoom: PdfZoom = PdfZoom.FitPage,
    val cropMargins: Boolean = false,
) {
    fun toJson(): JsonObject = JsonObject.of(
        "page" to jsonOf(pageIndex + 1),
        "zoom" to jsonOf(zoom.id),
        "crop" to jsonOf(cropMargins),
    )

    companion object {
        fun fromJson(json: JsonObject?, pageCount: Int): PdfPosition {
            if (json == null) return PdfPosition()
            val last = (pageCount - 1).coerceAtLeast(0)
            return PdfPosition(
                pageIndex = ((json.int("page") ?: 1) - 1).coerceIn(0, last),
                zoom = PdfZoom.fromId(json.string("zoom")),
                cropMargins = json.boolean("crop") ?: false,
            )
        }
    }
}
