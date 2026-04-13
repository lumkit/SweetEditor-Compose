package com.qiplat.compose.sweeteditor.core.state

import com.qiplat.compose.sweeteditor.core.EditorCoreTextPosition
import com.qiplat.compose.sweeteditor.core.EditorCoreTextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelectionStateCommonTest {
    @Test
    fun moveToWithoutSelectionCollapsesAnchorAndCursor() {
        val state = SelectionState(
            anchor = EditorCoreTextPosition(0, 2),
            cursor = EditorCoreTextPosition(0, 4),
        )

        val nextState = state.moveTo(
            position = EditorCoreTextPosition(1, 1),
            extendSelection = false,
        )

        assertEquals(EditorCoreTextPosition(1, 1), nextState.anchor)
        assertEquals(EditorCoreTextPosition(1, 1), nextState.cursor)
        assertNull(nextState.range)
    }

    @Test
    fun moveToWithSelectionKeepsAnchor() {
        val state = SelectionState(
            anchor = EditorCoreTextPosition(0, 2),
            cursor = EditorCoreTextPosition(0, 2),
        )

        val nextState = state.moveTo(
            position = EditorCoreTextPosition(0, 4),
            extendSelection = true,
        )

        assertEquals(
            EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 2),
                end = EditorCoreTextPosition(0, 4),
            ),
            nextState.range,
        )
    }
}
