package com.qiplat.compose.sweeteditor

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import com.qiplat.compose.sweeteditor.model.decoration.*
import com.qiplat.compose.sweeteditor.model.foundation.*
import com.qiplat.compose.sweeteditor.model.snippet.LinkedEditingModel
import com.qiplat.compose.sweeteditor.model.visual.CursorRect
import com.qiplat.compose.sweeteditor.model.visual.ScrollMetrics
import com.qiplat.compose.sweeteditor.runtime.*
import com.qiplat.compose.sweeteditor.theme.EditorTheme
import com.qiplat.compose.sweeteditor.theme.LanguageConfiguration
import kotlinx.coroutines.*
import kotlin.reflect.KClass

class SweetEditorController(
    textMeasurer: EditorTextMeasurer,
    val state: EditorState = EditorState(),
) {
    internal val editorController: EditorController = EditorController(
        state = state,
        textMeasurer = textMeasurer,
    )

    private val readyCallbacks = mutableListOf<() -> Unit>()
    private var isBound: Boolean = false
    private var isDisposed: Boolean = false
    private var themeSnapshot: EditorTheme = EditorTheme.dark()
    private var settingsSnapshot: EditorSettings = EditorSettings()
    internal val attachedDecorationProviders = mutableStateListOf<DecorationProvider>()
    private val completionProviderManager = CompletionProviderManager()
    private val newLineActionProviderManager = NewLineActionProviderManager()
    private val completionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val eventBus = EditorEventBus()
    private val _documentState = mutableStateOf(state.document)
    val documentState: State<EditorDocument?> = _documentState
    private val _totalLineCountState = mutableStateOf(getTotalLineCount())
    val totalLineCountState: State<Int> = _totalLineCountState
    private val _themeState = mutableStateOf(getTheme())
    val themeState: State<EditorTheme> = _themeState
    private val _settingsState = mutableStateOf(getSettings())
    val settingsState: State<EditorSettings> = _settingsState
    private val _languageConfigurationState = mutableStateOf(getLanguageConfiguration())
    val languageConfigurationState: State<LanguageConfiguration?> = _languageConfigurationState
    private val _metadataState = mutableStateOf(getMetadata())
    val metadataState: State<EditorMetadata?> = _metadataState
    private val _editorIconProviderState = mutableStateOf(getEditorIconProvider())
    val editorIconProviderState: State<EditorIconProvider?> = _editorIconProviderState
    private val _completionResultState = mutableStateOf(getCompletionResult())
    val completionResultState: State<CompletionResult?> = _completionResultState
    private val _selectedCompletionIndexState = mutableStateOf(getSelectedCompletionIndex())
    val selectedCompletionIndexState: State<Int> = _selectedCompletionIndexState
    private val _visibleCompletionState = mutableStateOf(hasVisibleCompletion())
    val visibleCompletionState: State<Boolean> = _visibleCompletionState
    private val _scrollMetricsState = mutableStateOf(getScrollMetrics())
    val scrollMetricsState: State<ScrollMetrics> = _scrollMetricsState
    private val _visibleLineRangeState = mutableStateOf(getVisibleLineRange())
    val visibleLineRangeState: State<IntRange?> = _visibleLineRangeState
    private val _foldArrowModeState = mutableStateOf(getFoldArrowMode())
    val foldArrowModeState: State<FoldArrowMode> = _foldArrowModeState
    private val _wrapModeState = mutableStateOf(getWrapMode())
    val wrapModeState: State<WrapMode> = _wrapModeState
    private val _tabSizeState = mutableStateOf(getTabSize())
    val tabSizeState: State<Int> = _tabSizeState
    private val _scaleState = mutableStateOf(getScale())
    val scaleState: State<Float> = _scaleState
    private val _lineSpacingState = mutableStateOf(getLineSpacing())
    val lineSpacingState: State<LineSpacing> = _lineSpacingState
    private val _showSplitLineState = mutableStateOf(isShowSplitLine())
    val showSplitLineState: State<Boolean> = _showSplitLineState
    private val _currentLineRenderModeState = mutableStateOf(getCurrentLineRenderMode())
    val currentLineRenderModeState: State<CurrentLineRenderMode> = _currentLineRenderModeState
    private val _gutterStickyState = mutableStateOf(isGutterSticky())
    val gutterStickyState: State<Boolean> = _gutterStickyState
    private val _gutterVisibleState = mutableStateOf(isGutterVisible())
    val gutterVisibleState: State<Boolean> = _gutterVisibleState
    private val _readOnlyState = mutableStateOf(isReadOnly())
    val readOnlyState: State<Boolean> = _readOnlyState
    private val _compositionEnabledState = mutableStateOf(isCompositionEnabled())
    val compositionEnabledState: State<Boolean> = _compositionEnabledState
    private val _autoIndentModeState = mutableStateOf(getAutoIndentMode())
    val autoIndentModeState: State<AutoIndentMode> = _autoIndentModeState
    private val _cursorPositionState = mutableStateOf(getCursorPosition())
    val cursorPositionState: State<TextPosition> = _cursorPositionState
    private val _selectionState = mutableStateOf(getSelection())
    val selectionState: State<TextRange?> = _selectionState
    private val _composingState = mutableStateOf(isComposing())
    val composingState: State<Boolean> = _composingState
    private val _canUndoState = mutableStateOf(canUndo())
    val canUndoState: State<Boolean> = _canUndoState
    private val _canRedoState = mutableStateOf(canRedo())
    val canRedoState: State<Boolean> = _canRedoState
    private val _selectedTextState = mutableStateOf(getSelectedText())
    val selectedTextState: State<String?> = _selectedTextState
    private val _wordRangeAtCursorState = mutableStateOf(
        TextRange(
            start = TextPosition(0, 0),
            end = TextPosition(0, 0),
        ),
    )
    val wordRangeAtCursorState: State<TextRange> = _wordRangeAtCursorState
    private val _wordAtCursorState = mutableStateOf(getWordAtCursor())
    val wordAtCursorState: State<String?> = _wordAtCursorState

    init {
        refreshComposeStates()
    }

    fun whenReady(callback: () -> Unit) {
        if (isDisposed) {
            return
        }
        if (isBound) {
            callback()
        } else {
            readyCallbacks += callback
        }
    }

    internal fun bind() {
        check(!isDisposed) {
            "SweetEditorController is already disposed."
        }
        check(!isBound) {
            "SweetEditorController cannot be bound to multiple editors at the same time."
        }
        isBound = true
        val callbacks = readyCallbacks.toList()
        readyCallbacks.clear()
        callbacks.forEach { it() }
    }

    internal fun unbind() {
        isBound = false
    }

    fun dispose() {
        if (isDisposed) {
            return
        }
        isDisposed = true
        readyCallbacks.clear()
        isBound = false
        completionScope.cancel()
        editorController.close()
    }

    fun close() {
        dispose()
    }

    fun events(): EditorEventBus = eventBus

    fun <T : EditorEvent> subscribe(
        eventType: KClass<T>,
        listener: (T) -> Unit,
    ): EditorEventSubscription = eventBus.subscribe(eventType, listener)

    fun loadDocument(document: EditorDocument?) {
        dismissCompletion()
        editorController.setDocument(document)
        eventBus.publish(DocumentLoadedEvent(state.document))
        refreshComposeStates()
    }

    fun getDocument(): EditorDocument? = state.document

    fun loadText(text: String): EditorDocument =
        editorController.loadText(text).also {
            dismissCompletion()
            eventBus.publish(DocumentLoadedEvent(state.document))
            publishCursorAndSelectionEvents()
        }

    fun loadFile(path: String): EditorDocument =
        editorController.loadFile(path).also {
            dismissCompletion()
            eventBus.publish(DocumentLoadedEvent(state.document))
            publishCursorAndSelectionEvents()
        }

    fun getTotalLineCount(): Int = state.document?.getLineCount() ?: 0

    fun applyTheme(theme: EditorTheme) {
        themeSnapshot = theme
        editorController.applyTheme(theme)
        refreshComposeStates()
    }

    fun getTheme(): EditorTheme = themeSnapshot

    fun applySettings(settings: EditorSettings) {
        settingsSnapshot = settings
        editorController.applySettings(settings)
        refreshComposeStates()
    }

    fun getSettings(): EditorSettings = settingsSnapshot

    fun setLanguageConfiguration(configuration: LanguageConfiguration?) {
        editorController.setLanguageConfiguration(configuration)
        refreshComposeStates()
    }

    fun getLanguageConfiguration(): LanguageConfiguration? = state.languageConfiguration

    fun setMetadata(metadata: EditorMetadata?) {
        state.metadata = metadata
        editorController.requestDecorationRefresh()
        refreshComposeStates()
    }

    fun getMetadata(): EditorMetadata? = state.metadata

    fun setEditorIconProvider(provider: EditorIconProvider?) {
        state.editorIconProvider = provider
        editorController.refresh()
        refreshComposeStates()
    }

    fun getEditorIconProvider(): EditorIconProvider? = state.editorIconProvider

    fun addDecorationProvider(provider: DecorationProvider) {
        val existingIndex = attachedDecorationProviders.indexOfFirst { it.id == provider.id }
        if (existingIndex >= 0) {
            attachedDecorationProviders[existingIndex] = provider
        } else {
            attachedDecorationProviders += provider
        }
        editorController.requestDecorationRefresh()
    }

    fun removeDecorationProvider(provider: DecorationProvider) {
        val removed = attachedDecorationProviders.removeAll { it.id == provider.id }
        if (removed) {
            editorController.requestDecorationRefresh()
        }
    }

    fun addCompletionProvider(provider: CompletionProvider) {
        completionProviderManager.addProvider(provider)
    }

    fun removeCompletionProvider(provider: CompletionProvider) {
        completionProviderManager.removeProvider(provider)
    }

    fun triggerCompletion() {
        if (isInLinkedEditing()) {
            dismissCompletion()
            return
        }
        requestCompletion(
            triggerKind = CompletionTriggerKind.Invoked,
            triggerCharacter = null,
        )
    }

    fun showCompletionItems(items: List<CompletionItem>) {
        if (isInLinkedEditing()) {
            dismissCompletion()
            return
        }
        updateCompletionResult(CompletionResult(items = items))
    }

    fun dismissCompletion() {
        completionProviderManager.dismiss()
        updateCompletionResult(null)
    }

    fun getCompletionResult(): CompletionResult? = state.completionResult

    fun getSelectedCompletionIndex(): Int = state.completionSelectedIndex

    fun selectCompletionItem(index: Int) {
        val result = state.completionResult ?: return
        if (result.items.isEmpty()) {
            return
        }
        state.completionSelectedIndex = index.coerceIn(0, result.items.lastIndex)
        refreshComposeStates()
    }

    fun selectNextCompletionItem() {
        val result = state.completionResult ?: return
        if (result.items.isEmpty()) {
            return
        }
        val nextIndex = (state.completionSelectedIndex + 1) % result.items.size
        state.completionSelectedIndex = nextIndex
        refreshComposeStates()
    }

    fun selectPreviousCompletionItem() {
        val result = state.completionResult ?: return
        if (result.items.isEmpty()) {
            return
        }
        val nextIndex = if (state.completionSelectedIndex <= 0) {
            result.items.lastIndex
        } else {
            state.completionSelectedIndex - 1
        }
        state.completionSelectedIndex = nextIndex
        refreshComposeStates()
    }

    fun applySelectedCompletionItem(): TextEditResult? {
        val result = state.completionResult ?: return null
        val item = result.items.getOrNull(state.completionSelectedIndex) ?: return null
        return applyCompletionItem(item)
    }

    fun applyCompletionItem(item: CompletionItem): TextEditResult {
        dismissCompletion()
        val textEdit = item.textEdit
        return when {
            item.insertTextFormat == CompletionItem.SNIPPET -> {
                val snippetTemplate = textEdit?.newText ?: item.insertText ?: item.label
                val replacementRange = textEdit?.range ?: buildCompletionContext(
                    triggerKind = CompletionTriggerKind.Invoked,
                    triggerCharacter = null,
                )?.wordRange
                if (replacementRange != null) {
                    editorController.setSelection(replacementRange)
                    publishCursorAndSelectionEvents()
                }
                insertSnippet(snippetTemplate)
            }

            textEdit != null -> replaceText(textEdit.range, textEdit.newText)
            else -> {
                val context = buildCompletionContext(
                    triggerKind = CompletionTriggerKind.Invoked,
                    triggerCharacter = null,
                )
                val replacementRange = context?.wordRange
                val replacementText = item.insertText ?: item.label
                if (replacementRange != null && !replacementRange.isCollapsed) {
                    replaceText(replacementRange, replacementText)
                } else {
                    insertText(replacementText)
                }
            }
        }
    }

    fun hasVisibleCompletion(): Boolean = state.completionResult?.items?.isNotEmpty() == true

    fun insertSnippet(template: String): TextEditResult {
        dismissCompletion()
        return editorController.insertSnippet(template).also(::publishTextEditEvents)
    }

    fun startLinkedEditing(model: LinkedEditingModel) {
        dismissCompletion()
        editorController.startLinkedEditing(model)
        publishCursorAndSelectionEvents()
    }

    fun isInLinkedEditing(): Boolean = editorController.isInLinkedEditing()

    fun linkedEditingNext(): Boolean {
        dismissCompletion()
        return editorController.linkedEditingNext().also { moved ->
            if (moved || isInLinkedEditing()) {
                publishCursorAndSelectionEvents()
            }
        }
    }

    fun linkedEditingPrev(): Boolean =
        editorController.linkedEditingPrev().also { moved ->
            dismissCompletion()
            if (moved || isInLinkedEditing()) {
                publishCursorAndSelectionEvents()
            }
        }

    fun cancelLinkedEditing() {
        dismissCompletion()
        editorController.cancelLinkedEditing()
        publishCursorAndSelectionEvents()
    }

    internal fun handleEnterAction(): Boolean {
        if (hasVisibleCompletion()) {
            applySelectedCompletionItem()
            return true
        }
        if (isInLinkedEditing()) {
            cancelLinkedEditing()
        }
        performNewLineAction()
        return true
    }

    internal fun handleTabAction(reverse: Boolean): Boolean {
        if (isInLinkedEditing()) {
            if (reverse) {
                linkedEditingPrev()
            } else {
                linkedEditingNext()
            }
            return true
        }
        if (hasVisibleCompletion()) {
            applySelectedCompletionItem()
            return true
        }
        return false
    }

    internal fun handleEscapeAction(): Boolean {
        if (isInLinkedEditing()) {
            cancelLinkedEditing()
            return true
        }
        if (hasVisibleCompletion()) {
            dismissCompletion()
            return true
        }
        return false
    }

    fun setCompletionItemRenderer(renderer: CompletionItemRenderer?) {
        state.completionItemRenderer = renderer
    }

    fun addNewLineActionProvider(provider: NewLineActionProvider) {
        newLineActionProviderManager.addProvider(provider)
    }

    fun removeNewLineActionProvider(provider: NewLineActionProvider) {
        newLineActionProviderManager.removeProvider(provider)
    }

    fun requestNewLineAction(): NewLineAction? =
        buildNewLineContext()?.let(newLineActionProviderManager::request)

    fun performNewLineAction(): TextEditResult {
        val action = requestNewLineAction()
        return insertText(action?.text ?: "\n")
    }

    internal fun synchronizeImeProxyValue(currentValue: TextFieldValue): TextFieldValue =
        editorController.synchronizeImeProxyValue(currentValue)

    internal fun handleImeEditCommands(
        commands: List<EditCommand>,
        previousValue: TextFieldValue,
        newValue: TextFieldValue,
    ): TextFieldValue {
        val committedText = commands.filterIsInstance<androidx.compose.ui.text.input.CommitTextCommand>()
            .lastOrNull()
            ?.text
        if (committedText == "\n" || committedText == "\r\n") {
            if (isComposing()) {
                compositionEnd(null)
            }
            handleEnterAction()
            return synchronizeImeProxyValue(TextFieldValue())
        }
        val normalizedValue = editorController.handleImeEditCommands(commands, previousValue, newValue)
        handleCompletionAfterImeCommands(commands)
        return normalizedValue
    }

    internal fun handleImeAction(
        action: ImeAction,
        currentValue: TextFieldValue,
    ): TextFieldValue {
        if (action == ImeAction.Default || action == ImeAction.None) {
            if (isComposing()) {
                compositionEnd(null)
            }
            handleEnterAction()
            return synchronizeImeProxyValue(TextFieldValue())
        }
        return editorController.handleImeAction(action, currentValue)
    }

    fun getScrollMetrics(): ScrollMetrics = state.scrollMetrics

    fun getVisibleLineRange(): IntRange? {
        val lines = state.renderModel?.lines ?: return null
        val firstLine = lines.minOfOrNull { it.logicalLine } ?: return null
        val lastLine = lines.maxOfOrNull { it.logicalLine } ?: return null
        return firstLine..lastLine
    }

    fun setViewport(width: Int, height: Int) {
        editorController.setViewport(width, height)
        refreshComposeStates()
    }

    fun onFontMetricsChanged() {
        editorController.onFontMetricsChanged()
        refreshComposeStates()
    }

    fun setFoldArrowMode(mode: FoldArrowMode) {
        settingsSnapshot = settingsSnapshot.copy(foldArrowMode = mode)
        editorController.setFoldArrowMode(mode)
        refreshComposeStates()
    }

    fun getFoldArrowMode(): FoldArrowMode = editorController.getFoldArrowMode()

    fun setWrapMode(mode: WrapMode) {
        settingsSnapshot = settingsSnapshot.copy(wrapMode = mode)
        editorController.setWrapMode(mode)
        refreshComposeStates()
    }

    fun getWrapMode(): WrapMode = editorController.getWrapMode()

    fun setTabSize(tabSize: Int) {
        settingsSnapshot = settingsSnapshot.copy(tabSize = tabSize)
        editorController.setTabSize(tabSize)
        refreshComposeStates()
    }

    fun getTabSize(): Int = editorController.getTabSize()

    fun setScale(scale: Float) {
        editorController.setScale(scale)
        eventBus.publish(ScaleChangedEvent(scale))
        refreshComposeStates()
    }

    fun getScale(): Float = editorController.getScale()

    fun setLineSpacing(add: Float, mult: Float) {
        settingsSnapshot = settingsSnapshot.copy(
            lineSpacingExtra = add,
            lineSpacingMultiplier = mult,
        )
        editorController.setLineSpacing(add, mult)
        refreshComposeStates()
    }

    fun getLineSpacing(): LineSpacing = editorController.getLineSpacing()

    fun setShowSplitLine(show: Boolean) {
        editorController.setShowSplitLine(show)
        refreshComposeStates()
    }

    fun isShowSplitLine(): Boolean = editorController.isShowSplitLine()

    fun setCurrentLineRenderMode(mode: CurrentLineRenderMode) {
        settingsSnapshot = settingsSnapshot.copy(currentLineRenderMode = mode)
        editorController.setCurrentLineRenderMode(mode)
        refreshComposeStates()
    }

    fun getCurrentLineRenderMode(): CurrentLineRenderMode = editorController.getCurrentLineRenderMode()

    fun setGutterSticky(sticky: Boolean) {
        settingsSnapshot = settingsSnapshot.copy(gutterSticky = sticky)
        editorController.setGutterSticky(sticky)
        refreshComposeStates()
    }

    fun isGutterSticky(): Boolean = editorController.isGutterSticky()

    fun setGutterVisible(visible: Boolean) {
        settingsSnapshot = settingsSnapshot.copy(gutterVisible = visible)
        editorController.setGutterVisible(visible)
        refreshComposeStates()
    }

    fun isGutterVisible(): Boolean = editorController.isGutterVisible()

    fun setReadOnly(readOnly: Boolean) {
        settingsSnapshot = settingsSnapshot.copy(readOnly = readOnly)
        editorController.setReadOnly(readOnly)
        refreshComposeStates()
    }

    fun isReadOnly(): Boolean = editorController.isReadOnly()

    fun setCompositionEnabled(enabled: Boolean) {
        settingsSnapshot = settingsSnapshot.copy(compositionEnabled = enabled)
        editorController.setCompositionEnabled(enabled)
        refreshComposeStates()
    }

    fun isCompositionEnabled(): Boolean = editorController.isCompositionEnabled()

    fun setAutoIndentMode(mode: AutoIndentMode) {
        settingsSnapshot = settingsSnapshot.copy(autoIndentMode = mode)
        editorController.setAutoIndentMode(mode)
        refreshComposeStates()
    }

    fun getAutoIndentMode(): AutoIndentMode = editorController.getAutoIndentMode()

    fun setCursorPosition(position: TextPosition) {
        editorController.setCursorPosition(position)
        publishCursorAndSelectionEvents()
    }

    fun getCursorPosition(): TextPosition = editorController.getCursorPosition()

    fun setSelection(range: TextRange) {
        editorController.setSelection(range)
        publishCursorAndSelectionEvents()
    }

    fun getSelection(): TextRange? = editorController.getSelection()

    fun compositionStart() = editorController.compositionStart()

    fun compositionUpdate(text: String) = editorController.compositionUpdate(text)

    fun compositionEnd(committedText: String? = null): TextEditResult =
        editorController.compositionEnd(committedText).also { result ->
            publishTextEditEvents(result)
            handleCompletionAfterEdit(
                editResult = result,
                insertedText = committedText,
            )
        }

    fun compositionCancel() = editorController.compositionCancel()

    fun isComposing(): Boolean = editorController.isComposing()

    fun insertText(text: String): TextEditResult =
        editorController.insertText(text).also { result ->
            publishTextEditEvents(result)
            handleCompletionAfterEdit(
                editResult = result,
                insertedText = text,
            )
        }

    fun replaceText(range: TextRange, text: String): TextEditResult =
        editorController.replaceText(range, text).also { result ->
            publishTextEditEvents(result)
            handleCompletionAfterEdit(
                editResult = result,
                insertedText = text,
            )
        }

    fun deleteText(range: TextRange): TextEditResult =
        editorController.deleteText(range).also { result ->
            publishTextEditEvents(result)
            handleCompletionAfterEdit(editResult = result)
        }

    fun backspace(): TextEditResult = editorController.backspace().also { result ->
        publishTextEditEvents(result)
        handleCompletionAfterEdit(editResult = result)
    }

    fun deleteForward(): TextEditResult = editorController.deleteForward().also { result ->
        publishTextEditEvents(result)
        handleCompletionAfterEdit(editResult = result)
    }

    fun moveLineUp(): TextEditResult = editorController.moveLineUp().also(::publishTextEditEvents)

    fun moveLineDown(): TextEditResult = editorController.moveLineDown().also(::publishTextEditEvents)

    fun copyLineUp(): TextEditResult = editorController.copyLineUp().also(::publishTextEditEvents)

    fun copyLineDown(): TextEditResult = editorController.copyLineDown().also(::publishTextEditEvents)

    fun deleteLine(): TextEditResult = editorController.deleteLine().also(::publishTextEditEvents)

    fun insertLineAbove(): TextEditResult = editorController.insertLineAbove().also(::publishTextEditEvents)

    fun insertLineBelow(): TextEditResult = editorController.insertLineBelow().also(::publishTextEditEvents)

    fun undo(): TextEditResult = editorController.undo().also(::publishTextEditEvents)

    fun redo(): TextEditResult = editorController.redo().also(::publishTextEditEvents)

    fun canUndo(): Boolean = editorController.canUndo()

    fun canRedo(): Boolean = editorController.canRedo()

    fun selectAll() {
        editorController.selectAll()
        publishCursorAndSelectionEvents()
    }

    fun getSelectedText(): String? = editorController.getSelectedText()

    fun getWordRangeAtCursor(): TextRange = editorController.getWordRangeAtCursor()

    fun getWordAtCursor(): String? = editorController.getWordAtCursor()

    fun moveCursorLeft(extendSelection: Boolean) {
        editorController.moveCursorLeft(extendSelection)
        publishCursorAndSelectionEvents()
    }

    fun moveCursorRight(extendSelection: Boolean) {
        editorController.moveCursorRight(extendSelection)
        publishCursorAndSelectionEvents()
    }

    fun moveCursorUp(extendSelection: Boolean) {
        editorController.moveCursorUp(extendSelection)
        publishCursorAndSelectionEvents()
    }

    fun moveCursorDown(extendSelection: Boolean) {
        editorController.moveCursorDown(extendSelection)
        publishCursorAndSelectionEvents()
    }

    fun moveCursorToLineStart(extendSelection: Boolean) {
        editorController.moveCursorToLineStart(extendSelection)
        publishCursorAndSelectionEvents()
    }

    fun moveCursorToLineEnd(extendSelection: Boolean) {
        editorController.moveCursorToLineEnd(extendSelection)
        publishCursorAndSelectionEvents()
    }

    fun scrollToLine(
        line: Int,
        behavior: ScrollBehavior,
    ) {
        editorController.scrollToLine(line, behavior)
        eventBus.publish(ScrollChangedEvent(state.scrollMetrics))
        refreshComposeStates()
    }

    fun gotoPosition(
        line: Int,
        column: Int,
    ) {
        editorController.gotoPosition(line, column)
        eventBus.publish(ScrollChangedEvent(state.scrollMetrics))
        publishCursorAndSelectionEvents()
        refreshComposeStates()
    }

    fun ensureCursorVisible() {
        editorController.ensureCursorVisible()
        eventBus.publish(ScrollChangedEvent(state.scrollMetrics))
        refreshComposeStates()
    }

    fun setScroll(
        scrollX: Float,
        scrollY: Float,
    ) {
        editorController.setScroll(scrollX, scrollY)
        eventBus.publish(ScrollChangedEvent(state.scrollMetrics))
        refreshComposeStates()
    }

    fun getPositionRect(
        line: Int,
        column: Int,
    ): CursorRect = editorController.getPositionRect(line, column)

    fun getCursorRect(): CursorRect = editorController.getCursorRect()

    fun handleKeyEvent(
        keyCode: Int,
        text: String? = null,
        modifiers: Int = 0,
    ): KeyEventResult = editorController.handleKeyEvent(keyCode, text, modifiers).also { result ->
        publishTextEditEvents(result.editResult)
        publishCursorAndSelectionEvents()
        handleCompletionAfterEdit(
            editResult = result.editResult,
            insertedText = text,
        )
    }

    fun handleGesture(
        type: GestureType,
        points: List<GesturePoint>,
    ): GestureResult = editorController.handleGesture(type, points).also(::publishGestureEvents)

    fun dispatchGestureEvent(
        type: EditorGestureEventType,
        points: List<GesturePoint>,
        modifiers: Int = 0,
        wheelDeltaX: Float = 0f,
        wheelDeltaY: Float = 0f,
        directScale: Float = 1f,
    ): GestureResult = editorController.dispatchGestureEvent(
        type = type,
        points = points,
        modifiers = modifiers,
        wheelDeltaX = wheelDeltaX,
        wheelDeltaY = wheelDeltaY,
        directScale = directScale,
    ).also(::publishGestureEvents)

    fun tickAnimations(): GestureResult = editorController.tickAnimations().also(::publishGestureEvents)

    fun registerBatchTextStyles(stylesById: Map<Int, TextStyle>) = editorController.registerTextStyles(stylesById)

    fun setBatchLineSpans(
        layer: SpanLayer,
        spansByLine: Map<Int, List<StyleSpan>>,
    ) = editorController.setLineSpans(layer, spansByLine)

    fun setBatchLineInlayHints(hintsByLine: Map<Int, List<InlayHint>>) = editorController.setLineInlayHints(hintsByLine)

    fun setBatchLinePhantomTexts(phantomsByLine: Map<Int, List<PhantomText>>) = editorController.setLinePhantomTexts(phantomsByLine)

    fun setBatchLineGutterIcons(iconsByLine: Map<Int, List<GutterIcon>>) = editorController.setLineGutterIcons(iconsByLine)

    fun setBatchLineDiagnostics(diagnosticsByLine: Map<Int, List<DiagnosticItem>>) = editorController.setLineDiagnostics(diagnosticsByLine)

    fun setIndentGuides(guides: List<IndentGuide>) = editorController.setIndentGuides(guides)

    fun setBracketGuides(guides: List<BracketGuide>) = editorController.setBracketGuides(guides)

    fun setFlowGuides(guides: List<FlowGuide>) = editorController.setFlowGuides(guides)

    fun setSeparatorGuides(guides: List<SeparatorGuide>) = editorController.setSeparatorGuides(guides)

    fun clearInlayHints() = editorController.clearInlayHints()

    fun clearPhantomTexts() = editorController.clearPhantomTexts()

    fun clearGutterIcons() = editorController.clearGutterIcons()

    fun clearDiagnostics() = editorController.clearDiagnostics()

    fun clearGuides() = editorController.clearGuides()

    fun clearAllDecorations() = editorController.clearAllDecorations()

    fun setFoldRegions(regions: List<FoldRegion>) = editorController.setFoldRegions(regions)

    fun setMaxGutterIcons(count: Int) = editorController.setMaxGutterIcons(count)

    fun requestDecorationRefresh() = editorController.requestDecorationRefresh()

    fun refresh() {
        editorController.refresh()
        eventBus.publish(ScrollChangedEvent(state.scrollMetrics))
        refreshComposeStates()
    }

    fun flush() {
        editorController.flush()
        eventBus.publish(ScrollChangedEvent(state.scrollMetrics))
        refreshComposeStates()
    }

    private fun buildCompletionContext(
        triggerKind: CompletionTriggerKind,
        triggerCharacter: String?,
    ): CompletionContext? {
        val document = state.document ?: return null
        val cursorPosition = getCursorPosition()
        val lineText = if (cursorPosition.line in 0 until document.getLineCount()) {
            document.getLineText(cursorPosition.line)
        } else {
            ""
        }
        val safeColumn = cursorPosition.column.coerceIn(0, lineText.length)
        return CompletionContext(
            triggerKind = triggerKind,
            triggerCharacter = triggerCharacter,
            cursorPosition = cursorPosition.copy(column = safeColumn),
            lineText = lineText,
            wordRange = computeWordRange(
                lineNumber = cursorPosition.line,
                lineText = lineText,
                column = safeColumn,
            ),
            languageConfiguration = state.languageConfiguration,
            editorMetadata = state.metadata,
        )
    }

    private fun computeWordRange(
        lineNumber: Int,
        lineText: String,
        column: Int,
    ): TextRange {
        var start = column.coerceIn(0, lineText.length)
        var end = start
        while (start > 0 && lineText[start - 1].isCompletionWordPart()) {
            start--
        }
        while (end < lineText.length && lineText[end].isCompletionWordPart()) {
            end++
        }
        return TextRange(
            start = TextPosition(lineNumber, start),
            end = TextPosition(lineNumber, end),
        )
    }

    private fun buildNewLineContext(): NewLineContext? {
        val document = state.document ?: return null
        val cursorPosition = getCursorPosition()
        if (cursorPosition.line !in 0 until document.getLineCount()) {
            return null
        }
        return NewLineContext(
            lineNumber = cursorPosition.line,
            column = cursorPosition.column,
            lineText = document.getLineText(cursorPosition.line),
            languageConfiguration = state.languageConfiguration,
            editorMetadata = state.metadata,
        )
    }

    private fun requestCompletion(
        triggerKind: CompletionTriggerKind,
        triggerCharacter: String?,
    ) {
        if (isInLinkedEditing()) {
            dismissCompletion()
            return
        }
        val context = buildCompletionContext(
            triggerKind = triggerKind,
            triggerCharacter = triggerCharacter,
        ) ?: run {
            dismissCompletion()
            return
        }
        completionScope.launch {
            val result = completionProviderManager.request(context)
            updateCompletionResult(result)
        }
    }

    private fun refreshComposeStates() {
        _documentState.value = state.document
        _totalLineCountState.value = getTotalLineCount()
        _themeState.value = themeSnapshot
        _settingsState.value = settingsSnapshot
        _languageConfigurationState.value = state.languageConfiguration
        _metadataState.value = state.metadata
        _editorIconProviderState.value = state.editorIconProvider
        _completionResultState.value = state.completionResult
        _selectedCompletionIndexState.value = state.completionSelectedIndex
        _visibleCompletionState.value = hasVisibleCompletion()
        _scrollMetricsState.value = state.scrollMetrics
        _visibleLineRangeState.value = getVisibleLineRange()
        _foldArrowModeState.value = getFoldArrowMode()
        _wrapModeState.value = getWrapMode()
        _tabSizeState.value = getTabSize()
        _scaleState.value = getScale()
        _lineSpacingState.value = getLineSpacing()
        _showSplitLineState.value = isShowSplitLine()
        _currentLineRenderModeState.value = getCurrentLineRenderMode()
        _gutterStickyState.value = isGutterSticky()
        _gutterVisibleState.value = isGutterVisible()
        _readOnlyState.value = isReadOnly()
        _compositionEnabledState.value = isCompositionEnabled()
        _autoIndentModeState.value = getAutoIndentMode()
        _cursorPositionState.value = getCursorPosition()
        _selectionState.value = getSelection()
        _composingState.value = isComposing()
        _canUndoState.value = canUndo()
        _canRedoState.value = canRedo()
        _selectedTextState.value = getSelectedText()
        if (state.document != null) {
            _wordRangeAtCursorState.value = getWordRangeAtCursor()
            _wordAtCursorState.value = getWordAtCursor()
        } else {
            _wordRangeAtCursorState.value = TextRange(
                start = TextPosition(0, 0),
                end = TextPosition(0, 0),
            )
            _wordAtCursorState.value = null
        }
    }

    private fun updateCompletionResult(result: CompletionResult?) {
        val normalizedResult = result?.takeIf { it.items.isNotEmpty() || it.isIncomplete }
        state.completionResult = normalizedResult
        state.completionSelectedIndex = when {
            normalizedResult == null || normalizedResult.items.isEmpty() -> 0
            else -> state.completionSelectedIndex.coerceIn(0, normalizedResult.items.lastIndex)
        }
        refreshComposeStates()
    }

    private fun handleCompletionAfterImeCommands(commands: List<EditCommand>) {
        val committedText = commands.filterIsInstance<androidx.compose.ui.text.input.CommitTextCommand>()
            .lastOrNull()
            ?.text
        val hasDeletion = commands.any {
            it is androidx.compose.ui.text.input.BackspaceCommand ||
                it is androidx.compose.ui.text.input.DeleteAllCommand ||
                it is androidx.compose.ui.text.input.DeleteSurroundingTextCommand ||
                it is androidx.compose.ui.text.input.DeleteSurroundingTextInCodePointsCommand
        }
        if (committedText != null) {
            handleCompletionAfterEdit(
                editResult = state.lastEditResult,
                insertedText = committedText,
            )
        } else if (hasDeletion) {
            handleCompletionAfterEdit(editResult = state.lastEditResult)
        }
    }

    private fun handleCompletionAfterEdit(
        editResult: TextEditResult,
        insertedText: String? = null,
    ) {
        if (!editResult.changed) {
            return
        }
        if (isInLinkedEditing()) {
            dismissCompletion()
            return
        }
        val triggerCharacter = insertedText
            ?.takeIf { it.length == 1 }
            ?.takeIf(completionProviderManager::isTriggerCharacter)
        when {
            triggerCharacter != null -> requestCompletion(
                triggerKind = CompletionTriggerKind.Character,
                triggerCharacter = triggerCharacter,
            )

            hasVisibleCompletion() -> requestCompletion(
                triggerKind = CompletionTriggerKind.Retrigger,
                triggerCharacter = null,
            )
        }
    }

    internal fun publishContextMenuEvent(request: EditorContextMenuRequest) {
        eventBus.publish(ContextMenuEvent(request))
    }

    internal fun publishGestureEventFromComposable(result: GestureResult) {
        publishGestureEvents(result)
    }

    private fun publishTextEditEvents(editResult: TextEditResult) {
        if (editResult.changed) {
            eventBus.publish(TextChangedEvent(editResult))
        }
        publishCursorAndSelectionEvents()
    }

    private fun publishCursorAndSelectionEvents() {
        eventBus.publish(CursorChangedEvent(getCursorPosition()))
        eventBus.publish(SelectionChangedEvent(getSelection()))
        refreshComposeStates()
    }

    private fun publishGestureEvents(result: GestureResult) {
        eventBus.publish(ScrollChangedEvent(state.scrollMetrics))
        eventBus.publish(ScaleChangedEvent(result.viewScale))
        publishCursorAndSelectionEvents()
        when (result.type) {
            GestureType.DoubleTap -> eventBus.publish(DoubleTapEvent(result.tapPoint))
            GestureType.LongPress -> eventBus.publish(LongPressEvent(result.tapPoint))
            GestureType.ContextMenu -> {
                eventBus.publish(
                    ContextMenuEvent(
                        EditorContextMenuRequest(
                            gestureResult = result,
                            hitTarget = result.hitTarget,
                        ),
                    ),
                )
            }

            else -> Unit
        }
        when (result.hitTarget.type) {
            com.qiplat.compose.sweeteditor.model.foundation.HitTargetType.GutterIcon ->
                eventBus.publish(GutterIconClickEvent(result.hitTarget))

            com.qiplat.compose.sweeteditor.model.foundation.HitTargetType.InlayHintText,
            com.qiplat.compose.sweeteditor.model.foundation.HitTargetType.InlayHintIcon,
            com.qiplat.compose.sweeteditor.model.foundation.HitTargetType.InlayHintColor,
            -> eventBus.publish(InlayHintClickEvent(result.hitTarget))

            com.qiplat.compose.sweeteditor.model.foundation.HitTargetType.FoldGutter ->
                eventBus.publish(FoldToggleEvent(result.hitTarget.line))

            else -> Unit
        }
    }
}

private fun Char.isCompletionWordPart(): Boolean =
    isLetterOrDigit() || this == '_'
