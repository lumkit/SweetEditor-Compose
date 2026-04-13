package com.qiplat.compose.sweeteditor.core

import com.qiplat.compose.sweeteditor.core.document.DocumentStore
import com.qiplat.compose.sweeteditor.core.edit.EditEngine
import com.qiplat.compose.sweeteditor.core.edit.EditExecutionResult
import com.qiplat.compose.sweeteditor.core.layout.LayoutEngine
import com.qiplat.compose.sweeteditor.core.layout.LayoutQuery
import com.qiplat.compose.sweeteditor.core.layout.LayoutSettings
import com.qiplat.compose.sweeteditor.core.render.RenderModelBuildInput
import com.qiplat.compose.sweeteditor.core.render.RenderModelBuilder
import com.qiplat.compose.sweeteditor.core.state.SelectionState
import com.qiplat.compose.sweeteditor.core.state.ViewportState

class EditorDocument(
    internal val documentStore: DocumentStore,
    override val handle: Long = nextDocumentHandle(),
) : EditorCoreDocument {
    override fun getLineCount(): Int = documentStore.getLineCount()

    override fun getLineText(line: Int): String = documentStore.getLineText(line)

    override fun release() = Unit
}

object EditorDocuments {
    fun fromText(text: String): EditorDocument =
        EditorDocument(
            documentStore = DocumentStore(text),
        )
}

class EditorCoreFactoryImpl : EditorCoreFactory {
    override fun create(textMeasurer: EditorCoreTextMeasurer): EditorCore =
        EditorCoreImpl(textMeasurer)
}

