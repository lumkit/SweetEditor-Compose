package com.qiplat.compose.sweeteditor.bridge

import com.qiplat.compose.sweeteditor.model.foundation.*
import com.qiplat.compose.sweeteditor.protocol.ProtocolEncoder
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

    override fun setCompositionEnabled(enabled: Boolean) {
        DesktopNativeBindings.nativeSetCompositionEnabled(handle, enabled)
    }

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

    override fun setFoldRegions(data: ByteArray) {
        DesktopNativeBindings.nativeSetFoldRegions(handle, data)
    }

    override fun setMaxGutterIcons(count: Int) {
        DesktopNativeBindings.nativeSetMaxGutterIcons(handle, count)
    }
}
