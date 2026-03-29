package com.qiplat.compose.sweeteditor.bridge

import com.qiplat.compose.sweeteditor.model.foundation.*
import com.qiplat.compose.sweeteditor.protocol.ProtocolEncoder
import com.qiplat.compose.sweeteditor.protocol.toNativeValue

internal object AndroidNativeBridgeFactory : NativeBridgeFactory {
    override fun createDocumentFromUtf16(text: String): NativeDocumentBridge =
        AndroidNativeDocumentBridge(
            handle = AndroidNativeBindings.nativeCreateDocumentFromUtf16(text),
        )

    override fun createDocumentFromFile(path: String): NativeDocumentBridge =
        AndroidNativeDocumentBridge(
            handle = AndroidNativeBindings.nativeCreateDocumentFromFile(path),
        )

    override fun createEditor(
        textMeasurer: NativeTextMeasurer,
        options: EditorOptions,
    ): NativeEditorBridge {
        val optionsData = ProtocolEncoder.encodeEditorOptions(options)
        return AndroidNativeEditorBridge(
            handle = AndroidNativeBindings.nativeCreateEditor(textMeasurer, optionsData),
        )
    }
}

private class AndroidNativeDocumentBridge(
    override var handle: Long,
) : NativeDocumentBridge {
    override fun getLineCount(): Int =
        AndroidNativeBindings.nativeGetDocumentLineCount(handle)

    override fun getLineText(line: Int): String =
        AndroidNativeBindings.nativeGetDocumentLineText(handle, line)

    override fun release() {
        if (handle == 0L) {
            return
        }
        AndroidNativeBindings.nativeFreeDocument(handle)
        handle = 0L
    }
}

