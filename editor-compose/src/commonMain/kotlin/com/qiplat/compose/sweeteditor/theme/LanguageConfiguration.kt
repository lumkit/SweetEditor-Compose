package com.qiplat.compose.sweeteditor.theme

data class LanguageCommentTokens(
    val lineComment: String? = null,
    val blockCommentStart: String? = null,
    val blockCommentEnd: String? = null,
)

data class LanguagePair(
    val open: String,
    val close: String,
)

data class LanguageStyleTarget(
    val group: Int,
    val style: String,
)

data class LanguageSubState(
    val group: Int,
    val state: String,
)

data class LanguageRule(
    val pattern: String? = null,
    val style: String? = null,
    val styles: List<LanguageStyleTarget> = emptyList(),
    val state: String? = null,
    val onLineEndState: String? = null,
    val include: String? = null,
    val includes: List<String> = emptyList(),
    val subStates: List<LanguageSubState> = emptyList(),
)

data class LanguageScopeRule(
    val start: String,
    val end: String,
)

data class LanguageConfiguration(
    val name: String = "",
    val scopeName: String = "",
    val fileExtensions: List<String> = emptyList(),
    val comments: LanguageCommentTokens = LanguageCommentTokens(),
    val bracketPairs: List<LanguagePair> = emptyList(),
    val autoClosingPairs: List<LanguagePair> = emptyList(),
    val surroundingPairs: List<LanguagePair> = emptyList(),
    val highlightStyleIds: Map<String, Int> = emptyMap(),
    val variables: Map<String, String> = emptyMap(),
    val fragments: Map<String, List<LanguageRule>> = emptyMap(),
    val states: Map<String, List<LanguageRule>> = emptyMap(),
    val scopeRules: List<LanguageScopeRule> = emptyList(),
)

object LanguageConfigurationParser {
    fun parse(json: String): LanguageConfiguration {
        val root = JsonParser(json).parseObject()
        val fragments = root.objectValue("fragments")
            ?.entries
            ?.mapNotNull { (key, value) ->
                (value as? JsonValue.JsonArray)?.let { key to parseRules(it) }
            }
            ?.toMap()
            .orEmpty()
        val states = root.objectValue("states")
            ?.entries
            ?.mapNotNull { (key, value) ->
                (value as? JsonValue.JsonArray)?.let { key to parseRules(it) }
            }
            ?.toMap()
            .orEmpty()
        val commentsObject = root.objectValue("comments")
        return LanguageConfiguration(
            name = root.string("name").orEmpty(),
            scopeName = root.string("scopeName").orEmpty(),
            fileExtensions = root.stringList("fileExtensions"),
            comments = LanguageCommentTokens(
                lineComment = commentsObject?.string("lineComment"),
                blockCommentStart = commentsObject?.stringList("blockComment")?.getOrNull(0),
                blockCommentEnd = commentsObject?.stringList("blockComment")?.getOrNull(1),
            ),
            bracketPairs = root.pairs("brackets"),
            autoClosingPairs = root.pairs("autoClosingPairs"),
            surroundingPairs = root.pairs("surroundingPairs"),
            highlightStyleIds = collectHighlightStyleIds(fragments, states),
            variables = root.objectValue("variables")
                ?.entries
                ?.mapNotNull { (key, value) -> (value as? JsonValue.JsonString)?.value?.let { key to it } }
                ?.toMap()
                .orEmpty(),
            fragments = fragments,
            states = states,
            scopeRules = root.arrayValue("scopeRules")
                ?.values
                ?.mapNotNull { value ->
                    val objectValue = value as? JsonValue.JsonObject ?: return@mapNotNull null
                    val start = objectValue.string("start") ?: return@mapNotNull null
                    val end = objectValue.string("end") ?: return@mapNotNull null
                    LanguageScopeRule(start = start, end = end)
                }
                .orEmpty(),
        )
    }

    private fun parseRules(array: JsonValue.JsonArray): List<LanguageRule> =
        array.values.mapNotNull { value ->
            val objectValue = value as? JsonValue.JsonObject ?: return@mapNotNull null
            LanguageRule(
                pattern = objectValue.string("pattern"),
                style = objectValue.string("style"),
                styles = parseStyleTargets(objectValue.arrayValue("styles")),
                state = objectValue.string("state"),
                onLineEndState = objectValue.string("onLineEndState"),
                include = objectValue.string("include"),
                includes = objectValue.stringList("includes"),
                subStates = parseSubStates(objectValue.arrayValue("subStates")),
            )
        }

    private fun parseStyleTargets(array: JsonValue.JsonArray?): List<LanguageStyleTarget> {
        if (array == null) {
            return emptyList()
        }
        return array.values.chunked(2).mapNotNull { pair ->
            val group = (pair.getOrNull(0) as? JsonValue.JsonNumber)?.value?.toInt()
                ?: return@mapNotNull null
            val style = (pair.getOrNull(1) as? JsonValue.JsonString)?.value
                ?: return@mapNotNull null
            LanguageStyleTarget(group = group, style = style)
        }
    }

    private fun parseSubStates(array: JsonValue.JsonArray?): List<LanguageSubState> {
        if (array == null) {
            return emptyList()
        }
        return array.values.chunked(2).mapNotNull { pair ->
            val group = (pair.getOrNull(0) as? JsonValue.JsonNumber)?.value?.toInt()
                ?: return@mapNotNull null
            val state = (pair.getOrNull(1) as? JsonValue.JsonString)?.value
                ?: return@mapNotNull null
            LanguageSubState(group = group, state = state)
        }
    }

