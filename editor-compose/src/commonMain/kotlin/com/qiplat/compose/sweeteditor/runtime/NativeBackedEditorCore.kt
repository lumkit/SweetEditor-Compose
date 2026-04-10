package com.qiplat.compose.sweeteditor.runtime

import com.qiplat.compose.sweeteditor.bridge.NativeBridgeFactory
import com.qiplat.compose.sweeteditor.bridge.NativeDocumentBridge
import com.qiplat.compose.sweeteditor.bridge.NativeEditorBridge
import com.qiplat.compose.sweeteditor.bridge.NativeTextMeasurer
import com.qiplat.compose.sweeteditor.core.*
import com.qiplat.compose.sweeteditor.model.foundation.*
import com.qiplat.compose.sweeteditor.model.snippet.LinkedEditingModel
import com.qiplat.compose.sweeteditor.model.snippet.TabStopGroup
import com.qiplat.compose.sweeteditor.model.visual.CursorRect

internal class NativeBackedEditorCoreFactory(
    private val bridgeFactory: NativeBridgeFactory,
    private val options: EditorOptions = EditorOptions(),
) : EditorCoreFactory {
    override fun create(textMeasurer: EditorCoreTextMeasurer): EditorCore {
        require(textMeasurer is NativeTextMeasurer)
        return NativeBackedEditorCore(
            nativeEditorBridge = bridgeFactory.createEditor(
                textMeasurer = textMeasurer,
                options = options,
            ),
        )
    }
}

