package com.qiplat.compose.sweeteditor.core

data class EditorCoreTextPosition(
    val line: Int = 0,
    val column: Int = 0,
) {
    init {
        require(line >= 0)
        require(column >= 0)
    }

    companion object {
        val Zero = EditorCoreTextPosition()
    }
}

data class EditorCoreTextRange(
    val start: EditorCoreTextPosition = EditorCoreTextPosition.Zero,
    val end: EditorCoreTextPosition = EditorCoreTextPosition.Zero,
)

data class EditorCoreCursorRect(
    val x: Float = 0f,
    val y: Float = 0f,
    val height: Float = 0f,
)

data class EditorCoreTabStopGroup(
    val index: Int,
    val ranges: List<EditorCoreTextRange>,
    val defaultText: String? = null,
)

data class EditorCoreLinkedEditingModel(
    val groups: List<EditorCoreTabStopGroup>,
)

enum class EditorCoreWrapMode {
    None,
    CharBreak,
    WordBreak,
}

enum class EditorCoreCurrentLineRenderMode {
    Background,
    Border,
    None,
}

enum class EditorCoreFoldArrowMode {
    Auto,
    Always,
    Hidden,
}

enum class EditorCoreAutoIndentMode {
    None,
    KeepIndent,
}

enum class EditorCoreScrollBehavior {
    GoToTop,
    GoToCenter,
    GoToBottom,
}

interface EditorCoreTextMeasurer {
    fun measureTextWidth(text: String, fontStyle: Int): Float

    fun measureInlayHintWidth(text: String): Float

    fun measureIconWidth(iconId: Int): Float

    fun getFontMetrics(): FloatArray
}

interface EditorCoreDocument {
    val handle: Long

    fun getLineCount(): Int

    fun getLineText(line: Int): String

    fun release()
}

interface EditorCore {
    fun release()

    fun setDocument(document: EditorCoreDocument?)

    fun setViewport(width: Int, height: Int)

    fun onFontMetricsChanged()

    fun setFoldArrowMode(mode: EditorCoreFoldArrowMode)

    fun setWrapMode(mode: EditorCoreWrapMode)

    fun setTabSize(tabSize: Int)

    fun setScale(scale: Float)

    fun setLineSpacing(add: Float, mult: Float)

    fun setShowSplitLine(show: Boolean)

    fun setCurrentLineRenderMode(mode: EditorCoreCurrentLineRenderMode)

    fun setGutterSticky(sticky: Boolean)

    fun setGutterVisible(visible: Boolean)

    fun setReadOnly(readOnly: Boolean)

    fun isReadOnly(): Boolean

    fun setCompositionEnabled(enabled: Boolean)

    fun isCompositionEnabled(): Boolean

    fun setAutoIndentMode(mode: EditorCoreAutoIndentMode)

    fun getAutoIndentMode(): EditorCoreAutoIndentMode

    fun setCursorPosition(position: EditorCoreTextPosition)

    fun setSelection(range: EditorCoreTextRange)

    fun getCursorPosition(): EditorCoreTextPosition

    fun getSelection(): EditorCoreTextRange?

    fun buildRenderModel(): ByteArray?

    fun getScrollMetrics(): ByteArray?

    fun handleGesture(
        type: Int,
        points: FloatArray,
        modifiers: Int = 0,
        wheelDeltaX: Float = 0f,
        wheelDeltaY: Float = 0f,
        directScale: Float = 1f,
    ): ByteArray?

    fun tickAnimations(): ByteArray?

    fun handleKeyEvent(keyCode: Int, text: String?, modifiers: Int): ByteArray?

    fun compositionStart()

    fun compositionUpdate(text: String)

    fun compositionEnd(committedText: String?): ByteArray?

    fun compositionCancel()

    fun isComposing(): Boolean

    fun insertText(text: String): ByteArray?

    fun replaceText(range: EditorCoreTextRange, text: String): ByteArray?

    fun deleteText(range: EditorCoreTextRange): ByteArray?

    fun backspace(): ByteArray?

    fun deleteForward(): ByteArray?

    fun insertSnippet(template: String): ByteArray?

    fun startLinkedEditing(model: EditorCoreLinkedEditingModel)

    fun isInLinkedEditing(): Boolean

    fun linkedEditingNext(): Boolean

    fun linkedEditingPrev(): Boolean

    fun cancelLinkedEditing()

    fun moveLineUp(): ByteArray?

    fun moveLineDown(): ByteArray?

    fun copyLineUp(): ByteArray?

    fun copyLineDown(): ByteArray?

    fun deleteLine(): ByteArray?

    fun insertLineAbove(): ByteArray?

    fun insertLineBelow(): ByteArray?

    fun undo(): ByteArray?

    fun redo(): ByteArray?

    fun canUndo(): Boolean

    fun canRedo(): Boolean

    fun selectAll()

    fun getSelectedText(): String?

    fun getWordRangeAtCursor(): EditorCoreTextRange

    fun getWordAtCursor(): String?

    fun moveCursorLeft(extendSelection: Boolean)

    fun moveCursorRight(extendSelection: Boolean)

    fun moveCursorUp(extendSelection: Boolean)

    fun moveCursorDown(extendSelection: Boolean)

    fun moveCursorToLineStart(extendSelection: Boolean)

    fun moveCursorToLineEnd(extendSelection: Boolean)

    fun scrollToLine(line: Int, behavior: EditorCoreScrollBehavior)

    fun gotoPosition(line: Int, column: Int)

    fun setScroll(scrollX: Float, scrollY: Float)

    fun getPositionRect(line: Int, column: Int): EditorCoreCursorRect

    fun getCursorRect(): EditorCoreCursorRect

    fun registerBatchTextStyles(data: ByteArray)

    fun setBatchLineSpans(data: ByteArray)

    fun setBatchLineInlayHints(data: ByteArray)

    fun setBatchLinePhantomTexts(data: ByteArray)

    fun setBatchLineGutterIcons(data: ByteArray)

    fun setBatchLineDiagnostics(data: ByteArray)

    fun clearInlayHints()

    fun clearPhantomTexts()

    fun clearGutterIcons()

    fun clearDiagnostics()

    fun setIndentGuides(data: ByteArray)

    fun setBracketGuides(data: ByteArray)

    fun setFlowGuides(data: ByteArray)

    fun setSeparatorGuides(data: ByteArray)

    fun clearGuides()

    fun setFoldRegions(data: ByteArray)

    fun clearAllDecorations()

    fun setMaxGutterIcons(count: Int)
}

fun interface EditorCoreFactory {
    fun create(textMeasurer: EditorCoreTextMeasurer): EditorCore
}
