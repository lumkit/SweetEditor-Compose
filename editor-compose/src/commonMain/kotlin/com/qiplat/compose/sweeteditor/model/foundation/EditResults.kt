package com.qiplat.compose.sweeteditor.model.foundation

data class TextChange(
    val range: TextRange,
    val newText: String,
)

data class TextEditResult(
    val changed: Boolean,
    val changes: List<TextChange>,
) {
    companion object {
        val Empty = TextEditResult(
            changed = false,
            changes = emptyList(),
        )
    }
}

data class KeyEventResult(
    val handled: Boolean = false,
    val contentChanged: Boolean = false,
    val cursorChanged: Boolean = false,
    val selectionChanged: Boolean = false,
    val editResult: TextEditResult = TextEditResult.Empty,
)