internal class NativeBackedEditorCore(
    private val nativeEditorBridge: NativeEditorBridge,
) : EditorCore {
    override fun release() {
        nativeEditorBridge.release()
    }

    override fun setDocument(document: EditorCoreDocument?) {
        require(document == null || document is NativeDocumentBridge)
        nativeEditorBridge.setDocument(document)
    }

    override fun setViewport(width: Int, height: Int) {
        nativeEditorBridge.setViewport(width, height)
    }

    override fun onFontMetricsChanged() {
        nativeEditorBridge.onFontMetricsChanged()
    }

    override fun setFoldArrowMode(mode: EditorCoreFoldArrowMode) {
        nativeEditorBridge.setFoldArrowMode(mode.toNative())
    }

    override fun setWrapMode(mode: EditorCoreWrapMode) {
        nativeEditorBridge.setWrapMode(mode.toNative())
    }

    override fun setTabSize(tabSize: Int) {
        nativeEditorBridge.setTabSize(tabSize)
    }

    override fun setScale(scale: Float) {
        nativeEditorBridge.setScale(scale)
    }

    override fun setLineSpacing(add: Float, mult: Float) {
        nativeEditorBridge.setLineSpacing(add, mult)
    }

    override fun setShowSplitLine(show: Boolean) {
        nativeEditorBridge.setShowSplitLine(show)
    }

    override fun setCurrentLineRenderMode(mode: EditorCoreCurrentLineRenderMode) {
        nativeEditorBridge.setCurrentLineRenderMode(mode.toNative())
    }

    override fun setGutterSticky(sticky: Boolean) {
        nativeEditorBridge.setGutterSticky(sticky)
    }

    override fun setGutterVisible(visible: Boolean) {
        nativeEditorBridge.setGutterVisible(visible)
    }

    override fun setReadOnly(readOnly: Boolean) {
        nativeEditorBridge.setReadOnly(readOnly)
    }

    override fun isReadOnly(): Boolean = nativeEditorBridge.isReadOnly()

    override fun setCompositionEnabled(enabled: Boolean) {
        nativeEditorBridge.setCompositionEnabled(enabled)
    }

    override fun isCompositionEnabled(): Boolean = nativeEditorBridge.isCompositionEnabled()

    override fun setAutoIndentMode(mode: EditorCoreAutoIndentMode) {
        nativeEditorBridge.setAutoIndentMode(mode.toNative())
    }

    override fun getAutoIndentMode(): EditorCoreAutoIndentMode =
        nativeEditorBridge.getAutoIndentMode().toEditorCore()

    override fun setCursorPosition(position: EditorCoreTextPosition) {
        nativeEditorBridge.setCursorPosition(position.toNative())
    }

    override fun setSelection(range: EditorCoreTextRange) {
        nativeEditorBridge.setSelection(range.toNative())
    }

    override fun getCursorPosition(): EditorCoreTextPosition =
        nativeEditorBridge.getCursorPosition().toCore()

    override fun getSelection(): EditorCoreTextRange? =
        nativeEditorBridge.getSelection()?.toCore()

    override fun buildRenderModel(): ByteArray? = nativeEditorBridge.buildRenderModel()

    override fun getScrollMetrics(): ByteArray? = nativeEditorBridge.getScrollMetrics()

    override fun handleGesture(
        type: Int,
        points: FloatArray,
        modifiers: Int,
        wheelDeltaX: Float,
        wheelDeltaY: Float,
        directScale: Float,
    ): ByteArray? = nativeEditorBridge.handleGesture(
        type = type,
        points = points,
        modifiers = modifiers,
        wheelDeltaX = wheelDeltaX,
        wheelDeltaY = wheelDeltaY,
        directScale = directScale,
    )

    override fun tickAnimations(): ByteArray? = nativeEditorBridge.tickAnimations()

    override fun handleKeyEvent(keyCode: Int, text: String?, modifiers: Int): ByteArray? =
        nativeEditorBridge.handleKeyEvent(
            keyCode = keyCode,
            text = text,
            modifiers = modifiers,
        )

    override fun compositionStart() {
        nativeEditorBridge.compositionStart()
    }

    override fun compositionUpdate(text: String) {
        nativeEditorBridge.compositionUpdate(text)
    }

    override fun compositionEnd(committedText: String?): ByteArray? =
        nativeEditorBridge.compositionEnd(committedText)

    override fun compositionCancel() {
        nativeEditorBridge.compositionCancel()
    }

    override fun isComposing(): Boolean = nativeEditorBridge.isComposing()

    override fun insertText(text: String): ByteArray? = nativeEditorBridge.insertText(text)

    override fun replaceText(range: EditorCoreTextRange, text: String): ByteArray? =
        nativeEditorBridge.replaceText(range.toNative(), text)

    override fun deleteText(range: EditorCoreTextRange): ByteArray? =
        nativeEditorBridge.deleteText(range.toNative())

    override fun backspace(): ByteArray? = nativeEditorBridge.backspace()

    override fun deleteForward(): ByteArray? = nativeEditorBridge.deleteForward()

    override fun insertSnippet(template: String): ByteArray? = nativeEditorBridge.insertSnippet(template)

    override fun startLinkedEditing(model: EditorCoreLinkedEditingModel) {
        nativeEditorBridge.startLinkedEditing(model.toNative())
    }

    override fun isInLinkedEditing(): Boolean = nativeEditorBridge.isInLinkedEditing()

    override fun linkedEditingNext(): Boolean = nativeEditorBridge.linkedEditingNext()

    override fun linkedEditingPrev(): Boolean = nativeEditorBridge.linkedEditingPrev()

    override fun cancelLinkedEditing() {
        nativeEditorBridge.cancelLinkedEditing()
    }

    override fun moveLineUp(): ByteArray? = nativeEditorBridge.moveLineUp()

    override fun moveLineDown(): ByteArray? = nativeEditorBridge.moveLineDown()

    override fun copyLineUp(): ByteArray? = nativeEditorBridge.copyLineUp()

    override fun copyLineDown(): ByteArray? = nativeEditorBridge.copyLineDown()

    override fun deleteLine(): ByteArray? = nativeEditorBridge.deleteLine()

    override fun insertLineAbove(): ByteArray? = nativeEditorBridge.insertLineAbove()

    override fun insertLineBelow(): ByteArray? = nativeEditorBridge.insertLineBelow()

    override fun undo(): ByteArray? = nativeEditorBridge.undo()

    override fun redo(): ByteArray? = nativeEditorBridge.redo()

    override fun canUndo(): Boolean = nativeEditorBridge.canUndo()

    override fun canRedo(): Boolean = nativeEditorBridge.canRedo()

    override fun selectAll() {
        nativeEditorBridge.selectAll()
    }

    override fun getSelectedText(): String? = nativeEditorBridge.getSelectedText()

    override fun getWordRangeAtCursor(): EditorCoreTextRange =
        nativeEditorBridge.getWordRangeAtCursor().toCore()

    override fun getWordAtCursor(): String? = nativeEditorBridge.getWordAtCursor()

    override fun moveCursorLeft(extendSelection: Boolean) {
        nativeEditorBridge.moveCursorLeft(extendSelection)
    }

    override fun moveCursorRight(extendSelection: Boolean) {
        nativeEditorBridge.moveCursorRight(extendSelection)
    }

    override fun moveCursorUp(extendSelection: Boolean) {
        nativeEditorBridge.moveCursorUp(extendSelection)
    }

    override fun moveCursorDown(extendSelection: Boolean) {
        nativeEditorBridge.moveCursorDown(extendSelection)
    }

    override fun moveCursorToLineStart(extendSelection: Boolean) {
        nativeEditorBridge.moveCursorToLineStart(extendSelection)
    }

    override fun moveCursorToLineEnd(extendSelection: Boolean) {
        nativeEditorBridge.moveCursorToLineEnd(extendSelection)
    }

    override fun scrollToLine(line: Int, behavior: EditorCoreScrollBehavior) {
        nativeEditorBridge.scrollToLine(line, behavior.toNative())
    }

    override fun gotoPosition(line: Int, column: Int) {
        nativeEditorBridge.gotoPosition(line, column)
    }

    override fun setScroll(scrollX: Float, scrollY: Float) {
        nativeEditorBridge.setScroll(scrollX, scrollY)
    }

    override fun getPositionRect(line: Int, column: Int): EditorCoreCursorRect =
        nativeEditorBridge.getPositionRect(line, column).toCore()

    override fun getCursorRect(): EditorCoreCursorRect =
        nativeEditorBridge.getCursorRect().toCore()

    override fun registerBatchTextStyles(data: ByteArray) {
        nativeEditorBridge.registerBatchTextStyles(data)
    }

    override fun setBatchLineSpans(data: ByteArray) {
        nativeEditorBridge.setBatchLineSpans(data)
    }

    override fun setBatchLineInlayHints(data: ByteArray) {
        nativeEditorBridge.setBatchLineInlayHints(data)
    }

    override fun setBatchLinePhantomTexts(data: ByteArray) {
        nativeEditorBridge.setBatchLinePhantomTexts(data)
    }

    override fun setBatchLineGutterIcons(data: ByteArray) {
        nativeEditorBridge.setBatchLineGutterIcons(data)
    }

    override fun setBatchLineDiagnostics(data: ByteArray) {
        nativeEditorBridge.setBatchLineDiagnostics(data)
    }

    override fun clearInlayHints() {
        nativeEditorBridge.clearInlayHints()
    }

    override fun clearPhantomTexts() {
        nativeEditorBridge.clearPhantomTexts()
    }

    override fun clearGutterIcons() {
        nativeEditorBridge.clearGutterIcons()
    }

    override fun clearDiagnostics() {
        nativeEditorBridge.clearDiagnostics()
    }

    override fun setIndentGuides(data: ByteArray) {
        nativeEditorBridge.setIndentGuides(data)
    }

    override fun setBracketGuides(data: ByteArray) {
        nativeEditorBridge.setBracketGuides(data)
    }

    override fun setFlowGuides(data: ByteArray) {
        nativeEditorBridge.setFlowGuides(data)
    }

    override fun setSeparatorGuides(data: ByteArray) {
        nativeEditorBridge.setSeparatorGuides(data)
    }

    override fun clearGuides() {
        nativeEditorBridge.clearGuides()
    }

    override fun setFoldRegions(data: ByteArray) {
        nativeEditorBridge.setFoldRegions(data)
    }

    override fun clearAllDecorations() {
        nativeEditorBridge.clearAllDecorations()
    }

    override fun setMaxGutterIcons(count: Int) {
        nativeEditorBridge.setMaxGutterIcons(count)
    }
}

