package com.qiplat.compose.sweeteditor.bridge

import com.qiplat.compose.sweeteditor.model.foundation.*
import com.qiplat.compose.sweeteditor.model.snippet.LinkedEditingModel
import com.qiplat.compose.sweeteditor.model.visual.CursorRect
import com.qiplat.compose.sweeteditor.protocol.ProtocolEncoder
import com.qiplat.compose.sweeteditor.protocol.toAutoIndentMode
import com.qiplat.compose.sweeteditor.protocol.toNativeValue

internal object DesktopNativeBridgeFactory : NativeBridgeFactory {
    override fun createDocumentFromUtf16(text: String): NativeDocumentBridge =
        DesktopNativeDocumentBridge(
            handle = DesktopNativeBindings.nativeCreateDocumentFromUtf16(text),
        )

    override fun createDocumentFromFile(path: String): NativeDocumentBridge =
        DesktopNativeDocumentBridge(
            handle = DesktopNativeBindings.nativeCreateDocumentFromFile(path),
        )

    override fun createEditor(
        textMeasurer: NativeTextMeasurer,
        options: EditorOptions,
    ): NativeEditorBridge {
        val optionsData = ProtocolEncoder.encodeEditorOptions(options)
        return DesktopNativeEditorBridge(
            handle = DesktopNativeBindings.nativeCreateEditor(textMeasurer, optionsData),
        )
    }
}

private class DesktopNativeDocumentBridge(
    override var handle: Long,
) : NativeDocumentBridge {
    override fun getLineCount(): Int =
        DesktopNativeBindings.nativeGetDocumentLineCount(handle)

    override fun getLineText(line: Int): String =
        DesktopNativeBindings.nativeGetDocumentLineText(handle, line)

    override fun release() {
        if (handle == 0L) {
            return
        }
        DesktopNativeBindings.nativeFreeDocument(handle)
        handle = 0L
    }
}

