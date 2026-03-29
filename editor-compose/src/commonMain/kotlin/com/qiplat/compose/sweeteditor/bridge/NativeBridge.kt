package com.qiplat.compose.sweeteditor.bridge

import com.qiplat.compose.sweeteditor.model.foundation.*

internal interface NativeTextMeasurer {
    fun measureTextWidth(text: String, fontStyle: Int): Float

    fun measureInlayHintWidth(text: String): Float

    fun measureIconWidth(iconId: Int): Float

    fun getFontMetrics(): FloatArray
}

internal interface NativeDocumentBridge {
    val handle: Long

    fun release()
}

internal interface NativeEditorBridge {
    val handle: Long

    fun release()

    fun setDocument(document: NativeDocumentBridge?)

    fun setViewport(width: Int, height: Int)

    fun onFontMetricsChanged()

    fun setFoldArrowMode(mode: FoldArrowMode)

    fun setWrapMode(mode: WrapMode)

    fun setTabSize(tabSize: Int)

    fun setScale(scale: Float)

    fun setLineSpacing(add: Float, mult: Float)

    fun setShowSplitLine(show: Boolean)

    fun setCurrentLineRenderMode(mode: CurrentLineRenderMode)

    fun setGutterSticky(sticky: Boolean)

    fun setGutterVisible(visible: Boolean)

    fun setReadOnly(readOnly: Boolean)

    fun setCompositionEnabled(enabled: Boolean)

    fun setCursorPosition(position: TextPosition)

    fun setSelection(range: TextRange)

    fun buildRenderModel(): ByteArray?

    fun getScrollMetrics(): ByteArray?

    fun handleGesture(type: Int, points: FloatArray): ByteArray?

    fun handleKeyEvent(keyCode: Int, text: String?, modifiers: Int): ByteArray?

    fun insertText(text: String): ByteArray?

    fun replaceText(range: TextRange, text: String): ByteArray?

    fun deleteText(range: TextRange): ByteArray?

    fun registerBatchTextStyles(data: ByteArray)

    fun setBatchLineSpans(data: ByteArray)

    fun setBatchLineInlayHints(data: ByteArray)

    fun setBatchLinePhantomTexts(data: ByteArray)

    fun setBatchLineGutterIcons(data: ByteArray)

    fun setBatchLineDiagnostics(data: ByteArray)

    fun setFoldRegions(data: ByteArray)

    fun setMaxGutterIcons(count: Int)
}

internal interface NativeBridgeFactory {
    fun createDocumentFromUtf16(text: String): NativeDocumentBridge

    fun createDocumentFromFile(path: String): NativeDocumentBridge

    fun createEditor(
        textMeasurer: NativeTextMeasurer,
        options: EditorOptions = EditorOptions(),
    ): NativeEditorBridge
}
