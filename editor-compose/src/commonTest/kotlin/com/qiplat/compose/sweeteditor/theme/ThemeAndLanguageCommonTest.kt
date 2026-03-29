package com.qiplat.compose.sweeteditor.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeAndLanguageCommonTest {
    @Test
    fun resolveThemeStyleAliases() {
        assertEquals(EditorThemeStyleIds.Function, EditorThemeStyleIds.resolve("method"))
        assertEquals(EditorThemeStyleIds.Property, EditorThemeStyleIds.resolve("property"))
        assertEquals(EditorThemeStyleIds.Namespace, EditorThemeStyleIds.resolve("module"))
    }

    @Test
    fun parseLanguageConfigurationMetadata() {
        val json = """
            {
              "name": "kotlin",
              "scopeName": "source.kotlin",
              "fileExtensions": [".kt", ".kts"],
              "comments": {
                "lineComment": "//",
                "blockComment": ["/*", "*/"]
              },
              "brackets": [["{", "}"], ["(", ")"]],
              "autoClosingPairs": [["\"", "\""]],
              "surroundingPairs": [["(", ")"]],
              "variables": {
                "identifier": "[a-zA-Z_]\\w*"
              },
              "fragments": {
                "callRule": [
                  { "pattern": "(${'$'}{identifier})(\\()", "styles": [1, "method", 2, "punctuation"] }
                ]
              },
              "states": {
                "default": [
                  { "pattern": "\\b(class)\\b", "style": "keyword" },
                  { "include": "callRule" },
                  { "styles": [1, "method", 2, "property"] },
                  { "subStates": [1, "default"] }
                ]
              },
              "scopeRules": [
                { "start": "{", "end": "}" }
              ]
            }
        """.trimIndent()

        val configuration = LanguageConfigurationParser.parse(json)

        assertEquals("kotlin", configuration.name)
        assertEquals("source.kotlin", configuration.scopeName)
        assertEquals(listOf(".kt", ".kts"), configuration.fileExtensions)
        assertEquals("//", configuration.comments.lineComment)
        assertEquals("/*", configuration.comments.blockCommentStart)
        assertEquals("*/", configuration.comments.blockCommentEnd)
        assertEquals(2, configuration.bracketPairs.size)
        assertEquals(1, configuration.autoClosingPairs.size)
        assertEquals(1, configuration.surroundingPairs.size)
        assertEquals(EditorThemeStyleIds.Keyword, configuration.highlightStyleIds["keyword"])
        assertEquals(EditorThemeStyleIds.Function, configuration.highlightStyleIds["method"])
        assertEquals(EditorThemeStyleIds.Property, configuration.highlightStyleIds["property"])
        assertEquals("[a-zA-Z_]\\w*", configuration.variables["identifier"])
        assertEquals(1, configuration.fragments["callRule"]?.size)
        assertEquals(4, configuration.states["default"]?.size)
        assertEquals(1, configuration.scopeRules.size)
        assertTrue(configuration.highlightStyleIds.isNotEmpty())
    }
}