private class DesktopNativeEditorBridge(
    override var handle: Long,
) : NativeEditorBridge {
    override fun release() {
        if (handle == 0L) {
            return
        }
        DesktopNativeBindings.nativeFreeEditor(handle)
        handle = 0L
    }

    override fun setDocument(document: NativeDocumentBridge?) {
        DesktopNativeBindings.nativeSetEditorDocument(handle, document?.handle ?: 0L)
    }

    override fun setViewport(width: Int, height: Int) {
        DesktopNativeBindings.nativeSetEditorViewport(handle, width, height)
    }

    override fun onFontMetricsChanged() {
        DesktopNativeBindings.nativeOnFontMetricsChanged(handle)
    }

    override fun setFoldArrowMode(mode: FoldArrowMode) {
        DesktopNativeBindings.nativeSetFoldArrowMode(handle, mode.toNativeValue())
    }

    override fun setWrapMode(mode: WrapMode) {
        DesktopNativeBindings.nativeSetWrapMode(handle, mode.toNativeValue())
    }

    override fun setTabSize(tabSize: Int) {
        DesktopNativeBindings.nativeSetTabSize(handle, tabSize)
    }

    override fun setScale(scale: Float) {
        DesktopNativeBindings.nativeSetScale(handle, scale)
    }

    override fun setLineSpacing(add: Float, mult: Float) {
        DesktopNativeBindings.nativeSetLineSpacing(handle, add, mult)
    }

    override fun setShowSplitLine(show: Boolean) {
        DesktopNativeBindings.nativeSetShowSplitLine(handle, show)
    }

    override fun setCurrentLineRenderMode(mode: CurrentLineRenderMode) {
        DesktopNativeBindings.nativeSetCurrentLineRenderMode(handle, mode.toNativeValue())
    }

    override fun setGutterSticky(sticky: Boolean) {
        DesktopNativeBindings.nativeSetGutterSticky(handle, sticky)
    }

    override fun setGutterVisible(visible: Boolean) {
        DesktopNativeBindings.nativeSetGutterVisible(handle, visible)
    }

    override fun setReadOnly(readOnly: Boolean) {
        DesktopNativeBindings.nativeSetReadOnly(handle, readOnly)
    }

    override fun isReadOnly(): Boolean =
        DesktopNativeBindings.nativeIsReadOnly(handle)

    override fun setCompositionEnabled(enabled: Boolean) {
        DesktopNativeBindings.nativeSetCompositionEnabled(handle, enabled)
    }

    override fun isCompositionEnabled(): Boolean =
        DesktopNativeBindings.nativeIsCompositionEnabled(handle)

    override fun setAutoIndentMode(mode: AutoIndentMode) {
        DesktopNativeBindings.nativeSetAutoIndentMode(handle, mode.toNativeValue())
    }

    override fun getAutoIndentMode(): AutoIndentMode =
        DesktopNativeBindings.nativeGetAutoIndentMode(handle).toAutoIndentMode()

    override fun setCursorPosition(position: TextPosition) {
        DesktopNativeBindings.nativeSetCursorPosition(handle, position.line, position.column)
    }

    override fun setSelection(range: TextRange) {
        DesktopNativeBindings.nativeSetSelection(
            handle,
            range.start.line,
            range.start.column,
            range.end.line,
            range.end.column,
        )
    }

    override fun getCursorPosition(): TextPosition =
        DesktopNativeBindings.nativeGetCursorPosition(handle).let { values ->
            TextPosition(
                line = values.getOrElse(0) { 0 },
                column = values.getOrElse(1) { 0 },
            )
        }

    override fun getSelection(): TextRange? =
        DesktopNativeBindings.nativeGetSelection(handle)?.let { values ->
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
        DesktopNativeBindings.nativeBuildRenderModel(handle)

    override fun getScrollMetrics(): ByteArray? =
        DesktopNativeBindings.nativeGetScrollMetrics(handle)

    override fun handleGesture(
        type: Int,
        points: FloatArray,
        modifiers: Int,
        wheelDeltaX: Float,
        wheelDeltaY: Float,
        directScale: Float,
    ): ByteArray? =
        DesktopNativeBindings.nativeHandleGesture(
            editorHandle = handle,
            type = type,
            points = points,
            modifiers = modifiers,
            wheelDeltaX = wheelDeltaX,
            wheelDeltaY = wheelDeltaY,
            directScale = directScale,
        )

    override fun tickAnimations(): ByteArray? =
        DesktopNativeBindings.nativeTickAnimations(handle)

    override fun handleKeyEvent(keyCode: Int, text: String?, modifiers: Int): ByteArray? =
        DesktopNativeBindings.nativeHandleKeyEvent(handle, keyCode, text, modifiers)

    override fun compositionStart() {
        DesktopNativeBindings.nativeCompositionStart(handle)
    }

    override fun compositionUpdate(text: String) {
        DesktopNativeBindings.nativeCompositionUpdate(handle, text)
    }

    override fun compositionEnd(committedText: String?): ByteArray? =
        DesktopNativeBindings.nativeCompositionEnd(handle, committedText)

    override fun compositionCancel() {
        DesktopNativeBindings.nativeCompositionCancel(handle)
    }

    override fun isComposing(): Boolean =
        DesktopNativeBindings.nativeIsComposing(handle)

    override fun insertText(text: String): ByteArray? =
        DesktopNativeBindings.nativeInsertText(handle, text)

    override fun replaceText(range: TextRange, text: String): ByteArray? =
        DesktopNativeBindings.nativeReplaceText(
            handle,
            range.start.line,
            range.start.column,
            range.end.line,
            range.end.column,
            text,
        )

    override fun deleteText(range: TextRange): ByteArray? =
        DesktopNativeBindings.nativeDeleteText(
            handle,
            range.start.line,
            range.start.column,
            range.end.line,
            range.end.column,
        )

    override fun backspace(): ByteArray? =
        DesktopNativeBindings.nativeBackspace(handle)

    override fun deleteForward(): ByteArray? =
        DesktopNativeBindings.nativeDeleteForward(handle)

    override fun insertSnippet(template: String): ByteArray? =
        DesktopNativeBindings.nativeInsertSnippet(handle, template)

    override fun startLinkedEditing(model: LinkedEditingModel) {
        DesktopNativeBindings.nativeStartLinkedEditing(
            handle,
            ProtocolEncoder.encodeLinkedEditingModel(model),
        )
    }

    override fun isInLinkedEditing(): Boolean =
        DesktopNativeBindings.nativeIsInLinkedEditing(handle)

    override fun linkedEditingNext(): Boolean =
        DesktopNativeBindings.nativeLinkedEditingNext(handle)

    override fun linkedEditingPrev(): Boolean =
        DesktopNativeBindings.nativeLinkedEditingPrev(handle)

    override fun cancelLinkedEditing() {
        DesktopNativeBindings.nativeCancelLinkedEditing(handle)
    }

    override fun moveLineUp(): ByteArray? =
        DesktopNativeBindings.nativeMoveLineUp(handle)

    override fun moveLineDown(): ByteArray? =
        DesktopNativeBindings.nativeMoveLineDown(handle)

    override fun copyLineUp(): ByteArray? =
        DesktopNativeBindings.nativeCopyLineUp(handle)

    override fun copyLineDown(): ByteArray? =
        DesktopNativeBindings.nativeCopyLineDown(handle)

    override fun deleteLine(): ByteArray? =
        DesktopNativeBindings.nativeDeleteLine(handle)

    override fun insertLineAbove(): ByteArray? =
        DesktopNativeBindings.nativeInsertLineAbove(handle)

    override fun insertLineBelow(): ByteArray? =
        DesktopNativeBindings.nativeInsertLineBelow(handle)

    override fun undo(): ByteArray? =
        DesktopNativeBindings.nativeUndo(handle)

    override fun redo(): ByteArray? =
        DesktopNativeBindings.nativeRedo(handle)

    override fun canUndo(): Boolean =
        DesktopNativeBindings.nativeCanUndo(handle)

    override fun canRedo(): Boolean =
        DesktopNativeBindings.nativeCanRedo(handle)

    override fun selectAll() {
        DesktopNativeBindings.nativeSelectAll(handle)
    }

    override fun getSelectedText(): String? =
        DesktopNativeBindings.nativeGetSelectedText(handle)

    override fun getWordRangeAtCursor(): TextRange =
        DesktopNativeBindings.nativeGetWordRangeAtCursor(handle).toTextRange()

    override fun getWordAtCursor(): String? =
        DesktopNativeBindings.nativeGetWordAtCursor(handle)

    override fun moveCursorLeft(extendSelection: Boolean) {
        DesktopNativeBindings.nativeMoveCursorLeft(handle, extendSelection)
    }

    override fun moveCursorRight(extendSelection: Boolean) {
        DesktopNativeBindings.nativeMoveCursorRight(handle, extendSelection)
    }

    override fun moveCursorUp(extendSelection: Boolean) {
        DesktopNativeBindings.nativeMoveCursorUp(handle, extendSelection)
    }

    override fun moveCursorDown(extendSelection: Boolean) {
        DesktopNativeBindings.nativeMoveCursorDown(handle, extendSelection)
    }

    override fun moveCursorToLineStart(extendSelection: Boolean) {
        DesktopNativeBindings.nativeMoveCursorToLineStart(handle, extendSelection)
    }

    override fun moveCursorToLineEnd(extendSelection: Boolean) {
        DesktopNativeBindings.nativeMoveCursorToLineEnd(handle, extendSelection)
    }

    override fun scrollToLine(line: Int, behavior: ScrollBehavior) {
        DesktopNativeBindings.nativeScrollToLine(handle, line, behavior.toNativeValue())
    }

    override fun gotoPosition(line: Int, column: Int) {
        DesktopNativeBindings.nativeGotoPosition(handle, line, column)
    }

    override fun setScroll(scrollX: Float, scrollY: Float) {
        DesktopNativeBindings.nativeSetScroll(handle, scrollX, scrollY)
    }

    override fun getPositionRect(line: Int, column: Int): CursorRect =
        DesktopNativeBindings.nativeGetPositionRect(handle, line, column).toCursorRect()

    override fun getCursorRect(): CursorRect =
        DesktopNativeBindings.nativeGetCursorRect(handle).toCursorRect()

    override fun registerBatchTextStyles(data: ByteArray) {
        DesktopNativeBindings.nativeRegisterBatchTextStyles(handle, data)
    }

    override fun setBatchLineSpans(data: ByteArray) {
        DesktopNativeBindings.nativeSetBatchLineSpans(handle, data)
    }

    override fun setBatchLineInlayHints(data: ByteArray) {
        DesktopNativeBindings.nativeSetBatchLineInlayHints(handle, data)
    }

    override fun setBatchLinePhantomTexts(data: ByteArray) {
        DesktopNativeBindings.nativeSetBatchLinePhantomTexts(handle, data)
    }

    override fun setBatchLineGutterIcons(data: ByteArray) {
        DesktopNativeBindings.nativeSetBatchLineGutterIcons(handle, data)
    }

    override fun setBatchLineDiagnostics(data: ByteArray) {
        DesktopNativeBindings.nativeSetBatchLineDiagnostics(handle, data)
    }

    override fun clearInlayHints() {
        DesktopNativeBindings.nativeClearInlayHints(handle)
    }

    override fun clearPhantomTexts() {
        DesktopNativeBindings.nativeClearPhantomTexts(handle)
    }

    override fun clearGutterIcons() {
        DesktopNativeBindings.nativeClearGutterIcons(handle)
    }

    override fun clearDiagnostics() {
        DesktopNativeBindings.nativeClearDiagnostics(handle)
    }

    override fun setIndentGuides(data: ByteArray) {
        DesktopNativeBindings.nativeSetIndentGuides(handle, data)
    }

    override fun setBracketGuides(data: ByteArray) {
        DesktopNativeBindings.nativeSetBracketGuides(handle, data)
    }

    override fun setFlowGuides(data: ByteArray) {
        DesktopNativeBindings.nativeSetFlowGuides(handle, data)
    }

    override fun setSeparatorGuides(data: ByteArray) {
        DesktopNativeBindings.nativeSetSeparatorGuides(handle, data)
    }

    override fun clearGuides() {
        DesktopNativeBindings.nativeClearGuides(handle)
    }

    override fun setFoldRegions(data: ByteArray) {
        DesktopNativeBindings.nativeSetFoldRegions(handle, data)
    }

    override fun clearAllDecorations() {
        DesktopNativeBindings.nativeClearAllDecorations(handle)
    }

    override fun setMaxGutterIcons(count: Int) {
        DesktopNativeBindings.nativeSetMaxGutterIcons(handle, count)
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
