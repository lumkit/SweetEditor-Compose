package com.qiplat.compose.sweeteditor.bridge

internal object AndroidNativeBindings {
    init {
        System.loadLibrary("sweeteditor_bridge")
    }

    @JvmStatic
    external fun nativeCreateDocumentFromUtf16(text: String): Long

    @JvmStatic
    external fun nativeCreateDocumentFromFile(path: String): Long

    @JvmStatic
    external fun nativeFreeDocument(handle: Long)

    @JvmStatic
    external fun nativeCreateEditor(textMeasurer: NativeTextMeasurer, optionsData: ByteArray): Long

    @JvmStatic
    external fun nativeFreeEditor(handle: Long)

    @JvmStatic
    external fun nativeSetEditorDocument(editorHandle: Long, documentHandle: Long)

    @JvmStatic
    external fun nativeSetEditorViewport(editorHandle: Long, width: Int, height: Int)

    @JvmStatic
    external fun nativeOnFontMetricsChanged(editorHandle: Long)

    @JvmStatic
    external fun nativeSetFoldArrowMode(editorHandle: Long, mode: Int)

    @JvmStatic
    external fun nativeSetWrapMode(editorHandle: Long, mode: Int)

    @JvmStatic
    external fun nativeSetTabSize(editorHandle: Long, tabSize: Int)

    @JvmStatic
    external fun nativeSetScale(editorHandle: Long, scale: Float)

    @JvmStatic
    external fun nativeSetLineSpacing(editorHandle: Long, add: Float, mult: Float)

    @JvmStatic
    external fun nativeSetShowSplitLine(editorHandle: Long, show: Boolean)

    @JvmStatic
    external fun nativeSetCurrentLineRenderMode(editorHandle: Long, mode: Int)

    @JvmStatic
    external fun nativeSetGutterSticky(editorHandle: Long, sticky: Boolean)

    @JvmStatic
    external fun nativeSetGutterVisible(editorHandle: Long, visible: Boolean)

    @JvmStatic
    external fun nativeSetReadOnly(editorHandle: Long, readOnly: Boolean)

    @JvmStatic
    external fun nativeSetCompositionEnabled(editorHandle: Long, enabled: Boolean)

    @JvmStatic
    external fun nativeSetCursorPosition(editorHandle: Long, line: Int, column: Int)

    @JvmStatic
    external fun nativeSetSelection(
        editorHandle: Long,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
    )

    @JvmStatic
    external fun nativeBuildRenderModel(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeGetScrollMetrics(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeHandleGesture(
        editorHandle: Long,
        type: Int,
        points: FloatArray,
        modifiers: Int,
        wheelDeltaX: Float,
        wheelDeltaY: Float,
        directScale: Float,
    ): ByteArray?

    @JvmStatic
    external fun nativeTickAnimations(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeSetScroll(editorHandle: Long, scrollX: Float, scrollY: Float)

    @JvmStatic
    external fun nativeHandleKeyEvent(
        editorHandle: Long,
        keyCode: Int,
        text: String?,
        modifiers: Int,
    ): ByteArray?

    @JvmStatic
    external fun nativeInsertText(editorHandle: Long, text: String): ByteArray?

    @JvmStatic
    external fun nativeReplaceText(
        editorHandle: Long,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
        text: String,
    ): ByteArray?

    @JvmStatic
    external fun nativeDeleteText(
        editorHandle: Long,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
    ): ByteArray?

    @JvmStatic
    external fun nativeRegisterBatchTextStyles(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeSetBatchLineSpans(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeSetBatchLineInlayHints(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeSetBatchLinePhantomTexts(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeSetBatchLineGutterIcons(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeSetBatchLineDiagnostics(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeSetFoldRegions(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeSetMaxGutterIcons(editorHandle: Long, count: Int)
}
