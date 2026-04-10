package com.qiplat.compose.sweeteditor.core

import com.qiplat.compose.sweeteditor.core.document.DocumentStore
import com.qiplat.compose.sweeteditor.core.edit.EditExecutionResult
import com.qiplat.compose.sweeteditor.core.edit.EditEngine
import com.qiplat.compose.sweeteditor.core.layout.LayoutEngine
import com.qiplat.compose.sweeteditor.core.layout.LayoutSettings
import com.qiplat.compose.sweeteditor.core.render.RenderModelBuildInput
import com.qiplat.compose.sweeteditor.core.render.RenderModelBuilder

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

    private val layoutEngine = LayoutEngine(textMeasurer)
    private val renderModelBuilder = RenderModelBuilder()
    private var document: EditorDocument? = null
    private var editEngine: EditEngine? = null
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private var scrollX: Float = 0f
    private var scrollY: Float = 0f
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
    private var cursor: EditorCoreTextPosition = EditorCoreTextPosition.Zero
    private var selection: EditorCoreTextRange? = null
    private var verticalCursorAnchor: VerticalCursorAnchor? = null

    override fun release() {
        document = null
        editEngine = null
        selection = null
        composing = false
        scrollX = 0f
        scrollY = 0f
        verticalCursorAnchor = null
    }

    override fun setDocument(document: EditorCoreDocument?) {
        require(document == null || document is EditorDocument)
        this.document = document
        editEngine = document?.let { EditEngine(it.documentStore) }
        cursor = EditorCoreTextPosition.Zero
        selection = null
        composing = false
        scrollX = 0f
        scrollY = 0f
        verticalCursorAnchor = null
    }

    override fun setViewport(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
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
        cursor = position.coerceWithinDocument()
        selection = null
        verticalCursorAnchor = null
    }

    override fun setSelection(range: EditorCoreTextRange) {
        val clampedRange = range.coerceWithinDocument()
        selection = clampedRange
        cursor = clampedRange.end
        verticalCursorAnchor = null
    }

    override fun getCursorPosition(): EditorCoreTextPosition = cursor

    override fun getSelection(): EditorCoreTextRange? = selection

    override fun buildRenderModel(): EditorCoreRenderModel? {
        val activeStore = document?.documentStore ?: return null
        val layoutSnapshot = buildLayoutSnapshot(activeStore)
        val cursorRect = getCursorRect()
        val selectionRects = layoutEngine.buildSelectionRects(
            documentStore = activeStore,
            range = selection,
            settings = currentLayoutSettings(),
        )
        return renderModelBuilder.build(
            RenderModelBuildInput(
                layoutSnapshot = layoutSnapshot,
                cursorRect = cursorRect,
                selectionRects = selectionRects,
            ),
        )
    }

    override fun getScrollMetrics(): EditorCoreScrollMetrics {
        val activeStore = document?.documentStore ?: return EditorCoreScrollMetrics()
        val layoutSnapshot = buildLayoutSnapshot(activeStore)
        return EditorCoreScrollMetrics(
            scrollX = scrollX,
            scrollY = scrollY,
            maxScrollX = (layoutSnapshot.contentWidth - viewportWidth).coerceAtLeast(0f),
            maxScrollY = (layoutSnapshot.contentHeight - viewportHeight).coerceAtLeast(0f),
        )
    }

    override fun handleGesture(
        type: Int,
        points: FloatArray,
        modifiers: Int,
        wheelDeltaX: Float,
        wheelDeltaY: Float,
        directScale: Float,
    ): EditorCoreGestureResult = EditorCoreGestureResult()

    override fun tickAnimations(): EditorCoreGestureResult = EditorCoreGestureResult()

    override fun handleKeyEvent(
        keyCode: Int,
        text: String?,
        modifiers: Int,
    ): EditorCoreKeyEventResult = EditorCoreKeyEventResult()

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

    override fun insertSnippet(template: String): EditorCoreTextEditResult = insertText(template)

    override fun startLinkedEditing(model: EditorCoreLinkedEditingModel) = Unit

    override fun isInLinkedEditing(): Boolean = false

    override fun linkedEditingNext(): Boolean = false

    override fun linkedEditingPrev(): Boolean = false

    override fun cancelLinkedEditing() = Unit

    override fun moveLineUp(): EditorCoreTextEditResult = EditorCoreTextEditResult.Unchanged

    override fun moveLineDown(): EditorCoreTextEditResult = EditorCoreTextEditResult.Unchanged

    override fun copyLineUp(): EditorCoreTextEditResult = EditorCoreTextEditResult.Unchanged

    override fun copyLineDown(): EditorCoreTextEditResult = EditorCoreTextEditResult.Unchanged

    override fun deleteLine(): EditorCoreTextEditResult = EditorCoreTextEditResult.Unchanged

    override fun insertLineAbove(): EditorCoreTextEditResult = EditorCoreTextEditResult.Unchanged

    override fun insertLineBelow(): EditorCoreTextEditResult = EditorCoreTextEditResult.Unchanged

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
        selection = EditorCoreTextRange(
            start = EditorCoreTextPosition.Zero,
            end = end,
        )
        cursor = end
    }

    override fun getSelectedText(): String? {
        val activeSelection = selection ?: return null
        val activeStore = document?.documentStore ?: return null
        val start = activeStore.getOffsetForPosition(activeSelection.start)
        val end = activeStore.getOffsetForPosition(activeSelection.end)
        return activeStore.getText().substring(start, end)
    }

    override fun getWordRangeAtCursor(): EditorCoreTextRange =
        EditorCoreTextRange(
            start = cursor,
            end = cursor,
        )

    override fun getWordAtCursor(): String? = null

    override fun moveCursorLeft(extendSelection: Boolean) {
        val activeStore = document?.documentStore ?: return
        val currentOffset = activeStore.getOffsetForPosition(cursor)
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
        val currentOffset = activeStore.getOffsetForPosition(cursor)
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
        val nextLine = (cursor.line - 1).coerceAtLeast(0)
        moveCursorVertically(
            targetLine = nextLine,
            extendSelection = extendSelection,
        )
    }

    override fun moveCursorDown(extendSelection: Boolean) {
        val activeStore = document?.documentStore ?: return
        val nextLine = (cursor.line + 1).coerceAtMost(activeStore.getLineCount() - 1)
        moveCursorVertically(
            targetLine = nextLine,
            extendSelection = extendSelection,
        )
    }

    override fun moveCursorToLineStart(extendSelection: Boolean) {
        updateCursorMovement(
            nextPosition = EditorCoreTextPosition(
                line = cursor.line,
                column = 0,
            ),
            extendSelection = extendSelection,
        )
    }

    override fun moveCursorToLineEnd(extendSelection: Boolean) {
        val activeStore = document?.documentStore ?: return
        updateCursorMovement(
            nextPosition = EditorCoreTextPosition(
                line = cursor.line,
                column = activeStore.getLineText(cursor.line).length,
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
        val lineHeight = buildLayoutSnapshot(activeStore).lineHeight
        val targetTop = targetLine * lineHeight
        val nextScrollY = when (behavior) {
            EditorCoreScrollBehavior.GoToTop -> targetTop
            EditorCoreScrollBehavior.GoToCenter -> targetTop - viewportHeight / 2f
            EditorCoreScrollBehavior.GoToBottom -> targetTop - viewportHeight + lineHeight
        }
        setScroll(scrollX, nextScrollY)
    }

    override fun gotoPosition(line: Int, column: Int) {
        cursor = EditorCoreTextPosition(
            line = line,
            column = column,
        ).coerceWithinDocument()
        selection = null
        verticalCursorAnchor = null
    }

    override fun setScroll(scrollX: Float, scrollY: Float) {
        val metrics = getScrollMetrics()
        this.scrollX = scrollX.coerceIn(0f, metrics.maxScrollX)
        this.scrollY = scrollY.coerceIn(0f, metrics.maxScrollY)
    }

    override fun getPositionRect(line: Int, column: Int): EditorCoreCursorRect =
        document?.documentStore
            ?.let { activeStore ->
                val rect = layoutEngine.measurePositionRect(
                    documentStore = activeStore,
                    position = EditorCoreTextPosition(line, column),
                    settings = currentLayoutSettings(),
                )
                EditorCoreCursorRect(
                    x = rect.x,
                    y = rect.y,
                    height = rect.height,
                )
            }
            ?: EditorCoreCursorRect()

    override fun getCursorRect(): EditorCoreCursorRect =
        getPositionRect(
            line = cursor.line,
            column = cursor.column,
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
            cursor = cursor,
            selection = selection,
        )

    private inline fun executeEdit(
        block: (
            EditEngine,
            com.qiplat.compose.sweeteditor.core.edit.EditSession,
        ) -> EditExecutionResult,
    ): EditorCoreTextEditResult {
        val engine = editEngine ?: return EditorCoreTextEditResult.Unchanged
        val result = block(engine, currentSession())
        cursor = result.cursor
        selection = result.selection
        verticalCursorAnchor = null
        return result.toEditorCoreTextEditResult()
    }

    private fun buildLayoutSnapshot(documentStore: DocumentStore) =
        layoutEngine.layout(
            documentStore = documentStore,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            scrollY = scrollY,
            settings = currentLayoutSettings(),
        )

    private fun currentLayoutSettings() =
        LayoutSettings(
            wrapMode = wrapMode,
            tabSize = tabSize,
            lineSpacingExtra = lineSpacingExtra,
            lineSpacingMultiplier = lineSpacingMultiplier,
        )

    private fun moveCursorVertically(
        targetLine: Int,
        extendSelection: Boolean,
    ) {
        val activeStore = document?.documentStore ?: return
        val anchor = verticalCursorAnchor ?: VerticalCursorAnchor(
            x = getCursorRect().x,
        ).also { verticalCursorAnchor = it }
        val hitTest = layoutEngine.hitTest(
            documentStore = activeStore,
            x = anchor.x,
            y = targetLine * resolveLineHeight(),
            settings = currentLayoutSettings(),
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
        val previousCursor = cursor
        if (!extendSelection) {
            cursor = clampedPosition
            selection = null
            if (clampedPosition.line != previousCursor.line) {
                verticalCursorAnchor = null
            }
            return
        }
        val baseSelectionStart = selection?.start ?: cursor
        cursor = clampedPosition
        selection = EditorCoreTextRange(
            start = minPosition(baseSelectionStart, clampedPosition),
            end = maxPosition(baseSelectionStart, clampedPosition),
        )
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

private operator fun EditorCoreTextPosition.compareTo(other: EditorCoreTextPosition): Int = when {
    line != other.line -> line.compareTo(other.line)
    else -> column.compareTo(other.column)
}

private fun minPosition(
    left: EditorCoreTextPosition,
    right: EditorCoreTextPosition,
): EditorCoreTextPosition = if (left <= right) left else right

private fun maxPosition(
    left: EditorCoreTextPosition,
    right: EditorCoreTextPosition,
): EditorCoreTextPosition = if (left >= right) left else right
