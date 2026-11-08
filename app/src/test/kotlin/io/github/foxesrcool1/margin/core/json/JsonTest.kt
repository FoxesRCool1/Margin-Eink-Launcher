package io.github.foxesrcool1.margin.core.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {

    @Test
    fun `the simple values read back`() {
        assertEquals(JsonNull, Json.parse("null"))
        assertEquals(JsonBoolean(true), Json.parse("true"))
        assertEquals(JsonBoolean(false), Json.parse("false"))
        assertEquals(JsonNumber(42.0), Json.parse("42"))
        assertEquals(JsonNumber(-1.5), Json.parse("-1.5"))
        assertEquals(JsonNumber(1500.0), Json.parse("1.5e3"))
        assertEquals(JsonString("hello"), Json.parse("\"hello\""))
    }

    @Test
    fun `space around a value is ignored`() {
        assertEquals(JsonNumber(1.0), Json.parse("  \n\t 1 \r\n "))
    }

    @Test
    fun `an object keeps the order its keys were written in`() {
        val parsed = Json.parse("""{"b": 1, "a": 2, "c": 3}""") as JsonObject
        assertEquals(listOf("b", "a", "c"), parsed.entries.keys.toList())
    }

    @Test
    fun `an object is read by name and by type`() {
        val parsed = Json.parse(
            """{"name": "Read", "streak": 12, "archived": false, "days": [1, 2]}""",
        ) as JsonObject

        assertEquals("Read", parsed.string("name"))
        assertEquals(12L, parsed.long("streak"))
        assertEquals(12, parsed.int("streak"))
        assertEquals(false, parsed.boolean("archived"))
        assertEquals(2, parsed.array("days")!!.size)
        assertNull(parsed.string("missing"))
        assertNull(parsed.long("name"))
    }

    @Test
    fun `empty objects and arrays are handled`() {
        assertEquals(JsonObject(emptyMap()), Json.parse("{}"))
        assertEquals(JsonArray(emptyList()), Json.parse("[]"))
        assertEquals("{}", Json.write(JsonObject(emptyMap())))
        assertEquals("[]", Json.write(JsonArray(emptyList())))
    }

    @Test
    fun `nesting works`() {
        val parsed = Json.parse("""{"a": {"b": [{"c": 1}]}}""") as JsonObject
        val inner = parsed.obj("a")!!.array("b")!!.objects().single()
        assertEquals(1L, inner.long("c"))
    }

    @Test
    fun `every escape in a string is understood`() {
        val parsed = Json.parse("\"a\\\"b\\\\c\\/d\\ne\\tf\\u0041g\"") as JsonString
        assertEquals("a\"b\\c/d\ne\tfAg", parsed.value)
    }

    @Test
    fun `a string with awkward characters survives a round trip`() {
        val awkward = "quote \" backslash \\ newline \n tab \t control \u0001 letter \u00e9"
        val text = Json.write(JsonString(awkward))
        assertEquals(JsonString(awkward), Json.parse(text))
    }

    @Test
    fun `a whole number is written without a decimal point`() {
        assertEquals("5", Json.write(JsonNumber(5L)))
        assertEquals("-5", Json.write(JsonNumber(-5)))
        assertEquals("0.5", Json.write(JsonNumber(0.5)))
    }

    @Test
    fun `a document survives a round trip`() {
        val original = JsonObject.of(
            "version" to jsonOf(1),
            "habits" to jsonArrayOf(
                listOf(
                    JsonObject.of(
                        "id" to jsonOf("read"),
                        "name" to jsonOf("Read 30 minutes"),
                        "archived" to jsonOf(false),
                    ),
                    JsonObject.of(
                        "id" to jsonOf("walk"),
                        "name" to jsonOf("Walk"),
                        "archived" to jsonOf(true),
                    ),
                ),
            ),
        )

        assertEquals(original, Json.parse(Json.write(original, pretty = true)))
        assertEquals(original, Json.parse(Json.write(original, pretty = false)))
    }

    @Test
    fun `pretty and compact differ only in space`() {
        val value = Json.parse("""{"a":[1,2]}""")
        assertTrue(Json.write(value, pretty = true).contains('\n'))
        assertEquals("""{"a":[1,2]}""", Json.write(value, pretty = false))
    }

    @Test
    fun `broken text is refused`() {
        val broken = listOf(
            "",
            "{",
            "}",
            "[1,",
            "[1,]",
            "{\"a\"}",
            "{\"a\": }",
            "{a: 1}",
            "\"unterminated",
            "tru",
            "1 2",
            "{\"a\": 1} extra",
            "\"bad escape \\q\"",
        )
        broken.forEach { text ->
            assertNull("should be refused: $text", Json.parseOrNull(text))
        }
    }

    @Test
    fun `a deeply nested document is refused instead of filling the stack`() {
        val deep = "[".repeat(Json.MAX_DEPTH + 10) + "]".repeat(Json.MAX_DEPTH + 10)
        assertNull(Json.parseOrNull(deep))
    }

    @Test
    fun `a control character inside a string must be escaped`() {
        assertNull(Json.parseOrNull("\"a\u0001b\""))
    }

    @Test
    fun `a leading byte order mark is skipped`() {
        assertEquals(Json.parse("{\"a\":1}"), Json.parse("\uFEFF{\"a\":1}"))
    }

    @Test
    fun `a byte order mark in the middle is refused`() {
        assertNull(Json.parseOrNull("{\"a\":\uFEFF1}"))
    }

    @Test
    fun `a number that is not finite is refused`() {
        assertNull(Json.parseOrNull("1e400"))
        assertNull(Json.parseOrNull("[1e999]"))
        assertEquals(JsonNumber(1e10), Json.parse("1e10"))
        assertEquals(JsonNumber(-0.5), Json.parse("-0.5"))
    }

    @Test
    fun `a unicode escape must be exactly four hex digits`() {
        assertNull(Json.parseOrNull("\"\\u-001\""))
        assertEquals(JsonString("A"), Json.parse("\"\\u0041\""))
    }

    @Test
    fun `the error says where it went wrong`() {
        val error = runCatching { Json.parse("""{"a": 1, "b": }""") }.exceptionOrNull()
        assertTrue(error is JsonException)
        assertTrue((error as JsonException).offset > 0)
    }
}
