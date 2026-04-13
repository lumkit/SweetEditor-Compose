package com.qiplat.compose.sweeteditor.core.state

import com.qiplat.compose.sweeteditor.core.EditorCoreTextPosition
import com.qiplat.compose.sweeteditor.core.EditorCoreTextRange
import com.qiplat.compose.sweeteditor.core.math.maxPosition
import com.qiplat.compose.sweeteditor.core.math.minPosition

data class SelectionState(
    val anchor: EditorCoreTextPosition = EditorCoreTextPosition.Zero,
    val cursor: EditorCoreTextPosition = EditorCoreTextPosition.Zero,
) {
    val range: EditorCoreTextRange?
        get() = if (anchor == cursor) {
            null
        } else {
            EditorCoreTextRange(
                start = minPosition(anchor, cursor),
                end = maxPosition(anchor, cursor),
            )
        }

    fun collapse(position: EditorCoreTextPosition): SelectionState =
        SelectionState(
            anchor = position,
            cursor = position,
        )

    fun moveTo(
        position: EditorCoreTextPosition,
        extendSelection: Boolean,
    ): SelectionState = if (extendSelection) {
        copy(cursor = position)
    } else {
        collapse(position)
    }

    fun select(range: EditorCoreTextRange): SelectionState =
        SelectionState(
            anchor = range.start,
            cursor = range.end,
        )
}
