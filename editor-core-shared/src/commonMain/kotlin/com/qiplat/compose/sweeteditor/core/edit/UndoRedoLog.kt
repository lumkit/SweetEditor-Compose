package com.qiplat.compose.sweeteditor.core.edit

import com.qiplat.compose.sweeteditor.core.document.TextChange
import com.qiplat.compose.sweeteditor.core.document.TextChangeSet

class UndoRedoLog(
    private val maxSize: Int = 512,
) {
    private val undoStack = ArrayDeque<UndoRedoEntry>()
    private val redoStack = ArrayDeque<UndoRedoEntry>()

    fun record(changeSet: TextChangeSet) {
        if (changeSet.changes.isEmpty()) {
            return
        }
        undoStack.addLast(
            UndoRedoEntry(
                forward = changeSet,
                backward = changeSet.invert(),
            ),
        )
        trimUndoStack()
        redoStack.clear()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo(): TextChangeSet? {
        val entry = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(entry)
        return entry.backward
    }

    fun redo(): TextChangeSet? {
        val entry = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(entry)
        return entry.forward
    }

    private fun trimUndoStack() {
        while (undoStack.size > maxSize) {
            undoStack.removeFirst()
        }
    }
}

private data class UndoRedoEntry(
    val forward: TextChangeSet,
    val backward: TextChangeSet,
)

internal fun TextChangeSet.invert(): TextChangeSet =
    TextChangeSet(
        beforeVersion = afterVersion,
        afterVersion = beforeVersion,
        changes = changes
            .asReversed()
            .map { change ->
                TextChange(
                    rangeBefore = change.rangeAfter,
                    rangeAfter = change.rangeBefore,
                    insertedText = change.deletedText,
                    deletedText = change.insertedText,
                )
            },
    )
