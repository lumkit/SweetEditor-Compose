package com.qiplat.compose.sweeteditor.copilot

/**
 * Inline suggestion anchored to a 0-based document position.
 */
data class InlineSuggestion(
    val line: Int,
    val column: Int,
    val text: String,
)
