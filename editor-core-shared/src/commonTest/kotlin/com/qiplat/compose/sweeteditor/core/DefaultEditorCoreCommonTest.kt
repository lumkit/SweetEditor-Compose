package com.qiplat.compose.sweeteditor.core

import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals("alpha", renderModel.lines[0].text)
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
}

private class FakeEditorCoreTextMeasurer : EditorCoreTextMeasurer {
    override fun measureTextWidth(text: String, fontStyle: Int): Float = text.length.toFloat()

    override fun measureInlayHintWidth(text: String): Float = text.length.toFloat()

    override fun measureIconWidth(iconId: Int): Float = iconId.toFloat()

    override fun getFontMetrics(): FloatArray = floatArrayOf(16f, 12f, 4f, 0f)
}
