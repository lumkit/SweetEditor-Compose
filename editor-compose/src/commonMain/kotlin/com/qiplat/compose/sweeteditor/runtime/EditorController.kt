package com.qiplat.compose.sweeteditor.runtime

import com.qiplat.compose.sweeteditor.DecorationBatch
import com.qiplat.compose.sweeteditor.EditorSettings
import com.qiplat.compose.sweeteditor.bridge.NativeEditorBridge
import com.qiplat.compose.sweeteditor.bridge.NativeTextMeasurer
import com.qiplat.compose.sweeteditor.model.decoration.*
import com.qiplat.compose.sweeteditor.model.foundation.*
import com.qiplat.compose.sweeteditor.protocol.ProtocolDecoder
import com.qiplat.compose.sweeteditor.protocol.ProtocolEncoder
import com.qiplat.compose.sweeteditor.protocol.toNativeValue
import com.qiplat.compose.sweeteditor.theme.EditorTheme
import com.qiplat.compose.sweeteditor.theme.LanguageConfiguration

class EditorController(
    val state: EditorState = EditorState(),
    textMeasurer: EditorTextMeasurer,
) {
    private val editorTextMeasurer: EditorTextMeasurer = textMeasurer
    private val nativeEditorBridge: NativeEditorBridge =
        state.bridgeFactory.createEditor(
            textMeasurer = editorTextMeasurer.asNativeTextMeasurer(),
        )

    fun setDocument(document: EditorDocument?) {
        ensureActive()
        nativeEditorBridge.setDocument(document?.nativeBridge)
        state.document = document
        state.isAttached = document != null
        refresh()
    }

    fun loadText(text: String): EditorDocument {
        val document = EditorDocuments.fromText(text, state.bridgeFactory)
        setDocument(document)
        return document
    }

    fun loadFile(path: String): EditorDocument {
        val document = EditorDocuments.fromFile(path, state.bridgeFactory)
        setDocument(document)
        return document
    }

    fun setViewport(width: Int, height: Int) {
        ensureActive()
        nativeEditorBridge.setViewport(width, height)
        refresh()
    }

    fun onFontMetricsChanged() {
        ensureActive()
        nativeEditorBridge.onFontMetricsChanged()
        refresh()
    }

    fun setFoldArrowMode(mode: FoldArrowMode) {
        ensureActive()
        nativeEditorBridge.setFoldArrowMode(mode)
        refresh()
    }

    fun setWrapMode(mode: WrapMode) {
        ensureActive()
        nativeEditorBridge.setWrapMode(mode)
        refresh()
    }

    fun setTabSize(tabSize: Int) {
        ensureActive()
        nativeEditorBridge.setTabSize(tabSize)
        refresh()
    }

    fun setScale(scale: Float) {
        ensureActive()
        nativeEditorBridge.setScale(scale)
        refresh()
    }

    fun syncPlatformScale(scale: Float) {
        ensureActive()
        editorTextMeasurer.setScale(scale)
        nativeEditorBridge.onFontMetricsChanged()
        refresh()
    }

    fun setLineSpacing(add: Float, mult: Float) {
        ensureActive()
        nativeEditorBridge.setLineSpacing(add, mult)
        refresh()
    }

    fun setShowSplitLine(show: Boolean) {
        ensureActive()
        nativeEditorBridge.setShowSplitLine(show)
        refresh()
    }

    fun applyTheme(theme: EditorTheme) {
        ensureActive()
        registerTextStyles(theme.textStyles)
    }

    fun applySettings(settings: EditorSettings) {
        ensureActive()
        nativeEditorBridge.setWrapMode(settings.wrapMode)
        nativeEditorBridge.setTabSize(settings.tabSize)
        nativeEditorBridge.setLineSpacing(
            settings.lineSpacingExtra,
            settings.lineSpacingMultiplier,
        )
        nativeEditorBridge.setFoldArrowMode(settings.foldArrowMode)
        nativeEditorBridge.setGutterSticky(settings.gutterSticky)
        nativeEditorBridge.setGutterVisible(settings.gutterVisible)
        nativeEditorBridge.setCurrentLineRenderMode(settings.currentLineRenderMode)
        nativeEditorBridge.setReadOnly(settings.readOnly)
        nativeEditorBridge.setCompositionEnabled(settings.compositionEnabled)
        refresh()
    }

    fun setLanguageConfiguration(configuration: LanguageConfiguration?) {
        ensureActive()
        state.languageConfiguration = configuration
    }

    fun setCurrentLineRenderMode(mode: CurrentLineRenderMode) {
        ensureActive()
        nativeEditorBridge.setCurrentLineRenderMode(mode)
        refresh()
    }

    fun setGutterSticky(sticky: Boolean) {
        ensureActive()
        nativeEditorBridge.setGutterSticky(sticky)
        refresh()
    }

    fun setGutterVisible(visible: Boolean) {
        ensureActive()
        nativeEditorBridge.setGutterVisible(visible)
        refresh()
    }

    fun setReadOnly(readOnly: Boolean) {
        ensureActive()
        nativeEditorBridge.setReadOnly(readOnly)
        refresh()
    }

    fun setCompositionEnabled(enabled: Boolean) {
        ensureActive()
        nativeEditorBridge.setCompositionEnabled(enabled)
        refresh()
    }

    fun setCursorPosition(position: TextPosition) {
        ensureActive()
        nativeEditorBridge.setCursorPosition(position)
        refresh()
    }

    fun setSelection(range: TextRange) {
        ensureActive()
        nativeEditorBridge.setSelection(range)
        refresh()
    }

    fun getCursorPosition(): TextPosition {
        ensureActive()
        return nativeEditorBridge.getCursorPosition()
    }

    fun getSelection(): TextRange? {
        ensureActive()
        return nativeEditorBridge.getSelection()
    }

    fun compositionStart() {
        ensureActive()
        nativeEditorBridge.compositionStart()
        refresh()
    }

    fun compositionUpdate(text: String) {
        ensureActive()
        nativeEditorBridge.compositionUpdate(text)
        refresh()
    }

    fun compositionEnd(committedText: String? = null): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.compositionEnd(committedText))
        refresh()
        return editResult
    }

    fun compositionCancel() {
        ensureActive()
        nativeEditorBridge.compositionCancel()
        refresh()
    }

    fun isComposing(): Boolean {
        ensureActive()
        return nativeEditorBridge.isComposing()
    }

    fun insertText(text: String): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.insertText(text))
        refresh()
        return editResult
    }

    fun replaceText(range: TextRange, text: String): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.replaceText(range, text))
        refresh()
        return editResult
    }

    fun deleteText(range: TextRange): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.deleteText(range))
        refresh()
        return editResult
    }

    fun backspace(): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.backspace())
        refresh()
        return editResult
    }

    fun deleteForward(): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.deleteForward())
        refresh()
        return editResult
    }

    fun handleKeyEvent(
        keyCode: Int,
        text: String? = null,
        modifiers: Int = 0,
    ): KeyEventResult {
        ensureActive()
        val result = ProtocolDecoder.decodeKeyEventResult(
            nativeEditorBridge.handleKeyEvent(
                keyCode = keyCode,
                text = text,
                modifiers = modifiers,
            ),
        )
        state.lastKeyEventResult = result
        state.lastEditResult = result.editResult
        refresh()
        return result
    }

    fun handleGesture(
        type: GestureType,
        points: List<GesturePoint>,
    ): GestureResult {
        return dispatchGestureEvent(
            type = type.asEventType(),
            points = points,
        )
    }

    fun dispatchGestureEvent(
        type: EditorGestureEventType,
        points: List<GesturePoint>,
        modifiers: Int = 0,
        wheelDeltaX: Float = 0f,
        wheelDeltaY: Float = 0f,
        directScale: Float = 1f,
    ): GestureResult {
        ensureActive()
        val result = ProtocolDecoder.decodeGestureResult(
            nativeEditorBridge.handleGesture(
                type = type.toNativeValue(),
                points = points.toNativePointArray(),
                modifiers = modifiers,
                wheelDeltaX = wheelDeltaX,
                wheelDeltaY = wheelDeltaY,
                directScale = directScale,
            ),
        )
        state.lastGestureResult = result
        refresh()
        return result
    }

    fun tickAnimations(): GestureResult {
        ensureActive()
        val result = ProtocolDecoder.decodeGestureResult(
            nativeEditorBridge.tickAnimations(),
        )
        state.lastGestureResult = result
        refresh()
        return result
    }

    fun registerTextStyles(stylesById: Map<Int, TextStyle>) {
        ensureActive()
        nativeEditorBridge.registerBatchTextStyles(
            ProtocolEncoder.encodeBatchTextStyles(stylesById),
        )
        refresh()
    }

    fun setLineSpans(
        layer: SpanLayer,
        spansByLine: Map<Int, List<StyleSpan>>,
    ) {
        ensureActive()
        nativeEditorBridge.setBatchLineSpans(
            ProtocolEncoder.encodeBatchLineSpans(layer, spansByLine),
        )
        refresh()
    }

    fun setLineInlayHints(hintsByLine: Map<Int, List<InlayHint>>) {
        ensureActive()
        nativeEditorBridge.setBatchLineInlayHints(
            ProtocolEncoder.encodeBatchLineInlayHints(hintsByLine),
        )
        refresh()
    }

    fun setLinePhantomTexts(phantomsByLine: Map<Int, List<PhantomText>>) {
        ensureActive()
        nativeEditorBridge.setBatchLinePhantomTexts(
            ProtocolEncoder.encodeBatchLinePhantomTexts(phantomsByLine),
        )
        refresh()
    }

    fun setLineGutterIcons(iconsByLine: Map<Int, List<GutterIcon>>) {
        ensureActive()
        nativeEditorBridge.setBatchLineGutterIcons(
            ProtocolEncoder.encodeBatchLineGutterIcons(iconsByLine),
        )
        refresh()
    }

    fun setLineDiagnostics(diagnosticsByLine: Map<Int, List<DiagnosticItem>>) {
        ensureActive()
        nativeEditorBridge.setBatchLineDiagnostics(
            ProtocolEncoder.encodeBatchLineDiagnostics(diagnosticsByLine),
        )
        refresh()
    }

    fun setFoldRegions(regions: List<FoldRegion>) {
        ensureActive()
        nativeEditorBridge.setFoldRegions(
            ProtocolEncoder.encodeFoldRegions(regions),
        )
        refresh()
    }

    internal fun applyDecorationBatch(batch: DecorationBatch) {
        ensureActive()
        nativeEditorBridge.registerBatchTextStyles(
            ProtocolEncoder.encodeBatchTextStyles(batch.textStyles),
        )
        batch.spansByLayer.forEach { (layer, spansByLine) ->
            nativeEditorBridge.setBatchLineSpans(
                ProtocolEncoder.encodeBatchLineSpans(layer, spansByLine),
            )
        }
        nativeEditorBridge.setBatchLineInlayHints(
            ProtocolEncoder.encodeBatchLineInlayHints(batch.inlayHints),
        )
        nativeEditorBridge.setBatchLinePhantomTexts(
            ProtocolEncoder.encodeBatchLinePhantomTexts(batch.phantomTexts),
        )
        nativeEditorBridge.setBatchLineGutterIcons(
            ProtocolEncoder.encodeBatchLineGutterIcons(batch.gutterIcons),
        )
        nativeEditorBridge.setBatchLineDiagnostics(
            ProtocolEncoder.encodeBatchLineDiagnostics(batch.diagnostics),
        )
        nativeEditorBridge.setFoldRegions(
            ProtocolEncoder.encodeFoldRegions(batch.foldRegions),
        )
        refresh()
    }

    fun setMaxGutterIcons(count: Int) {
        ensureActive()
        nativeEditorBridge.setMaxGutterIcons(count)
        refresh()
    }

    fun refresh() {
        ensureActive()
        state.renderModel = ProtocolDecoder.decodeRenderModel(nativeEditorBridge.buildRenderModel())
        state.scrollMetrics = ProtocolDecoder.decodeScrollMetrics(nativeEditorBridge.getScrollMetrics())
    }

    fun close() {
        if (state.isDisposed) {
            return
        }
        nativeEditorBridge.release()
        state.isAttached = false
        state.isDisposed = true
        state.renderModel = null
        state.scrollMetrics = com.qiplat.compose.sweeteditor.model.visual.ScrollMetrics()
    }

    private fun decodeEditResult(data: ByteArray?): TextEditResult {
        val result = ProtocolDecoder.decodeTextEditResult(data)
        state.lastEditResult = result
        return result
    }

    private fun ensureActive() {
        check(!state.isDisposed) {
            "EditorController is already disposed."
        }
    }
}

