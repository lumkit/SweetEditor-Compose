package com.qiplat.compose.sweeteditor.bridge

import com.qiplat.compose.sweeteditor.model.foundation.*
import com.qiplat.compose.sweeteditor.model.snippet.LinkedEditingModel
import com.qiplat.compose.sweeteditor.model.visual.CursorRect
import com.qiplat.compose.sweeteditor.protocol.ProtocolEncoder
import com.qiplat.compose.sweeteditor.protocol.toAutoIndentMode
import com.qiplat.compose.sweeteditor.protocol.toNativeValue

internal object IosNativeBridgeFactory : NativeBridgeFactory {
    override fun createDocumentFromUtf16(text: String): NativeDocumentBridge =
        IosNativeDocumentBridge(
            handle = IosNativeBindings.nativeCreateDocumentFromUtf16(text),
        )

    override fun createDocumentFromFile(path: String): NativeDocumentBridge =
        IosNativeDocumentBridge(
            handle = IosNativeBindings.nativeCreateDocumentFromFile(path),
        )

    override fun createEditor(
        textMeasurer: NativeTextMeasurer,
        options: EditorOptions,
    ): NativeEditorBridge {
        val optionsData = ProtocolEncoder.encodeEditorOptions(options)
        return IosNativeEditorBridge(
            handle = IosNativeBindings.nativeCreateEditor(textMeasurer, optionsData),
        )
    }
}

private class IosNativeDocumentBridge(
    override var handle: Long,
) : NativeDocumentBridge {
    override fun getLineCount(): Int =
        IosNativeBindings.nativeGetDocumentLineCount(handle)

    override fun getLineText(line: Int): String =
        IosNativeBindings.nativeGetDocumentLineText(handle, line)

    override fun release() {
        if (handle == 0L) {
            return
        }
        IosNativeBindings.nativeFreeDocument(handle)
        handle = 0L
    }
}