internal fun TextPosition.toEditorCore(): EditorCoreTextPosition =
    EditorCoreTextPosition(
        line = line,
        column = column,
    )

internal fun TextRange.toEditorCore(): EditorCoreTextRange =
    EditorCoreTextRange(
        start = start.toEditorCore(),
        end = end.toEditorCore(),
    )

internal fun EditorCoreTextPosition.toNative(): TextPosition =
    TextPosition(
        line = line,
        column = column,
    )

internal fun EditorCoreTextRange.toNative(): TextRange =
    TextRange(
        start = start.toNative(),
        end = end.toNative(),
    )

internal fun EditorCoreLinkedEditingModel.toNative(): LinkedEditingModel =
    LinkedEditingModel(
        groups = groups.map { group ->
            TabStopGroup(
                index = group.index,
                ranges = group.ranges.map { it.toNative() },
                defaultText = group.defaultText,
            )
        },
    )

internal fun LinkedEditingModel.toEditorCore(): EditorCoreLinkedEditingModel =
    EditorCoreLinkedEditingModel(
        groups = groups.map { group ->
            EditorCoreTabStopGroup(
                index = group.index,
                ranges = group.ranges.map { it.toEditorCore() },
                defaultText = group.defaultText,
            )
        },
    )

internal fun FoldArrowMode.toEditorCore(): EditorCoreFoldArrowMode = when (this) {
    FoldArrowMode.Auto -> EditorCoreFoldArrowMode.Auto
    FoldArrowMode.Always -> EditorCoreFoldArrowMode.Always
    FoldArrowMode.Hidden -> EditorCoreFoldArrowMode.Hidden
}

