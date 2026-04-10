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

data class EditorCoreRect(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
)

data class EditorCoreTextChange(
    val range: EditorCoreTextRange,
    val text: String,
)

data class EditorCoreTextEditResult(
    val changed: Boolean = false,
    val changes: List<EditorCoreTextChange> = emptyList(),
    val cursor: EditorCoreTextPosition = EditorCoreTextPosition.Zero,
    val selection: EditorCoreTextRange? = null,
) {
    companion object {
        val Unchanged = EditorCoreTextEditResult()
    }
}

data class EditorCoreKeyEventResult(
    val handled: Boolean = false,
    val editResult: EditorCoreTextEditResult = EditorCoreTextEditResult.Unchanged,
)

data class EditorCoreGestureResult(
    val handled: Boolean = false,
)

data class EditorCoreScrollMetrics(
    val scrollX: Float = 0f,
    val scrollY: Float = 0f,
    val maxScrollX: Float = 0f,
    val maxScrollY: Float = 0f,
)

data class EditorCoreRenderRun(
    val text: String,
    val x: Float,
    val width: Float,
)

data class EditorCoreRenderLine(
    val logicalLine: Int,
    val wrapIndex: Int,
    val top: Float,
    val height: Float,
    val text: String,
    val runs: List<EditorCoreRenderRun>,
)

data class EditorCoreRenderModel(
    val documentVersion: Long = 0L,
    val lines: List<EditorCoreRenderLine> = emptyList(),
    val cursorRect: EditorCoreCursorRect? = null,
    val selectionRects: List<EditorCoreRect> = emptyList(),
    val contentWidth: Float = 0f,
    val contentHeight: Float = 0f,
    val lineHeight: Float = 0f,
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

    fun buildRenderModel(): EditorCoreRenderModel?

    fun getScrollMetrics(): EditorCoreScrollMetrics

    fun handleGesture(
        type: Int,
        points: FloatArray,
        modifiers: Int = 0,
        wheelDeltaX: Float = 0f,
        wheelDeltaY: Float = 0f,
        directScale: Float = 1f,
    ): EditorCoreGestureResult

    fun tickAnimations(): EditorCoreGestureResult

    fun handleKeyEvent(keyCode: Int, text: String?, modifiers: Int): EditorCoreKeyEventResult

    fun compositionStart()

    fun compositionUpdate(text: String)

    fun compositionEnd(committedText: String?): EditorCoreTextEditResult

    fun compositionCancel()

    fun isComposing(): Boolean

    fun insertText(text: String): EditorCoreTextEditResult

    fun replaceText(range: EditorCoreTextRange, text: String): EditorCoreTextEditResult

    fun deleteText(range: EditorCoreTextRange): EditorCoreTextEditResult

    fun backspace(): EditorCoreTextEditResult

    fun deleteForward(): EditorCoreTextEditResult

    fun insertSnippet(template: String): EditorCoreTextEditResult

    fun startLinkedEditing(model: EditorCoreLinkedEditingModel)

    fun isInLinkedEditing(): Boolean

    fun linkedEditingNext(): Boolean

    fun linkedEditingPrev(): Boolean

    fun cancelLinkedEditing()

    fun moveLineUp(): EditorCoreTextEditResult

    fun moveLineDown(): EditorCoreTextEditResult

    fun copyLineUp(): EditorCoreTextEditResult

    fun copyLineDown(): EditorCoreTextEditResult

    fun deleteLine(): EditorCoreTextEditResult

    fun insertLineAbove(): EditorCoreTextEditResult

    fun insertLineBelow(): EditorCoreTextEditResult

    fun undo(): EditorCoreTextEditResult

    fun redo(): EditorCoreTextEditResult

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
