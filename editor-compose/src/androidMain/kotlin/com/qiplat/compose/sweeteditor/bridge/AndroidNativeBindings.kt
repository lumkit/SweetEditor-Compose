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
    external fun nativeGetDocumentLineCount(handle: Long): Int

    @JvmStatic
    external fun nativeGetDocumentLineText(handle: Long, line: Int): String

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
    external fun nativeIsReadOnly(editorHandle: Long): Boolean

    @JvmStatic
    external fun nativeSetCompositionEnabled(editorHandle: Long, enabled: Boolean)

    @JvmStatic
    external fun nativeIsCompositionEnabled(editorHandle: Long): Boolean

    @JvmStatic
    external fun nativeSetAutoIndentMode(editorHandle: Long, mode: Int)

    @JvmStatic
    external fun nativeGetAutoIndentMode(editorHandle: Long): Int

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
    external fun nativeGetCursorPosition(editorHandle: Long): IntArray

    @JvmStatic
    external fun nativeGetSelection(editorHandle: Long): IntArray?

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
    external fun nativeHandleKeyEvent(
        editorHandle: Long,
        keyCode: Int,
        text: String?,
        modifiers: Int,
    ): ByteArray?

    @JvmStatic
    external fun nativeCompositionStart(editorHandle: Long)

    @JvmStatic
    external fun nativeCompositionUpdate(editorHandle: Long, text: String)

    @JvmStatic
    external fun nativeCompositionEnd(editorHandle: Long, committedText: String?): ByteArray?

    @JvmStatic
    external fun nativeCompositionCancel(editorHandle: Long)

    @JvmStatic
    external fun nativeIsComposing(editorHandle: Long): Boolean

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
    external fun nativeBackspace(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeDeleteForward(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeInsertSnippet(editorHandle: Long, template: String): ByteArray?

    @JvmStatic
    external fun nativeStartLinkedEditing(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeIsInLinkedEditing(editorHandle: Long): Boolean

    @JvmStatic
    external fun nativeLinkedEditingNext(editorHandle: Long): Boolean

    @JvmStatic
    external fun nativeLinkedEditingPrev(editorHandle: Long): Boolean

    @JvmStatic
    external fun nativeCancelLinkedEditing(editorHandle: Long)

    @JvmStatic
    external fun nativeMoveLineUp(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeMoveLineDown(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeCopyLineUp(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeCopyLineDown(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeDeleteLine(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeInsertLineAbove(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeInsertLineBelow(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeUndo(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeRedo(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeCanUndo(editorHandle: Long): Boolean

    @JvmStatic
    external fun nativeCanRedo(editorHandle: Long): Boolean

    @JvmStatic
    external fun nativeSelectAll(editorHandle: Long)

    @JvmStatic
    external fun nativeGetSelectedText(editorHandle: Long): String?

    @JvmStatic
    external fun nativeGetWordRangeAtCursor(editorHandle: Long): IntArray

    @JvmStatic
    external fun nativeGetWordAtCursor(editorHandle: Long): String?

    @JvmStatic
    external fun nativeMoveCursorLeft(editorHandle: Long, extendSelection: Boolean)

    @JvmStatic
    external fun nativeMoveCursorRight(editorHandle: Long, extendSelection: Boolean)

    @JvmStatic
    external fun nativeMoveCursorUp(editorHandle: Long, extendSelection: Boolean)

    @JvmStatic
    external fun nativeMoveCursorDown(editorHandle: Long, extendSelection: Boolean)

    @JvmStatic
    external fun nativeMoveCursorToLineStart(editorHandle: Long, extendSelection: Boolean)

    @JvmStatic
    external fun nativeMoveCursorToLineEnd(editorHandle: Long, extendSelection: Boolean)

    @JvmStatic
    external fun nativeScrollToLine(editorHandle: Long, line: Int, behavior: Int)

    @JvmStatic
    external fun nativeGotoPosition(editorHandle: Long, line: Int, column: Int)

    @JvmStatic
    external fun nativeSetScroll(editorHandle: Long, scrollX: Float, scrollY: Float)

    @JvmStatic
    external fun nativeGetPositionRect(editorHandle: Long, line: Int, column: Int): FloatArray

    @JvmStatic
    external fun nativeGetCursorRect(editorHandle: Long): FloatArray

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
    external fun nativeClearInlayHints(editorHandle: Long)

    @JvmStatic
    external fun nativeClearPhantomTexts(editorHandle: Long)

    @JvmStatic
    external fun nativeClearGutterIcons(editorHandle: Long)

    @JvmStatic
    external fun nativeClearDiagnostics(editorHandle: Long)

    @JvmStatic
    external fun nativeSetIndentGuides(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeSetBracketGuides(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeSetFlowGuides(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeSetSeparatorGuides(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeClearGuides(editorHandle: Long)

    @JvmStatic
    external fun nativeSetFoldRegions(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeClearAllDecorations(editorHandle: Long)

    @JvmStatic
    external fun nativeSetMaxGutterIcons(editorHandle: Long, count: Int)
}
