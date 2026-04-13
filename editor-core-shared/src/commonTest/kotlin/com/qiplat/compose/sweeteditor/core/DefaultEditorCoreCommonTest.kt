package com.qiplat.compose.sweeteditor.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorCoreImplCommonTest {
    @Test
    fun editorCoreImplTracksUndoRedoAvailability() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("abc")

        editorCore.setDocument(document)
        editorCore.setCursorPosition(EditorCoreTextPosition(0, 1))
        editorCore.insertText("Z")

        assertTrue(editorCore.canUndo())
        editorCore.undo()
        assertEquals("abc", document.getLineText(0))
        assertTrue(editorCore.canRedo())
        editorCore.redo()
        assertEquals("aZbc", document.getLineText(0))
    }

    @Test
    fun editorCoreImplReturnsStructuredEditResultForInsert() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("abc")

        editorCore.setDocument(document)
        editorCore.setCursorPosition(EditorCoreTextPosition(0, 1))

        val result = editorCore.insertText("Z")

        assertTrue(result.changed)
        assertEquals(1, result.changes.size)
        assertEquals("Z", result.changes.first().text)
        assertEquals(EditorCoreTextPosition(0, 2), result.cursor)
        assertEquals("aZbc", document.getLineText(0))
    }

    @Test
    fun editorCoreImplBuildsBasicRenderModelAndScrollMetrics() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("alpha\nbeta\ngamma")

        editorCore.setDocument(document)
        editorCore.setViewport(width = 4, height = 15)
        editorCore.setCursorPosition(EditorCoreTextPosition(1, 2))

        val renderModel = editorCore.buildRenderModel()
        val scrollMetrics = editorCore.getScrollMetrics()

        requireNotNull(renderModel)
        assertEquals(2, renderModel.lines.size)
        assertEquals("beta", renderModel.lines[0].text)
        assertEquals(33f, scrollMetrics.maxScrollY)
        assertEquals(EditorCoreTextPosition(1, 2), editorCore.getCursorPosition())
    }

    @Test
    fun editorCoreImplMovesCursorHorizontallyAndVertically() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("alpha\nbe\ngamma")

        editorCore.setDocument(document)
        editorCore.setCursorPosition(EditorCoreTextPosition(0, 3))

        editorCore.moveCursorDown(extendSelection = false)
        assertEquals(EditorCoreTextPosition(1, 2), editorCore.getCursorPosition())

        editorCore.moveCursorRight(extendSelection = false)
        assertEquals(EditorCoreTextPosition(2, 0), editorCore.getCursorPosition())

        editorCore.moveCursorLeft(extendSelection = false)
        assertEquals(EditorCoreTextPosition(1, 2), editorCore.getCursorPosition())
    }

    @Test
    fun editorCoreImplExtendsSelectionWhenMovingCursor() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("alpha")

        editorCore.setDocument(document)
        editorCore.setCursorPosition(EditorCoreTextPosition(0, 2))

        editorCore.moveCursorRight(extendSelection = true)

        assertEquals(
            EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 2),
                end = EditorCoreTextPosition(0, 3),
            ),
            editorCore.getSelection(),
        )
    }

    @Test
    fun editorCoreImplKeepsSelectionAnchorWhileShrinkingSelection() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("alpha")

        editorCore.setDocument(document)
        editorCore.setCursorPosition(EditorCoreTextPosition(0, 2))

        editorCore.moveCursorRight(extendSelection = true)
        editorCore.moveCursorRight(extendSelection = true)
        editorCore.moveCursorLeft(extendSelection = true)

        assertEquals(
            EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 2),
                end = EditorCoreTextPosition(0, 3),
            ),
            editorCore.getSelection(),
        )
    }

    @Test
    fun editorCoreImplEnsuresCursorVisible() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("alpha\nbeta\ngamma\ndelta")

        editorCore.setDocument(document)
        editorCore.setViewport(width = 4, height = 10)
        editorCore.gotoPosition(line = 3, column = 5)

        val metrics = editorCore.getScrollMetrics()

        assertEquals(54f, metrics.scrollY)
    }

    @Test
    fun editorCoreImplResolvesPositionFromPoint() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("alpha\nbeta")

        editorCore.setDocument(document)

        val position = editorCore.getPositionForPoint(
            x = 2.6f,
            y = 17f,
        )

        assertEquals(EditorCoreTextPosition(1, 3), position)
    }

    @Test
    fun editorCoreImplResolvesWrappedCursorRect() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("abcdef")

        editorCore.setDocument(document)
        editorCore.setViewport(width = 3, height = 20)
        editorCore.setWrapMode(EditorCoreWrapMode.CharBreak)
        editorCore.setCursorPosition(EditorCoreTextPosition(0, 4))

        val rect = editorCore.getCursorRect()

        assertEquals(1f, rect.x)
        assertEquals(16f, rect.y)
    }

    @Test
    fun editorCoreImplBuildsRenderModelUsingUnifiedGeometryQuery() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("abcdef")

        editorCore.setDocument(document)
        editorCore.setViewport(width = 3, height = 20)
        editorCore.setWrapMode(EditorCoreWrapMode.CharBreak)
        editorCore.setSelection(
            EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 2),
                end = EditorCoreTextPosition(0, 5),
            ),
        )

        val renderModel = requireNotNull(editorCore.buildRenderModel())

        assertEquals(2, renderModel.selectionRects.size)
        assertEquals(2f, renderModel.cursorRect?.x)
        assertEquals(16f, renderModel.cursorRect?.y)
    }

    @Test
    fun editorCoreImplScrollToLineUsesWrappedVisualLineGeometry() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("abcdef\ng")

        editorCore.setDocument(document)
        editorCore.setViewport(width = 3, height = 10)
        editorCore.setWrapMode(EditorCoreWrapMode.CharBreak)
        editorCore.setCursorPosition(EditorCoreTextPosition(0, 4))

        editorCore.scrollToLine(
            line = 0,
            behavior = EditorCoreScrollBehavior.GoToTop,
        )

        assertEquals(16f, editorCore.getScrollMetrics().scrollY)
    }

    @Test
    fun editorCoreImplMoveCursorVerticallyKeepsPreferredWrapIndex() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("abcdef\nuvwxyz")

        editorCore.setDocument(document)
        editorCore.setViewport(width = 3, height = 30)
        editorCore.setWrapMode(EditorCoreWrapMode.CharBreak)
        editorCore.setCursorPosition(EditorCoreTextPosition(0, 4))

        editorCore.moveCursorDown(extendSelection = false)

        assertEquals(EditorCoreTextPosition(1, 4), editorCore.getCursorPosition())
    }

    @Test
    fun editorCoreImplResolvesWordRangeAndWordAtCursor() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("alpha beta_value")

        editorCore.setDocument(document)
        editorCore.setCursorPosition(EditorCoreTextPosition(0, 10))

        assertEquals(
            EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 6),
                end = EditorCoreTextPosition(0, 16),
            ),
            editorCore.getWordRangeAtCursor(),
        )
        assertEquals("beta_value", editorCore.getWordAtCursor())
    }

    @Test
    fun editorCoreImplCollapsesSelectionOnHorizontalMoveWithoutExtension() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("alpha")

        editorCore.setDocument(document)
        editorCore.setSelection(
            EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 1),
                end = EditorCoreTextPosition(0, 4),
            ),
        )

        editorCore.moveCursorLeft(extendSelection = false)
        assertEquals(EditorCoreTextPosition(0, 1), editorCore.getCursorPosition())
        assertEquals(null, editorCore.getSelection())

        editorCore.setSelection(
            EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 1),
                end = EditorCoreTextPosition(0, 4),
            ),
        )

        editorCore.moveCursorRight(extendSelection = false)
        assertEquals(EditorCoreTextPosition(0, 4), editorCore.getCursorPosition())
        assertEquals(null, editorCore.getSelection())
    }

    @Test
    fun editorCoreImplMovesCursorByWordBoundaries() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("alpha beta_value gamma")

        editorCore.setDocument(document)
        editorCore.setCursorPosition(EditorCoreTextPosition(0, 8))

        editorCore.moveCursorToPreviousWord(extendSelection = false)
        assertEquals(EditorCoreTextPosition(0, 6), editorCore.getCursorPosition())

        editorCore.moveCursorToNextWord(extendSelection = false)
        assertEquals(EditorCoreTextPosition(0, 17), editorCore.getCursorPosition())

        editorCore.moveCursorToNextWord(extendSelection = false)
        assertEquals(EditorCoreTextPosition(0, 22), editorCore.getCursorPosition())
    }

    @Test
    fun editorCoreImplExtendsSelectionWhenMovingByWord() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("alpha beta")

        editorCore.setDocument(document)
        editorCore.setCursorPosition(EditorCoreTextPosition(0, 2))

        editorCore.moveCursorToNextWord(extendSelection = true)

        assertEquals(
            EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 2),
                end = EditorCoreTextPosition(0, 6),
            ),
            editorCore.getSelection(),
        )
    }

    @Test
    fun editorCoreImplCollapsesSelectionOnWordMoveWithoutExtension() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("alpha beta")

        editorCore.setDocument(document)
        editorCore.setSelection(
            EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 1),
                end = EditorCoreTextPosition(0, 7),
            ),
        )

        editorCore.moveCursorToPreviousWord(extendSelection = false)
        assertEquals(EditorCoreTextPosition(0, 1), editorCore.getCursorPosition())
        assertEquals(null, editorCore.getSelection())

        editorCore.setSelection(
            EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 1),
                end = EditorCoreTextPosition(0, 7),
            ),
        )

        editorCore.moveCursorToNextWord(extendSelection = false)
        assertEquals(EditorCoreTextPosition(0, 7), editorCore.getCursorPosition())
        assertEquals(null, editorCore.getSelection())
    }

    @Test
    fun editorCoreImplHandlesKeyboardNavigationAndInsertion() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("alpha beta")

        editorCore.setDocument(document)
        editorCore.setViewport(width = 20, height = 20)
        editorCore.setCursorPosition(EditorCoreTextPosition(0, 2))

        val moveResult = editorCore.handleKeyEvent(
            keyCode = 39,
            text = null,
            modifiers = 2,
        )
        assertTrue(moveResult.handled)
        assertEquals(EditorCoreTextPosition(0, 6), editorCore.getCursorPosition())

        val insertResult = editorCore.handleKeyEvent(
            keyCode = 0,
            text = "!",
            modifiers = 0,
        )
        assertTrue(insertResult.handled)
        assertTrue(insertResult.editResult.changed)
        assertEquals("alpha !beta", document.getLineText(0))
    }

    @Test
    fun editorCoreImplHandlesKeyboardSelectionAndPageNavigation() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("line0\nline1\nline2\nline3")

        editorCore.setDocument(document)
        editorCore.setViewport(width = 20, height = 32)
        editorCore.setCursorPosition(EditorCoreTextPosition(0, 2))

        val selectionResult = editorCore.handleKeyEvent(
            keyCode = 40,
            text = null,
            modifiers = 1,
        )
        assertTrue(selectionResult.handled)
        assertEquals(
            EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 2),
                end = EditorCoreTextPosition(1, 2),
            ),
            editorCore.getSelection(),
        )

        val pageResult = editorCore.handleKeyEvent(
            keyCode = 34,
            text = null,
            modifiers = 0,
        )
        assertTrue(pageResult.handled)
        assertEquals(EditorCoreTextPosition(3, 2), editorCore.getCursorPosition())
    }

    @Test
    fun editorCoreImplDeletesAndInsertsLines() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("alpha\nbeta\ngamma")

        editorCore.setDocument(document)
        editorCore.setCursorPosition(EditorCoreTextPosition(1, 1))

        val deleteResult = editorCore.deleteLine()
        assertTrue(deleteResult.changed)
        assertEquals("alpha\ngamma", document.documentStore.getText())

        val insertBelowResult = editorCore.insertLineAbove()
        assertTrue(insertBelowResult.changed)
        assertEquals("alpha\n\ngamma", document.documentStore.getText())
    }

    @Test
    fun editorCoreImplCopiesAndMovesLines() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("alpha\nbeta\ngamma")

        editorCore.setDocument(document)
        editorCore.setCursorPosition(EditorCoreTextPosition(1, 0))

        val copyResult = editorCore.copyLineDown()
        assertTrue(copyResult.changed)
        assertEquals("alpha\nbeta\nbeta\ngamma", document.documentStore.getText())

        editorCore.setCursorPosition(EditorCoreTextPosition(3, 0))
        val moveResult = editorCore.moveLineUp()
        assertTrue(moveResult.changed)
        assertEquals("alpha\nbeta\ngamma\nbeta\n", document.documentStore.getText())
    }

    @Test
    fun editorCoreImplHandlesKeyboardLineCommands() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("alpha\nbeta\ngamma")

        editorCore.setDocument(document)
        editorCore.setCursorPosition(EditorCoreTextPosition(1, 0))

        val moveResult = editorCore.handleKeyEvent(
            keyCode = 40,
            text = null,
            modifiers = 4,
        )
        assertTrue(moveResult.handled)
        assertTrue(moveResult.editResult.changed)
        assertEquals("alpha\ngamma\nbeta\n", document.documentStore.getText())

        editorCore.setCursorPosition(EditorCoreTextPosition(1, 0))
        val copyResult = editorCore.handleKeyEvent(
            keyCode = 40,
            text = null,
            modifiers = 5,
        )
        assertTrue(copyResult.handled)
        assertTrue(copyResult.editResult.changed)
        assertEquals("alpha\ngamma\ngamma\nbeta\n", document.documentStore.getText())

        editorCore.setCursorPosition(EditorCoreTextPosition(1, 0))
        val deleteResult = editorCore.handleKeyEvent(
            keyCode = 75,
            text = null,
            modifiers = 3,
        )
        assertTrue(deleteResult.handled)
        assertTrue(deleteResult.editResult.changed)
        assertEquals("alpha\ngamma\nbeta\n", document.documentStore.getText())
    }

    @Test
    fun editorCoreImplSelectsCurrentLineAndDuplicatesSelection() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("alpha\nbeta")

        editorCore.setDocument(document)
        editorCore.setCursorPosition(EditorCoreTextPosition(0, 2))

        editorCore.selectCurrentLine()
        assertEquals(
            EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 0),
                end = EditorCoreTextPosition(1, 0),
            ),
            editorCore.getSelection(),
        )

        val duplicateResult = editorCore.duplicateSelectionOrLine()
        assertTrue(duplicateResult.changed)
        assertEquals("alpha\nalpha\nbeta", document.documentStore.getText())
        assertEquals(
            EditorCoreTextRange(
                start = EditorCoreTextPosition(1, 0),
                end = EditorCoreTextPosition(2, 0),
            ),
            editorCore.getSelection(),
        )
    }

    @Test
    fun editorCoreImplHandlesKeyboardSelectionCommands() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("alpha\nbeta")

        editorCore.setDocument(document)
        editorCore.setCursorPosition(EditorCoreTextPosition(0, 1))

        val selectLineResult = editorCore.handleKeyEvent(
            keyCode = 76,
            text = null,
            modifiers = 2,
        )
        assertTrue(selectLineResult.handled)
        assertEquals(
            EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 0),
                end = EditorCoreTextPosition(1, 0),
            ),
            editorCore.getSelection(),
        )

        val duplicateResult = editorCore.handleKeyEvent(
            keyCode = 68,
            text = null,
            modifiers = 2,
        )
        assertTrue(duplicateResult.handled)
        assertTrue(duplicateResult.editResult.changed)
        assertEquals("alpha\nalpha\nbeta", document.documentStore.getText())
    }

    @Test
    fun editorCoreImplHandlesBasicGestureSelectionAndScroll() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("alpha\nbeta\ngamma\ndelta")

        editorCore.setDocument(document)
        editorCore.setViewport(width = 40, height = 48)

        val downResult = editorCore.handleGesture(
            type = 7,
            points = floatArrayOf(2f, 20f),
            modifiers = 0,
        )
        assertTrue(downResult.handled)
        assertEquals(EditorCoreTextPosition(1, 2), editorCore.getCursorPosition())

        val shiftDownResult = editorCore.handleGesture(
            type = 7,
            points = floatArrayOf(1f, 36f),
            modifiers = 1,
        )
        assertTrue(shiftDownResult.handled)
        assertEquals(
            EditorCoreTextRange(
                start = EditorCoreTextPosition(1, 2),
                end = EditorCoreTextPosition(2, 1),
            ),
            editorCore.getSelection(),
        )

        editorCore.handleGesture(
            type = 7,
            points = floatArrayOf(0f, 0f),
            modifiers = 0,
        )
        val dragResult = editorCore.handleGesture(
            type = 8,
            points = floatArrayOf(3f, 20f),
            modifiers = 0,
        )
        assertTrue(dragResult.handled)
        assertEquals(
            EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 0),
                end = EditorCoreTextPosition(1, 3),
            ),
            editorCore.getSelection(),
        )

        val wheelResult = editorCore.handleGesture(
            type = 10,
            points = floatArrayOf(),
            modifiers = 0,
            wheelDeltaY = 8f,
        )
        assertTrue(wheelResult.handled)
        assertEquals(8f, editorCore.getScrollMetrics().scrollY)
    }

    @Test
    fun editorCoreImplSupportsLinkedEditingLifecycleAndTabNavigation() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("foo bar")

        editorCore.setDocument(document)
        editorCore.startLinkedEditing(
            EditorCoreLinkedEditingModel(
                groups = listOf(
                    EditorCoreTabStopGroup(
                        index = 0,
                        ranges = listOf(
                            EditorCoreTextRange(
                                start = EditorCoreTextPosition(0, 0),
                                end = EditorCoreTextPosition(0, 3),
                            ),
                        ),
                    ),
                    EditorCoreTabStopGroup(
                        index = 1,
                        ranges = listOf(
                            EditorCoreTextRange(
                                start = EditorCoreTextPosition(0, 4),
                                end = EditorCoreTextPosition(0, 7),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(editorCore.isInLinkedEditing())
        assertEquals(
            EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 0),
                end = EditorCoreTextPosition(0, 3),
            ),
            editorCore.getSelection(),
        )

        val replaceResult = editorCore.replaceText(
            range = requireNotNull(editorCore.getSelection()),
            text = "hello",
        )
        assertTrue(replaceResult.changed)
        assertEquals("hello bar", document.documentStore.getText())

        val tabResult = editorCore.handleKeyEvent(
            keyCode = 9,
            text = null,
            modifiers = 0,
        )
        assertTrue(tabResult.handled)
        assertEquals(
            EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 6),
                end = EditorCoreTextPosition(0, 9),
            ),
            editorCore.getSelection(),
        )

        assertTrue(editorCore.linkedEditingNext())
        assertFalse(editorCore.isInLinkedEditing())
    }

    @Test
    fun editorCoreImplInsertsSnippetAndAdvancesThroughTabStops() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("")

        editorCore.setDocument(document)

        val snippetResult = editorCore.insertSnippet("fun \${1:name}(\${2:value}) {\n\t\$0\n}")

        assertTrue(snippetResult.changed)
        assertEquals("fun name(value) {\n\t\n}", document.documentStore.getText())
        assertTrue(editorCore.isInLinkedEditing())
        assertEquals(
            EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 4),
                end = EditorCoreTextPosition(0, 8),
            ),
            editorCore.getSelection(),
        )

        assertTrue(editorCore.linkedEditingNext())
        assertEquals(
            EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 9),
                end = EditorCoreTextPosition(0, 14),
            ),
            editorCore.getSelection(),
        )

        assertTrue(editorCore.linkedEditingNext())
        assertFalse(editorCore.isInLinkedEditing())
        assertEquals(EditorCoreTextPosition(1, 1), editorCore.getCursorPosition())
    }

    @Test
    fun editorCoreImplMirrorsEditsAcrossLinkedEditingGroup() {
        val editorCore = EditorCoreFactoryImpl().create(FakeEditorCoreTextMeasurer())
        val document = EditorDocuments.fromText("")

        editorCore.setDocument(document)

        val snippetResult = editorCore.insertSnippet("const \${1:name} = \${1:name}")
        assertTrue(snippetResult.changed)
        assertEquals("const name = name", document.documentStore.getText())

        val replaceResult = editorCore.replaceText(
            range = requireNotNull(editorCore.getSelection()),
            text = "value",
        )

        assertTrue(replaceResult.changed)
        assertEquals("const value = value", document.documentStore.getText())
        assertTrue(editorCore.isInLinkedEditing())
    }
}

private class FakeEditorCoreTextMeasurer : EditorCoreTextMeasurer {
    override fun measureTextWidth(text: String, fontStyle: Int): Float = text.length.toFloat()

    override fun measureInlayHintWidth(text: String): Float = text.length.toFloat()

    override fun measureIconWidth(iconId: Int): Float = iconId.toFloat()

    override fun getFontMetrics(): FloatArray = floatArrayOf(16f, 12f, 4f, 0f)
}