private fun GestureType.asEventType(): EditorGestureEventType = when (this) {
    GestureType.Undefined -> EditorGestureEventType.Undefined
    GestureType.Tap -> EditorGestureEventType.MouseDown
    GestureType.DoubleTap -> EditorGestureEventType.MouseDown
    GestureType.LongPress -> EditorGestureEventType.MouseDown
    GestureType.Scale -> EditorGestureEventType.DirectScale
    GestureType.Scroll -> EditorGestureEventType.DirectScroll
    GestureType.FastScroll -> EditorGestureEventType.DirectScroll
    GestureType.DragSelect -> EditorGestureEventType.MouseMove
    GestureType.ContextMenu -> EditorGestureEventType.MouseRightDown
}

private fun EditorTextMeasurer.asNativeTextMeasurer(): NativeTextMeasurer = object : NativeTextMeasurer {
    override fun measureTextWidth(text: String, fontStyle: Int): Float =
        this@asNativeTextMeasurer.measureTextWidth(text, fontStyle)

    override fun measureInlayHintWidth(text: String): Float =
        this@asNativeTextMeasurer.measureInlayHintWidth(text)

    override fun measureIconWidth(iconId: Int): Float =
        this@asNativeTextMeasurer.measureIconWidth(iconId)

    override fun getFontMetrics(): FloatArray =
        this@asNativeTextMeasurer.getFontMetrics()
}

private fun List<GesturePoint>.toNativePointArray(): FloatArray {
    val result = FloatArray(size * 2)
    forEachIndexed { index, point ->
        val baseIndex = index * 2
        result[baseIndex] = point.x
        result[baseIndex + 1] = point.y
    }
    return result
}
