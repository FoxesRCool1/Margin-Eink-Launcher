package io.github.foxesrcool1.margin.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {

    private fun doc(kind: SearchKind, title: String, text: String, path: String = "p") =
        SearchDoc(kind, path, title, text)

    @Test
    fun `a blank query finds nothing`() {
        val docs = listOf(doc(SearchKind.Note, "Walden", "the pond is quiet"))
        assertTrue(search(docs, "").isEmpty())
        assertTrue(search(docs, "   ").isEmpty())
    }

    @Test
    fun `every word of the query must stand in the title or the text, in any case`() {
        val both = doc(SearchKind.Note, "My note", "By the POND I read WALDEN all day")
        val onlyPond = doc(SearchKind.Note, "Other", "The pond is quiet")
        val docs = listOf(both, onlyPond)

        val hits = search(docs, "Walden pond")
        assertEquals(1, hits.size)
        assertEquals("My note", hits.single().doc.title)
    }

    @Test
    fun `a word in the title counts as a match for the whole query`() {
        val docs = listOf(doc(SearchKind.Journal, "Walden", "nothing about a pond here"))
        assertEquals(1, search(docs, "walden").size)
    }

    @Test
    fun `a title match comes before a text match`() {
        val inText = doc(SearchKind.Note, "A note", "Walden by the pond")
        val inTitle = doc(SearchKind.Note, "Walden", "the pond is quiet")
        val docs = listOf(inText, inTitle)

        val hits = search(docs, "Walden")
        assertEquals(listOf("Walden", "A note"), hits.map { it.doc.title })
    }

    @Test
    fun `the snippet is one line of about eighty characters`() {
        val text = "The morning was clear and cold. " + "The pond water stood still under the trees. ".repeat(5)
        val docs = listOf(doc(SearchKind.Note, "A note", text))

        val snippet = search(docs, "pond").single().snippet
        assertFalse(snippet.contains("\n"))
        assertFalse(snippet.contains("\r"))
        assertTrue(snippet.length <= 90)
        assertTrue(snippet.contains("pond"))
    }

    @Test
    fun `a cut snippet has three dots where it was cut`() {
        val text = "x".repeat(80) + " pond " + "y".repeat(80)
        val docs = listOf(doc(SearchKind.Note, "A note", text))

        val snippet = search(docs, "pond").single().snippet
        assertTrue(snippet.startsWith("..."))
        assertTrue(snippet.endsWith("..."))
    }

    @Test
    fun `when only the title matched, the snippet is the start of the text`() {
        val docs = listOf(doc(SearchKind.Highlight, "Walden", "The pond is quiet at dawn."))
        val snippet = search(docs, "walden").single().snippet
        assertEquals("The pond is quiet at dawn.", snippet)
    }

    @Test
    fun `a heading mark is not part of the snippet`() {
        val docs = listOf(doc(SearchKind.Note, "Pond", "# Pond\nThe water stood still."))
        assertEquals("Pond The water stood still.", search(docs, "water").single().snippet)
    }
}
