package com.qiplat.compose.sweeteditor.model.decoration

import com.qiplat.compose.sweeteditor.model.foundation.TextPosition
import com.qiplat.compose.sweeteditor.model.foundation.TextRange

enum class SpanLayer {
    Syntax,
    Semantic,
}

data class TextStyle(
    val color: Int = 0,
    val backgroundColor: Int = 0,
    val fontStyle: Int = Normal,
) {
    companion object {
        const val Normal: Int = 0
        const val Bold: Int = 1 shl 0
        const val Italic: Int = 1 shl 1
        const val Strikethrough: Int = 1 shl 2
    }
}

data class StyleSpan(
    val column: Int,
    val length: Int,
    val styleId: Int,
)

enum class InlayType {
    Text,
    Icon,
    Color,
}

data class InlayHint(
    val type: InlayType = InlayType.Text,
    val column: Int,
    val text: String = "",
    val iconId: Int = 0,
    val color: Int = 0,
)

data class PhantomText(
    val column: Int,
    val text: String,
)

data class GutterIcon(
    val iconId: Int,
)

enum class DiagnosticSeverity {
    Error,
    Warning,
    Info,
    Hint,
}

data class DiagnosticItem(
    val column: Int,
    val length: Int,
    val severity: DiagnosticSeverity = DiagnosticSeverity.Error,
    val color: Int = 0,
)

data class FoldRegion(
    val startLine: Int,
    val endLine: Int,
    val collapsed: Boolean = false,
)

data class IndentGuide(
    val start: TextPosition,
    val end: TextPosition,
)

data class BracketGuide(
    val parent: TextPosition,
    val end: TextPosition,
    val children: List<TextPosition> = emptyList(),
)

data class FlowGuide(
    val start: TextPosition,
    val end: TextPosition,
)

enum class SeparatorStyle {
    Single,
    Double,
}

data class SeparatorGuide(
    val line: Int,
    val style: SeparatorStyle,
    val count: Int,
    val textEndColumn: Int,
)

data class MatchedBracketPair(
    val open: TextPosition,
    val close: TextPosition,
)

data class BracketPair(
    val open: Int,
    val close: Int,
)

data class LinkedEditingHighlight(
    val range: TextRange,
    val isActive: Boolean,
)
