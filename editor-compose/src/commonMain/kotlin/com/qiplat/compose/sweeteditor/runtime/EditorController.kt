package com.qiplat.compose.sweeteditor.runtime

import com.qiplat.compose.sweeteditor.DecorationBatch
import com.qiplat.compose.sweeteditor.EditorSettings
import com.qiplat.compose.sweeteditor.bridge.NativeEditorBridge
import com.qiplat.compose.sweeteditor.bridge.NativeTextMeasurer
import com.qiplat.compose.sweeteditor.model.decoration.*
import com.qiplat.compose.sweeteditor.model.foundation.*
import com.qiplat.compose.sweeteditor.model.snippet.LinkedEditingModel
import com.qiplat.compose.sweeteditor.model.visual.CursorRect
import com.qiplat.compose.sweeteditor.model.visual.ScrollMetrics
import com.qiplat.compose.sweeteditor.protocol.ProtocolDecoder
import com.qiplat.compose.sweeteditor.protocol.ProtocolEncoder
import com.qiplat.compose.sweeteditor.protocol.toNativeValue
import com.qiplat.compose.sweeteditor.theme.EditorTheme
import com.qiplat.compose.sweeteditor.theme.LanguageConfiguration

/**
 * Coordinates the public editor API, native bridge calls, and Compose-facing state updates.
 *
 * The controller is the only place that should talk to the native editor bridge directly. It keeps
 * [EditorState] synchronized with native results and batches dirty flags so rendering can happen lazily.
 *
 * @property state mutable editor state observed by Compose.
 * @param textMeasurer platform text measurer used by the native bridge for layout-related queries.
 */
