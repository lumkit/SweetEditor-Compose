package com.qiplat.compose.sweeteditor.core.layout

import com.qiplat.compose.sweeteditor.core.*
import com.qiplat.compose.sweeteditor.core.document.DocumentStore

data class LayoutSettings(
    val wrapMode: EditorCoreWrapMode = EditorCoreWrapMode.None,
    val tabSize: Int = 4,
    val lineSpacingExtra: Float = 0f,
    val lineSpacingMultiplier: Float = 1f,
)

data class LayoutSnapshot(
    val documentVersion: Long,
    val lines: List<EditorCoreRenderLine>,
    val lineHeight: Float,
    val contentWidth: Float,
    val contentHeight: Float,
)

data class LayoutPositionQueryResult(
    val position: EditorCoreTextPosition,
    val rect: EditorCoreRect,
    val visualLine: LayoutVisualLine,
    val localColumn: Int,
    val boundary: LayoutColumnBoundary,
)

data class LayoutSelectionQueryResult(
    val range: EditorCoreTextRange?,
    val fragments: List<LayoutSelectionFragment>,
) {
    val rects: List<EditorCoreRect>
        get() = fragments.map { fragment -> fragment.rect }

    val visualLines: List<LayoutVisualLine>
        get() = fragments.map { fragment -> fragment.visualLine }
}

data class LayoutSelectionFragment(
    val visualLine: LayoutVisualLine,
    val startColumn: Int,
    val endColumn: Int,
    val rect: EditorCoreRect,
)

data class LayoutGeometryQueryResult(
    val cursor: LayoutPositionQueryResult,
    val selection: LayoutSelectionQueryResult,
)

data class LayoutLineQueryResult(
    val logicalLine: Int,
    val visualLine: LayoutVisualLine,
    val rect: EditorCoreRect,
)

enum class LayoutColumnAffinity {
    Leading,
    Trailing,
}

data class LayoutColumnMappingResult(
    val visualLine: LayoutVisualLine,
    val x: Float,
    val localColumn: Int,
    val column: Int,
    val affinity: LayoutColumnAffinity,
)

data class LayoutHitTestResult(
    val position: EditorCoreTextPosition,
    val rect: EditorCoreRect,
    val visualLine: LayoutVisualLine,
    val positionResult: LayoutPositionQueryResult,
    val columnMapping: LayoutColumnMappingResult,
)

data class LayoutColumnBoundary(
    val sourceColumn: Int,
    val x: Float,
)

data class LayoutVisualLine(
    val logicalLine: Int,
    val wrapIndex: Int,
    val startColumn: Int,
    val endColumn: Int,
    val top: Float,
    val height: Float,
    val width: Float,
    val text: String,
    val columnBoundaries: List<LayoutColumnBoundary>,
)

