package ru.nksk.lctapp.domain.history

import kotlinx.serialization.json.Json

/** Immutable encoded documents with at most one decoded checkpoint retained by this list. */
internal class IndexedDecodedList<T>(override val size: Int, private val decode: (Int) -> T) : AbstractList<T>() {
    private data class Cached<T>(val index: Int, val value: T)
    private var cached: Cached<T>? = null

    @Synchronized
    override fun get(index: Int): T {
        if (index !in 0 until size) throw IndexOutOfBoundsException("Index $index, size $size")
        return cached?.takeIf { it.index == index }?.value ?: decode(index).also { cached = Cached(index, it) }
    }
}

internal class EncodedAuditHistory(private val entries: List<String>, private val canonical: Boolean,
    checkEntry: (Int, AuditEntry) -> Unit = { _, _ -> }) : AbstractList<AuditEntry>() {
    private val decoded = IndexedDecodedList(entries.size) { index ->
        HistoryCodec.decodeEntry(entries[index]).also { checkEntry(index, it) }
    }
    override val size: Int get() = entries.size
    override fun get(index: Int): AuditEntry = decoded[index]
    fun canonicalEntry(index: Int): String? = if (canonical) entries[index] else null
}

/** Locates JSON values without materializing the large history array as a JSON tree. */
internal object SnapshotJsonSlices {
    fun objectFields(text: String): Map<String, IntRange> {
        val reader = Reader(text)
        val fields = linkedMapOf<String, IntRange>()
        reader.expect('{')
        if (!reader.consume('}')) {
            do {
                reader.whitespace()
                val keyStart = reader.position
                reader.string()
                val key = Json.decodeFromString<String>(text.substring(keyStart, reader.position))
                reader.expect(':')
                val value = reader.value()
                require(fields.put(key, value) == null) { "Repeated snapshot field: $key" }
            } while (reader.consume(','))
            reader.expect('}')
        }
        reader.end()
        return fields
    }

    fun arrayElements(text: String, range: IntRange): List<IntRange> {
        val reader = Reader(text, range.first, range.last + 1)
        val elements = mutableListOf<IntRange>()
        reader.expect('[')
        if (!reader.consume(']')) {
            do { elements += reader.value() } while (reader.consume(','))
            reader.expect(']')
        }
        reader.end()
        return elements
    }

    private class Reader(private val text: String, var position: Int = 0, private val limit: Int = text.length) {
        fun whitespace() { while (position < limit && text[position] in " \n\r\t") position++ }
        fun consume(char: Char): Boolean {
            whitespace()
            return if (position < limit && text[position] == char) { position++; true } else false
        }
        fun expect(char: Char) { require(consume(char)) { "Expected '$char' at $position" } }
        fun end() { whitespace(); require(position == limit) { "Trailing snapshot JSON" } }
        fun string() {
            expect('"')
            while (position < limit) {
                when (val char = text[position++]) {
                    '"' -> return
                    '\\' -> {
                        require(position < limit) { "Unterminated JSON escape" }
                        val escaped = text[position++]
                        require(escaped in "\"\\/bfnrtu") { "Invalid JSON escape" }
                        if (escaped == 'u') repeat(4) {
                            require(position < limit && text[position++] in "0123456789abcdefABCDEF") { "Invalid Unicode escape" }
                        }
                    }
                    else -> require(char >= ' ') { "Invalid JSON string" }
                }
            }
            error("Unterminated JSON string")
        }
        fun value(): IntRange {
            whitespace()
            val start = position
            require(position < limit) { "Missing JSON value" }
            when (text[position]) {
                '"' -> string()
                '[', '{' -> {
                    val closes = ArrayDeque<Char>()
                    closes.addLast(if (text[position++] == '[') ']' else '}')
                    while (closes.isNotEmpty()) {
                        require(position < limit) { "Unterminated JSON container" }
                        when (text[position]) {
                            '"' -> string()
                            '[' -> { position++; closes.addLast(']') }
                            '{' -> { position++; closes.addLast('}') }
                            ']', '}' -> require(text[position++] == closes.removeLast()) { "Mismatched JSON container" }
                            else -> position++
                        }
                    }
                }
                else -> {
                    while (position < limit && text[position] !in ",]} \n\r\t") position++
                    require(position > start) { "Missing JSON value" }
                }
            }
            return start until position
        }
    }
}
