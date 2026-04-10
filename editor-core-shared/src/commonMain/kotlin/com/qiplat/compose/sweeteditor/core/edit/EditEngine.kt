package com.qiplat.compose.sweeteditor.core.edit

import com.qiplat.compose.sweeteditor.core.EditorCoreTextPosition
import com.qiplat.compose.sweeteditor.core.EditorCoreTextRange
import com.qiplat.compose.sweeteditor.core.document.DocumentSnapshot
import com.qiplat.compose.sweeteditor.core.document.DocumentStore
import com.qiplat.compose.sweeteditor.core.document.TextChangeSet

sealed interface EditCommand {
    data class InsertText(
        val text: String,
    ) : EditCommand

    data class ReplaceRange(
        val range: EditorCoreTextRange,
        val text: String,
    ) : EditCommand

    data class DeleteRange(
        val range: EditorCoreTextRange,
    ) : EditCommand

    data object Backspace : EditCommand

    data object DeleteForward : EditCommand

    data object Undo : EditCommand

    data object Redo : EditCommand
}

data class EditSession(
    val cursor: EditorCoreTextPosition = EditorCoreTextPosition.Zero,
    val selection: EditorCoreTextRange? = null,
)

data class EditExecutionResult(
    val changed: Boolean,
    val changeSet: TextChangeSet?,
    val cursor: EditorCoreTextPosition,
    val selection: EditorCoreTextRange?,
    val snapshot: DocumentSnapshot,
)

class EditEngine(
    private val documentStore: DocumentStore,
    private val undoRedoLog: UndoRedoLog = UndoRedoLog(),
) {
    fun canUndo(): Boolean = undoRedoLog.canUndo()

    fun canRedo(): Boolean = undoRedoLog.canRedo()

    fun execute(
        command: EditCommand,
        session: EditSession,
    ): EditExecutionResult = when (command) {
        is EditCommand.InsertText -> insertText(command.text, session)
        is EditCommand.ReplaceRange -> replaceRange(command.range, command.text)
        is EditCommand.DeleteRange -> deleteRange(command.range)
        EditCommand.Backspace -> backspace(session)
        EditCommand.DeleteForward -> deleteForward(session)
        EditCommand.Undo -> undo(session)
        EditCommand.Redo -> redo(session)
    }

    private fun insertText(
        text: String,
        session: EditSession,
    ): EditExecutionResult {
        val targetRange = session.effectiveSelection()
            ?: EditorCoreTextRange(
                start = session.cursor,
                end = session.cursor,
            )
        return replaceRange(targetRange, text)
    }

    private fun replaceRange(
        range: EditorCoreTextRange,
        text: String,
    ): EditExecutionResult {
        val changeSet = documentStore.replace(range, text)
        undoRedoLog.record(changeSet)
        return buildChangedResult(changeSet)
    }

    private fun deleteRange(range: EditorCoreTextRange): EditExecutionResult =
        replaceRange(range, "")

    private fun undo(session: EditSession): EditExecutionResult {
        val changeSet = undoRedoLog.undo() ?: return unchanged(session.cursor)
        documentStore.apply(changeSet)
        return buildChangedResult(changeSet)
    }

    private fun redo(session: EditSession): EditExecutionResult {
        val changeSet = undoRedoLog.redo() ?: return unchanged(session.cursor)
        documentStore.apply(changeSet)
        return buildChangedResult(changeSet)
    }

    private fun buildChangedResult(changeSet: TextChangeSet): EditExecutionResult {
        val cursorAfter = changeSet.changes.last().rangeAfter.end
        return EditExecutionResult(
            changed = changeSet.changes.any { it.insertedText != it.deletedText },
            changeSet = changeSet,
            cursor = cursorAfter,
            selection = null,
            snapshot = documentStore.snapshot(),
        )
    }

    private fun backspace(session: EditSession): EditExecutionResult {
        session.effectiveSelection()?.let { selection ->
            return deleteRange(selection)
        }
        val cursorOffset = documentStore.getOffsetForPosition(session.cursor)
        if (cursorOffset == 0) {
            return unchanged(session.cursor)
        }
        val start = documentStore.getPositionForOffset(cursorOffset - 1)
        val end = documentStore.getPositionForOffset(cursorOffset)
        return deleteRange(
            EditorCoreTextRange(
                start = start,
                end = end,
            ),
        )
    }

    private fun deleteForward(session: EditSession): EditExecutionResult {
        session.effectiveSelection()?.let { selection ->
            return deleteRange(selection)
        }
        val cursorOffset = documentStore.getOffsetForPosition(session.cursor)
        if (cursorOffset >= documentStore.getText().length) {
            return unchanged(session.cursor)
        }
        val start = documentStore.getPositionForOffset(cursorOffset)
        val end = documentStore.getPositionForOffset(cursorOffset + 1)
        return deleteRange(
            EditorCoreTextRange(
                start = start,
                end = end,
            ),
        )
    }

    private fun unchanged(cursor: EditorCoreTextPosition): EditExecutionResult =
        EditExecutionResult(
            changed = false,
            changeSet = null,
            cursor = cursor,
            selection = null,
            snapshot = documentStore.snapshot(),
        )
}

private fun EditSession.effectiveSelection(): EditorCoreTextRange? =
    selection?.takeUnless { it.start == it.end }
