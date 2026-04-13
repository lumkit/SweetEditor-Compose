package com.qiplat.compose.sweeteditor.core.math

import com.qiplat.compose.sweeteditor.core.EditorCoreTextPosition

internal fun minPosition(
    left: EditorCoreTextPosition,
    right: EditorCoreTextPosition,
): EditorCoreTextPosition = if (left <= right) left else right

internal fun maxPosition(
    left: EditorCoreTextPosition,
    right: EditorCoreTextPosition,
): EditorCoreTextPosition = if (left >= right) left else right

private operator fun EditorCoreTextPosition.compareTo(other: EditorCoreTextPosition): Int = when {
    line != other.line -> line.compareTo(other.line)
    else -> column.compareTo(other.column)
}