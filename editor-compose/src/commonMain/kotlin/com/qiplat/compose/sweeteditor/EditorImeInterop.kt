package com.qiplat.compose.sweeteditor

import androidx.compose.ui.text.input.*
import com.qiplat.compose.sweeteditor.model.foundation.TextPosition
import com.qiplat.compose.sweeteditor.model.foundation.TextRange
import com.qiplat.compose.sweeteditor.model.visual.EditorRenderModel
import com.qiplat.compose.sweeteditor.model.visual.VisualLine
import com.qiplat.compose.sweeteditor.model.visual.VisualRunType
import com.qiplat.compose.sweeteditor.runtime.EditorController
import androidx.compose.ui.text.TextRange as ComposeTextRange

internal fun EditorController.handleImeEditCommands(
    commands: List<EditCommand>,
    previousValue: TextFieldValue,
    newValue: TextFieldValue,
): TextFieldValue {
    var shouldClearProxyValue = false
    val composingText = newValue.extractComposingText()
    commands.forEach { command ->
        when (command) {
            is SetComposingTextCommand,
            is SetComposingRegionCommand,
            -> {
                if (!isComposing()) {
                    compositionStart()
                }
                compositionUpdate(composingText.orEmpty())
            }

            is FinishComposingTextCommand -> {
                compositionEnd(null)
                shouldClearProxyValue = true
            }

            is CommitTextCommand -> {
                val committedText = command.text
                if (isComposing()) {
                    compositionEnd(committedText)
                } else if (committedText.isNotEmpty()) {
                    insertText(committedText)
                }
                shouldClearProxyValue = true
            }

            is BackspaceCommand -> {
                if (newValue.composition != null) {
                    if (!isComposing()) {
                        compositionStart()
                    }
                    if (!composingText.isNullOrEmpty()) {
                        compositionUpdate(composingText)
                    } else {
                        compositionCancel()
                        shouldClearProxyValue = true
                    }
                } else {
                    backspace()
                    shouldClearProxyValue = true
                }
            }

            is DeleteSurroundingTextCommand,
            is DeleteSurroundingTextInCodePointsCommand,
            is DeleteAllCommand,
            -> {
                if (newValue.composition != null) {
                    if (!isComposing()) {
                        compositionStart()
                    }
                    compositionUpdate(composingText.orEmpty())
                } else {
                    backspace()
                    shouldClearProxyValue = true
                }
            }
        }
    }

    return if (newValue.composition != null) {
        val normalizedSelection = newValue.selection.normalized(newValue.text.length)
        val normalizedComposition = newValue.composition?.normalized(newValue.text.length)
        newValue.copy(
            selection = normalizedSelection,
            composition = normalizedComposition,
        )
    } else if (shouldClearProxyValue || previousValue.text != newValue.text) {
        synchronizeImeProxyValue(TextFieldValue())
    } else {
        newValue
    }
}

private fun TextFieldValue.extractComposingText(): String? {
    val range = composition?.normalized(text.length) ?: return null
    if (range.collapsed) {
        return ""
    }
    return text.substring(range.start, range.end)
}

private fun ComposeTextRange.normalized(textLength: Int): ComposeTextRange {
    val boundedStart = start.coerceIn(0, textLength)
    val boundedEnd = end.coerceIn(0, textLength)
    return ComposeTextRange(
        start = minOf(boundedStart, boundedEnd),
        end = maxOf(boundedStart, boundedEnd),
    )
}

internal fun EditorController.handleImeAction(
    action: ImeAction,
    currentValue: TextFieldValue,
): TextFieldValue {
    when (action) {
        ImeAction.Done,
        ImeAction.Go,
        ImeAction.Search,
        ImeAction.Send,
        ImeAction.Next,
        ImeAction.Previous,
        -> {
            if (isComposing()) {
                compositionEnd(null)
            }
            return TextFieldValue()
        }

        ImeAction.Default,
        ImeAction.None,
        -> return synchronizeImeProxyValue(currentValue)
    }
    return synchronizeImeProxyValue(currentValue)
}

