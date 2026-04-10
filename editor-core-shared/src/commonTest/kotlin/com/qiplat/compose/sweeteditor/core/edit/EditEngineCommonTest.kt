package com.qiplat.compose.sweeteditor.core.edit

import com.qiplat.compose.sweeteditor.core.EditorCoreTextPosition
import com.qiplat.compose.sweeteditor.core.EditorCoreTextRange
import com.qiplat.compose.sweeteditor.core.document.DocumentStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditEngineCommonTest {
    @Test
    fun insertTextAtCursorUpdatesDocumentAndCursor() {
        val store = DocumentStore("abc")
        val engine = EditEngine(store)

        val result = engine.execute(
            command = EditCommand.InsertText("XYZ"),
            session = EditSession(
                cursor = EditorCoreTextPosition(0, 1),
            ),
        )

        assertTrue(result.changed)
        assertEquals("aXYZbc", store.getText())
        assertEquals(EditorCoreTextPosition(0, 4), result.cursor)
    }

    @Test
    fun replaceSelectionUsesSelectionRange() {
        val store = DocumentStore("kotlin")
        val engine = EditEngine(store)

        val result = engine.execute(
            command = EditCommand.InsertText("swift"),
            session = EditSession(
                cursor = EditorCoreTextPosition(0, 6),
                selection = EditorCoreTextRange(
                    start = EditorCoreTextPosition(0, 0),
                    end = EditorCoreTextPosition(0, 6),
                ),
            ),
        )

        assertTrue(result.changed)
        assertEquals("swift", store.getText())
        assertEquals(EditorCoreTextPosition(0, 5), result.cursor)
        assertEquals(null, result.selection)
    }

    @Test
    fun backspaceDeletesPreviousCharacterWhenSelectionIsEmpty() {
        val store = DocumentStore("abcd")
        val engine = EditEngine(store)

        val result = engine.execute(
            command = EditCommand.Backspace,
            session = EditSession(
                cursor = EditorCoreTextPosition(0, 2),
            ),
        )

        assertTrue(result.changed)
        assertEquals("acd", store.getText())
        assertEquals(EditorCoreTextPosition(0, 1), result.cursor)
    }

    @Test
    fun deleteForwardRemovesSelectedRangeFirst() {
        val store = DocumentStore("compose")
        val engine = EditEngine(store)

        val result = engine.execute(
            command = EditCommand.DeleteForward,
            session = EditSession(
                cursor = EditorCoreTextPosition(0, 1),
                selection = EditorCoreTextRange(
                    start = EditorCoreTextPosition(0, 1),
                    end = EditorCoreTextPosition(0, 4),
                ),
            ),
        )

        assertTrue(result.changed)
        assertEquals("cose", store.getText())
        assertEquals(EditorCoreTextPosition(0, 1), result.cursor)
    }

    @Test
    fun deleteForwardAtDocumentEndReturnsUnchangedResult() {
        val store = DocumentStore("xy")
        val engine = EditEngine(store)

        val result = engine.execute(
            command = EditCommand.DeleteForward,
            session = EditSession(
                cursor = EditorCoreTextPosition(0, 2),
            ),
        )

        assertFalse(result.changed)
        assertEquals("xy", store.getText())
        assertEquals(EditorCoreTextPosition(0, 2), result.cursor)
    }

    @Test
    fun undoAndRedoRestoreDocumentState() {
        val store = DocumentStore("abc")
        val engine = EditEngine(store)

        val insertResult = engine.execute(
            command = EditCommand.InsertText("Z"),
            session = EditSession(
                cursor = EditorCoreTextPosition(0, 1),
            ),
        )

        assertTrue(engine.canUndo())
        assertEquals("aZbc", store.getText())

        val undoResult = engine.execute(
            command = EditCommand.Undo,
            session = EditSession(
                cursor = insertResult.cursor,
            ),
        )

        assertTrue(undoResult.changed)
        assertEquals("abc", store.getText())
        assertTrue(engine.canRedo())

        val redoResult = engine.execute(
            command = EditCommand.Redo,
            session = EditSession(
                cursor = undoResult.cursor,
            ),
        )

        assertTrue(redoResult.changed)
        assertEquals("aZbc", store.getText())
        assertEquals(EditorCoreTextPosition(0, 2), redoResult.cursor)
    }
}