    private fun collectHighlightStyleIds(
        fragments: Map<String, List<LanguageRule>>,
        states: Map<String, List<LanguageRule>>,
    ): Map<String, Int> {
        val styleNames = buildSet {
            (fragments.values + states.values).forEach { rules ->
                rules.forEach { rule ->
                    rule.style?.let(::add)
                    rule.styles.mapTo(this) { it.style }
                }
            }
        }
        return buildMap {
            styleNames.forEach { styleName ->
                EditorThemeStyleIds.resolve(styleName)?.let { put(styleName, it) }
            }
        }
    }
}

private sealed interface JsonValue {
    data class JsonObject(val entries: Map<String, JsonValue>) : JsonValue
    data class JsonArray(val values: List<JsonValue>) : JsonValue
    data class JsonString(val value: String) : JsonValue
    data class JsonNumber(val value: Double) : JsonValue
    data class JsonBoolean(val value: Boolean) : JsonValue
    data object JsonNull : JsonValue
}

private class JsonParser(
    private val source: String,
) {
    private var index: Int = 0

    fun parseObject(): JsonValue.JsonObject {
        skipWhitespace()
        return parseValue() as? JsonValue.JsonObject
            ?: error("Expected JSON object")
    }

    private fun parseValue(): JsonValue {
        skipWhitespace()
        return when (peek()) {
            '{' -> parseObjectValue()
            '[' -> parseArrayValue()
            '"' -> JsonValue.JsonString(parseString())
            '-', in '0'..'9' -> JsonValue.JsonNumber(parseNumber())
            't' -> {
                expectLiteral("true")
                JsonValue.JsonBoolean(true)
            }
            'f' -> {
                expectLiteral("false")
                JsonValue.JsonBoolean(false)
            }
            'n' -> {
                expectLiteral("null")
                JsonValue.JsonNull
            }
            else -> error("Unexpected JSON token at index $index")
        }
    }

    private fun parseObjectValue(): JsonValue.JsonObject {
        expect('{')
        skipWhitespace()
        if (tryConsume('}')) {
            return JsonValue.JsonObject(emptyMap())
        }
        val entries = linkedMapOf<String, JsonValue>()
        while (true) {
            val key = parseString()
            skipWhitespace()
            expect(':')
            entries[key] = parseValue()
            skipWhitespace()
            if (tryConsume('}')) {
                return JsonValue.JsonObject(entries)
            }
            expect(',')
        }
    }

    private fun parseArrayValue(): JsonValue.JsonArray {
        expect('[')
        skipWhitespace()
        if (tryConsume(']')) {
            return JsonValue.JsonArray(emptyList())
        }
        val values = mutableListOf<JsonValue>()
        while (true) {
            values += parseValue()
            skipWhitespace()
            if (tryConsume(']')) {
                return JsonValue.JsonArray(values)
            }
            expect(',')
        }
    }

    private fun parseString(): String {
        expect('"')
        return buildString {
            while (true) {
                val current = source[index++]
                when (current) {
                    '"' -> return@buildString
                    '\\' -> {
                        val escaped = source[index++]
                        append(
                            when (escaped) {
                                '\\' -> '\\'
                                '"' -> '"'
                                '/' -> '/'
                                'b' -> '\b'
                                'f' -> '\u000C'
                                'n' -> '\n'
                                'r' -> '\r'
                                't' -> '\t'
                                'u' -> {
                                    val hex = source.substring(index, index + 4)
                                    index += 4
                                    hex.toInt(16).toChar()
                                }
                                else -> escaped
                            },
                        )
                    }
                    else -> append(current)
                }
            }
        }
    }

    private fun parseNumber(): Double {
        val start = index
        if (peek() == '-') {
            index++
        }
        while (peek().isDigit()) {
            index++
        }
        if (peek() == '.') {
            index++
            while (peek().isDigit()) {
                index++
            }
        }
        if (peek() == 'e' || peek() == 'E') {
            index++
            if (peek() == '+' || peek() == '-') {
                index++
            }
            while (peek().isDigit()) {
                index++
            }
        }
        return source.substring(start, index).toDouble()
    }

    private fun expect(char: Char) {
        skipWhitespace()
        check(source[index] == char) {
            "Expected '$char' at index $index"
        }
        index++
    }

    private fun tryConsume(char: Char): Boolean {
        skipWhitespace()
        return if (index < source.length && source[index] == char) {
            index++
            true
        } else {
            false
        }
    }

    private fun expectLiteral(literal: String) {
        check(source.startsWith(literal, index)) {
            "Expected $literal at index $index"
        }
        index += literal.length
    }

    private fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) {
            index++
        }
    }

    private fun peek(): Char =
        source.getOrElse(index) { '\u0000' }
}

private fun JsonValue.JsonObject.string(key: String): String? =
    (entries[key] as? JsonValue.JsonString)?.value

private fun JsonValue.JsonObject.objectValue(key: String): JsonValue.JsonObject? =
    entries[key] as? JsonValue.JsonObject

private fun JsonValue.JsonObject.arrayValue(key: String): JsonValue.JsonArray? =
    entries[key] as? JsonValue.JsonArray

private fun JsonValue.JsonObject.stringList(key: String): List<String> =
    arrayValue(key)?.stringValues().orEmpty()

private fun JsonValue.JsonObject.pairs(key: String): List<LanguagePair> =
    arrayValue(key)
        ?.values
        ?.mapNotNull { value ->
            val pairArray = value as? JsonValue.JsonArray ?: return@mapNotNull null
            val values = pairArray.stringValues()
            if (values.size < 2) {
                null
            } else {
                LanguagePair(values[0], values[1])
            }
        }
        .orEmpty()

private fun JsonValue.JsonArray.stringValues(): List<String> =
    values.mapNotNull { (it as? JsonValue.JsonString)?.value }