class EditorCoreImpl(
    private val textMeasurer: EditorCoreTextMeasurer,
) : EditorCore {
    private data class VerticalCursorAnchor(
        val x: Float,
    )

    private data class LinkedEditingState(
        val groups: List<EditorCoreTabStopGroup>,
        val activeGroupIndex: Int,
        val exitPosition: EditorCoreTextPosition?,
    )

    private val layoutEngine = LayoutEngine(textMeasurer)
    private val renderModelBuilder = RenderModelBuilder()
    private var document: EditorDocument? = null
    private var editEngine: EditEngine? = null
    private var viewportState: ViewportState = ViewportState()
    private var scale: Float = 1f
    private var readOnly: Boolean = false
    private var compositionEnabled: Boolean = true
    private var composing: Boolean = false
    private var gutterSticky: Boolean = true
    private var gutterVisible: Boolean = true
    private var wrapMode: EditorCoreWrapMode = EditorCoreWrapMode.None
    private var tabSize: Int = 4
    private var lineSpacingExtra: Float = 0f
    private var lineSpacingMultiplier: Float = 1f
    private var autoIndentMode: EditorCoreAutoIndentMode = EditorCoreAutoIndentMode.None
    private var selectionState: SelectionState = SelectionState()
    private var verticalCursorAnchor: VerticalCursorAnchor? = null
    private var gestureSelectionAnchor: EditorCoreTextPosition? = null
    private var linkedEditingState: LinkedEditingState? = null

    override fun release() {
        document = null
        editEngine = null
        selectionState = SelectionState()
        composing = false
        viewportState = ViewportState()
        verticalCursorAnchor = null
        gestureSelectionAnchor = null
        linkedEditingState = null
    }

    override fun setDocument(document: EditorCoreDocument?) {
        require(document == null || document is EditorDocument)
        this.document = document
        editEngine = document?.let { EditEngine(it.documentStore) }
        selectionState = SelectionState()
        composing = false
        viewportState = ViewportState()
        verticalCursorAnchor = null
        gestureSelectionAnchor = null
        linkedEditingState = null
    }

    override fun setViewport(width: Int, height: Int) {
        viewportState = viewportState.withViewport(width, height)
    }

    override fun onFontMetricsChanged() {
        textMeasurer.getFontMetrics()
    }

    override fun setFoldArrowMode(mode: EditorCoreFoldArrowMode) = Unit

    override fun setWrapMode(mode: EditorCoreWrapMode) {
        wrapMode = mode
    }

    override fun setTabSize(tabSize: Int) {
        this.tabSize = tabSize.coerceAtLeast(1)
    }

    override fun setScale(scale: Float) {
        this.scale = scale
    }

    override fun setLineSpacing(add: Float, mult: Float) {
        lineSpacingExtra = add
        lineSpacingMultiplier = mult
    }

    override fun setShowSplitLine(show: Boolean) = Unit

    override fun setCurrentLineRenderMode(mode: EditorCoreCurrentLineRenderMode) = Unit

    override fun setGutterSticky(sticky: Boolean) {
        gutterSticky = sticky
    }

    override fun setGutterVisible(visible: Boolean) {
        gutterVisible = visible
    }

    override fun setReadOnly(readOnly: Boolean) {
        this.readOnly = readOnly
    }

    override fun isReadOnly(): Boolean = readOnly

    override fun setCompositionEnabled(enabled: Boolean) {
        compositionEnabled = enabled
    }

    override fun isCompositionEnabled(): Boolean = compositionEnabled

    override fun setAutoIndentMode(mode: EditorCoreAutoIndentMode) {
        autoIndentMode = mode
    }

    override fun getAutoIndentMode(): EditorCoreAutoIndentMode = autoIndentMode

    override fun setCursorPosition(position: EditorCoreTextPosition) {
        selectionState = selectionState.collapse(position.coerceWithinDocument())
        verticalCursorAnchor = null
        ensureCursorVisible()
    }

    override fun setSelection(range: EditorCoreTextRange) {
        val clampedRange = range.coerceWithinDocument()
        selectionState = selectionState.select(clampedRange)
        verticalCursorAnchor = null
        ensureCursorVisible()
    }

    override fun getCursorPosition(): EditorCoreTextPosition = selectionState.cursor

    override fun getSelection(): EditorCoreTextRange? = selectionState.range

    override fun buildRenderModel(): EditorCoreRenderModel? {
        val activeStore = document?.documentStore ?: return null
        val layoutQuery = buildLayoutQuery(activeStore)
        val layoutSnapshot = layoutQuery.toSnapshot(
            documentVersion = activeStore.version,
            viewportHeight = viewportState.height,
            scrollY = viewportState.scrollY,
        )
        val geometry = layoutQuery.queryGeometry(
            cursor = selectionState.cursor,
            selection = selectionState.range,
        )
        val cursorRect = geometry.cursor.rect
        val selectionRects = geometry.selection.rects
        return renderModelBuilder.build(
            RenderModelBuildInput(
                layoutSnapshot = layoutSnapshot,
                cursorRect = EditorCoreCursorRect(
                    x = cursorRect.x,
                    y = cursorRect.y,
                    height = cursorRect.height,
                ),
                selectionRects = selectionRects,
            ),
        )
    }

    override fun getScrollMetrics(): EditorCoreScrollMetrics {
        val activeStore = document?.documentStore ?: return EditorCoreScrollMetrics()
        val layoutQuery = buildLayoutQuery(activeStore)
        return viewportState.toScrollMetrics(
            contentWidth = layoutQuery.contentWidth,
            contentHeight = layoutQuery.contentHeight,
        )
    }

    override fun handleGesture(
        type: Int,
        points: FloatArray,
        modifiers: Int,
        wheelDeltaX: Float,
        wheelDeltaY: Float,
        directScale: Float,
    ): EditorCoreGestureResult {
        return when (type) {
            GESTURE_TOUCH_DOWN,
            GESTURE_MOUSE_DOWN,
            GESTURE_MOUSE_RIGHT_DOWN,
            -> {
                val position = resolveGesturePosition(points)
                gestureSelectionAnchor = if (modifiers.hasShiftModifier()) {
                    selectionState.anchor
                } else {
                    position
                }
                updateCursorMovement(
                    nextPosition = position,
                    extendSelection = modifiers.hasShiftModifier(),
                )
                EditorCoreGestureResult(handled = true)
            }
            GESTURE_TOUCH_MOVE,
            GESTURE_MOUSE_MOVE,
            -> {
                val anchor = gestureSelectionAnchor ?: return EditorCoreGestureResult()
                val position = resolveGesturePosition(points)
                selectionState = SelectionState(
                    anchor = anchor.coerceWithinDocument(),
                    cursor = position.coerceWithinDocument(),
                )
                verticalCursorAnchor = null
                EditorCoreGestureResult(handled = true)
            }
            GESTURE_TOUCH_UP,
            GESTURE_TOUCH_CANCEL,
            GESTURE_MOUSE_UP,
            -> {
                gestureSelectionAnchor = null
                EditorCoreGestureResult(handled = true)
            }
            GESTURE_MOUSE_WHEEL,
            GESTURE_DIRECT_SCROLL,
            -> {
                val metrics = getScrollMetrics()
                viewportState = viewportState.scrollBy(
                    dx = wheelDeltaX,
                    dy = wheelDeltaY,
                    maxScrollX = metrics.maxScrollX,
                    maxScrollY = metrics.maxScrollY,
                )
                EditorCoreGestureResult(handled = true)
            }
            GESTURE_DIRECT_SCALE -> {
                if (directScale > 0f) {
                    scale *= directScale
                }
                EditorCoreGestureResult(handled = true)
            }
            else -> EditorCoreGestureResult()
        }
    }

    override fun tickAnimations(): EditorCoreGestureResult = EditorCoreGestureResult()

    override fun handleKeyEvent(
        keyCode: Int,
        text: String?,
        modifiers: Int,
    ): EditorCoreKeyEventResult {
        val extendSelection = modifiers.hasShiftModifier()
        val ctrlOrMetaPressed = modifiers.hasCtrlModifier() || modifiers.hasMetaModifier()
        val altPressed = modifiers.hasAltModifier()
        return when {
            isInLinkedEditing() && keyCode == KEY_CODE_TAB -> {
                if (extendSelection) {
                    linkedEditingPrev()
                } else {
                    linkedEditingNext()
                }
                EditorCoreKeyEventResult(handled = true)
            }
            ctrlOrMetaPressed && keyCode == KEY_CODE_A -> {
                selectAll()
                EditorCoreKeyEventResult(handled = true)
            }
            ctrlOrMetaPressed && keyCode == KEY_CODE_D -> EditorCoreKeyEventResult(
                handled = true,
                editResult = duplicateSelectionOrLine(),
            )
            ctrlOrMetaPressed && keyCode == KEY_CODE_L -> {
                selectCurrentLine()
                EditorCoreKeyEventResult(handled = true)
            }
            ctrlOrMetaPressed && extendSelection && keyCode == KEY_CODE_ENTER -> EditorCoreKeyEventResult(
                handled = true,
                editResult = insertLineAbove(),
            )
            ctrlOrMetaPressed && keyCode == KEY_CODE_ENTER -> EditorCoreKeyEventResult(
                handled = true,
                editResult = insertLineBelow(),
            )
            ctrlOrMetaPressed && extendSelection && keyCode == KEY_CODE_K -> EditorCoreKeyEventResult(
                handled = true,
                editResult = deleteLine(),
            )
            ctrlOrMetaPressed && keyCode == KEY_CODE_Z -> EditorCoreKeyEventResult(
                handled = true,
                editResult = if (extendSelection) redo() else undo(),
            )
            ctrlOrMetaPressed && keyCode == KEY_CODE_Y -> EditorCoreKeyEventResult(
                handled = true,
                editResult = redo(),
            )
            altPressed && extendSelection && keyCode == KEY_CODE_UP -> EditorCoreKeyEventResult(
                handled = true,
                editResult = copyLineUp(),
            )
            altPressed && extendSelection && keyCode == KEY_CODE_DOWN -> EditorCoreKeyEventResult(
                handled = true,
                editResult = copyLineDown(),
            )
            altPressed && keyCode == KEY_CODE_UP -> EditorCoreKeyEventResult(
                handled = true,
                editResult = moveLineUp(),
            )
            altPressed && keyCode == KEY_CODE_DOWN -> EditorCoreKeyEventResult(
                handled = true,
                editResult = moveLineDown(),
            )
            keyCode == KEY_CODE_LEFT -> {
                if (ctrlOrMetaPressed) {
                    moveCursorToPreviousWord(extendSelection)
                } else {
                    moveCursorLeft(extendSelection)
                }
                EditorCoreKeyEventResult(handled = true)
            }
            keyCode == KEY_CODE_RIGHT -> {
                if (ctrlOrMetaPressed) {
                    moveCursorToNextWord(extendSelection)
                } else {
                    moveCursorRight(extendSelection)
                }
                EditorCoreKeyEventResult(handled = true)
            }
            keyCode == KEY_CODE_UP -> {
                moveCursorUp(extendSelection)
                EditorCoreKeyEventResult(handled = true)
            }
            keyCode == KEY_CODE_DOWN -> {
                moveCursorDown(extendSelection)
                EditorCoreKeyEventResult(handled = true)
            }
            keyCode == KEY_CODE_HOME -> {
                moveCursorToLineStart(extendSelection)
                EditorCoreKeyEventResult(handled = true)
            }
            keyCode == KEY_CODE_END -> {
                moveCursorToLineEnd(extendSelection)
                EditorCoreKeyEventResult(handled = true)
            }
            keyCode == KEY_CODE_PAGE_UP -> {
                moveCursorByPage(
                    direction = -1,
                    extendSelection = extendSelection,
                )
                EditorCoreKeyEventResult(handled = true)
            }
            keyCode == KEY_CODE_PAGE_DOWN -> {
                moveCursorByPage(
                    direction = 1,
                    extendSelection = extendSelection,
                )
                EditorCoreKeyEventResult(handled = true)
            }
            keyCode == KEY_CODE_BACKSPACE -> EditorCoreKeyEventResult(
                handled = true,
                editResult = backspace(),
            )
            keyCode == KEY_CODE_DELETE -> EditorCoreKeyEventResult(
                handled = true,
                editResult = deleteForward(),
            )
            keyCode == KEY_CODE_ENTER -> EditorCoreKeyEventResult(
                handled = true,
                editResult = insertText("\n"),
            )
            keyCode == KEY_CODE_TAB -> EditorCoreKeyEventResult(
                handled = true,
                editResult = insertText("\t"),
            )
            keyCode == KEY_CODE_ESCAPE -> {
                compositionCancel()
                if (isInLinkedEditing()) {
                    cancelLinkedEditing()
                }
                EditorCoreKeyEventResult(handled = true)
            }
            !ctrlOrMetaPressed && !text.isNullOrEmpty() -> EditorCoreKeyEventResult(
                handled = true,
                editResult = insertText(text),
            )
            else -> EditorCoreKeyEventResult()
        }
    }

    override fun compositionStart() {
        if (!compositionEnabled) {
            return
        }
        composing = true
    }

    override fun compositionUpdate(text: String) = Unit

    override fun compositionEnd(committedText: String?): EditorCoreTextEditResult {
        composing = false
        if (committedText.isNullOrEmpty()) {
            return EditorCoreTextEditResult.Unchanged
        }
        return insertText(committedText)
    }

    override fun compositionCancel() {
        composing = false
    }

    override fun isComposing(): Boolean = composing

    override fun insertText(text: String): EditorCoreTextEditResult {
        return executeEdit { engine, session ->
            engine.execute(com.qiplat.compose.sweeteditor.core.edit.EditCommand.InsertText(text), session)
        }
    }

    override fun replaceText(range: EditorCoreTextRange, text: String): EditorCoreTextEditResult {
        return executeEdit { engine, _ ->
            engine.execute(
                com.qiplat.compose.sweeteditor.core.edit.EditCommand.ReplaceRange(range, text),
                currentSession(),
            )
        }
    }

    override fun deleteText(range: EditorCoreTextRange): EditorCoreTextEditResult {
        return executeEdit { engine, _ ->
            engine.execute(
                com.qiplat.compose.sweeteditor.core.edit.EditCommand.DeleteRange(range),
                currentSession(),
            )
        }
    }

    override fun backspace(): EditorCoreTextEditResult {
        return executeEdit { engine, session ->
            engine.execute(com.qiplat.compose.sweeteditor.core.edit.EditCommand.Backspace, session)
        }
    }

    override fun deleteForward(): EditorCoreTextEditResult {
        return executeEdit { engine, session ->
            engine.execute(com.qiplat.compose.sweeteditor.core.edit.EditCommand.DeleteForward, session)
        }
    }

    override fun insertSnippet(template: String): EditorCoreTextEditResult {
        val activeStore = document?.documentStore ?: return EditorCoreTextEditResult.Unchanged
        val parsedSnippet = SnippetParser.parse(template)
        val insertionStartOffset = selectionState.range
            ?.let { activeStore.getOffsetForPosition(it.start) }
            ?: activeStore.getOffsetForPosition(selectionState.cursor)
        val result = insertText(parsedSnippet.text)
        if (!result.changed) {
            return result
        }
        val updatedStore = document?.documentStore ?: return result
        val linkedEditingModel = parsedSnippet.groups
            .takeIf { it.isNotEmpty() }
            ?.let { groups ->
                EditorCoreLinkedEditingModel(
                    groups = groups.map { group ->
                        group.copy(
                            ranges = group.ranges.map { range ->
                                shiftSnippetRange(
                                    range = range,
                                    baseOffset = insertionStartOffset,
                                    documentStore = updatedStore,
                                )
                            },
                        )
                    },
                )
            }
        val exitPosition = parsedSnippet.finalCursorOffset
            ?.let { finalOffset ->
                updatedStore.getPositionForOffset(insertionStartOffset + finalOffset)
            }
        if (linkedEditingModel != null) {
            startLinkedEditing(
                model = linkedEditingModel,
                exitPosition = exitPosition,
            )
            return result.copy(
                cursor = selectionState.cursor,
                selection = selectionState.range,
            )
        }
        if (exitPosition != null) {
            selectionState = selectionState.collapse(exitPosition)
            ensureCursorVisible()
            return result.copy(
                cursor = selectionState.cursor,
                selection = selectionState.range,
            )
        }
        return result
    }

    override fun startLinkedEditing(model: EditorCoreLinkedEditingModel) {
        startLinkedEditing(
            model = model,
            exitPosition = null,
        )
    }

    private fun startLinkedEditing(
        model: EditorCoreLinkedEditingModel,
        exitPosition: EditorCoreTextPosition?,
    ) {
        val normalizedGroups = model.groups
            .sortedBy(EditorCoreTabStopGroup::index)
            .filter { group -> group.ranges.isNotEmpty() }
        linkedEditingState = normalizedGroups
            .takeIf { it.isNotEmpty() }
            ?.let { groups ->
                LinkedEditingState(
                    groups = groups.map { group ->
                        group.copy(
                            ranges = group.ranges.map { range -> range.coerceWithinDocument() },
                        )
                    },
                    activeGroupIndex = 0,
                    exitPosition = exitPosition?.coerceWithinDocument(),
                )
            }
        if (linkedEditingState != null) {
            activateLinkedEditingGroup(0)
        } else if (exitPosition != null) {
            selectionState = selectionState.collapse(exitPosition.coerceWithinDocument())
            ensureCursorVisible()
        }
    }

    override fun isInLinkedEditing(): Boolean = linkedEditingState != null

    override fun linkedEditingNext(): Boolean {
        val state = linkedEditingState ?: return false
        val nextIndex = state.activeGroupIndex + 1
        if (nextIndex > state.groups.lastIndex) {
            val exitPosition = state.exitPosition
            cancelLinkedEditing()
            if (exitPosition != null) {
                selectionState = selectionState.collapse(exitPosition.coerceWithinDocument())
                ensureCursorVisible()
            }
            return true
        }
        activateLinkedEditingGroup(nextIndex)
        return true
    }

    override fun linkedEditingPrev(): Boolean {
        val state = linkedEditingState ?: return false
        val previousIndex = state.activeGroupIndex - 1
        if (previousIndex < 0) {
            return false
        }
        activateLinkedEditingGroup(previousIndex)
        return true
    }

    override fun cancelLinkedEditing() {
        linkedEditingState = null
    }

    override fun moveLineUp(): EditorCoreTextEditResult {
        val activeStore = document?.documentStore ?: return EditorCoreTextEditResult.Unchanged
        val lineRange = resolveSelectedLineRange(activeStore)
        if (lineRange.first <= 0) {
            return EditorCoreTextEditResult.Unchanged
        }
        val movedText = readLineBlockText(activeStore, lineRange.first, lineRange.last)
        val previousText = readLineBlockText(activeStore, lineRange.first - 1, lineRange.first - 1)
        return replaceLineBlock(
            documentStore = activeStore,
            startLine = lineRange.first - 1,
            endLine = lineRange.last,
            text = joinLineBlocks(movedText, previousText),
        )
    }

    override fun moveLineDown(): EditorCoreTextEditResult {
        val activeStore = document?.documentStore ?: return EditorCoreTextEditResult.Unchanged
        val lineRange = resolveSelectedLineRange(activeStore)
        if (lineRange.last >= activeStore.getLineCount() - 1) {
            return EditorCoreTextEditResult.Unchanged
        }
        val movedText = readLineBlockText(activeStore, lineRange.first, lineRange.last)
        val nextText = readLineBlockText(activeStore, lineRange.last + 1, lineRange.last + 1)
        return replaceLineBlock(
            documentStore = activeStore,
            startLine = lineRange.first,
            endLine = lineRange.last + 1,
            text = joinLineBlocks(nextText, movedText),
        )
    }

    override fun copyLineUp(): EditorCoreTextEditResult {
        val activeStore = document?.documentStore ?: return EditorCoreTextEditResult.Unchanged
        val lineRange = resolveSelectedLineRange(activeStore)
        val text = readLineBlockText(activeStore, lineRange.first, lineRange.last)
        return insertAtPosition(
            position = EditorCoreTextPosition(lineRange.first, 0),
            text = text,
        )
    }

    override fun copyLineDown(): EditorCoreTextEditResult {
        val activeStore = document?.documentStore ?: return EditorCoreTextEditResult.Unchanged
        val lineRange = resolveSelectedLineRange(activeStore)
        val insertionPosition = if (lineRange.last < activeStore.getLineCount() - 1) {
            EditorCoreTextPosition(lineRange.last + 1, 0)
        } else {
            EditorCoreTextPosition(
                line = lineRange.last,
                column = activeStore.getLineText(lineRange.last).length,
            )
        }
        val text = readLineBlockText(activeStore, lineRange.first, lineRange.last)
        val insertedText = if (lineRange.last < activeStore.getLineCount() - 1) {
            text
        } else {
            "\n$text"
        }
        return insertAtPosition(
            position = insertionPosition,
            text = insertedText,
        )
    }

    override fun deleteLine(): EditorCoreTextEditResult {
        val activeStore = document?.documentStore ?: return EditorCoreTextEditResult.Unchanged
        val lineRange = resolveSelectedLineRange(activeStore)
        return replaceLineBlock(
            documentStore = activeStore,
            startLine = lineRange.first,
            endLine = lineRange.last,
            text = "",
        )
    }

    override fun insertLineAbove(): EditorCoreTextEditResult {
        val activeStore = document?.documentStore ?: return EditorCoreTextEditResult.Unchanged
        val targetLine = resolveSelectedLineRange(activeStore).first
        val insertionPosition = if (targetLine == 0) {
            EditorCoreTextPosition.Zero
        } else {
            lineBlockRange(activeStore, targetLine - 1, targetLine - 1).end
        }
        return insertAtPosition(
            position = insertionPosition,
            text = "\n",
        )
    }

    override fun insertLineBelow(): EditorCoreTextEditResult {
        val activeStore = document?.documentStore ?: return EditorCoreTextEditResult.Unchanged
        val targetLine = resolveSelectedLineRange(activeStore).last
        val insertionPosition = lineBlockRange(activeStore, targetLine, targetLine).end
        val text = if (targetLine < activeStore.getLineCount() - 1) "\n" else "\n"
        return insertAtPosition(
            position = insertionPosition,
            text = text,
        )
    }

    override fun undo(): EditorCoreTextEditResult {
        return executeEdit { engine, session ->
            engine.execute(com.qiplat.compose.sweeteditor.core.edit.EditCommand.Undo, session)
        }
    }

    override fun redo(): EditorCoreTextEditResult {
        return executeEdit { engine, session ->
            engine.execute(com.qiplat.compose.sweeteditor.core.edit.EditCommand.Redo, session)
        }
    }

    override fun canUndo(): Boolean = editEngine?.canUndo() == true

    override fun canRedo(): Boolean = editEngine?.canRedo() == true

    override fun selectAll() {
        val activeDocument = document ?: return
        val lastLine = (activeDocument.getLineCount() - 1).coerceAtLeast(0)
        val end = EditorCoreTextPosition(
            line = lastLine,
            column = activeDocument.getLineText(lastLine).length,
        )
        selectionState = selectionState.select(
            EditorCoreTextRange(
            start = EditorCoreTextPosition.Zero,
            end = end,
            ),
        )
        ensureCursorVisible()
    }

    override fun selectCurrentLine() {
        val activeStore = document?.documentStore ?: return
        val line = selectionState.cursor.line.coerceIn(0, activeStore.getLineCount() - 1)
        selectionState = selectionState.select(
            lineBlockRange(
                documentStore = activeStore,
                startLine = line,
                endLine = line,
            ),
        )
        ensureCursorVisible()
    }

    override fun duplicateSelectionOrLine(): EditorCoreTextEditResult {
        val activeStore = document?.documentStore ?: return EditorCoreTextEditResult.Unchanged
        val activeSelection = selectionState.range
        if (activeSelection == null || activeSelection.start == activeSelection.end) {
            return copyLineDown()
        }
        val selectedText = getSelectedText() ?: return EditorCoreTextEditResult.Unchanged
        val insertionPosition = activeSelection.end
        val insertionOffset = activeStore.getOffsetForPosition(insertionPosition)
        val result = insertAtPosition(
            position = insertionPosition,
            text = selectedText,
        )
        if (!result.changed) {
            return result
        }
        val updatedStore = document?.documentStore ?: return result
        val duplicatedRange = EditorCoreTextRange(
            start = updatedStore.getPositionForOffset(insertionOffset),
            end = updatedStore.getPositionForOffset(insertionOffset + selectedText.length),
        )
        selectionState = selectionState.select(duplicatedRange)
        ensureCursorVisible()
        return result.copy(
            cursor = selectionState.cursor,
            selection = selectionState.range,
        )
    }

    override fun getSelectedText(): String? {
        val activeSelection = selectionState.range ?: return null
        val activeStore = document?.documentStore ?: return null
        val start = activeStore.getOffsetForPosition(activeSelection.start)
        val end = activeStore.getOffsetForPosition(activeSelection.end)
        return activeStore.getText().substring(start, end)
    }

    override fun getWordRangeAtCursor(): EditorCoreTextRange {
        val activeStore = document?.documentStore ?: return collapsedWordRange()
        return resolveWordRange(
            documentStore = activeStore,
            position = selectionState.cursor,
        )
    }

    override fun getWordAtCursor(): String? {
        val activeStore = document?.documentStore ?: return null
        val range = resolveWordRange(
            documentStore = activeStore,
            position = selectionState.cursor,
        )
        if (range.start == range.end) {
            return null
        }
        val start = activeStore.getOffsetForPosition(range.start)
        val end = activeStore.getOffsetForPosition(range.end)
        return activeStore.getText().substring(start, end)
    }

    override fun moveCursorLeft(extendSelection: Boolean) {
        val activeStore = document?.documentStore ?: return
        if (!extendSelection) {
            val activeSelection = selectionState.range
            if (activeSelection != null) {
                updateCursorMovement(
                    nextPosition = activeSelection.start,
                    extendSelection = false,
                )
                return
            }
        }
        val currentOffset = activeStore.getOffsetForPosition(selectionState.cursor)
        val nextPosition = if (currentOffset <= 0) {
            EditorCoreTextPosition.Zero
        } else {
            activeStore.getPositionForOffset(currentOffset - 1)
        }
        updateCursorMovement(
            nextPosition = nextPosition,
            extendSelection = extendSelection,
        )
    }

    override fun moveCursorRight(extendSelection: Boolean) {
        val activeStore = document?.documentStore ?: return
        if (!extendSelection) {
            val activeSelection = selectionState.range
            if (activeSelection != null) {
                updateCursorMovement(
                    nextPosition = activeSelection.end,
                    extendSelection = false,
                )
                return
            }
        }
        val currentOffset = activeStore.getOffsetForPosition(selectionState.cursor)
        val nextPosition = if (currentOffset >= activeStore.getText().length) {
            activeStore.getPositionForOffset(activeStore.getText().length)
        } else {
            activeStore.getPositionForOffset(currentOffset + 1)
        }
        updateCursorMovement(
            nextPosition = nextPosition,
            extendSelection = extendSelection,
        )
    }

    override fun moveCursorUp(extendSelection: Boolean) {
        val nextLine = (selectionState.cursor.line - 1).coerceAtLeast(0)
        moveCursorVertically(
            targetLine = nextLine,
            extendSelection = extendSelection,
        )
    }

    override fun moveCursorDown(extendSelection: Boolean) {
        val activeStore = document?.documentStore ?: return
        val nextLine = (selectionState.cursor.line + 1).coerceAtMost(activeStore.getLineCount() - 1)
        moveCursorVertically(
            targetLine = nextLine,
            extendSelection = extendSelection,
        )
    }

    override fun moveCursorToLineStart(extendSelection: Boolean) {
        updateCursorMovement(
            nextPosition = EditorCoreTextPosition(
                line = selectionState.cursor.line,
                column = 0,
            ),
            extendSelection = extendSelection,
        )
    }

    override fun moveCursorToLineEnd(extendSelection: Boolean) {
        val activeStore = document?.documentStore ?: return
        updateCursorMovement(
            nextPosition = EditorCoreTextPosition(
                line = selectionState.cursor.line,
                column = activeStore.getLineText(selectionState.cursor.line).length,
            ),
            extendSelection = extendSelection,
        )
    }

    override fun moveCursorToPreviousWord(extendSelection: Boolean) {
        val activeStore = document?.documentStore ?: return
        if (!extendSelection) {
            val activeSelection = selectionState.range
            if (activeSelection != null) {
                updateCursorMovement(
                    nextPosition = activeSelection.start,
                    extendSelection = false,
                )
                return
            }
        }
        updateCursorMovement(
            nextPosition = findPreviousWordBoundary(
                documentStore = activeStore,
                position = selectionState.cursor,
            ),
            extendSelection = extendSelection,
        )
    }

    override fun moveCursorToNextWord(extendSelection: Boolean) {
        val activeStore = document?.documentStore ?: return
        if (!extendSelection) {
            val activeSelection = selectionState.range
            if (activeSelection != null) {
                updateCursorMovement(
                    nextPosition = activeSelection.end,
                    extendSelection = false,
                )
                return
            }
        }
        updateCursorMovement(
            nextPosition = findNextWordBoundary(
                documentStore = activeStore,
                position = selectionState.cursor,
            ),
            extendSelection = extendSelection,
        )
    }

    override fun scrollToLine(line: Int, behavior: EditorCoreScrollBehavior) {
        val activeStore = document?.documentStore ?: return
        val lineCount = activeStore.getLineCount()
        if (lineCount == 0) {
            return
        }
        val targetLine = line.coerceIn(0, lineCount - 1)
        val layoutQuery = buildLayoutQuery(activeStore)
        val metrics = getScrollMetrics()
        val currentWrapIndex = layoutQuery
            .queryPosition(selectionState.cursor)
            .visualLine
            .wrapIndex
        val lineQuery = layoutQuery.queryLogicalLine(
            logicalLine = targetLine,
            preferredWrapIndex = currentWrapIndex,
        )
        viewportState = viewportState.scrollToLine(
            lineQuery = lineQuery,
            behavior = behavior,
            maxScrollY = metrics.maxScrollY,
        )
    }

    override fun gotoPosition(line: Int, column: Int) {
        selectionState = selectionState.collapse(
            EditorCoreTextPosition(
            line = line,
            column = column,
            ).coerceWithinDocument(),
        )
        verticalCursorAnchor = null
        ensureCursorVisible()
    }

    override fun setScroll(scrollX: Float, scrollY: Float) {
        val metrics = getScrollMetrics()
        viewportState = viewportState.withScroll(
            scrollX = scrollX,
            scrollY = scrollY,
            maxScrollX = metrics.maxScrollX,
            maxScrollY = metrics.maxScrollY,
        )
    }

    override fun ensureCursorVisible() {
        val metrics = getScrollMetrics()
        val geometry = document?.documentStore
            ?.let { activeStore ->
                buildLayoutQuery(activeStore)
                    .queryGeometry(
                        cursor = selectionState.cursor,
                        selection = selectionState.range,
                    )
            }
            ?: return
        viewportState = viewportState.ensureGeometryVisible(
            geometry = geometry,
            maxScrollX = metrics.maxScrollX,
            maxScrollY = metrics.maxScrollY,
        )
    }

    override fun getPositionForPoint(x: Float, y: Float): EditorCoreTextPosition =
        document?.documentStore
            ?.let { activeStore ->
                buildLayoutQuery(activeStore).hitTest(
                    x = x + viewportState.scrollX,
                    y = y + viewportState.scrollY,
                ).position
            }
            ?: EditorCoreTextPosition.Zero

    override fun getPositionRect(line: Int, column: Int): EditorCoreCursorRect =
        document?.documentStore
            ?.let { activeStore ->
                val rect = buildLayoutQuery(activeStore)
                    .queryGeometry(
                        cursor = EditorCoreTextPosition(line, column),
                        selection = null,
                    )
                    .cursor
                    .rect
                EditorCoreCursorRect(
                    x = rect.x,
                    y = rect.y,
                    height = rect.height,
                )
            }
            ?: EditorCoreCursorRect()

    override fun getCursorRect(): EditorCoreCursorRect =
        getPositionRect(
            line = selectionState.cursor.line,
            column = selectionState.cursor.column,
        )

    override fun registerBatchTextStyles(data: ByteArray) = Unit

    override fun setBatchLineSpans(data: ByteArray) = Unit

    override fun setBatchLineInlayHints(data: ByteArray) = Unit

    override fun setBatchLinePhantomTexts(data: ByteArray) = Unit

    override fun setBatchLineGutterIcons(data: ByteArray) = Unit

    override fun setBatchLineDiagnostics(data: ByteArray) = Unit

    override fun clearInlayHints() = Unit

    override fun clearPhantomTexts() = Unit

    override fun clearGutterIcons() = Unit

    override fun clearDiagnostics() = Unit

    override fun setIndentGuides(data: ByteArray) = Unit

    override fun setBracketGuides(data: ByteArray) = Unit

    override fun setFlowGuides(data: ByteArray) = Unit

    override fun setSeparatorGuides(data: ByteArray) = Unit

    override fun clearGuides() = Unit

    override fun setFoldRegions(data: ByteArray) = Unit

    override fun clearAllDecorations() = Unit

    override fun setMaxGutterIcons(count: Int) = Unit

    private fun currentSession() =
        com.qiplat.compose.sweeteditor.core.edit.EditSession(
            cursor = selectionState.cursor,
            selection = selectionState.range,
        )

    private inline fun executeEdit(
        block: (
            EditEngine,
            com.qiplat.compose.sweeteditor.core.edit.EditSession,
        ) -> EditExecutionResult,
    ): EditorCoreTextEditResult {
        val engine = editEngine ?: return EditorCoreTextEditResult.Unchanged
        val activeStore = document?.documentStore ?: return EditorCoreTextEditResult.Unchanged
        val linkedEditingStateBefore = linkedEditingState
        val linkedEditingOffsetsBefore = linkedEditingState?.groups?.map { group ->
            group.ranges.map { range ->
                activeStore.getOffsetForPosition(range.start) to activeStore.getOffsetForPosition(range.end)
            }
        }
        val session = currentSession()
        val selectionStartOffsetBefore = session.selection?.let { activeStore.getOffsetForPosition(it.start) }
        val cursorOffsetBefore = activeStore.getOffsetForPosition(session.cursor)
        val result = block(engine, currentSession())
        val collectedResults = mutableListOf(result)
        selectionState = result.selection?.let { selectionState.select(it) }
            ?: selectionState.collapse(result.cursor)
        val primaryChange = result.changeSet?.changes?.singleOrNull()
        if (linkedEditingStateBefore != null && linkedEditingOffsetsBefore != null && primaryChange != null) {
            val changeStartBefore = activeStore.getOffsetForPosition(primaryChange.rangeBefore.start)
            val changeEndBefore = activeStore.getOffsetForPosition(primaryChange.rangeBefore.end)
            val editedRange = resolveEditedLinkedEditingRange(
                linkedEditingOffsetsBefore = linkedEditingOffsetsBefore,
                changeStartOffset = changeStartBefore,
                changeEndOffset = changeEndBefore,
            )
            if (editedRange != null) {
                updateLinkedEditingAfterEdit(
                    linkedEditingOffsetsBefore = linkedEditingOffsetsBefore,
                    editedGroupIndex = editedRange.first,
                    editedRangeIndex = editedRange.second,
                    changedStartOffset = selectionStartOffsetBefore ?: cursorOffsetBefore,
                    changedDeletedLength = primaryChange.deletedText.length,
                    insertedTextLength = primaryChange.insertedText.length,
                    updatedStore = document?.documentStore ?: activeStore,
                )
                if (
                    editedRange.first == linkedEditingStateBefore.activeGroupIndex &&
                    editedRange.second == 0
                ) {
                    applyLinkedEditingMirrors(
                        engine = engine,
                        editedGroupIndex = editedRange.first,
                        originalPrimaryOffsets = linkedEditingOffsetsBefore[editedRange.first][0],
                        primaryChange = primaryChange,
                        activeStoreBefore = activeStore,
                        collectedResults = collectedResults,
                    )
                }
            }
        }
        verticalCursorAnchor = null
        ensureCursorVisible()
        return collectedResults.toEditorCoreTextEditResult(
            cursor = selectionState.cursor,
            selection = selectionState.range,
        )
    }

    private fun buildLayoutSnapshot(documentStore: DocumentStore) =
        buildLayoutQuery(documentStore).toSnapshot(
            documentVersion = documentStore.version,
            viewportHeight = viewportState.height,
            scrollY = viewportState.scrollY,
        )

    private fun buildLayoutQuery(documentStore: DocumentStore): LayoutQuery =
        layoutEngine.createQuery(
            documentStore = documentStore,
            viewportWidth = viewportState.width,
            settings = currentLayoutSettings(),
        )

    private fun currentLayoutSettings() =
        LayoutSettings(
            wrapMode = wrapMode,
            tabSize = tabSize,
            lineSpacingExtra = lineSpacingExtra,
            lineSpacingMultiplier = lineSpacingMultiplier,
        )

    private fun activateLinkedEditingGroup(groupIndex: Int) {
        val state = linkedEditingState ?: return
        val group = state.groups.getOrNull(groupIndex) ?: run {
            linkedEditingState = null
            return
        }
        val primaryRange = group.ranges.firstOrNull()?.coerceWithinDocument() ?: run {
            linkedEditingState = null
            return
        }
        linkedEditingState = state.copy(
            activeGroupIndex = groupIndex.coerceIn(0, state.groups.lastIndex),
        )
        selectionState = selectionState.select(primaryRange)
        verticalCursorAnchor = null
        ensureCursorVisible()
    }

    private fun updateLinkedEditingAfterEdit(
        linkedEditingOffsetsBefore: List<List<Pair<Int, Int>>>,
        editedGroupIndex: Int,
        editedRangeIndex: Int,
        changedStartOffset: Int,
        changedDeletedLength: Int,
        insertedTextLength: Int,
        updatedStore: DocumentStore,
    ) {
        val state = linkedEditingState ?: return
        val delta = insertedTextLength - changedDeletedLength
        linkedEditingState = state.copy(
            groups = state.groups.mapIndexed { groupIndex, group ->
                group.copy(
                    ranges = group.ranges.mapIndexed { rangeIndex, _ ->
                        updateLinkedEditingRange(
                            rangeOffsetsBefore = linkedEditingOffsetsBefore[groupIndex][rangeIndex],
                            changedStartOffset = changedStartOffset,
                            changedDeletedLength = changedDeletedLength,
                            insertedTextLength = insertedTextLength,
                            delta = delta,
                            isEditedRange = groupIndex == editedGroupIndex && rangeIndex == editedRangeIndex,
                            documentStore = updatedStore,
                        )
                    },
                )
            },
            exitPosition = state.exitPosition?.let { exitPosition ->
                updatedExitPosition(
                    currentPosition = exitPosition,
                    changedStartOffset = changedStartOffset,
                    changedDeletedLength = changedDeletedLength,
                    insertedTextLength = insertedTextLength,
                    delta = delta,
                    documentStore = updatedStore,
                )
            },
        )
    }

    private fun applyLinkedEditingMirrors(
        engine: EditEngine,
        editedGroupIndex: Int,
        originalPrimaryOffsets: Pair<Int, Int>,
        primaryChange: com.qiplat.compose.sweeteditor.core.document.TextChange,
        activeStoreBefore: DocumentStore,
        collectedResults: MutableList<EditExecutionResult>,
    ) {
        val currentState = linkedEditingState ?: return
        val primaryStartOffsetBefore = originalPrimaryOffsets.first
        val relativeStart = activeStoreBefore.getOffsetForPosition(primaryChange.rangeBefore.start) - primaryStartOffsetBefore
        val relativeEnd = activeStoreBefore.getOffsetForPosition(primaryChange.rangeBefore.end) - primaryStartOffsetBefore
        val siblingIndices = currentState.groups[editedGroupIndex].ranges
            .indices
            .filter { it != 0 }
            .sortedByDescending { index ->
                val siblingRange = currentState.groups[editedGroupIndex].ranges[index]
                document?.documentStore?.getOffsetForPosition(siblingRange.start) ?: 0
            }
        siblingIndices.forEach { siblingIndex ->
            val linkedEditingOffsetsBefore = linkedEditingState?.groups?.map { group ->
                group.ranges.map { range ->
                    val activeStore = document?.documentStore ?: return@forEach
                    activeStore.getOffsetForPosition(range.start) to activeStore.getOffsetForPosition(range.end)
                }
            } ?: return@forEach
            val updatedStore = document?.documentStore ?: return@forEach
            val siblingRange = linkedEditingState
                ?.groups
                ?.getOrNull(editedGroupIndex)
                ?.ranges
                ?.getOrNull(siblingIndex)
                ?: return@forEach
            val siblingStartOffset = updatedStore.getOffsetForPosition(siblingRange.start)
            val targetStartOffset = siblingStartOffset + relativeStart
            val targetEndOffset = siblingStartOffset + relativeEnd
            val siblingTargetRange = rangeFromOffsets(
                documentStore = updatedStore,
                startOffset = targetStartOffset,
                endOffset = targetEndOffset,
            )
            val mirrorResult = engine.execute(
                command = com.qiplat.compose.sweeteditor.core.edit.EditCommand.ReplaceRange(
                    range = siblingTargetRange,
                    text = primaryChange.insertedText,
                ),
                session = currentSession(),
            )
            collectedResults += mirrorResult
            shiftSelectionForMirroredChange(
                changedStartOffset = targetStartOffset,
                changedDeletedLength = targetEndOffset - targetStartOffset,
                insertedTextLength = primaryChange.insertedText.length,
                documentStore = document?.documentStore ?: updatedStore,
            )
            updateLinkedEditingAfterEdit(
                linkedEditingOffsetsBefore = linkedEditingOffsetsBefore,
                editedGroupIndex = editedGroupIndex,
                editedRangeIndex = siblingIndex,
                changedStartOffset = targetStartOffset,
                changedDeletedLength = targetEndOffset - targetStartOffset,
                insertedTextLength = primaryChange.insertedText.length,
                updatedStore = document?.documentStore ?: updatedStore,
            )
        }
    }

    private fun resolveEditedLinkedEditingRange(
        linkedEditingOffsetsBefore: List<List<Pair<Int, Int>>>,
        changeStartOffset: Int,
        changeEndOffset: Int,
    ): Pair<Int, Int>? {
        linkedEditingOffsetsBefore.forEachIndexed { groupIndex, group ->
            group.forEachIndexed { rangeIndex, (rangeStart, rangeEnd) ->
                if (changeStartOffset in rangeStart..rangeEnd && changeEndOffset in rangeStart..rangeEnd) {
                    return groupIndex to rangeIndex
                }
            }
        }
        return null
    }

    private fun shiftSelectionForMirroredChange(
        changedStartOffset: Int,
        changedDeletedLength: Int,
        insertedTextLength: Int,
        documentStore: DocumentStore,
    ) {
        val delta = insertedTextLength - changedDeletedLength
        val updatedCursor = updatedExitPosition(
            currentPosition = selectionState.cursor,
            changedStartOffset = changedStartOffset,
            changedDeletedLength = changedDeletedLength,
            insertedTextLength = insertedTextLength,
            delta = delta,
            documentStore = documentStore,
        )
        selectionState = selectionState.range
            ?.let { range ->
                SelectionState(
                    anchor = updatedExitPosition(
                        currentPosition = range.start,
                        changedStartOffset = changedStartOffset,
                        changedDeletedLength = changedDeletedLength,
                        insertedTextLength = insertedTextLength,
                        delta = delta,
                        documentStore = documentStore,
                    ),
                    cursor = updatedCursor,
                )
            }
            ?: selectionState.collapse(updatedCursor)
    }

    private fun resolveSelectedLineRange(documentStore: DocumentStore): IntRange {
        val activeSelection = selectionState.range ?: return selectionState.cursor.line..selectionState.cursor.line
        val startLine = activeSelection.start.line
        val endLine = if (
            activeSelection.end.column == 0 &&
            activeSelection.end.line > activeSelection.start.line
        ) {
            activeSelection.end.line - 1
        } else {
            activeSelection.end.line
        }
        return startLine..endLine.coerceAtLeast(startLine)
    }

    private fun lineBlockRange(
        documentStore: DocumentStore,
        startLine: Int,
        endLine: Int,
    ): EditorCoreTextRange {
        val clampedStartLine = startLine.coerceIn(0, documentStore.getLineCount() - 1)
        val clampedEndLine = endLine.coerceIn(clampedStartLine, documentStore.getLineCount() - 1)
        val endPosition = if (clampedEndLine < documentStore.getLineCount() - 1) {
            EditorCoreTextPosition(clampedEndLine + 1, 0)
        } else {
            EditorCoreTextPosition(
                line = clampedEndLine,
                column = documentStore.getLineText(clampedEndLine).length,
            )
        }
        return EditorCoreTextRange(
            start = EditorCoreTextPosition(clampedStartLine, 0),
            end = endPosition,
        )
    }

    private fun readLineBlockText(
        documentStore: DocumentStore,
        startLine: Int,
        endLine: Int,
    ): String {
        val range = lineBlockRange(documentStore, startLine, endLine)
        return documentStore.getText().substring(
            documentStore.getOffsetForPosition(range.start),
            documentStore.getOffsetForPosition(range.end),
        )
    }

    private fun replaceLineBlock(
        documentStore: DocumentStore,
        startLine: Int,
        endLine: Int,
        text: String,
    ): EditorCoreTextEditResult =
        replaceText(
            range = lineBlockRange(documentStore, startLine, endLine),
            text = text,
        )

    private fun insertAtPosition(
        position: EditorCoreTextPosition,
        text: String,
    ): EditorCoreTextEditResult =
        replaceText(
            range = EditorCoreTextRange(
                start = position,
                end = position,
            ),
            text = text,
        )

    private fun joinLineBlocks(
        first: String,
        second: String,
    ): String = when {
        first.isEmpty() -> second
        second.isEmpty() -> first
        first.endsWith('\n') || first.endsWith('\r') || second.startsWith('\n') || second.startsWith('\r') ->
            first + second
        else -> "$first\n$second"
    }

    private fun shiftSnippetRange(
        range: EditorCoreTextRange,
        baseOffset: Int,
        documentStore: DocumentStore,
    ): EditorCoreTextRange =
        rangeFromOffsets(
            documentStore = documentStore,
            startOffset = baseOffset + range.start.column,
            endOffset = baseOffset + range.end.column,
        )

    private fun updatedExitPosition(
        currentPosition: EditorCoreTextPosition,
        changedStartOffset: Int,
        changedDeletedLength: Int,
        insertedTextLength: Int,
        delta: Int,
        documentStore: DocumentStore,
    ): EditorCoreTextPosition {
        val currentOffset = documentStore.getOffsetForPosition(currentPosition)
        val changeEndOffsetBefore = changedStartOffset + changedDeletedLength
        val updatedOffset = when {
            currentOffset >= changeEndOffsetBefore -> currentOffset + delta
            currentOffset <= changedStartOffset -> currentOffset
            else -> changedStartOffset + insertedTextLength
        }
        return documentStore.getPositionForOffset(updatedOffset.coerceAtLeast(0))
    }

    private fun moveCursorByPage(
        direction: Int,
        extendSelection: Boolean,
    ) {
        val activeStore = document?.documentStore ?: return
        val lineDelta = ((viewportState.height / resolveLineHeight()).toInt()).coerceAtLeast(1)
        val targetLine = (selectionState.cursor.line + direction * lineDelta)
            .coerceIn(0, activeStore.getLineCount() - 1)
        moveCursorVertically(
            targetLine = targetLine,
            extendSelection = extendSelection,
        )
    }

    private fun resolveWordRange(
        documentStore: DocumentStore,
        position: EditorCoreTextPosition,
    ): EditorCoreTextRange {
        val text = documentStore.getText()
        if (text.isEmpty()) {
            return collapsedWordRange()
        }
        val offset = documentStore.getOffsetForPosition(position)
        val anchorOffset = when {
            offset < text.length && text[offset].isWordCharacter() -> offset
            offset > 0 && text[offset - 1].isWordCharacter() -> offset - 1
            else -> return collapsedWordRange()
        }
        var startOffset = anchorOffset
        while (startOffset > 0 && text[startOffset - 1].isWordCharacter()) {
            startOffset -= 1
        }
        var endOffset = anchorOffset + 1
        while (endOffset < text.length && text[endOffset].isWordCharacter()) {
            endOffset += 1
        }
        return EditorCoreTextRange(
            start = documentStore.getPositionForOffset(startOffset),
            end = documentStore.getPositionForOffset(endOffset),
        )
    }

    private fun findPreviousWordBoundary(
        documentStore: DocumentStore,
        position: EditorCoreTextPosition,
    ): EditorCoreTextPosition {
        val text = documentStore.getText()
        if (text.isEmpty()) {
            return EditorCoreTextPosition.Zero
        }
        var offset = documentStore.getOffsetForPosition(position).coerceIn(0, text.length)
        while (offset > 0 && !text[offset - 1].isWordCharacter()) {
            offset -= 1
        }
        while (offset > 0 && text[offset - 1].isWordCharacter()) {
            offset -= 1
        }
        return documentStore.getPositionForOffset(offset)
    }

    private fun findNextWordBoundary(
        documentStore: DocumentStore,
        position: EditorCoreTextPosition,
    ): EditorCoreTextPosition {
        val text = documentStore.getText()
        if (text.isEmpty()) {
            return EditorCoreTextPosition.Zero
        }
        var offset = documentStore.getOffsetForPosition(position).coerceIn(0, text.length)
        while (offset < text.length && text[offset].isWordCharacter()) {
            offset += 1
        }
        while (offset < text.length && !text[offset].isWordCharacter()) {
            offset += 1
        }
        return documentStore.getPositionForOffset(offset)
    }

    private fun collapsedWordRange(): EditorCoreTextRange =
        EditorCoreTextRange(
            start = selectionState.cursor,
            end = selectionState.cursor,
        )

    private fun moveCursorVertically(
        targetLine: Int,
        extendSelection: Boolean,
    ) {
        val activeStore = document?.documentStore ?: return
        val layoutQuery = buildLayoutQuery(activeStore)
        val currentPosition = layoutQuery.queryPosition(selectionState.cursor)
        val anchor = verticalCursorAnchor ?: VerticalCursorAnchor(
            x = currentPosition.rect.x,
        ).also { verticalCursorAnchor = it }
        val lineQuery = layoutQuery.queryLogicalLine(
            logicalLine = targetLine,
            preferredWrapIndex = currentPosition.visualLine.wrapIndex,
        )
        val hitTest = layoutQuery.hitTest(
            x = anchor.x,
            y = lineQuery.rect.y,
        )
        updateCursorMovement(
            nextPosition = hitTest.position,
            extendSelection = extendSelection,
        )
    }

    private fun updateCursorMovement(
        nextPosition: EditorCoreTextPosition,
        extendSelection: Boolean,
    ) {
        val clampedPosition = nextPosition.coerceWithinDocument()
        val previousCursor = selectionState.cursor
        if (!extendSelection) {
            selectionState = selectionState.collapse(clampedPosition)
            if (clampedPosition.line != previousCursor.line) {
                verticalCursorAnchor = null
            }
            ensureCursorVisible()
            return
        }
        selectionState = selectionState.moveTo(
            position = clampedPosition,
            extendSelection = true,
        )
        ensureCursorVisible()
    }

    private fun EditorCoreTextRange.coerceWithinDocument(): EditorCoreTextRange =
        EditorCoreTextRange(
            start = start.coerceWithinDocument(),
            end = end.coerceWithinDocument(),
        ).let { range ->
            if (range.start <= range.end) {
                range
            } else {
                EditorCoreTextRange(
                    start = range.end,
                    end = range.start,
                )
            }
        }

    private fun EditorCoreTextPosition.coerceWithinDocument(): EditorCoreTextPosition {
        val activeStore = document?.documentStore ?: return this
        val line = line.coerceIn(0, (activeStore.getLineCount() - 1).coerceAtLeast(0))
        val column = column.coerceIn(0, activeStore.getLineText(line).length)
        return EditorCoreTextPosition(
            line = line,
            column = column,
        )
    }

    private fun resolveLineHeight(): Float =
        textMeasurer.getFontMetrics()
            .firstOrNull()
            ?.takeIf { it > 0f }
            ?.let { it * lineSpacingMultiplier + lineSpacingExtra }
            ?.coerceAtLeast(1f)
            ?: 16f

    private fun updateLinkedEditingRange(
        rangeOffsetsBefore: Pair<Int, Int>,
        changedStartOffset: Int,
        changedDeletedLength: Int,
        insertedTextLength: Int,
        delta: Int,
        isEditedRange: Boolean,
        documentStore: DocumentStore,
    ): EditorCoreTextRange {
        val oldStartOffset = rangeOffsetsBefore.first
        val oldEndOffset = rangeOffsetsBefore.second
        if (isEditedRange) {
            return rangeFromOffsets(
                documentStore = documentStore,
                startOffset = changedStartOffset,
                endOffset = changedStartOffset + insertedTextLength,
            )
        }
        val changeEndOffsetBefore = changedStartOffset + changedDeletedLength
        val nextStartOffset = when {
            oldStartOffset >= changeEndOffsetBefore -> oldStartOffset + delta
            else -> oldStartOffset
        }
        val nextEndOffset = when {
            oldEndOffset >= changeEndOffsetBefore -> oldEndOffset + delta
            oldEndOffset <= changedStartOffset -> oldEndOffset
            else -> maxOf(nextStartOffset, changedStartOffset + insertedTextLength)
        }
        return rangeFromOffsets(
            documentStore = documentStore,
            startOffset = nextStartOffset,
            endOffset = nextEndOffset,
        )
    }

    private fun rangeFromOffsets(
        documentStore: DocumentStore,
        startOffset: Int,
        endOffset: Int,
    ): EditorCoreTextRange =
        EditorCoreTextRange(
            start = documentStore.getPositionForOffset(startOffset.coerceAtLeast(0)),
            end = documentStore.getPositionForOffset(endOffset.coerceAtLeast(startOffset.coerceAtLeast(0))),
        )

    private fun resolveGesturePosition(points: FloatArray): EditorCoreTextPosition {
        val x = points.getOrNull(0) ?: 0f
        val y = points.getOrNull(1) ?: 0f
        return getPositionForPoint(x, y)
    }
}