internal fun EditorCoreFoldArrowMode.toNative(): FoldArrowMode = when (this) {
    EditorCoreFoldArrowMode.Auto -> FoldArrowMode.Auto
    EditorCoreFoldArrowMode.Always -> FoldArrowMode.Always
    EditorCoreFoldArrowMode.Hidden -> FoldArrowMode.Hidden
}

internal fun WrapMode.toEditorCore(): EditorCoreWrapMode = when (this) {
    WrapMode.None -> EditorCoreWrapMode.None
    WrapMode.CharBreak -> EditorCoreWrapMode.CharBreak
    WrapMode.WordBreak -> EditorCoreWrapMode.WordBreak
}

internal fun EditorCoreWrapMode.toNative(): WrapMode = when (this) {
    EditorCoreWrapMode.None -> WrapMode.None
    EditorCoreWrapMode.CharBreak -> WrapMode.CharBreak
    EditorCoreWrapMode.WordBreak -> WrapMode.WordBreak
}

internal fun CurrentLineRenderMode.toEditorCore(): EditorCoreCurrentLineRenderMode = when (this) {
    CurrentLineRenderMode.Background -> EditorCoreCurrentLineRenderMode.Background
    CurrentLineRenderMode.Border -> EditorCoreCurrentLineRenderMode.Border
    CurrentLineRenderMode.None -> EditorCoreCurrentLineRenderMode.None
}

internal fun EditorCoreCurrentLineRenderMode.toNative(): CurrentLineRenderMode = when (this) {
    EditorCoreCurrentLineRenderMode.Background -> CurrentLineRenderMode.Background
    EditorCoreCurrentLineRenderMode.Border -> CurrentLineRenderMode.Border
    EditorCoreCurrentLineRenderMode.None -> CurrentLineRenderMode.None
}

internal fun AutoIndentMode.toEditorCore(): EditorCoreAutoIndentMode = when (this) {
    AutoIndentMode.None -> EditorCoreAutoIndentMode.None
    AutoIndentMode.KeepIndent -> EditorCoreAutoIndentMode.KeepIndent
}

internal fun EditorCoreAutoIndentMode.toNative(): AutoIndentMode = when (this) {
    EditorCoreAutoIndentMode.None -> AutoIndentMode.None
    EditorCoreAutoIndentMode.KeepIndent -> AutoIndentMode.KeepIndent
}

internal fun ScrollBehavior.toEditorCore(): EditorCoreScrollBehavior = when (this) {
    ScrollBehavior.GoToTop -> EditorCoreScrollBehavior.GoToTop
    ScrollBehavior.GoToCenter -> EditorCoreScrollBehavior.GoToCenter
    ScrollBehavior.GoToBottom -> EditorCoreScrollBehavior.GoToBottom
}

internal fun EditorCoreScrollBehavior.toNative(): ScrollBehavior = when (this) {
    EditorCoreScrollBehavior.GoToTop -> ScrollBehavior.GoToTop
    EditorCoreScrollBehavior.GoToCenter -> ScrollBehavior.GoToCenter
    EditorCoreScrollBehavior.GoToBottom -> ScrollBehavior.GoToBottom
}

private fun TextPosition.toCore(): EditorCoreTextPosition = toEditorCore()

private fun TextRange.toCore(): EditorCoreTextRange = toEditorCore()

private fun CursorRect.toCore(): EditorCoreCursorRect =
    EditorCoreCursorRect(
        x = x,
        y = y,
        height = height,
    )

internal fun EditorCoreCursorRect.toNative(): CursorRect =
    CursorRect(
        x = x,
        y = y,
        height = height,
    )
