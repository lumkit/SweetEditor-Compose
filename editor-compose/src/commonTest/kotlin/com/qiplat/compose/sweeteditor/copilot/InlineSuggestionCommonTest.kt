package com.qiplat.compose.sweeteditor.copilot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineSuggestionCommonTest {
    @Test
    fun buildInlineSuggestionPhantomTextsReturnsSingleLineMapping() {
        val result = buildInlineSuggestionPhantomTexts(
            suggestion = InlineSuggestion(
                line = 12,
                column = 4,
                text = "println(value)",
            ),
            requestedLineRange = 10..20,
        )

        assertEquals(setOf(12), result.keys)
        val phantom = result.getValue(12).single()
        assertEquals(4, phantom.column)
        assertEquals("println(value)", phantom.text)
    }

    @Test
    fun buildInlineSuggestionPhantomTextsSkipsEmptyOrOutOfRangeSuggestion() {
        assertTrue(
            buildInlineSuggestionPhantomTexts(
                suggestion = InlineSuggestion(line = 30, column = 0, text = "demo"),
                requestedLineRange = 1..10,
            ).isEmpty(),
        )
        assertTrue(
            buildInlineSuggestionPhantomTexts(
                suggestion = InlineSuggestion(line = 3, column = 1, text = ""),
                requestedLineRange = 1..10,
            ).isEmpty(),
        )
    }
}