private var editorDocumentHandleSeed: Long = 1L

private fun nextDocumentHandle(): Long = editorDocumentHandleSeed++

private fun EditExecutionResult.toEditorCoreTextEditResult(): EditorCoreTextEditResult =
    EditorCoreTextEditResult(
        changed = changed,
        changes = changeSet
            ?.changes
            ?.map { change ->
                EditorCoreTextChange(
                    range = change.rangeBefore,
                    text = change.insertedText,
                )
            }
            .orEmpty(),
        cursor = cursor,
        selection = selection,
    )

private fun List<EditExecutionResult>.toEditorCoreTextEditResult(
    cursor: EditorCoreTextPosition,
    selection: EditorCoreTextRange?,
): EditorCoreTextEditResult =
    EditorCoreTextEditResult(
        changed = any(EditExecutionResult::changed),
        changes = flatMap { result ->
            result.changeSet
                ?.changes
                ?.map { change ->
                    EditorCoreTextChange(
                        range = change.rangeBefore,
                        text = change.insertedText,
                    )
                }
                .orEmpty()
        },
        cursor = cursor,
        selection = selection,
    )

private operator fun EditorCoreTextPosition.compareTo(other: EditorCoreTextPosition): Int = when {
    line != other.line -> line.compareTo(other.line)
    else -> column.compareTo(other.column)
}