internal fun EditorController.applyImeProxyValueChange(
    previousValue: TextFieldValue,
    newValue: TextFieldValue,
): TextFieldValue {
    if (newValue == previousValue) {
        return previousValue
    }

    val newComposition = newValue.composition
    if (newComposition != null) {
        val composingText = newValue.text.substring(
            startIndex = newComposition.start.coerceIn(0, newValue.text.length),
            endIndex = newComposition.end.coerceIn(0, newValue.text.length),
        )
        if (!isComposing()) {
            compositionStart()
        }
        compositionUpdate(composingText)
        return synchronizeImeProxyValue(newValue)
    }

    if (previousValue.composition != null) {
        val diff = calculateImeTextDiff(previousValue.text, newValue.text)
        compositionEnd(diff.insertedText.ifEmpty { null })
        return synchronizeImeProxyValue(TextFieldValue())
    }

    val diff = calculateImeTextDiff(previousValue.text, newValue.text)
    if (!diff.changed) {
        return synchronizeImeProxyValue(newValue)
    }

    val line = getCursorPosition().line
    val range = TextRange(
        start = TextPosition(line, diff.startColumn),
        end = TextPosition(line, diff.endColumn),
    )
    when {
        diff.startColumn != diff.endColumn -> replaceText(range, diff.insertedText)
        diff.insertedText.isNotEmpty() -> replaceText(range, diff.insertedText)
        else -> deleteText(range)
    }
    return synchronizeImeProxyValue(TextFieldValue())
}

internal fun EditorController.synchronizeImeProxyValue(
    currentValue: TextFieldValue,
): TextFieldValue {
    val renderModel = state.renderModel
    if (renderModel != null) {
        val synchronizedValue = buildImeProxyValue(
            renderModel = renderModel,
            selectionRange = getSelection(),
            compositionText = currentValue.text.takeIf { isComposing() },
        )
        if (synchronizedValue != null) {
            return synchronizedValue
        }
    }
    if (isComposing()) {
        val compositionLength = currentValue.text.length
        return currentValue.copy(
            selection = ComposeTextRange(compositionLength),
            composition = ComposeTextRange(0, compositionLength),
        )
    }
    return if (currentValue.text.isNotEmpty() || currentValue.composition != null) {
        TextFieldValue()
    } else {
        currentValue
    }
}

private fun EditorController.buildImeProxyValue(
    renderModel: EditorRenderModel,
    selectionRange: com.qiplat.compose.sweeteditor.model.foundation.TextRange?,
    compositionText: String?,
): TextFieldValue? {
    val cursor = getCursorPosition()
    val lineText = renderModel.currentLogicalLineText(cursor.line)
    if (lineText.isEmpty() && compositionText.isNullOrEmpty()) {
        return null
    }
    val cursorIndex = cursor.column.coerceIn(0, lineText.length)
    val sameLineSelection = selectionRange?.takeIf {
        it.start.line == cursor.line && it.end.line == cursor.line
    }
    val selection = if (sameLineSelection != null) {
        ComposeTextRange(
            start = sameLineSelection.start.column.coerceIn(0, lineText.length),
            end = sameLineSelection.end.column.coerceIn(0, lineText.length),
        )
    } else {
        ComposeTextRange(cursorIndex)
    }
    val composition = compositionText
        ?.takeIf { it.isNotEmpty() }
        ?.let {
            val start = (cursorIndex - it.length).coerceAtLeast(0)
            ComposeTextRange(start, cursorIndex)
        }
    return TextFieldValue(
        text = lineText,
        selection = selection,
        composition = composition,
    )
}

private fun EditorRenderModel.currentLogicalLineText(logicalLine: Int): String =
    lines
        .filter { it.logicalLine == logicalLine }
        .sortedBy(VisualLine::wrapIndex)
        .joinToString(separator = "") { line ->
            line.runs
                .filter { run ->
                    when (run.type) {
                        VisualRunType.Text,
                        VisualRunType.Whitespace,
                        VisualRunType.Tab,
                        -> true

                        VisualRunType.Newline,
                        VisualRunType.InlayHint,
                        VisualRunType.PhantomText,
                        VisualRunType.FoldPlaceholder,
                        -> false
                    }
                }
                .joinToString(separator = "") { it.text }
        }

private fun calculateImeTextDiff(
    previousText: String,
    newText: String,
): ImeTextDiff {
    var prefixLength = 0
    val prefixLimit = minOf(previousText.length, newText.length)
    while (prefixLength < prefixLimit && previousText[prefixLength] == newText[prefixLength]) {
        prefixLength++
    }

    var suffixLength = 0
    val previousRemaining = previousText.length - prefixLength
    val newRemaining = newText.length - prefixLength
    val suffixLimit = minOf(previousRemaining, newRemaining)
    while (
        suffixLength < suffixLimit &&
        previousText[previousText.length - suffixLength - 1] == newText[newText.length - suffixLength - 1]
    ) {
        suffixLength++
    }

    val removedEnd = previousText.length - suffixLength
    val insertedEnd = newText.length - suffixLength
    return ImeTextDiff(
        startColumn = prefixLength,
        endColumn = removedEnd,
        insertedText = newText.substring(prefixLength, insertedEnd),
        changed = prefixLength != removedEnd || prefixLength != insertedEnd,
    )
}

private data class ImeTextDiff(
    val startColumn: Int,
    val endColumn: Int,
    val insertedText: String,
    val changed: Boolean,
)
