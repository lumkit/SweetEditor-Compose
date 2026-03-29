package com.qiplat.compose.sweeteditor.theme

/**
 * Comment token configuration extracted from a language definition file.
 *
 * @property lineComment single-line comment prefix.
 * @property blockCommentStart block comment start token.
 * @property blockCommentEnd block comment end token.
 */
data class LanguageCommentTokens(
    val lineComment: String? = null,
    val blockCommentStart: String? = null,
    val blockCommentEnd: String? = null,
)

/**
 * Open and close token pair used by language features such as brackets and auto-closing rules.
 *
 * @property open opening token.
 * @property close closing token.
 */
data class LanguagePair(
    val open: String,
    val close: String,
)

/**
 * Maps one regex capture group to one named style.
 *
 * @property group capture group index defined by a language rule.
 * @property style named style resolved through [EditorThemeStyleIds].
 */
data class LanguageStyleTarget(
    val group: Int,
    val style: String,
)

/**
 * Maps one regex capture group to a nested lexer state.
 *
 * @property group capture group index defined by a language rule.
 * @property state target state name entered for the captured segment.
 */
data class LanguageSubState(
    val group: Int,
    val state: String,
)

/**
 * One declarative language rule parsed from a language configuration file.
 *
 * @property pattern regex pattern used to match the rule.
 * @property style style applied to the whole match.
 * @property styles style mappings applied to specific capture groups.
 * @property state state to enter after the rule matches.
 * @property onLineEndState state to apply at the end of the line.
 * @property include fragment name to inline into the current rule list.
 * @property includes fragment names to inline into the current rule list.
 * @property subStates nested state mappings for specific capture groups.
 */
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

/**
 * Scope rule used by higher-level features that need start/end block awareness.
 *
 * @property start scope start token.
 * @property end scope end token.
 */
data class LanguageScopeRule(
    val start: String,
    val end: String,
)

/**
 * Parsed language configuration used by decoration providers and editor features.
 *
 * @property name logical language name.
 * @property scopeName scope identifier used by external tooling.
 * @property fileExtensions supported file extensions.
 * @property comments comment token configuration.
 * @property bracketPairs bracket pairs used by the editor.
 * @property autoClosingPairs pairs used by auto-closing logic.
 * @property surroundingPairs pairs used by surrounding edits.
 * @property highlightStyleIds resolved style ids keyed by language style name.
 * @property variables reusable pattern variables declared by the configuration.
 * @property fragments reusable rule fragments keyed by fragment name.
 * @property states lexer-like rule sets keyed by state name.
 * @property scopeRules scope rules defined by the language.
 */
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

/**
 * Parses the JSON language definition used by the example and provider layer.
 */
object LanguageConfigurationParser {
    /**
     * Parses a JSON string into [LanguageConfiguration].
     *
     * @param json raw JSON language definition.
     * @return parsed language configuration.
     */
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

    /**
     * Converts a JSON array of rule objects into strongly typed language rules.
     *
     * @param array source JSON array.
     * @return parsed language rules.
     */
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

    /**
     * Parses grouped style targets from the compact `[group, style]` format.
     *
     * @param array optional JSON array containing group/style pairs.
     * @return parsed style target list.
     */
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

    /**
     * Parses grouped sub-state targets from the compact `[group, state]` format.
     *
     * @param array optional JSON array containing group/state pairs.
     * @return parsed sub-state list.
     */
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

    /**
     * Collects style ids referenced by all fragments and states.
     *
     * @param fragments parsed fragment rules.
     * @param states parsed state rules.
     * @return resolved style id map keyed by style name.
     */
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

/**
 * Minimal JSON model used by the built-in parser.
 */
private sealed interface JsonValue {
    data class JsonObject(val entries: Map<String, JsonValue>) : JsonValue
    data class JsonArray(val values: List<JsonValue>) : JsonValue
    data class JsonString(val value: String) : JsonValue
    data class JsonNumber(val value: Double) : JsonValue
    data class JsonBoolean(val value: Boolean) : JsonValue
    data object JsonNull : JsonValue
}

/**
 * Lightweight JSON parser used to avoid introducing new dependencies into commonMain.
 *
 * @property source raw JSON input.
 */
private class JsonParser(
    private val source: String,
) {
    private var index: Int = 0

    /**
     * Parses the top-level JSON object.
     *
     * @return parsed JSON object.
     */
    fun parseObject(): JsonValue.JsonObject {
        skipWhitespace()
        return parseValue() as? JsonValue.JsonObject
            ?: error("Expected JSON object")
    }

    /**
     * Parses one JSON value from the current cursor.
     *
     * @return parsed JSON value.
     */
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

    /**
     * Parses a JSON object value.
     *
     * @return parsed object node.
     */
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

    /**
     * Parses a JSON array value.
     *
     * @return parsed array node.
     */
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

    /**
     * Parses a JSON string and resolves escape sequences.
     *
     * @return decoded string value.
     */
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

    /**
     * Parses a JSON number as a double.
     *
     * @return parsed numeric value.
     */
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

    /**
     * Consumes the expected non-whitespace character.
     *
     * @param char expected character.
     */
    private fun expect(char: Char) {
        skipWhitespace()
        check(source[index] == char) {
            "Expected '$char' at index $index"
        }
        index++
    }

    /**
     * Consumes a non-whitespace character when it matches the expected value.
     *
     * @param char expected character.
     * @return true when the character is consumed, false otherwise.
     */
    private fun tryConsume(char: Char): Boolean {
        skipWhitespace()
        return if (index < source.length && source[index] == char) {
            index++
            true
        } else {
            false
        }
    }

    /**
     * Consumes the expected literal token.
     *
     * @param literal expected token text.
     */
    private fun expectLiteral(literal: String) {
        check(source.startsWith(literal, index)) {
            "Expected $literal at index $index"
        }
        index += literal.length
    }

    /**
     * Skips whitespace characters from the current cursor.
     */
    private fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) {
            index++
        }
    }

    /**
     * Returns the current character or `\u0000` when the cursor is out of range.
     *
     * @return current character or sentinel value.
     */
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