private class AndroidNativeEditorBridge(
    override var handle: Long,
) : NativeEditorBridge {
    override fun release() {
        if (handle == 0L) {
            return
        }
        AndroidNativeBindings.nativeFreeEditor(handle)
        handle = 0L
    }

    override fun setDocument(document: NativeDocumentBridge?) {
        AndroidNativeBindings.nativeSetEditorDocument(handle, document?.handle ?: 0L)
    }

    override fun setViewport(width: Int, height: Int) {
        AndroidNativeBindings.nativeSetEditorViewport(handle, width, height)
    }

    override fun onFontMetricsChanged() {
        AndroidNativeBindings.nativeOnFontMetricsChanged(handle)
    }

    override fun setFoldArrowMode(mode: FoldArrowMode) {
        AndroidNativeBindings.nativeSetFoldArrowMode(handle, mode.toNativeValue())
    }

    override fun setWrapMode(mode: WrapMode) {
        AndroidNativeBindings.nativeSetWrapMode(handle, mode.toNativeValue())
    }

    override fun setTabSize(tabSize: Int) {
        AndroidNativeBindings.nativeSetTabSize(handle, tabSize)
    }

    override fun setScale(scale: Float) {
        AndroidNativeBindings.nativeSetScale(handle, scale)
    }

    override fun setLineSpacing(add: Float, mult: Float) {
        AndroidNativeBindings.nativeSetLineSpacing(handle, add, mult)
    }

    override fun setShowSplitLine(show: Boolean) {
        AndroidNativeBindings.nativeSetShowSplitLine(handle, show)
    }

    override fun setCurrentLineRenderMode(mode: CurrentLineRenderMode) {
        AndroidNativeBindings.nativeSetCurrentLineRenderMode(handle, mode.toNativeValue())
    }

    override fun setGutterSticky(sticky: Boolean) {
        AndroidNativeBindings.nativeSetGutterSticky(handle, sticky)
    }

    override fun setGutterVisible(visible: Boolean) {
        AndroidNativeBindings.nativeSetGutterVisible(handle, visible)
    }

    override fun setReadOnly(readOnly: Boolean) {
        AndroidNativeBindings.nativeSetReadOnly(handle, readOnly)
    }

    override fun setCompositionEnabled(enabled: Boolean) {
        AndroidNativeBindings.nativeSetCompositionEnabled(handle, enabled)
    }

    override fun setCursorPosition(position: TextPosition) {
        AndroidNativeBindings.nativeSetCursorPosition(handle, position.line, position.column)
    }

    override fun setSelection(range: TextRange) {
        AndroidNativeBindings.nativeSetSelection(
            handle,
            range.start.line,
            range.start.column,
            range.end.line,
            range.end.column,
        )
    }

    override fun getCursorPosition(): TextPosition =
        AndroidNativeBindings.nativeGetCursorPosition(handle).let { values ->
            TextPosition(
                line = values.getOrElse(0) { 0 },
                column = values.getOrElse(1) { 0 },
            )
        }

    override fun getSelection(): TextRange? =
        AndroidNativeBindings.nativeGetSelection(handle)?.let { values ->
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
        AndroidNativeBindings.nativeBuildRenderModel(handle)

    override fun getScrollMetrics(): ByteArray? =
        AndroidNativeBindings.nativeGetScrollMetrics(handle)

    override fun handleGesture(
        type: Int,
        points: FloatArray,
        modifiers: Int,
        wheelDeltaX: Float,
        wheelDeltaY: Float,
        directScale: Float,
    ): ByteArray? =
        AndroidNativeBindings.nativeHandleGesture(
            editorHandle = handle,
            type = type,
            points = points,
            modifiers = modifiers,
            wheelDeltaX = wheelDeltaX,
            wheelDeltaY = wheelDeltaY,
            directScale = directScale,
        )

    override fun tickAnimations(): ByteArray? =
        AndroidNativeBindings.nativeTickAnimations(handle)

    override fun handleKeyEvent(keyCode: Int, text: String?, modifiers: Int): ByteArray? =
        AndroidNativeBindings.nativeHandleKeyEvent(handle, keyCode, text, modifiers)

    override fun compositionStart() {
        AndroidNativeBindings.nativeCompositionStart(handle)
    }

    override fun compositionUpdate(text: String) {
        AndroidNativeBindings.nativeCompositionUpdate(handle, text)
    }

    override fun compositionEnd(committedText: String?): ByteArray? =
        AndroidNativeBindings.nativeCompositionEnd(handle, committedText)

    override fun compositionCancel() {
        AndroidNativeBindings.nativeCompositionCancel(handle)
    }

    override fun isComposing(): Boolean =
        AndroidNativeBindings.nativeIsComposing(handle)

    override fun insertText(text: String): ByteArray? =
        AndroidNativeBindings.nativeInsertText(handle, text)

    override fun replaceText(range: TextRange, text: String): ByteArray? =
        AndroidNativeBindings.nativeReplaceText(
            handle,
            range.start.line,
            range.start.column,
            range.end.line,
            range.end.column,
            text,
        )

    override fun deleteText(range: TextRange): ByteArray? =
        AndroidNativeBindings.nativeDeleteText(
            handle,
            range.start.line,
            range.start.column,
            range.end.line,
            range.end.column,
        )

    override fun backspace(): ByteArray? =
        AndroidNativeBindings.nativeBackspace(handle)

    override fun deleteForward(): ByteArray? =
        AndroidNativeBindings.nativeDeleteForward(handle)

    override fun registerBatchTextStyles(data: ByteArray) {
        AndroidNativeBindings.nativeRegisterBatchTextStyles(handle, data)
    }

    override fun setBatchLineSpans(data: ByteArray) {
        AndroidNativeBindings.nativeSetBatchLineSpans(handle, data)
    }

    override fun setBatchLineInlayHints(data: ByteArray) {
        AndroidNativeBindings.nativeSetBatchLineInlayHints(handle, data)
    }

    override fun setBatchLinePhantomTexts(data: ByteArray) {
        AndroidNativeBindings.nativeSetBatchLinePhantomTexts(handle, data)
    }

    override fun setBatchLineGutterIcons(data: ByteArray) {
        AndroidNativeBindings.nativeSetBatchLineGutterIcons(handle, data)
    }

    override fun setBatchLineDiagnostics(data: ByteArray) {
        AndroidNativeBindings.nativeSetBatchLineDiagnostics(handle, data)
    }

    override fun setFoldRegions(data: ByteArray) {
        AndroidNativeBindings.nativeSetFoldRegions(handle, data)
    }

    override fun setMaxGutterIcons(count: Int) {
        AndroidNativeBindings.nativeSetMaxGutterIcons(handle, count)
    }
}
