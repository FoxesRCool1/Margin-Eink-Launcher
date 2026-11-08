package io.github.foxesrcool1.margin.core.json

/** The file was not JSON, or was JSON this reader refuses. */
class JsonException(message: String, val offset: Int) :
    IllegalArgumentException("$message at character $offset")

/**
 * Reads and writes JSON.
 *
 * It covers the whole of RFC 8259 except numbers that do not fit a Double,
 * which is everything this app writes. It is small on purpose: a file the app
 * wrote and a file a user edited by hand are the only inputs.
 */
object Json {

    /** How deep an array or object may nest. Stops a crafted file from filling the stack. */
    const val MAX_DEPTH = 64

    fun parse(text: String): JsonValue {
        val reader = Reader(text)
        reader.skipWhitespace()
        val value = reader.readValue(depth = 0)
        reader.skipWhitespace()
        if (!reader.atEnd()) reader.fail("Unexpected text after the value")
        return value
    }

    /** Returns null instead of throwing. Use it when a damaged file must not crash a screen. */
    fun parseOrNull(text: String): JsonValue? = runCatching { parse(text) }.getOrNull()

    fun write(value: JsonValue, pretty: Boolean = true): String =
        StringBuilder().also { out -> writeValue(out, value, pretty, 0) }.toString()

    private fun writeValue(out: StringBuilder, value: JsonValue, pretty: Boolean, depth: Int) {
        when (value) {
            is JsonNull -> out.append("null")
            is JsonBoolean -> out.append(if (value.value) "true" else "false")
            is JsonNumber -> out.append(formatNumber(value.value))
            is JsonString -> writeString(out, value.value)

            is JsonArray -> {
                if (value.items.isEmpty()) {
                    out.append("[]")
                    return
                }
                out.append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    newLine(out, pretty, depth + 1)
                    writeValue(out, item, pretty, depth + 1)
                }
                newLine(out, pretty, depth)
                out.append(']')
            }

            is JsonObject -> {
                if (value.entries.isEmpty()) {
                    out.append("{}")
                    return
                }
                out.append('{')
                var first = true
                value.entries.forEach { (key, item) ->
                    if (!first) out.append(',')
                    first = false
                    newLine(out, pretty, depth + 1)
                    writeString(out, key)
                    out.append(':')
                    if (pretty) out.append(' ')
                    writeValue(out, item, pretty, depth + 1)
                }
                newLine(out, pretty, depth)
                out.append('}')
            }
        }
    }

    private fun newLine(out: StringBuilder, pretty: Boolean, depth: Int) {
        if (!pretty) return
        out.append('\n')
        repeat(depth) { out.append("  ") }
    }

    /** A whole number is written without a trailing `.0`, so the file reads cleanly. */
    private fun formatNumber(value: Double): String = when {
        value.isNaN() || value.isInfinite() -> "0"
        value == value.toLong().toDouble() -> value.toLong().toString()
        else -> value.toString()
    }

    private fun writeString(out: StringBuilder, text: String) {
        out.append('"')
        text.forEach { character ->
            when (character) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else ->
                    if (character < ' ') {
                        out.append("\\u").append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        out.append(character)
                    }
            }
        }
        out.append('"')
    }

    private class Reader(private val text: String) {

        // A byte order mark at the very start is skipped; one anywhere else is an error.
        private var index = if (text.startsWith('\uFEFF')) 1 else 0

        fun atEnd(): Boolean = index >= text.length

        fun fail(message: String): Nothing = throw JsonException(message, index)

        fun skipWhitespace() {
            while (index < text.length && text[index].isJsonWhitespace()) index++
        }

        fun readValue(depth: Int): JsonValue {
            if (depth > MAX_DEPTH) fail("Nested too deeply")
            if (atEnd()) fail("The value is missing")

            return when (val character = text[index]) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> JsonString(readString())
                't' -> readLiteral("true", JsonBoolean(true))
                'f' -> readLiteral("false", JsonBoolean(false))
                'n' -> readLiteral("null", JsonNull)
                else ->
                    if (character == '-' || character.isDigit()) {
                        readNumber()
                    } else {
                        fail("Unexpected character '$character'")
                    }
            }
        }

        private fun readLiteral(word: String, value: JsonValue): JsonValue {
            if (!text.startsWith(word, index)) fail("Expected $word")
            index += word.length
            return value
        }

        private fun readObject(depth: Int): JsonObject {
            index++ // past '{'
            val entries = LinkedHashMap<String, JsonValue>()
            skipWhitespace()

            if (!atEnd() && text[index] == '}') {
                index++
                return JsonObject(entries)
            }

            while (true) {
                skipWhitespace()
                if (atEnd() || text[index] != '"') fail("Expected a name in quotes")
                val key = readString()

                skipWhitespace()
                if (atEnd() || text[index] != ':') fail("Expected ':'")
                index++

                skipWhitespace()
                entries[key] = readValue(depth + 1)

                skipWhitespace()
                if (atEnd()) fail("The object was not closed")
                when (text[index]) {
                    ',' -> index++
                    '}' -> {
                        index++
                        return JsonObject(entries)
                    }
                    else -> fail("Expected ',' or '}'")
                }
            }
        }

        private fun readArray(depth: Int): JsonArray {
            index++ // past '['
            val items = mutableListOf<JsonValue>()
            skipWhitespace()

            if (!atEnd() && text[index] == ']') {
                index++
                return JsonArray(items)
            }

            while (true) {
                skipWhitespace()
                items += readValue(depth + 1)

                skipWhitespace()
                if (atEnd()) fail("The array was not closed")
                when (text[index]) {
                    ',' -> index++
                    ']' -> {
                        index++
                        return JsonArray(items)
                    }
                    else -> fail("Expected ',' or ']'")
                }
            }
        }

        private fun readString(): String {
            index++ // past the opening quote
            val out = StringBuilder()

            while (true) {
                if (atEnd()) fail("The text was not closed")
                when (val character = text[index]) {
                    '"' -> {
                        index++
                        return out.toString()
                    }

                    '\\' -> {
                        index++
                        if (atEnd()) fail("The escape was not finished")
                        when (val escaped = text[index]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (index + 4 >= text.length) fail("The \\u escape is too short")
                                val code = text.substring(index + 1, index + 5)
                                if (!code.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                                    fail("'$code' is not four hex digits")
                                }
                                out.append(code.toInt(16).toChar())
                                index += 4
                            }
                            else -> fail("Unknown escape '\\$escaped'")
                        }
                        index++
                    }

                    else -> {
                        if (character < ' ') fail("A control character must be escaped")
                        out.append(character)
                        index++
                    }
                }
            }
        }

        private fun readNumber(): JsonNumber {
            val start = index
            if (!atEnd() && text[index] == '-') index++
            while (!atEnd() && text[index].isDigit()) index++
            if (!atEnd() && text[index] == '.') {
                index++
                while (!atEnd() && text[index].isDigit()) index++
            }
            if (!atEnd() && (text[index] == 'e' || text[index] == 'E')) {
                index++
                if (!atEnd() && (text[index] == '+' || text[index] == '-')) index++
                while (!atEnd() && text[index].isDigit()) index++
            }

            val slice = text.substring(start, index)
            val value = slice.toDoubleOrNull()?.takeIf { it.isFinite() } ?: run {
                index = start
                fail("'$slice' is not a finite number")
            }
            return JsonNumber(value)
        }

        private fun Char.isJsonWhitespace(): Boolean =
            this == ' ' || this == '\t' || this == '\n' || this == '\r'
    }
}
