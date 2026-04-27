package com.qiplat.compose.sweeteditor.model.decoration

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.qiplat.compose.sweeteditor.model.foundation.TextPosition
import com.qiplat.compose.sweeteditor.model.foundation.TextRange
import kotlin.jvm.JvmInline

enum class SpanLayer {
    Syntax,
    Semantic,
}

@JvmInline
value class SpanFontStyle private constructor(val bits: Int) {
    companion object {
        val Normal: SpanFontStyle = SpanFontStyle(0)
        val Bold: SpanFontStyle = SpanFontStyle(1 shl 0)
        val Italic: SpanFontStyle = SpanFontStyle(1 shl 1)
        val Strikethrough: SpanFontStyle = SpanFontStyle(1 shl 2)

        private const val KnownBitsMask: Int = (1 shl 3) - 1

        internal fun fromBits(bits: Int): SpanFontStyle = SpanFontStyle(bits and KnownBitsMask)
    }

    infix fun or(other: SpanFontStyle): SpanFontStyle = fromBits(bits or other.bits)

    fun contains(style: SpanFontStyle): Boolean = (bits and style.bits) == style.bits
}

data class SpanStyle(
    val color: Color = Color.Unspecified,
    val backgroundColor: Color = Color.Unspecified,
    val fontStyle: SpanFontStyle = SpanFontStyle.Normal,
) {
    private val internalStyle: SpanStyleInternal = SpanStyleInternal(
        color = if (color == Color.Unspecified) 0 else color.toArgb(),
        backgroundColor = if (backgroundColor == Color.Unspecified) 0 else backgroundColor.toArgb(),
        fontStyleBits = fontStyle.bits,
    )

    internal fun toInternal(): SpanStyleInternal = internalStyle

    internal companion object {
        fun fromInternal(style: SpanStyleInternal): SpanStyle = SpanStyle(
            color = if (style.color == 0) Color.Unspecified else Color(style.color),
            backgroundColor = if (style.backgroundColor == 0) Color.Unspecified else Color(style.backgroundColor),
            fontStyle = SpanFontStyle.fromBits(style.fontStyleBits),
        )
    }
}

internal data class SpanStyleInternal(
    val color: Int,
    val backgroundColor: Int,
    val fontStyleBits: Int,
)

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