class LayoutEngine(
    private val textMeasurer: EditorCoreTextMeasurer,
) {
    fun createQuery(
        documentStore: DocumentStore,
        viewportWidth: Int,
        settings: LayoutSettings,
    ): LayoutQuery {
        val lineHeight = resolveLineHeight(settings)
        val visualLines = resolveVisualLines(
            documentStore = documentStore,
            viewportWidth = viewportWidth,
            settings = settings,
        )
        return LayoutQuery(
            visualLines = visualLines,
            lineHeight = lineHeight,
            contentWidth = visualLines.maxOfOrNull { visualLine -> visualLine.width } ?: 0f,
            contentHeight = visualLines.size * lineHeight,
        )
    }

    fun layout(
        documentStore: DocumentStore,
        viewportWidth: Int,
        viewportHeight: Int,
        scrollY: Float,
        settings: LayoutSettings,
    ): LayoutSnapshot =
        createQuery(
            documentStore = documentStore,
            viewportWidth = viewportWidth,
            settings = settings,
        ).toSnapshot(
            documentVersion = documentStore.version,
            viewportHeight = viewportHeight,
            scrollY = scrollY,
        )

    fun measurePositionRect(
        documentStore: DocumentStore,
        position: EditorCoreTextPosition,
        viewportWidth: Int,
        settings: LayoutSettings,
    ): EditorCoreRect =
        createQuery(
            documentStore = documentStore,
            viewportWidth = viewportWidth,
            settings = settings,
        ).measurePositionRect(position)

    fun buildSelectionRects(
        documentStore: DocumentStore,
        range: EditorCoreTextRange?,
        viewportWidth: Int,
        settings: LayoutSettings,
    ): List<EditorCoreRect> =
        createQuery(
            documentStore = documentStore,
            viewportWidth = viewportWidth,
            settings = settings,
        ).buildSelectionRects(range)

    fun hitTest(
        documentStore: DocumentStore,
        x: Float,
        y: Float,
        viewportWidth: Int,
        settings: LayoutSettings,
    ): LayoutHitTestResult =
        createQuery(
            documentStore = documentStore,
            viewportWidth = viewportWidth,
            settings = settings,
        ).hitTest(x = x, y = y)

    fun getVisualLineForPoint(
        documentStore: DocumentStore,
        y: Float,
        viewportWidth: Int,
        settings: LayoutSettings,
    ): LayoutVisualLine =
        createQuery(
            documentStore = documentStore,
            viewportWidth = viewportWidth,
            settings = settings,
        ).getVisualLineForPoint(y)

    private fun wrapLine(
        text: String,
        viewportWidth: Int,
        settings: LayoutSettings,
    ): List<MeasuredSegment> {
        val lineMeasurement = measureLine(
            text = text,
            tabSize = settings.tabSize,
        )
        if (lineMeasurement.sourceText.isEmpty()) {
            return listOf(
                createMeasuredSegment(
                    lineMeasurement = lineMeasurement,
                    startColumn = 0,
                    endColumn = 0,
                ),
            )
        }
        if (settings.wrapMode == EditorCoreWrapMode.None || viewportWidth <= 0) {
            return listOf(
                createMeasuredSegment(
                    lineMeasurement = lineMeasurement,
                    startColumn = 0,
                    endColumn = lineMeasurement.sourceText.length,
                ),
            )
        }
        val maxWidth = viewportWidth.toFloat()
        val segments = ArrayList<MeasuredSegment>()
        var startIndex = 0
        while (startIndex < lineMeasurement.sourceText.length) {
            var bestEnd = findLongestFittingEnd(
                lineMeasurement = lineMeasurement,
                startIndex = startIndex,
                maxWidth = maxWidth,
            )
            if (settings.wrapMode == EditorCoreWrapMode.WordBreak) {
                bestEnd = findWordBreakEnd(
                    text = lineMeasurement.sourceText,
                    startIndex = startIndex,
                    fittedEnd = bestEnd,
                )
            }
            segments += createMeasuredSegment(
                lineMeasurement = lineMeasurement,
                startColumn = startIndex,
                endColumn = bestEnd,
            )
            startIndex = bestEnd
        }
        return segments
    }

    private fun resolveVisualLines(
        documentStore: DocumentStore,
        viewportWidth: Int,
        settings: LayoutSettings,
    ): List<LayoutVisualLine> {
        val lineHeight = resolveLineHeight(settings)
        val visualLines = ArrayList<LayoutVisualLine>()
        var visualIndex = 0
        for (logicalLine in 0 until documentStore.getLineCount()) {
            val segments = wrapLine(
                text = documentStore.getLineText(logicalLine),
                viewportWidth = viewportWidth,
                settings = settings,
            )
            segments.forEachIndexed { wrapIndex, segment ->
                visualLines += LayoutVisualLine(
                    logicalLine = logicalLine,
                    wrapIndex = wrapIndex,
                    startColumn = segment.startColumn,
                    endColumn = segment.endColumn,
                    top = visualIndex * lineHeight,
                    height = lineHeight,
                    width = segment.width,
                    text = segment.text,
                    columnBoundaries = segment.columnBoundaries,
                )
                visualIndex += 1
            }
        }
        return visualLines
    }

    private fun findLongestFittingEnd(
        lineMeasurement: LineMeasurement,
        startIndex: Int,
        maxWidth: Float,
    ): Int {
        var low = startIndex + 1
        var high = lineMeasurement.sourceText.length
        var bestEnd = low
        while (low <= high) {
            val mid = (low + high).ushr(1)
            val candidateWidth = lineMeasurement.boundaries[mid].x - lineMeasurement.boundaries[startIndex].x
            if (candidateWidth <= maxWidth || mid == startIndex + 1) {
                bestEnd = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return bestEnd
    }

    private fun findWordBreakEnd(
        text: String,
        startIndex: Int,
        fittedEnd: Int,
    ): Int {
        for (index in fittedEnd downTo (startIndex + 1)) {
            if (text[index - 1].isWhitespace()) {
                return index
            }
        }
        return fittedEnd
    }

    private fun resolveLineHeight(settings: LayoutSettings): Float {
        val metrics = textMeasurer.getFontMetrics()
        val baseLineHeight = metrics.firstOrNull()?.takeIf { it > 0f } ?: 16f
        return (baseLineHeight * settings.lineSpacingMultiplier + settings.lineSpacingExtra).coerceAtLeast(1f)
    }

    private fun measureNormalizedTextWidth(text: String): Float =
        textMeasurer.measureTextWidth(
            text = text,
            fontStyle = 0,
        )

    private fun measureLine(
        text: String,
        tabSize: Int,
    ): LineMeasurement {
        if (text.isEmpty()) {
            return LineMeasurement(
                sourceText = "",
                renderedPieces = emptyList(),
                boundaries = listOf(
                    MeasuredBoundary(
                        sourceColumn = 0,
                        x = 0f,
                    ),
                ),
            )
        }
        val renderedPieces = ArrayList<String>(text.length)
        val boundaries = ArrayList<MeasuredBoundary>(text.length + 1)
        var renderedText = ""
        var visualColumn = 0
        boundaries += MeasuredBoundary(
            sourceColumn = 0,
            x = 0f,
        )
        text.forEachIndexed { index, char ->
            val piece = expandChar(
                char = char,
                visualColumn = visualColumn,
                tabSize = tabSize,
            )
            renderedPieces += piece
            renderedText += piece
            visualColumn += piece.length
            boundaries += MeasuredBoundary(
                sourceColumn = index + 1,
                x = measureNormalizedTextWidth(renderedText),
            )
        }
        return LineMeasurement(
            sourceText = text,
            renderedPieces = renderedPieces,
            boundaries = boundaries,
        )
    }

    private fun expandChar(
        char: Char,
        visualColumn: Int,
        tabSize: Int,
    ): String = if (char != '\t') {
        char.toString()
    } else {
        " ".repeat((tabSize - (visualColumn % tabSize)).coerceAtLeast(1))
    }

    private fun createMeasuredSegment(
        lineMeasurement: LineMeasurement,
        startColumn: Int,
        endColumn: Int,
    ): MeasuredSegment {
        val startBoundary = lineMeasurement.boundaries[startColumn]
        val endBoundary = lineMeasurement.boundaries[endColumn]
        return MeasuredSegment(
            text = lineMeasurement.renderedPieces
                .subList(startColumn, endColumn)
                .joinToString(separator = ""),
            width = endBoundary.x - startBoundary.x,
            startColumn = startColumn,
            endColumn = endColumn,
            columnBoundaries = lineMeasurement.boundaries
                .subList(startColumn, endColumn + 1)
                .map { boundary ->
                    LayoutColumnBoundary(
                        sourceColumn = boundary.sourceColumn,
                        x = boundary.x - startBoundary.x,
                    )
                },
        )
    }
}

private data class MeasuredSegment(
    val text: String,
    val width: Float,
    val startColumn: Int,
    val endColumn: Int,
    val columnBoundaries: List<LayoutColumnBoundary>,
)

private data class LineMeasurement(
    val sourceText: String,
    val renderedPieces: List<String>,
    val boundaries: List<MeasuredBoundary>,
)

private data class MeasuredBoundary(
    val sourceColumn: Int,
    val x: Float,
)
