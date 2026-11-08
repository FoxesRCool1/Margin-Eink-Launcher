package io.github.foxesrcool1.margin.core.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTextTest {

    @Test
    fun `words are counted across any whitespace`() {
        assertEquals(0, NoteText.wordCount(""))
        assertEquals(0, NoteText.wordCount("   \n\t  "))
        assertEquals(1, NoteText.wordCount("hello"))
        assertEquals(2, NoteText.wordCount("hello world"))
        assertEquals(3, NoteText.wordCount("one\ntwo\tthree"))
        assertEquals(2, NoteText.wordCount("  spaced   out  "))
    }

    @Test
    fun `a hyphenated word is one word`() {
        assertEquals(1, NoteText.wordCount("well-meaning"))
        assertEquals(2, NoteText.wordCount("well-meaning people"))
    }

    @Test
    fun `punctuation on its own is not a word`() {
        assertEquals(2, NoteText.wordCount("hello , world"))
        assertEquals(0, NoteText.wordCount("--- ... ###"))
    }

    @Test
    fun `a heading marker does not make the count go up`() {
        assertEquals(2, NoteText.wordCount("# My Plan"))
        assertEquals(2, NoteText.wordCount("## My Plan"))
    }

    @Test
    fun `characters are counted with and without spaces`() {
        assertEquals(11, NoteText.characterCount("hello world"))
        assertEquals(10, NoteText.characterCountWithoutSpaces("hello world"))
        assertEquals(0, NoteText.characterCountWithoutSpaces("  \n "))
    }

    @Test
    fun `the first heading is the title`() {
        assertEquals("My Plan", NoteText.titleFrom("# My Plan\n\nSome text", "fallback"))
        assertEquals("My Plan", NoteText.titleFrom("### My Plan", "fallback"))
    }

    @Test
    fun `blank lines before the heading are skipped`() {
        assertEquals("My Plan", NoteText.titleFrom("\n\n   \n# My Plan\n", "fallback"))
    }

    @Test
    fun `without a heading the first line with something on it is the title`() {
        assertEquals("Just a line", NoteText.titleFrom("Just a line\nmore", "fallback"))
    }

    @Test
    fun `a plain first line is the title even when a heading comes later`() {
        assertEquals("Just a line", NoteText.titleFrom("Just a line\n# Later heading", "fallback"))
    }

    @Test
    fun `an empty note falls back to the file name`() {
        assertEquals("note-3", NoteText.titleFrom("", "note-3"))
        assertEquals("note-3", NoteText.titleFrom("  \n\n ", "note-3"))
    }

    @Test
    fun `a heading with nothing after it is not a title`() {
        assertEquals("###", NoteText.titleFrom("###\nreal text", "fallback"))
    }

    @Test
    fun `a very long title is cut short`() {
        val long = "x".repeat(500)
        assertEquals(80, NoteText.titleFrom("# $long", "fallback").length)
    }

    @Test
    fun `the preview leaves out the title line`() {
        assertEquals(
            "Some text and more",
            NoteText.preview("# My Plan\nSome text\nand more"),
        )
    }

    @Test
    fun `a note with only a title has an empty preview`() {
        assertEquals("", NoteText.preview("# My Plan"))
        assertEquals("", NoteText.preview(""))
    }

    @Test
    fun `a long preview is cut and marked`() {
        val text = "# Title\n" + "word ".repeat(100)
        val preview = NoteText.preview(text, maxCharacters = 20)
        assertEquals(true, preview.endsWith("..."))
        assertEquals(true, preview.length <= 23)
    }

    @Test
    fun `a new title replaces the heading and keeps its level`() {
        assertEquals("# New\n\nText", NoteText.withTitle("# Old\n\nText", "New"))
        assertEquals("\n## New\nText", NoteText.withTitle("\n## Old\nText", " New "))
    }

    @Test
    fun `a note without a heading gets no new title`() {
        assertEquals(null, NoteText.withTitle("Just text\n# Later heading", "New"))
        assertEquals(null, NoteText.withTitle("", "New"))
        assertEquals(null, NoteText.withTitle("#\nText", "New"))
    }
}
