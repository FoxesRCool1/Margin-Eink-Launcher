package io.github.foxesrcool1.margin.core.reading

import io.github.foxesrcool1.margin.core.json.Json
import io.github.foxesrcool1.margin.core.json.JsonArray
import io.github.foxesrcool1.margin.core.json.JsonNumber
import io.github.foxesrcool1.margin.core.json.JsonObject
import io.github.foxesrcool1.margin.core.json.JsonValue
import io.github.foxesrcool1.margin.core.json.jsonArrayOf
import io.github.foxesrcool1.margin.core.json.jsonOf

/**
 * One highlight in a book.
 *
 * [locator] is the place, as the reader engine writes it. For an EPUB that is
 * a Readium locator. This class never looks inside it, so the storage layer
 * and its tests need no reader engine.
 */
data class Highlight(
    val id: String,
    val locator: JsonObject,
    /** The words that were marked, so the list and the export can show them. */
    val text: String,
    val note: String = "",
    /** Path of a handwritten note card, relative to the data folder, or empty. */
    val inkNotePath: String = "",
    /** Where in the book, 0 to 1, to sort the list in reading order. */
    val progression: Double = 0.0,
    val chapter: String = "",
    val createdAt: String = "",
)

/** Everything the app keeps about one book. It lives in `annotations/<book-id>.json`. */
data class BookAnnotations(
    val bookPath: String = "",
    val title: String = "",
    /** The last place read, in the words of the reader engine. */
    val position: JsonObject? = null,
    /** 0 to 1, for the library list. */
    val progress: Double = 0.0,
    val highlights: List<Highlight> = emptyList(),
    /** When the reader last saved, in epoch millis. 0 means unknown, for files written before the stamp. */
    val lastOpened: Long = 0L,
) {
    fun withHighlight(highlight: Highlight): BookAnnotations =
        copy(highlights = (highlights.filterNot { it.id == highlight.id } + highlight).sortedBy { it.progression })

    fun without(highlightId: String): BookAnnotations =
        copy(highlights = highlights.filterNot { it.id == highlightId })

    fun highlight(id: String): Highlight? = highlights.firstOrNull { it.id == id }
}

/**
 * Reads and writes `annotations/<book-id>.json`.
 *
 * Like the habits file, it is meant to survive a text editor: one broken
 * highlight is dropped and the rest are kept.
 */
object AnnotationsFile {

    const val VERSION = 1

    fun serialise(annotations: BookAnnotations): String {
        val entries = linkedMapOf<String, JsonValue>(
            "version" to jsonOf(VERSION),
            "bookPath" to jsonOf(annotations.bookPath),
            "title" to jsonOf(annotations.title),
            "progress" to JsonNumber(annotations.progress),
        )
        annotations.position?.let { entries["position"] = it }
        if (annotations.lastOpened > 0L) entries["lastOpened"] = JsonNumber(annotations.lastOpened.toDouble())
        entries["highlights"] = jsonArrayOf(
            annotations.highlights.map { highlight ->
                JsonObject.of(
                    "id" to jsonOf(highlight.id),
                    "text" to jsonOf(highlight.text),
                    "note" to jsonOf(highlight.note),
                    "inkNote" to jsonOf(highlight.inkNotePath),
                    "progression" to JsonNumber(highlight.progression),
                    "chapter" to jsonOf(highlight.chapter),
                    "createdAt" to jsonOf(highlight.createdAt),
                    "locator" to highlight.locator,
                )
            },
        )
        return Json.write(JsonObject(entries))
    }

    /** Null when the text is not this file at all, or comes from a newer app. */
    fun parse(text: String): BookAnnotations? {
        val root = Json.parseOrNull(text) as? JsonObject ?: return null
        val version = root.int("version") ?: return null
        if (version > VERSION) return null

        val highlights = (root["highlights"] as? JsonArray)
            ?.objects()
            ?.mapNotNull { entry ->
                val id = entry.string("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val locator = entry.obj("locator") ?: return@mapNotNull null
                Highlight(
                    id = id,
                    locator = locator,
                    text = entry.string("text").orEmpty(),
                    note = entry.string("note").orEmpty(),
                    inkNotePath = entry.string("inkNote").orEmpty(),
                    progression = entry.number("progression")?.coerceIn(0.0, 1.0) ?: 0.0,
                    chapter = entry.string("chapter").orEmpty(),
                    createdAt = entry.string("createdAt").orEmpty(),
                )
            }
            ?.sortedBy { it.progression }
            ?: emptyList()

        return BookAnnotations(
            bookPath = root.string("bookPath").orEmpty(),
            title = root.string("title").orEmpty(),
            position = root.obj("position"),
            progress = root.number("progress")?.coerceIn(0.0, 1.0) ?: 0.0,
            highlights = highlights,
            lastOpened = root.long("lastOpened")?.takeIf { it > 0L } ?: 0L,
        )
    }

    /** The annotation list of a book as Markdown, for the export. */
    fun toMarkdown(annotations: BookAnnotations): String = buildString {
        append("# Notes on ").append(annotations.title.ifBlank { "a book" }).append("\n\n")
        if (annotations.highlights.isEmpty()) {
            append("No highlights yet.\n")
            return@buildString
        }
        var chapter: String? = null
        annotations.highlights.forEach { highlight ->
            if (highlight.chapter.isNotBlank() && highlight.chapter != chapter) {
                chapter = highlight.chapter
                append("## ").append(highlight.chapter).append("\n\n")
            }
            highlight.text.trim().lines().forEach { line -> append("> ").append(line.trim()).append('\n') }
            append('\n')
            if (highlight.note.isNotBlank()) append(highlight.note.trim()).append("\n\n")
            if (highlight.inkNotePath.isNotBlank()) {
                append("Handwritten note: `").append(highlight.inkNotePath).append("`\n\n")
            }
        }
    }
}
