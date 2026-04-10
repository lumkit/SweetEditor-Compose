package com.qiplat.compose.sweeteditor.core.document

import com.qiplat.compose.sweeteditor.core.EditorCoreTextPosition
import com.qiplat.compose.sweeteditor.core.EditorCoreTextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentStoreCommonTest {
    @Test
    fun lineAccessSupportsLfAndCrlf() {
        val store = DocumentStore("alpha\r\nbeta\ngamma")

        assertEquals(3, store.getLineCount())
        assertEquals("alpha", store.getLineText(0))
        assertEquals("beta", store.getLineText(1))
        assertEquals("gamma", store.getLineText(2))
    }

    @Test
    fun replaceUpdatesTextRangesAndVersion() {
        val store = DocumentStore("hello\nworld")

        val changeSet = store.replace(
            range = EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 5),
                end = EditorCoreTextPosition(1, 5),
            ),
            text = "\ncompose",
        )

        assertEquals(1L, store.version)
        assertEquals("hello\ncompose", store.getText())
        assertEquals("\nworld", changeSet.changes.first().deletedText)
        assertEquals("\ncompose", changeSet.changes.first().insertedText)
        assertEquals(EditorCoreTextPosition(0, 5), changeSet.changes.first().rangeAfter.start)
        assertEquals(EditorCoreTextPosition(1, 7), changeSet.changes.first().rangeAfter.end)
    }

    @Test
    fun positionAndOffsetMappingClampAtLineEnding() {
        val store = DocumentStore("ab\r\ncd")

        assertEquals(2, store.getOffsetForPosition(EditorCoreTextPosition(0, 2)))
        assertEquals(EditorCoreTextPosition(0, 2), store.getPositionForOffset(2))
        assertEquals(EditorCoreTextPosition(0, 2), store.getPositionForOffset(3))
        assertEquals(EditorCoreTextPosition(1, 0), store.getPositionForOffset(4))
    }
}
