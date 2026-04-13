package com.qiplat.compose.sweeteditor.core.layout

import com.qiplat.compose.sweeteditor.core.*
import com.qiplat.compose.sweeteditor.core.math.maxPosition
import com.qiplat.compose.sweeteditor.core.math.minPosition

data class LayoutQuery(
    val visualLines: List<LayoutVisualLine>,
    val lineHeight: Float,
    val contentWidth: Float,
    val contentHeight: Float,
) {
    fun queryPosition(position: EditorCoreTextPosition): LayoutPositionQueryResult {
        val visualLine = findVisualLineForPosition(position)
        val boundary = visualLine.columnBoundaries.firstOrNull { columnBoundary ->
            columnBoundary.sourceColumn == position.column.coerceIn(visualLine.startColumn, visualLine.endColumn)
        } ?: visualLine.columnBoundaries.last()
        return LayoutPositionQueryResult(
            position = position,
            rect = EditorCoreRect(
                x = boundary.x,
                y = visualLine.top,
                width = 0f,
                height = visualLine.height,
            ),
            visualLine = visualLine,
            localColumn = (boundary.sourceColumn - visualLine.startColumn).coerceAtLeast(0),
            boundary = boundary,
        )
    }

    fun toSnapshot(
        documentVersion: Long,
        viewportHeight: Int,
        scrollY: Float,
    ): LayoutSnapshot {
        val firstVisibleIndex = (scrollY / lineHeight).toInt().coerceAtLeast(0)
        val visibleLineCapacity = (viewportHeight / lineHeight).toInt().coerceAtLeast(1) + 1
        val lastVisibleIndex = (firstVisibleIndex + visibleLineCapacity).coerceAtMost(visualLines.size)
        val lines = visualLines
            .subList(firstVisibleIndex, lastVisibleIndex)
            .map { visualLine ->
                EditorCoreRenderLine(
                    logicalLine = visualLine.logicalLine,
                    wrapIndex = visualLine.wrapIndex,
                    top = visualLine.top,
                    height = visualLine.height,
                    text = visualLine.text,
                    runs = listOf(
                        EditorCoreRenderRun(
                            text = visualLine.text,
                            x = 0f,
                            width = visualLine.width,
                        ),
                    ),
                )
            }
        return LayoutSnapshot(
            documentVersion = documentVersion,
            lines = lines,
            lineHeight = lineHeight,
            contentWidth = contentWidth,
            contentHeight = contentHeight,
        )
    }

    fun measurePositionRect(position: EditorCoreTextPosition): EditorCoreRect =
        queryPosition(position).rect

    fun queryGeometry(
        cursor: EditorCoreTextPosition,
        selection: EditorCoreTextRange?,
    ): LayoutGeometryQueryResult =
        LayoutGeometryQueryResult(
            cursor = queryPosition(cursor),
            selection = querySelection(selection),
        )

    fun queryLogicalLine(
        logicalLine: Int,
        preferredWrapIndex: Int = 0,
    ): LayoutLineQueryResult {
        val clampedLogicalLine = logicalLine.coerceIn(
            minimumValue = 0,
            maximumValue = visualLines.maxOfOrNull { it.logicalLine } ?: 0,
        )
        val matchingLines = visualLines.filter { visualLine ->
            visualLine.logicalLine == clampedLogicalLine
        }
        val visualLine = matchingLines.firstOrNull { line ->
            line.wrapIndex == preferredWrapIndex
        } ?: matchingLines.lastOrNull() ?: visualLines.first()
        return LayoutLineQueryResult(
            logicalLine = clampedLogicalLine,
            visualLine = visualLine,
            rect = EditorCoreRect(
                x = 0f,
                y = visualLine.top,
                width = visualLine.width,
                height = visualLine.height,
            ),
        )
    }

    fun queryColumn(
        visualLine: LayoutVisualLine,
        x: Float,
    ): LayoutColumnMappingResult {
        val targetX = x.coerceAtLeast(0f)
        val resolvedLocalColumn = findClosestColumn(
            visualLine = visualLine,
            x = targetX,
        )
        val resolvedBoundary = visualLine.columnBoundaries
            .firstOrNull { boundary -> boundary.sourceColumn == visualLine.startColumn + resolvedLocalColumn }
            ?: visualLine.columnBoundaries.last()
        val resolvedX = resolvedBoundary.x
        val affinity = if (resolvedLocalColumn >= visualLine.text.length) {
            LayoutColumnAffinity.Trailing
        } else if (targetX <= resolvedX) {
            LayoutColumnAffinity.Leading
        } else {
            LayoutColumnAffinity.Trailing
        }
        return LayoutColumnMappingResult(
            visualLine = visualLine,
            x = targetX,
            localColumn = resolvedLocalColumn,
            column = (visualLine.startColumn + resolvedLocalColumn).coerceAtMost(visualLine.endColumn),
            affinity = affinity,
        )
    }

    fun querySelection(range: EditorCoreTextRange?): LayoutSelectionQueryResult {
        val activeRange = range ?: return LayoutSelectionQueryResult(
            range = null,
            fragments = emptyList(),
        )
        if (activeRange.start == activeRange.end) {
            return LayoutSelectionQueryResult(
                range = activeRange,
                fragments = emptyList(),
            )
        }
        val orderedRange = EditorCoreTextRange(
            start = minPosition(activeRange.start, activeRange.end),
            end = maxPosition(activeRange.start, activeRange.end),
        )
        val selectionVisualLines = visualLines.filter { visualLine ->
            visualLine.logicalLine in orderedRange.start.line..orderedRange.end.line &&
                !(visualLine.logicalLine == orderedRange.start.line && orderedRange.start.column >= visualLine.endColumn) &&
                !(visualLine.logicalLine == orderedRange.end.line && orderedRange.end.column <= visualLine.startColumn)
        }
        val fragments = selectionVisualLines.mapNotNull { visualLine ->
            if (visualLine.logicalLine !in orderedRange.start.line..orderedRange.end.line) {
                return@mapNotNull null
            }
            val startColumn = when {
                visualLine.logicalLine == orderedRange.start.line ->
                    maxOf(orderedRange.start.column, visualLine.startColumn)
                else -> visualLine.startColumn
            }
            val endColumn = when {
                visualLine.logicalLine == orderedRange.end.line ->
                    minOf(orderedRange.end.column, visualLine.endColumn)
                else -> visualLine.endColumn
            }
            if (endColumn <= startColumn) {
                return@mapNotNull null
            }
            val startLocalColumn = startColumn - visualLine.startColumn
            val endLocalColumn = endColumn - visualLine.startColumn
            val startX = visualLine.columnBoundaries[startLocalColumn].x
            val endX = visualLine.columnBoundaries[endLocalColumn].x
            LayoutSelectionFragment(
                visualLine = visualLine,
                startColumn = startColumn,
                endColumn = endColumn,
                rect = EditorCoreRect(
                    x = startX,
                    y = visualLine.top,
                    width = (endX - startX).coerceAtLeast(0f),
                    height = visualLine.height,
                ),
            )
        }
        return LayoutSelectionQueryResult(
            range = orderedRange,
            fragments = fragments,
        )
    }

    fun buildSelectionRects(range: EditorCoreTextRange?): List<EditorCoreRect> =
        querySelection(range).rects

    fun hitTest(
        x: Float,
        y: Float,
    ): LayoutHitTestResult {
        val targetVisualLine = getVisualLineForPoint(y)
        val columnMapping = queryColumn(
            visualLine = targetVisualLine,
            x = x,
        )
        val position = EditorCoreTextPosition(
            line = targetVisualLine.logicalLine,
            column = columnMapping.column,
        )
        val positionResult = queryPosition(position)
        return LayoutHitTestResult(
            position = positionResult.position,
            rect = positionResult.rect,
            visualLine = positionResult.visualLine,
            positionResult = positionResult,
            columnMapping = columnMapping,
        )
    }

    fun getVisualLineForPoint(y: Float): LayoutVisualLine =
        visualLines[
            (y / lineHeight).toInt().coerceIn(
                minimumValue = 0,
                maximumValue = visualLines.lastIndex.coerceAtLeast(0),
            )
        ]

    fun findVisualLineForPosition(position: EditorCoreTextPosition): LayoutVisualLine {
        val clampedLine = position.line.coerceIn(
            minimumValue = 0,
            maximumValue = visualLines.maxOfOrNull { it.logicalLine } ?: 0,
        )
        val clampedColumn = position.column.coerceAtLeast(0)
        val matchingVisualLines = visualLines.filter { visualLine ->
            visualLine.logicalLine == clampedLine &&
                clampedColumn in visualLine.startColumn..visualLine.endColumn
        }
        return matchingVisualLines.firstOrNull { visualLine ->
            visualLine.startColumn == clampedColumn
        } ?: matchingVisualLines.firstOrNull() ?: visualLines.last { visualLine ->
            visualLine.logicalLine == clampedLine
        }
    }

    private fun findClosestColumn(
        visualLine: LayoutVisualLine,
        x: Float,
    ): Int {
        if (visualLine.text.isEmpty()) {
            return 0
        }
        var low = 0
        var high = visualLine.columnBoundaries.lastIndex
        var best = 0
        while (low <= high) {
            val mid = (low + high).ushr(1)
            val width = visualLine.columnBoundaries[mid].x
            if (width <= x) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (best >= visualLine.columnBoundaries.lastIndex) {
            return visualLine.columnBoundaries.lastIndex
        }
        val leftWidth = visualLine.columnBoundaries[best].x
        val rightWidth = visualLine.columnBoundaries[best + 1].x
        return if (x - leftWidth <= rightWidth - x) best else best + 1
    }
}
