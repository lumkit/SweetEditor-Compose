package com.qiplat.compose.sweeteditor

import com.qiplat.compose.sweeteditor.bridge.NativeDocumentBridge
import com.qiplat.compose.sweeteditor.model.foundation.TextEditResult
import com.qiplat.compose.sweeteditor.model.visual.ScrollMetrics
import com.qiplat.compose.sweeteditor.runtime.EditorDocument
import com.qiplat.compose.sweeteditor.theme.EditorThemeStyleIds
import com.qiplat.compose.sweeteditor.theme.LanguageConfigurationParser
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LanguageConfigDecorationProviderCommonTest {
    @Test
    fun providerBuildsSyntaxSpansFromLanguageConfiguration() {
        val configuration = LanguageConfigurationParser.parse(
            """
                {
                  "name": "demo",
                  "variables": {
                    "identifier": "[a-zA-Z_]\\w*",
                    "any": "[\\S\\s]"
                  },
                  "fragments": {
                    "callRule": [
                      {
                        "pattern": "(${'$'}{identifier})(\\()",
                        "styles": [1, "method", 2, "punctuation"]
                      }
                    ]
                  },
                  "states": {
                    "default": [
                      {
                        "pattern": "\\b(class)\\b\\s+(${'$'}{identifier})",
                        "styles": [1, "keyword", 2, "class"]
                      },
                      {
                        "pattern": "\\b(class|fun)\\b",
                        "style": "keyword"
                      },
                      {
                        "include": "callRule"
                      },
                      {
                        "pattern": "/\\*",
                        "style": "comment",
                        "state": "comment"
                      }
                    ],
                    "comment": [
                      {
                        "pattern": "\\*/",
                        "style": "comment",
                        "state": "default"
                      },
                      {
                        "pattern": "${'$'}{any}",
                        "style": "comment"
                      }
                    ]
                  }
                }
            """.trimIndent(),
        )
        val document = EditorDocument(
            FakeLanguageDocumentBridge(
                listOf(
                    "class Demo",
                    "fun greet(",
                    "/* block",
                    "comment */",
                ),
            ),
        )
        val provider = LanguageConfigDecorationProvider()

        val update = runSuspend {
            provider.provide(
                DecorationProviderContext(
                    document = document,
                    visibleLineRange = 0..3,
                    requestedLineRange = 0..3,
                    renderModel = null,
                    scrollMetrics = ScrollMetrics(),
                    lastEditResult = TextEditResult.Empty,
                    languageConfiguration = configuration,
                    editorMetadata = null,
                ),
            )
        }

        assertNotNull(update)
        val syntaxSpans = update.decorations.syntaxSpans
        assertNotNull(syntaxSpans)
        assertTrue(syntaxSpans.isNotEmpty())
        assertTrue(syntaxSpans.getValue(0).any { it.styleId == EditorThemeStyleIds.Class })
        assertTrue(syntaxSpans.getValue(1).any { it.styleId == EditorThemeStyleIds.Function })
        assertTrue(syntaxSpans.getValue(2).any { it.styleId == EditorThemeStyleIds.Comment })
        assertTrue(syntaxSpans.getValue(3).any { it.styleId == EditorThemeStyleIds.Comment })
    }

    @Test
    fun providerAcceptsUnicodeScriptPropertyPatterns() {
        val configuration = LanguageConfigurationParser.parse(
            """
                {
                  "name": "unicode-demo",
                  "variables": {
                    "identifier": "(?:(?:[\\p{Han}\\w_$]+)(?:[\\p{Han}\\w_$0-9]*))"
                  },
                  "states": {
                    "default": [
                      {
                        "pattern": "(@interface)\\b(?:[ \\t\\f])+(${'$'}{identifier})",
                        "styles": [1, "keyword", 2, "class"]
                      }
                    ]
                  }
                }
            """.trimIndent(),
        )
        val document = EditorDocument(
            FakeLanguageDocumentBridge(
                listOf("@interface 示例"),
            ),
        )
        val provider = LanguageConfigDecorationProvider()

        val update = runSuspend {
            provider.provide(
                DecorationProviderContext(
                    document = document,
                    visibleLineRange = 0..0,
                    requestedLineRange = 0..0,
                    renderModel = null,
                    scrollMetrics = ScrollMetrics(),
                    lastEditResult = TextEditResult.Empty,
                    languageConfiguration = configuration,
                    editorMetadata = null,
                ),
            )
        }

        assertNotNull(update)
        val syntaxSpans = update.decorations.syntaxSpans
        assertNotNull(syntaxSpans)
        assertTrue(syntaxSpans.getValue(0).any { it.styleId == EditorThemeStyleIds.Class })
    }
}

private class FakeLanguageDocumentBridge(
    private val lines: List<String>,
) : NativeDocumentBridge {
    override val handle: Long = 1L

    override fun getLineCount(): Int = lines.size

    override fun getLineText(line: Int): String = lines[line]

    override fun release() = Unit
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var value: T? = null
    var error: Throwable? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                result
                    .onSuccess { value = it }
                    .onFailure { error = it }
            }
        },
    )
    error?.let { throw it }
    return checkNotNull(value)
}
