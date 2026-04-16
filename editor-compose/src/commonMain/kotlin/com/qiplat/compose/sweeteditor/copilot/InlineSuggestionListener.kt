package com.qiplat.compose.sweeteditor.copilot

fun interface InlineSuggestionAcceptedListener {
    fun onSuggestionAccepted(suggestion: InlineSuggestion)
}

interface InlineSuggestionListener {
    fun onSuggestionAccepted(suggestion: InlineSuggestion) = Unit

    fun onSuggestionDismissed(suggestion: InlineSuggestion) = Unit
}
