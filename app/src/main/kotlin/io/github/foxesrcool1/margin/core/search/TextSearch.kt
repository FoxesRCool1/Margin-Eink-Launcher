package io.github.foxesrcool1.margin.core.search

/**
 * The search over the words the user wrote themselves.
 *
 * Pure Kotlin, no Android: the screen hands it the documents it read from the
 * data folder, and gets back the hits to show. The rules of the match and the
 * shape of the snippet are pinned down by the unit tests.
 */

/** What a document is. The row on the search screen shows it. */
enum class SearchKind { Note, Journal, Highlight }

/** One thing the search can find. [text] is what is searched in, [title] what is shown. */
data class SearchDoc(
    val kind: SearchKind,
    val path: String,
    val title: String,
    val text: String,
    /** For a highlight, the id of it in the book's annotations, so the book opens there. Empty for the rest. */
    val target: String = "",
)

/** A document that matched, with the line of it to show under the title. */
data class SearchHit(
    val doc: SearchDoc,
    val snippet: String,
)

/**
 * How much of the text to show around a match. About eighty characters, so the
 * line reads as one line on the tablet and not as a paragraph.
 */
private const val SNIPPET_WIDTH = 80

/**
 * The words of the query, split on whitespace, lowercased, with the empties
 * left out. Empty for a blank query.
 */
private fun wordsOf(query: String): List<String> =
    query.split(Regex("\\s+")).map { it.lowercase() }.filter { it.isNotEmpty() }

/**
 * Every word of the query must stand in the title or the text, in any case.
 * A blank query finds nothing: an empty search box is not a search.
 */
fun search(docs: List<SearchDoc>, query: String, limit: Int = 200): List<SearchHit> {
    val words = wordsOf(query)
    if (words.isEmpty()) return emptyList()

    val hits = docs.mapNotNull { doc ->
        val title = doc.title.lowercase()
        val text = doc.text.lowercase()
        if (words.all { word -> word in title || word in text }) {
            SearchHit(doc, snippetOf(doc, words.first()))
        } else {
            null
        }
    }

    // A word that stands in the title is a closer answer than one that stands
    // only in the text. The rest keep the order the documents came in.
    return hits
        .partition { hit -> hit.doc.title.lowercase().contains(words.first()) }
        .let { (titleHits, rest) -> titleHits + rest }
        .take(limit)
}

/**
 * A snippet that starts with the title, after trimming and ignoring case, has
 * the title and the separator characters right after it (spaces, newlines,
 * '.', '-', ':') removed, so the row does not show the same words twice.
 * When nothing is left, the empty string is returned and the row shows only
 * the title.
 */
fun withoutTitle(snippet: String, title: String): String {
    val trimmedSnippet = snippet.trim()
    val trimmedTitle = title.trim()
    if (trimmedTitle.isEmpty()) return trimmedSnippet
    if (!trimmedSnippet.lowercase().startsWith(trimmedTitle.lowercase())) return trimmedSnippet
    val rest = trimmedSnippet.substring(trimmedTitle.length)
    val cleaned = rest.trimStart(' ', '\n', '\r', '.', '-', ':')
    return cleaned
}

/**
 * About [SNIPPET_WIDTH] characters of the text around the first match of the
 * first word, on one line. Newlines become spaces, and a "..." marks where the
 * line was cut. When the match is in the title only, the start of the text is
 * shown instead, cut the same way.
 */
private fun snippetOf(doc: SearchDoc, word: String): String {
    // Markdown heading marks ("# ") are left out: they are not the words.
    val flat = doc.text.replace(Regex("(?m)^#+\\s*"), "").replace(Regex("\\s+"), " ").trim()
    val flatLower = flat.lowercase()
    val at = flatLower.indexOf(word)

    return if (at >= 0) {
        val from = (at - SNIPPET_WIDTH / 2).coerceIn(0, flat.length)
        val to = (at + word.length + SNIPPET_WIDTH / 2).coerceAtMost(flat.length)
        val cutFront = from > 0
        val cutBack = to < flat.length
        buildString {
            if (cutFront) append("...")
            append(flat.substring(from, to))
            if (cutBack) append("...")
        }
    } else {
        val cut = flat.length > SNIPPET_WIDTH
        buildString {
            append(flat.substring(0, minOf(SNIPPET_WIDTH, flat.length)))
            if (cut) append("...")
        }
    }
}