class EditorController(
    val state: EditorState = EditorState(),
    textMeasurer: EditorTextMeasurer,
) {
    private val editorTextMeasurer: EditorTextMeasurer = textMeasurer
    private val nativeEditorBridge: NativeEditorBridge =
        state.bridgeFactory.createEditor(
            textMeasurer = editorTextMeasurer.asNativeTextMeasurer(),
        )
    private var settingsSnapshot: EditorSettings = EditorSettings()
    private var scaleSnapshot: Float = 1f
    private var showSplitLineSnapshot: Boolean = true
    private var pendingDecorationBatch: DecorationBatch? = null
    private var appliedDecorationBatch: DecorationBatch? = null
    private var lastRenderModelPayload: ByteArray? = null
    private var lastScrollMetricsPayload: ByteArray? = null
    private var viewportWidthSnapshot: Int = -1
    private var viewportHeightSnapshot: Int = -1

    internal fun textMeasurer(): EditorTextMeasurer = editorTextMeasurer

    /**
     * Attaches a document to the controller.
     *
     * @param document document to attach, or null to detach the current document.
     */
    fun setDocument(document: EditorDocument?) {
        ensureActive()
        nativeEditorBridge.setDocument(document?.nativeBridge)
        state.document = document
        state.isAttached = document != null
        invalidateAppliedDecorationBatch()
        invalidateDecodedPayloadCache()
        requestRefresh(
            renderModel = true,
            scrollMetrics = true,
            decorations = true,
        )
    }

    /**
     * Creates a document from plain text and attaches it to the controller.
     *
     * @param text full document text.
     * @return attached document instance.
     */
    fun loadText(text: String): EditorDocument {
        val document = EditorDocuments.fromText(text, state.bridgeFactory)
        setDocument(document)
        return document
    }

    /**
     * Creates a document from a file path and attaches it to the controller.
     *
     * @param path absolute or platform-valid file path.
     * @return attached document instance.
     */
    fun loadFile(path: String): EditorDocument {
        val document = EditorDocuments.fromFile(path, state.bridgeFactory)
        setDocument(document)
        return document
    }

    /**
     * Updates the viewport size reported to the native editor.
     *
     * @param width viewport width in pixels.
     * @param height viewport height in pixels.
     */
    fun setViewport(width: Int, height: Int) {
        ensureActive()
        if (width == viewportWidthSnapshot && height == viewportHeightSnapshot) {
            return
        }
        nativeEditorBridge.setViewport(width, height)
        viewportWidthSnapshot = width
        viewportHeightSnapshot = height
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    /**
     * Notifies the native editor that font metrics have changed.
     */
    fun onFontMetricsChanged() {
        ensureActive()
        nativeEditorBridge.onFontMetricsChanged()
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    /**
     * Updates the fold arrow render mode.
     *
     * @param mode fold marker mode used by the kernel.
     */
    fun setFoldArrowMode(mode: FoldArrowMode) {
        ensureActive()
        nativeEditorBridge.setFoldArrowMode(mode)
        settingsSnapshot = settingsSnapshot.copy(foldArrowMode = mode)
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun getFoldArrowMode(): FoldArrowMode {
        ensureActive()
        return settingsSnapshot.foldArrowMode
    }

    /**
     * Updates the wrap mode.
     *
     * @param mode wrap behavior used by the kernel.
     */
    fun setWrapMode(mode: WrapMode) {
        ensureActive()
        nativeEditorBridge.setWrapMode(mode)
        settingsSnapshot = settingsSnapshot.copy(wrapMode = mode)
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun getWrapMode(): WrapMode {
        ensureActive()
        return settingsSnapshot.wrapMode
    }

    /**
     * Updates the logical tab size.
     *
     * @param tabSize number of spaces represented by one tab.
     */
    fun setTabSize(tabSize: Int) {
        ensureActive()
        nativeEditorBridge.setTabSize(tabSize)
        settingsSnapshot = settingsSnapshot.copy(tabSize = tabSize)
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun getTabSize(): Int {
        ensureActive()
        return settingsSnapshot.tabSize
    }

    /**
     * Updates the editor scale used by the native kernel.
     *
     * @param scale platform scale factor.
     */
    fun setScale(scale: Float) {
        ensureActive()
        if (approximatelyEqual(scaleSnapshot, scale)) {
            return
        }
        editorTextMeasurer.setScale(scale)
        nativeEditorBridge.setScale(scale)
        nativeEditorBridge.onFontMetricsChanged()
        scaleSnapshot = scale
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun getScale(): Float {
        ensureActive()
        return scaleSnapshot
    }

    /**
     * Synchronizes Compose scale to both the text measurer and the native editor.
     *
     * @param scale platform scale factor derived from gestures or viewport metrics.
     */
    fun syncPlatformScale(scale: Float) {
        ensureActive()
        if (approximatelyEqual(scaleSnapshot, scale)) {
            return
        }
        scaleSnapshot = scale
        editorTextMeasurer.setScale(scale)
        nativeEditorBridge.onFontMetricsChanged()
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    /**
     * Updates line spacing parameters.
     *
     * @param add extra spacing added to each line.
     * @param mult multiplier applied to the base line height.
     */
    fun setLineSpacing(add: Float, mult: Float) {
        ensureActive()
        nativeEditorBridge.setLineSpacing(add, mult)
        settingsSnapshot = settingsSnapshot.copy(
            lineSpacingExtra = add,
            lineSpacingMultiplier = mult,
        )
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun getLineSpacing(): LineSpacing {
        ensureActive()
        return LineSpacing(
            extra = settingsSnapshot.lineSpacingExtra,
            multiplier = settingsSnapshot.lineSpacingMultiplier,
        )
    }

    /**
     * Shows or hides the gutter split line.
     *
     * @param show true to show the split line, false to hide it.
     */
    fun setShowSplitLine(show: Boolean) {
        ensureActive()
        nativeEditorBridge.setShowSplitLine(show)
        showSplitLineSnapshot = show
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun isShowSplitLine(): Boolean {
        ensureActive()
        return showSplitLineSnapshot
    }

    /**
     * Registers all theme text styles required by the editor.
     *
     * @param theme theme whose text style definitions should be registered.
     */
    fun applyTheme(theme: EditorTheme) {
        ensureActive()
        registerTextStyles(theme.textStyles)
    }

    /**
     * Applies the high-level editor settings to the native editor.
     *
     * @param settings settings snapshot to apply.
     */
    fun applySettings(settings: EditorSettings) {
        ensureActive()
        settingsSnapshot = settings
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
        nativeEditorBridge.setAutoIndentMode(settings.autoIndentMode)
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    /**
     * Stores the active language configuration for provider-driven features.
     *
     * @param configuration language configuration to attach, or null to clear it.
     */
    fun setLanguageConfiguration(configuration: LanguageConfiguration?) {
        ensureActive()
        state.languageConfiguration = configuration
        requestRefresh(decorations = true)
    }

    /**
     * Updates current line render mode.
     *
     * @param mode render mode for the current line highlight.
     */
    fun setCurrentLineRenderMode(mode: CurrentLineRenderMode) {
        ensureActive()
        nativeEditorBridge.setCurrentLineRenderMode(mode)
        settingsSnapshot = settingsSnapshot.copy(currentLineRenderMode = mode)
        requestRefresh(renderModel = true)
    }

    fun getCurrentLineRenderMode(): CurrentLineRenderMode {
        ensureActive()
        return settingsSnapshot.currentLineRenderMode
    }

    /**
     * Updates whether the gutter should remain sticky while scrolling.
     *
     * @param sticky true to keep the gutter sticky, false otherwise.
     */
    fun setGutterSticky(sticky: Boolean) {
        ensureActive()
        nativeEditorBridge.setGutterSticky(sticky)
        settingsSnapshot = settingsSnapshot.copy(gutterSticky = sticky)
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun isGutterSticky(): Boolean {
        ensureActive()
        return settingsSnapshot.gutterSticky
    }

    /**
     * Shows or hides the gutter.
     *
     * @param visible true to show the gutter, false to hide it.
     */
    fun setGutterVisible(visible: Boolean) {
        ensureActive()
        nativeEditorBridge.setGutterVisible(visible)
        settingsSnapshot = settingsSnapshot.copy(gutterVisible = visible)
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun isGutterVisible(): Boolean {
        ensureActive()
        return settingsSnapshot.gutterVisible
    }

    /**
     * Updates read-only state.
     *
     * @param readOnly true to disable editing, false to allow editing.
     */
    fun setReadOnly(readOnly: Boolean) {
        ensureActive()
        nativeEditorBridge.setReadOnly(readOnly)
        settingsSnapshot = settingsSnapshot.copy(readOnly = readOnly)
        requestRefresh(renderModel = true)
    }

    fun isReadOnly(): Boolean {
        ensureActive()
        return nativeEditorBridge.isReadOnly()
    }

    /**
     * Enables or disables IME composition support.
     *
     * @param enabled true to enable composition, false to disable it.
     */
    fun setCompositionEnabled(enabled: Boolean) {
        ensureActive()
        nativeEditorBridge.setCompositionEnabled(enabled)
        settingsSnapshot = settingsSnapshot.copy(compositionEnabled = enabled)
        requestRefresh(renderModel = true)
    }

    fun isCompositionEnabled(): Boolean {
        ensureActive()
        return nativeEditorBridge.isCompositionEnabled()
    }

    fun setAutoIndentMode(mode: AutoIndentMode) {
        ensureActive()
        nativeEditorBridge.setAutoIndentMode(mode)
        settingsSnapshot = settingsSnapshot.copy(autoIndentMode = mode)
        requestRefresh(renderModel = true)
    }

    fun getAutoIndentMode(): AutoIndentMode {
        ensureActive()
        return nativeEditorBridge.getAutoIndentMode()
    }

    /**
     * Moves the cursor to the requested position.
     *
     * @param position target text position.
     */
    fun setCursorPosition(position: TextPosition) {
        ensureActive()
        nativeEditorBridge.setCursorPosition(position)
        requestRefresh(renderModel = true)
    }

    /**
     * Updates the current selection range.
     *
     * @param range new selection range.
     */
    fun setSelection(range: TextRange) {
        ensureActive()
        nativeEditorBridge.setSelection(range)
        requestRefresh(renderModel = true)
    }

    /**
     * Returns the current cursor position.
     *
     * @return current text position reported by the native editor.
     */
    fun getCursorPosition(): TextPosition {
        ensureActive()
        return nativeEditorBridge.getCursorPosition()
    }

    /**
     * Returns the current selection range.
     *
     * @return selected range, or null when no selection exists.
     */
    fun getSelection(): TextRange? {
        ensureActive()
        return nativeEditorBridge.getSelection()
    }

    /**
     * Starts an IME composition session.
     */
    fun compositionStart() {
        ensureActive()
        nativeEditorBridge.compositionStart()
        requestRefresh(renderModel = true, decorations = true)
    }

    /**
     * Updates the current IME composition text.
     *
     * @param text composition text supplied by the platform IME.
     */
    fun compositionUpdate(text: String) {
        ensureActive()
        nativeEditorBridge.compositionUpdate(text)
        requestRefresh(renderModel = true, decorations = true)
    }

    /**
     * Finishes the current IME composition session.
     *
     * @param committedText optional committed text supplied by the IME.
     * @return decoded edit result produced by the native editor.
     */
    fun compositionEnd(committedText: String? = null): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.compositionEnd(committedText))
        requestRefresh(renderModel = true, scrollMetrics = true, decorations = true)
        return editResult
    }

    /**
     * Cancels the current IME composition session.
     */
    fun compositionCancel() {
        ensureActive()
        nativeEditorBridge.compositionCancel()
        requestRefresh(renderModel = true, decorations = true)
    }

    /**
     * Returns whether the native editor is currently composing text.
     *
     * @return true when an IME composition is active.
     */
    fun isComposing(): Boolean {
        ensureActive()
        return nativeEditorBridge.isComposing()
    }

    /**
     * Inserts text at the current cursor or selection.
     *
     * @param text text to insert.
     * @return decoded edit result produced by the native editor.
     */
    fun insertText(text: String): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.insertText(text))
        requestRefresh(renderModel = true, scrollMetrics = true, decorations = true)
        return editResult
    }

    /**
     * Replaces the specified range with new text.
     *
     * @param range target range to replace.
     * @param text replacement text.
     * @return decoded edit result produced by the native editor.
     */
    fun replaceText(range: TextRange, text: String): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.replaceText(range, text))
        requestRefresh(renderModel = true, scrollMetrics = true, decorations = true)
        return editResult
    }

    /**
     * Deletes the specified range.
     *
     * @param range target range to delete.
     * @return decoded edit result produced by the native editor.
     */
    fun deleteText(range: TextRange): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.deleteText(range))
        requestRefresh(renderModel = true, scrollMetrics = true, decorations = true)
        return editResult
    }

    /**
     * Deletes one unit backward.
     *
     * @return decoded edit result produced by the native editor.
     */
    fun backspace(): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.backspace())
        requestRefresh(renderModel = true, scrollMetrics = true, decorations = true)
        return editResult
    }

    /**
     * Deletes one unit forward.
     *
     * @return decoded edit result produced by the native editor.
     */
    fun deleteForward(): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.deleteForward())
        requestRefresh(renderModel = true, scrollMetrics = true, decorations = true)
        return editResult
    }

    fun insertSnippet(template: String): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.insertSnippet(template))
        requestRefresh(renderModel = true, scrollMetrics = true, decorations = true)
        return editResult
    }

    fun startLinkedEditing(model: LinkedEditingModel) {
        ensureActive()
        nativeEditorBridge.startLinkedEditing(model)
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun isInLinkedEditing(): Boolean {
        ensureActive()
        return nativeEditorBridge.isInLinkedEditing()
    }

    fun linkedEditingNext(): Boolean {
        ensureActive()
        val moved = nativeEditorBridge.linkedEditingNext()
        requestRefresh(renderModel = true, scrollMetrics = true)
        return moved
    }

    fun linkedEditingPrev(): Boolean {
        ensureActive()
        val moved = nativeEditorBridge.linkedEditingPrev()
        requestRefresh(renderModel = true, scrollMetrics = true)
        return moved
    }

    fun cancelLinkedEditing() {
        ensureActive()
        nativeEditorBridge.cancelLinkedEditing()
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun moveLineUp(): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.moveLineUp())
        requestRefresh(renderModel = true, scrollMetrics = true, decorations = true)
        return editResult
    }

    fun moveLineDown(): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.moveLineDown())
        requestRefresh(renderModel = true, scrollMetrics = true, decorations = true)
        return editResult
    }

    fun copyLineUp(): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.copyLineUp())
        requestRefresh(renderModel = true, scrollMetrics = true, decorations = true)
        return editResult
    }

    fun copyLineDown(): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.copyLineDown())
        requestRefresh(renderModel = true, scrollMetrics = true, decorations = true)
        return editResult
    }

    fun deleteLine(): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.deleteLine())
        requestRefresh(renderModel = true, scrollMetrics = true, decorations = true)
        return editResult
    }

    fun insertLineAbove(): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.insertLineAbove())
        requestRefresh(renderModel = true, scrollMetrics = true, decorations = true)
        return editResult
    }

    fun insertLineBelow(): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.insertLineBelow())
        requestRefresh(renderModel = true, scrollMetrics = true, decorations = true)
        return editResult
    }

    fun undo(): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.undo())
        requestRefresh(renderModel = true, scrollMetrics = true, decorations = true)
        return editResult
    }

    fun redo(): TextEditResult {
        ensureActive()
        val editResult = decodeEditResult(nativeEditorBridge.redo())
        requestRefresh(renderModel = true, scrollMetrics = true, decorations = true)
        return editResult
    }

    fun canUndo(): Boolean {
        ensureActive()
        return nativeEditorBridge.canUndo()
    }

    fun canRedo(): Boolean {
        ensureActive()
        return nativeEditorBridge.canRedo()
    }

    fun selectAll() {
        ensureActive()
        nativeEditorBridge.selectAll()
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun getSelectedText(): String? {
        ensureActive()
        return nativeEditorBridge.getSelectedText()
    }

    fun getWordRangeAtCursor(): TextRange {
        ensureActive()
        return nativeEditorBridge.getWordRangeAtCursor()
    }

    fun getWordAtCursor(): String? {
        ensureActive()
        return nativeEditorBridge.getWordAtCursor()
    }

    fun moveCursorLeft(extendSelection: Boolean) {
        ensureActive()
        nativeEditorBridge.moveCursorLeft(extendSelection)
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun moveCursorRight(extendSelection: Boolean) {
        ensureActive()
        nativeEditorBridge.moveCursorRight(extendSelection)
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun moveCursorUp(extendSelection: Boolean) {
        ensureActive()
        nativeEditorBridge.moveCursorUp(extendSelection)
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun moveCursorDown(extendSelection: Boolean) {
        ensureActive()
        nativeEditorBridge.moveCursorDown(extendSelection)
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun moveCursorToLineStart(extendSelection: Boolean) {
        ensureActive()
        nativeEditorBridge.moveCursorToLineStart(extendSelection)
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun moveCursorToLineEnd(extendSelection: Boolean) {
        ensureActive()
        nativeEditorBridge.moveCursorToLineEnd(extendSelection)
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun scrollToLine(
        line: Int,
        behavior: ScrollBehavior,
    ) {
        ensureActive()
        nativeEditorBridge.scrollToLine(line, behavior)
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun gotoPosition(
        line: Int,
        column: Int,
    ) {
        ensureActive()
        nativeEditorBridge.gotoPosition(line, column)
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun ensureCursorVisible() {
        ensureActive()
        val cursor = nativeEditorBridge.getCursorPosition()
        nativeEditorBridge.gotoPosition(cursor.line, cursor.column)
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun setScroll(
        scrollX: Float,
        scrollY: Float,
    ) {
        ensureActive()
        nativeEditorBridge.setScroll(scrollX, scrollY)
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun getPositionRect(
        line: Int,
        column: Int,
    ): CursorRect {
        ensureActive()
        return nativeEditorBridge.getPositionRect(line, column)
    }

    fun getCursorRect(): CursorRect {
        ensureActive()
        return nativeEditorBridge.getCursorRect()
    }

    /**
     * Sends a logical key event to the native editor.
     *
     * @param keyCode platform-independent key code.
     * @param text optional inserted text associated with the key event.
     * @param modifiers modifier bit mask encoded by the caller.
     * @return decoded key event result, including any edit result produced by the key event.
     */
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
        requestRefresh(renderModel = true, scrollMetrics = true, decorations = true)
        return result
    }

    /**
     * Sends a high-level gesture to the editor using public gesture types.
     *
     * @param type gesture type exposed by the public model.
     * @param points gesture points in editor coordinates.
     * @return decoded gesture result produced by the native editor.
     */
    fun handleGesture(
        type: GestureType,
        points: List<GesturePoint>,
    ): GestureResult {
        return dispatchGestureEvent(
            type = type.asEventType(),
            points = points,
        )
    }

    /**
     * Sends a low-level gesture event to the native editor.
     *
     * @param type native-compatible gesture event type.
     * @param points gesture points encoded in editor coordinates.
     * @param modifiers modifier bit mask.
     * @param wheelDeltaX horizontal wheel delta.
     * @param wheelDeltaY vertical wheel delta.
     * @param directScale direct pinch scale delta.
     * @return decoded gesture result produced by the native editor.
     */
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
        requestRefresh(renderModel = true, scrollMetrics = true)
        return result
    }

    /**
     * Advances native gesture animations by one frame.
     *
     * @return decoded gesture result produced by the animation tick.
     */
    fun tickAnimations(): GestureResult {
        ensureActive()
        val result = ProtocolDecoder.decodeGestureResult(
            nativeEditorBridge.tickAnimations(),
        )
        state.lastGestureResult = result
        requestRefresh(renderModel = true, scrollMetrics = true)
        return result
    }

    /**
     * Registers text styles in bulk.
     *
     * @param stylesById style definitions keyed by style id.
     */
    fun registerTextStyles(stylesById: Map<Int, TextStyle>) {
        ensureActive()
        invalidateAppliedDecorationBatch()
        nativeEditorBridge.registerBatchTextStyles(
            ProtocolEncoder.encodeBatchTextStyles(stylesById),
        )
        requestRefresh(renderModel = true)
    }

    /**
     * Applies line spans in bulk for the specified layer.
     *
     * @param layer span layer to update.
     * @param spansByLine spans grouped by logical line.
     */
    fun setLineSpans(
        layer: SpanLayer,
        spansByLine: Map<Int, List<StyleSpan>>,
    ) {
        ensureActive()
        invalidateAppliedDecorationBatch()
        nativeEditorBridge.setBatchLineSpans(
            ProtocolEncoder.encodeBatchLineSpans(layer, spansByLine),
        )
        requestRefresh(renderModel = true)
    }

    /**
     * Applies inlay hints grouped by line.
     *
     * @param hintsByLine inlay hints keyed by logical line.
     */
    fun setLineInlayHints(hintsByLine: Map<Int, List<InlayHint>>) {
        ensureActive()
        invalidateAppliedDecorationBatch()
        nativeEditorBridge.setBatchLineInlayHints(
            ProtocolEncoder.encodeBatchLineInlayHints(hintsByLine),
        )
        requestRefresh(renderModel = true)
    }

    /**
     * Applies phantom texts grouped by line.
     *
     * @param phantomsByLine phantom texts keyed by logical line.
     */
    fun setLinePhantomTexts(phantomsByLine: Map<Int, List<PhantomText>>) {
        ensureActive()
        invalidateAppliedDecorationBatch()
        nativeEditorBridge.setBatchLinePhantomTexts(
            ProtocolEncoder.encodeBatchLinePhantomTexts(phantomsByLine),
        )
        requestRefresh(renderModel = true)
    }

    /**
     * Applies gutter icons grouped by line.
     *
     * @param iconsByLine gutter icons keyed by logical line.
     */
    fun setLineGutterIcons(iconsByLine: Map<Int, List<GutterIcon>>) {
        ensureActive()
        invalidateAppliedDecorationBatch()
        nativeEditorBridge.setBatchLineGutterIcons(
            ProtocolEncoder.encodeBatchLineGutterIcons(iconsByLine),
        )
        requestRefresh(renderModel = true)
    }

    /**
     * Applies diagnostics grouped by line.
     *
     * @param diagnosticsByLine diagnostics keyed by logical line.
     */
    fun setLineDiagnostics(diagnosticsByLine: Map<Int, List<DiagnosticItem>>) {
        ensureActive()
        invalidateAppliedDecorationBatch()
        nativeEditorBridge.setBatchLineDiagnostics(
            ProtocolEncoder.encodeBatchLineDiagnostics(diagnosticsByLine),
        )
        requestRefresh(renderModel = true)
    }

    /**
     * Applies fold regions in bulk.
     *
     * @param regions fold regions to submit to the native editor.
     */
    fun setFoldRegions(regions: List<FoldRegion>) {
        ensureActive()
        invalidateAppliedDecorationBatch()
        nativeEditorBridge.setFoldRegions(
            ProtocolEncoder.encodeFoldRegions(regions),
        )
        requestRefresh(renderModel = true)
    }

    fun clearInlayHints() {
        ensureActive()
        invalidateAppliedDecorationBatch()
        nativeEditorBridge.clearInlayHints()
        requestRefresh(renderModel = true)
    }

    fun clearPhantomTexts() {
        ensureActive()
        invalidateAppliedDecorationBatch()
        nativeEditorBridge.clearPhantomTexts()
        requestRefresh(renderModel = true)
    }

    fun clearGutterIcons() {
        ensureActive()
        invalidateAppliedDecorationBatch()
        nativeEditorBridge.clearGutterIcons()
        requestRefresh(renderModel = true)
    }

    fun clearDiagnostics() {
        ensureActive()
        invalidateAppliedDecorationBatch()
        nativeEditorBridge.clearDiagnostics()
        requestRefresh(renderModel = true)
    }

    fun setIndentGuides(guides: List<IndentGuide>) {
        ensureActive()
        invalidateAppliedDecorationBatch()
        nativeEditorBridge.setIndentGuides(
            ProtocolEncoder.encodeIndentGuides(guides),
        )
        requestRefresh(renderModel = true)
    }

    fun setBracketGuides(guides: List<BracketGuide>) {
        ensureActive()
        invalidateAppliedDecorationBatch()
        nativeEditorBridge.setBracketGuides(
            ProtocolEncoder.encodeBracketGuides(guides),
        )
        requestRefresh(renderModel = true)
    }

    fun setFlowGuides(guides: List<FlowGuide>) {
        ensureActive()
        invalidateAppliedDecorationBatch()
        nativeEditorBridge.setFlowGuides(
            ProtocolEncoder.encodeFlowGuides(guides),
        )
        requestRefresh(renderModel = true)
    }

    fun setSeparatorGuides(guides: List<SeparatorGuide>) {
        ensureActive()
        invalidateAppliedDecorationBatch()
        nativeEditorBridge.setSeparatorGuides(
            ProtocolEncoder.encodeSeparatorGuides(guides),
        )
        requestRefresh(renderModel = true)
    }

    fun clearGuides() {
        ensureActive()
        invalidateAppliedDecorationBatch()
        nativeEditorBridge.clearGuides()
        requestRefresh(renderModel = true)
    }

    fun clearAllDecorations() {
        ensureActive()
        invalidateAppliedDecorationBatch()
        nativeEditorBridge.clearAllDecorations()
        requestRefresh(renderModel = true)
    }

    /**
     * Flushes a merged decoration batch to the native editor.
     *
     * @param batch aggregated decoration payload ready for native submission.
     */
    internal fun applyDecorationBatch(batch: DecorationBatch) {
        ensureActive()
        if (batch == pendingDecorationBatch || batch == appliedDecorationBatch) {
            return
        }
        pendingDecorationBatch = batch
        requestRefresh(renderModel = true)
    }

    /**
     * Updates the maximum number of gutter icons allowed on a single line.
     *
     * @param count maximum icon count per line.
     */
    fun setMaxGutterIcons(count: Int) {
        ensureActive()
        nativeEditorBridge.setMaxGutterIcons(count)
        requestRefresh(renderModel = true)
    }

    fun requestDecorationRefresh() {
        ensureActive()
        requestRefresh(decorations = true)
    }

    /**
     * Requests both render model and scroll metrics refresh on the next frame.
     */
    fun refresh() {
        ensureActive()
        requestRefresh(renderModel = true, scrollMetrics = true)
    }

    fun flush() {
        refreshNow()
    }

    /**
     * Performs deferred refresh work immediately when dirty flags require it.
     */
    internal fun refreshNow() {
        ensureActive()
        if (!state.isRenderModelDirty && !state.isScrollMetricsDirty) {
            return
        }
        if (state.isRenderModelDirty) {
            flushPendingDecorationBatch()
            val payload = nativeEditorBridge.buildRenderModel()
            if (!payload.contentEqualsNullable(lastRenderModelPayload)) {
                state.renderModel = ProtocolDecoder.decodeRenderModel(payload)
                lastRenderModelPayload = payload?.copyOf()
            }
            state.isRenderModelDirty = false
        }
        if (state.isScrollMetricsDirty) {
            val payload = nativeEditorBridge.getScrollMetrics()
            if (!payload.contentEqualsNullable(lastScrollMetricsPayload)) {
                state.scrollMetrics = ProtocolDecoder.decodeScrollMetrics(payload)
                lastScrollMetricsPayload = payload?.copyOf()
            }
            state.isScrollMetricsDirty = false
        }
    }

    /**
     * Releases native resources and marks the controller as disposed.
     */
    fun close() {
        if (state.isDisposed) {
            return
        }
        nativeEditorBridge.release()
        state.isAttached = false
        state.isDisposed = true
        state.renderModel = null
        state.scrollMetrics = ScrollMetrics()
        state.isRenderModelDirty = false
        state.isScrollMetricsDirty = false
        state.isDecorationDirty = false
        pendingDecorationBatch = null
        appliedDecorationBatch = null
        viewportWidthSnapshot = -1
        viewportHeightSnapshot = -1
        invalidateDecodedPayloadCache()
    }

    /**
     * Decodes an edit result and stores it in the public state snapshot.
     *
     * @param data encoded edit result returned by the native bridge.
     * @return decoded edit result.
     */
    private fun decodeEditResult(data: ByteArray?): TextEditResult {
        val result = ProtocolDecoder.decodeTextEditResult(data)
        state.lastEditResult = result
        return result
    }

    /**
     * Ensures the controller has not been disposed.
     */
    private fun ensureActive() {
        check(!state.isDisposed) {
            "EditorController is already disposed."
        }
    }

    private fun invalidateAppliedDecorationBatch() {
        pendingDecorationBatch = null
        appliedDecorationBatch = null
    }

    private fun invalidateDecodedPayloadCache() {
        lastRenderModelPayload = null
        lastScrollMetricsPayload = null
    }

    private fun flushPendingDecorationBatch() {
        val batch = pendingDecorationBatch ?: return
        val previous = appliedDecorationBatch
        if (previous?.textStyles != batch.textStyles) {
            nativeEditorBridge.registerBatchTextStyles(
                ProtocolEncoder.encodeBatchTextStyles(batch.textStyles),
            )
        }
        val syntaxSpans = batch.spansByLayer[SpanLayer.Syntax].orEmpty()
        if (previous?.spansByLayer?.get(SpanLayer.Syntax).orEmpty() != syntaxSpans) {
            nativeEditorBridge.setBatchLineSpans(
                ProtocolEncoder.encodeBatchLineSpans(SpanLayer.Syntax, syntaxSpans),
            )
        }
        val semanticSpans = batch.spansByLayer[SpanLayer.Semantic].orEmpty()
        if (previous?.spansByLayer?.get(SpanLayer.Semantic).orEmpty() != semanticSpans) {
            nativeEditorBridge.setBatchLineSpans(
                ProtocolEncoder.encodeBatchLineSpans(SpanLayer.Semantic, semanticSpans),
            )
        }
        if (previous?.inlayHints != batch.inlayHints) {
            nativeEditorBridge.setBatchLineInlayHints(
                ProtocolEncoder.encodeBatchLineInlayHints(batch.inlayHints),
            )
        }
        if (previous?.phantomTexts != batch.phantomTexts) {
            nativeEditorBridge.setBatchLinePhantomTexts(
                ProtocolEncoder.encodeBatchLinePhantomTexts(batch.phantomTexts),
            )
        }
        if (previous?.gutterIcons != batch.gutterIcons) {
            nativeEditorBridge.setBatchLineGutterIcons(
                ProtocolEncoder.encodeBatchLineGutterIcons(batch.gutterIcons),
            )
        }
        if (previous?.diagnostics != batch.diagnostics) {
            nativeEditorBridge.setBatchLineDiagnostics(
                ProtocolEncoder.encodeBatchLineDiagnostics(batch.diagnostics),
            )
        }
        if (previous?.indentGuides != batch.indentGuides) {
            nativeEditorBridge.setIndentGuides(
                ProtocolEncoder.encodeIndentGuides(batch.indentGuides),
            )
        }
        if (previous?.bracketGuides != batch.bracketGuides) {
            nativeEditorBridge.setBracketGuides(
                ProtocolEncoder.encodeBracketGuides(batch.bracketGuides),
            )
        }
        if (previous?.flowGuides != batch.flowGuides) {
            nativeEditorBridge.setFlowGuides(
                ProtocolEncoder.encodeFlowGuides(batch.flowGuides),
            )
        }
        if (previous?.separatorGuides != batch.separatorGuides) {
            nativeEditorBridge.setSeparatorGuides(
                ProtocolEncoder.encodeSeparatorGuides(batch.separatorGuides),
            )
        }
        if (previous?.foldRegions != batch.foldRegions) {
            nativeEditorBridge.setFoldRegions(
                ProtocolEncoder.encodeFoldRegions(batch.foldRegions),
            )
        }
        appliedDecorationBatch = batch
        pendingDecorationBatch = null
    }

    private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean {
        if (this == null || other == null) {
            return this == null && other == null
        }
        return this.contentEquals(other)
    }

    /**
     * Marks deferred refresh categories that should be processed by Compose effects.
     *
     * @param renderModel true when the render model must be rebuilt.
     * @param scrollMetrics true when scroll metrics must be refreshed.
     * @param decorations true when decoration providers should run again.
     */
    private fun requestRefresh(
        renderModel: Boolean = false,
        scrollMetrics: Boolean = false,
        decorations: Boolean = false,
    ) {
        if (renderModel) {
            if (!state.isRenderModelDirty) {
                state.isRenderModelDirty = true
                state.renderModelRequestVersion += 1
            }
        }
        if (scrollMetrics) {
            if (!state.isScrollMetricsDirty) {
                state.isScrollMetricsDirty = true
                state.scrollMetricsRequestVersion += 1
            }
        }
        if (decorations) {
            if (!state.isDecorationDirty) {
                state.isDecorationDirty = true
                state.decorationRequestVersion += 1
            }
        }
    }

    private fun approximatelyEqual(first: Float, second: Float): Boolean =
        kotlin.math.abs(first - second) <= 0.0001f
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