private class IosNativeEditorBridge(
    override var handle: Long,
) : NativeEditorBridge {
    override fun release() {
        if (handle == 0L) {
            return
        }
        IosNativeBindings.nativeFreeEditor(handle)
        handle = 0L
    }

    override fun setDocument(document: NativeDocumentBridge?) {
        IosNativeBindings.nativeSetEditorDocument(handle, document?.handle ?: 0L)
    }

    override fun setViewport(width: Int, height: Int) {
        IosNativeBindings.nativeSetEditorViewport(handle, width, height)
    }

    override fun onFontMetricsChanged() {
        IosNativeBindings.nativeOnFontMetricsChanged(handle)
    }

    override fun setFoldArrowMode(mode: FoldArrowMode) {
        IosNativeBindings.nativeSetFoldArrowMode(handle, mode.toNativeValue())
    }

    override fun setWrapMode(mode: WrapMode) {
        IosNativeBindings.nativeSetWrapMode(handle, mode.toNativeValue())
    }

    override fun setTabSize(tabSize: Int) {
        IosNativeBindings.nativeSetTabSize(handle, tabSize)
    }

    override fun setScale(scale: Float) {
        IosNativeBindings.nativeSetScale(handle, scale)
    }

    override fun setLineSpacing(add: Float, mult: Float) {
        IosNativeBindings.nativeSetLineSpacing(handle, add, mult)
    }

    override fun setShowSplitLine(show: Boolean) {
        IosNativeBindings.nativeSetShowSplitLine(handle, show)
    }

    override fun setCurrentLineRenderMode(mode: CurrentLineRenderMode) {
        IosNativeBindings.nativeSetCurrentLineRenderMode(handle, mode.toNativeValue())
    }

    override fun setGutterSticky(sticky: Boolean) {
        IosNativeBindings.nativeSetGutterSticky(handle, sticky)
    }

    override fun setGutterVisible(visible: Boolean) {
        IosNativeBindings.nativeSetGutterVisible(handle, visible)
    }

    override fun setReadOnly(readOnly: Boolean) {
        IosNativeBindings.nativeSetReadOnly(handle, readOnly)
    }

    override fun isReadOnly(): Boolean =
        IosNativeBindings.nativeIsReadOnly(handle)

    override fun setCompositionEnabled(enabled: Boolean) {
        IosNativeBindings.nativeSetCompositionEnabled(handle, enabled)
    }

    override fun isCompositionEnabled(): Boolean =
        IosNativeBindings.nativeIsCompositionEnabled(handle)

    override fun setAutoIndentMode(mode: AutoIndentMode) {
        IosNativeBindings.nativeSetAutoIndentMode(handle, mode.toNativeValue())
    }

    override fun getAutoIndentMode(): AutoIndentMode =
        IosNativeBindings.nativeGetAutoIndentMode(handle).toAutoIndentMode()

    override fun setCursorPosition(position: TextPosition) {
        IosNativeBindings.nativeSetCursorPosition(handle, position.line, position.column)
    }

    override fun setSelection(range: TextRange) {
        IosNativeBindings.nativeSetSelection(
            handle,
            range.start.line,
            range.start.column,
            range.end.line,
            range.end.column,
        )
    }

    override fun getCursorPosition(): TextPosition =
        IosNativeBindings.nativeGetCursorPosition(handle).let { values ->
            TextPosition(
                line = values.getOrElse(0) { 0 },
                column = values.getOrElse(1) { 0 },
            )
        }

    override fun getSelection(): TextRange? =
        IosNativeBindings.nativeGetSelection(handle)?.let { values ->
            if (values.size < 4) {
                null
            } else {
                TextRange(
                    start = TextPosition(values[0], values[1]),
                    end = TextPosition(values[2], values[3]),
                )
            }
        }

    override fun buildRenderModel(): ByteArray? =
        IosNativeBindings.nativeBuildRenderModel(handle)

    override fun getScrollMetrics(): ByteArray? =
        IosNativeBindings.nativeGetScrollMetrics(handle)

    override fun handleGesture(
        type: Int,
        points: FloatArray,
        modifiers: Int,
        wheelDeltaX: Float,
        wheelDeltaY: Float,
        directScale: Float,
    ): ByteArray? =
        IosNativeBindings.nativeHandleGesture(
            editorHandle = handle,
            type = type,
            points = points,
            modifiers = modifiers,
            wheelDeltaX = wheelDeltaX,
            wheelDeltaY = wheelDeltaY,
            directScale = directScale,
        )

    override fun tickAnimations(): ByteArray? =
        IosNativeBindings.nativeTickAnimations(handle)

    override fun handleKeyEvent(keyCode: Int, text: String?, modifiers: Int): ByteArray? =
        IosNativeBindings.nativeHandleKeyEvent(handle, keyCode, text, modifiers)

    override fun compositionStart() {
        IosNativeBindings.nativeCompositionStart(handle)
    }

    override fun compositionUpdate(text: String) {
        IosNativeBindings.nativeCompositionUpdate(handle, text)
    }

    override fun compositionEnd(committedText: String?): ByteArray? =
        IosNativeBindings.nativeCompositionEnd(handle, committedText)

    override fun compositionCancel() {
        IosNativeBindings.nativeCompositionCancel(handle)
    }

    override fun isComposing(): Boolean =
        IosNativeBindings.nativeIsComposing(handle)

    override fun insertText(text: String): ByteArray? =
        IosNativeBindings.nativeInsertText(handle, text)

    override fun replaceText(range: TextRange, text: String): ByteArray? =
        IosNativeBindings.nativeReplaceText(
            handle,
            range.start.line,
            range.start.column,
            range.end.line,
            range.end.column,
            text,
        )

    override fun deleteText(range: TextRange): ByteArray? =
        IosNativeBindings.nativeDeleteText(
            handle,
            range.start.line,
            range.start.column,
            range.end.line,
            range.end.column,
        )

    override fun backspace(): ByteArray? =
        IosNativeBindings.nativeBackspace(handle)

    override fun deleteForward(): ByteArray? =
        IosNativeBindings.nativeDeleteForward(handle)

    override fun insertSnippet(template: String): ByteArray? =
        IosNativeBindings.nativeInsertSnippet(handle, template)

    override fun startLinkedEditing(model: LinkedEditingModel) {
        IosNativeBindings.nativeStartLinkedEditing(
            handle,
            ProtocolEncoder.encodeLinkedEditingModel(model),
        )
    }

    override fun isInLinkedEditing(): Boolean =
        IosNativeBindings.nativeIsInLinkedEditing(handle)

    override fun linkedEditingNext(): Boolean =
        IosNativeBindings.nativeLinkedEditingNext(handle)

    override fun linkedEditingPrev(): Boolean =
        IosNativeBindings.nativeLinkedEditingPrev(handle)

    override fun cancelLinkedEditing() {
        IosNativeBindings.nativeCancelLinkedEditing(handle)
    }

    override fun moveLineUp(): ByteArray? =
        IosNativeBindings.nativeMoveLineUp(handle)

    override fun moveLineDown(): ByteArray? =
        IosNativeBindings.nativeMoveLineDown(handle)

    override fun copyLineUp(): ByteArray? =
        IosNativeBindings.nativeCopyLineUp(handle)

    override fun copyLineDown(): ByteArray? =
        IosNativeBindings.nativeCopyLineDown(handle)

    override fun deleteLine(): ByteArray? =
        IosNativeBindings.nativeDeleteLine(handle)

    override fun insertLineAbove(): ByteArray? =
        IosNativeBindings.nativeInsertLineAbove(handle)

    override fun insertLineBelow(): ByteArray? =
        IosNativeBindings.nativeInsertLineBelow(handle)

    override fun undo(): ByteArray? =
        IosNativeBindings.nativeUndo(handle)

    override fun redo(): ByteArray? =
        IosNativeBindings.nativeRedo(handle)

    override fun canUndo(): Boolean =
        IosNativeBindings.nativeCanUndo(handle)

    override fun canRedo(): Boolean =
        IosNativeBindings.nativeCanRedo(handle)

    override fun selectAll() {
        IosNativeBindings.nativeSelectAll(handle)
    }

    override fun getSelectedText(): String? =
        IosNativeBindings.nativeGetSelectedText(handle)

    override fun getWordRangeAtCursor(): TextRange =
        IosNativeBindings.nativeGetWordRangeAtCursor(handle).toTextRange()

    override fun getWordAtCursor(): String? =
        IosNativeBindings.nativeGetWordAtCursor(handle)

    override fun moveCursorLeft(extendSelection: Boolean) {
        IosNativeBindings.nativeMoveCursorLeft(handle, extendSelection)
    }

    override fun moveCursorRight(extendSelection: Boolean) {
        IosNativeBindings.nativeMoveCursorRight(handle, extendSelection)
    }

    override fun moveCursorUp(extendSelection: Boolean) {
        IosNativeBindings.nativeMoveCursorUp(handle, extendSelection)
    }

    override fun moveCursorDown(extendSelection: Boolean) {
        IosNativeBindings.nativeMoveCursorDown(handle, extendSelection)
    }

    override fun moveCursorToLineStart(extendSelection: Boolean) {
        IosNativeBindings.nativeMoveCursorToLineStart(handle, extendSelection)
    }

    override fun moveCursorToLineEnd(extendSelection: Boolean) {
        IosNativeBindings.nativeMoveCursorToLineEnd(handle, extendSelection)
    }

    override fun scrollToLine(line: Int, behavior: ScrollBehavior) {
        IosNativeBindings.nativeScrollToLine(handle, line, behavior.toNativeValue())
    }

    override fun gotoPosition(line: Int, column: Int) {
        IosNativeBindings.nativeGotoPosition(handle, line, column)
    }

    override fun setScroll(scrollX: Float, scrollY: Float) {
        IosNativeBindings.nativeSetScroll(handle, scrollX, scrollY)
    }

    override fun getPositionRect(line: Int, column: Int): CursorRect =
        IosNativeBindings.nativeGetPositionRect(handle, line, column).toCursorRect()

    override fun getCursorRect(): CursorRect =
        IosNativeBindings.nativeGetCursorRect(handle).toCursorRect()

    override fun registerBatchTextStyles(data: ByteArray) {
        IosNativeBindings.nativeRegisterBatchTextStyles(handle, data)
    }

    override fun setBatchLineSpans(data: ByteArray) {
        IosNativeBindings.nativeSetBatchLineSpans(handle, data)
    }

    override fun setBatchLineInlayHints(data: ByteArray) {
        IosNativeBindings.nativeSetBatchLineInlayHints(handle, data)
    }

    override fun setBatchLinePhantomTexts(data: ByteArray) {
        IosNativeBindings.nativeSetBatchLinePhantomTexts(handle, data)
    }

    override fun setBatchLineGutterIcons(data: ByteArray) {
        IosNativeBindings.nativeSetBatchLineGutterIcons(handle, data)
    }

    override fun setBatchLineDiagnostics(data: ByteArray) {
        IosNativeBindings.nativeSetBatchLineDiagnostics(handle, data)
    }

    override fun clearInlayHints() {
        IosNativeBindings.nativeClearInlayHints(handle)
    }

    override fun clearPhantomTexts() {
        IosNativeBindings.nativeClearPhantomTexts(handle)
    }

    override fun clearGutterIcons() {
        IosNativeBindings.nativeClearGutterIcons(handle)
    }

    override fun clearDiagnostics() {
        IosNativeBindings.nativeClearDiagnostics(handle)
    }

    override fun setIndentGuides(data: ByteArray) {
        IosNativeBindings.nativeSetIndentGuides(handle, data)
    }

    override fun setBracketGuides(data: ByteArray) {
        IosNativeBindings.nativeSetBracketGuides(handle, data)
    }

    override fun setFlowGuides(data: ByteArray) {
        IosNativeBindings.nativeSetFlowGuides(handle, data)
    }

    override fun setSeparatorGuides(data: ByteArray) {
        IosNativeBindings.nativeSetSeparatorGuides(handle, data)
    }

    override fun clearGuides() {
        IosNativeBindings.nativeClearGuides(handle)
    }

    override fun setFoldRegions(data: ByteArray) {
        IosNativeBindings.nativeSetFoldRegions(handle, data)
    }

    override fun clearAllDecorations() {
        IosNativeBindings.nativeClearAllDecorations(handle)
    }

    override fun setMaxGutterIcons(count: Int) {
        IosNativeBindings.nativeSetMaxGutterIcons(handle, count)
    }
}

private fun FloatArray.toCursorRect(): CursorRect = CursorRect(
    x = getOrElse(0) { 0f },
    y = getOrElse(1) { 0f },
    height = getOrElse(2) { 0f },
)

private fun IntArray.toTextRange(): TextRange = TextRange(
    start = TextPosition(
        line = getOrElse(0) { 0 },
        column = getOrElse(1) { 0 },
    ),
    end = TextPosition(
        line = getOrElse(2) { 0 },
        column = getOrElse(3) { 0 },
    ),
)
