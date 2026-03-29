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

data class LanguageConfiguration(
    val name: String = "",
    val scopeName: String = "",
    val fileExtensions: List<String> = emptyList(),
    val comments: LanguageCommentTokens = LanguageCommentTokens(),
    val bracketPairs: List<LanguagePair> = emptyList(),
    val autoClosingPairs: List<LanguagePair> = emptyList(),
    val surroundingPairs: List<LanguagePair> = emptyList(),
    val highlightStyleIds: Map<String, Int> = emptyMap(),
)

object LanguageConfigurationParser {
    fun parse(json: String): LanguageConfiguration {
        val name = findStringValue(json, "name").orEmpty()
        val scopeName = findStringValue(json, "scopeName").orEmpty()
        val fileExtensions = findArrayValues(json, "fileExtensions")
        val comments = LanguageCommentTokens(
            lineComment = findCommentToken(json, "lineComment"),
            blockCommentStart = findPairValues(json, "blockComment").getOrNull(0),
            blockCommentEnd = findPairValues(json, "blockComment").getOrNull(1),
        )
        return LanguageConfiguration(
            name = name,
            scopeName = scopeName,
            fileExtensions = fileExtensions,
            comments = comments,
            bracketPairs = findPairs(json, "brackets"),
            autoClosingPairs = findPairs(json, "autoClosingPairs"),
            surroundingPairs = findPairs(json, "surroundingPairs"),
            highlightStyleIds = findHighlightStyleIds(json),
        )
    }

    private fun findStringValue(json: String, key: String): String? {
        val marker = "\"$key\""
        val keyIndex = json.indexOf(marker)
        if (keyIndex < 0) {
            return null
        }
        val colonIndex = json.indexOf(':', startIndex = keyIndex + marker.length)
        if (colonIndex < 0) {
            return null
        }
        val firstQuoteIndex = json.indexOf('"', startIndex = colonIndex + 1)
        if (firstQuoteIndex < 0) {
            return null
        }
        val secondQuoteIndex = json.indexOf('"', startIndex = firstQuoteIndex + 1)
        if (secondQuoteIndex < 0) {
            return null
        }
        return json.substring(firstQuoteIndex + 1, secondQuoteIndex)
    }

    private fun findArrayValues(json: String, key: String): List<String> {
        val marker = "\"$key\""
        val keyIndex = json.indexOf(marker)
        if (keyIndex < 0) {
            return emptyList()
        }
        val arrayStart = json.indexOf('[', startIndex = keyIndex + marker.length)
        if (arrayStart < 0) {
            return emptyList()
        }
        val arrayEnd = json.indexOf(']', startIndex = arrayStart + 1)
        if (arrayEnd < 0) {
            return emptyList()
        }
        val content = json.substring(arrayStart + 1, arrayEnd)

        return extractJsonStrings(content)
    }

    private fun extractJsonStrings(content: String): List<String> =
        Regex("\"((?:\\\\.|[^\"])*)\"")
            .findAll(content)
            .map { decodeJsonString(it.groupValues[1]) }
            .toList()

    private fun findCommentToken(json: String, key: String): String? {
        val block = findObjectBlock(json, "comments") ?: return null
        return findStringValue(block, key)
    }

    private fun findPairValues(json: String, key: String): List<String> {
        val block = findObjectBlock(json, "comments") ?: return emptyList()
        val marker = "\"$key\""
        val keyIndex = block.indexOf(marker)
        if (keyIndex < 0) {
            return emptyList()
        }
        val arrayStart = block.indexOf('[', startIndex = keyIndex + marker.length)
        if (arrayStart < 0) {
            return emptyList()
        }
        val arrayEnd = block.indexOf(']', startIndex = arrayStart + 1)
        if (arrayEnd < 0) {
            return emptyList()
        }
        return extractJsonStrings(block.substring(arrayStart + 1, arrayEnd))
    }

    private fun findPairs(json: String, key: String): List<LanguagePair> {
        val block = findArrayBlock(json, key) ?: return emptyList()
        return Regex("\\[\\s*\"((?:\\\\.|[^\"])*)\"\\s*,\\s*\"((?:\\\\.|[^\"])*)\"\\s*\\]")
            .findAll(block)
            .map {
                LanguagePair(
                    open = decodeJsonString(it.groupValues[1]),
                    close = decodeJsonString(it.groupValues[2]),
                )
            }
            .toList()
    }

    private fun findHighlightStyleIds(json: String): Map<String, Int> {
        val styleNames = mutableSetOf<String>()
        Regex("\"style\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(json)
            .forEach { styleNames += it.groupValues[1] }
        Regex("\"styles\"\\s*:\\s*\\[([^\\]]+)\\]")
            .findAll(json)
            .forEach { match ->
                Regex("\"([^\"]+)\"")
                    .findAll(match.groupValues[1])
                    .forEach { styleNames += it.groupValues[1] }
            }
        return buildMap {
            styleNames.forEach { styleName ->
                EditorThemeStyleIds.resolve(styleName)?.let { put(styleName, it) }
            }
        }
    }

    private fun findObjectBlock(json: String, key: String): String? {
        val marker = "\"$key\""
        val keyIndex = json.indexOf(marker)
        if (keyIndex < 0) {
            return null
        }
        val start = json.indexOf('{', keyIndex + marker.length)
        if (start < 0) {
            return null
        }
        var depth = 0
        for (index in start until json.length) {
            when (json[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return json.substring(start, index + 1)
                    }
                }
            }
        }
        return null
    }

    private fun findArrayBlock(json: String, key: String): String? {
        val marker = "\"$key\""
        val keyIndex = json.indexOf(marker)
        if (keyIndex < 0) {
            return null
        }
        val start = json.indexOf('[', keyIndex + marker.length)
        if (start < 0) {
            return null
        }
        var depth = 0
        for (index in start until json.length) {
            when (json[index]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        return json.substring(start + 1, index)
                    }
                }
            }
        }
        return null
    }

    private fun decodeJsonString(value: String): String =
        buildString(value.length) {
            var index = 0
            while (index < value.length) {
                val current = value[index]
                if (current == '\\' && index + 1 < value.length) {
                    val next = value[index + 1]
                    append(
                        when (next) {
                            '\\' -> '\\'
                            '"' -> '"'
                            '/' -> '/'
                            'b' -> '\b'
                            'f' -> '\u000C'
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            else -> next
                        },
                    )
                    index += 2
                } else {
                    append(current)
                    index++
                }
            }
        }
}
