package com.qiplat.compose.sweeteditor.model.foundation

data class TextPosition(
    val line: Int = 0,
    val column: Int = 0,
) {
    init {
        require(line >= 0)
        require(column >= 0)
    }

    companion object {
        val Zero = TextPosition()
    }
}

data class TextRange(
    val start: TextPosition = TextPosition.Zero,
    val end: TextPosition = TextPosition.Zero,
) {
    val isCollapsed: Boolean
        get() = start == end
}

data class LineSpacing(
    val extra: Float = 0f,
    val multiplier: Float = 1f,
)

enum class ScrollBehavior {
    GoToTop,
    GoToCenter,
    GoToBottom,
}

enum class AutoIndentMode {
    None,
    KeepIndent,
}

enum class WrapMode {
    None,
    CharBreak,
    WordBreak,
}

enum class CurrentLineRenderMode {
    Background,
    Border,
    None,
}

enum class FoldArrowMode {
    Auto,
    Always,
    Hidden,
}