private fun Char.isWordCharacter(): Boolean = isLetterOrDigit() || this == '_'

private fun Int.hasShiftModifier(): Boolean = this and MODIFIER_SHIFT != 0

private fun Int.hasCtrlModifier(): Boolean = this and MODIFIER_CTRL != 0

private fun Int.hasAltModifier(): Boolean = this and MODIFIER_ALT != 0

private fun Int.hasMetaModifier(): Boolean = this and MODIFIER_META != 0

private const val MODIFIER_SHIFT = 1
private const val MODIFIER_CTRL = 2
private const val MODIFIER_ALT = 4
private const val MODIFIER_META = 8

private const val KEY_CODE_BACKSPACE = 8
private const val KEY_CODE_TAB = 9
private const val KEY_CODE_ENTER = 13
private const val KEY_CODE_ESCAPE = 27
private const val KEY_CODE_PAGE_UP = 33
private const val KEY_CODE_PAGE_DOWN = 34
private const val KEY_CODE_END = 35
private const val KEY_CODE_HOME = 36
private const val KEY_CODE_LEFT = 37
private const val KEY_CODE_UP = 38
private const val KEY_CODE_RIGHT = 39
private const val KEY_CODE_DOWN = 40
private const val KEY_CODE_DELETE = 46
private const val KEY_CODE_A = 65
private const val KEY_CODE_D = 68
private const val KEY_CODE_K = 75
private const val KEY_CODE_L = 76
private const val KEY_CODE_Y = 89
private const val KEY_CODE_Z = 90

private const val GESTURE_TOUCH_DOWN = 1
private const val GESTURE_TOUCH_MOVE = 3
private const val GESTURE_TOUCH_UP = 5
private const val GESTURE_TOUCH_CANCEL = 6
private const val GESTURE_MOUSE_DOWN = 7
private const val GESTURE_MOUSE_MOVE = 8
private const val GESTURE_MOUSE_UP = 9
private const val GESTURE_MOUSE_WHEEL = 10
private const val GESTURE_MOUSE_RIGHT_DOWN = 11
private const val GESTURE_DIRECT_SCALE = 12
private const val GESTURE_DIRECT_SCROLL = 13
