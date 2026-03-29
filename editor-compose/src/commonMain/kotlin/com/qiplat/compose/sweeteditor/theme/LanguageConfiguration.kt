package com.qiplat.compose.sweeteditor.theme

data class LanguageConfiguration(
    val name: String,
    val fileExtensions: List<String>,
)

object LanguageConfigurationParser {
    fun parse(json: String): LanguageConfiguration {
        val name = findStringValue(json, "name").orEmpty()
        val fileExtensions = findArrayValues(json, "fileExtensions")
        return LanguageConfiguration(
            name = name,
            fileExtensions = fileExtensions,
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

        return Regex("\"([^\"]+)\"")
            .findAll(content)
            .map { it.groupValues[1] }
            .toList()
    }
}
