package com.qiplat.compose.sweeteditor.core.layout

import com.qiplat.compose.sweeteditor.core.EditorCoreRect
import com.qiplat.compose.sweeteditor.core.EditorCoreRenderLine
import com.qiplat.compose.sweeteditor.core.EditorCoreRenderRun
import com.qiplat.compose.sweeteditor.core.EditorCoreTextMeasurer
import com.qiplat.compose.sweeteditor.core.EditorCoreTextPosition
import com.qiplat.compose.sweeteditor.core.EditorCoreTextRange
import com.qiplat.compose.sweeteditor.core.EditorCoreWrapMode
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

data class LayoutHitTestResult(
    val position: EditorCoreTextPosition,
    val rect: EditorCoreRect,
)

class LayoutEngine(
    private val textMeasurer: EditorCoreTextMeasurer,
) {
    fun layout(
        documentStore: DocumentStore,
        viewportWidth: Int,
        viewportHeight: Int,
        scrollY: Float,
        settings: LayoutSettings,
    ): LayoutSnapshot {
        val lineHeight = resolveLineHeight(settings)
        val segmentsByLine = (0 until documentStore.getLineCount()).map { line ->
            wrapLine(
                text = documentStore.getLineText(line),
                viewportWidth = viewportWidth,
                settings = settings,
            )
        }
        val totalVisualLines = segmentsByLine.sumOf { it.size }
        val contentHeight = totalVisualLines * lineHeight
        val contentWidth = segmentsByLine
            .flatten()
            .maxOfOrNull { segment -> segment.width }
            ?: 0f
        val firstVisibleIndex = (scrollY / lineHeight).toInt().coerceAtLeast(0)
        val visibleLineCapacity = (viewportHeight / lineHeight).toInt().coerceAtLeast(1) + 1
        val lastVisibleIndex = (firstVisibleIndex + visibleLineCapacity).coerceAtMost(totalVisualLines)
        val lines = ArrayList<EditorCoreRenderLine>()
        var visualIndex = 0
        segmentsByLine.forEachIndexed { logicalLine, segments ->
            segments.forEachIndexed { wrapIndex, segment ->
                if (visualIndex in firstVisibleIndex until lastVisibleIndex) {
                    lines += EditorCoreRenderLine(
                        logicalLine = logicalLine,
                        wrapIndex = wrapIndex,
                        top = visualIndex * lineHeight,
                        height = lineHeight,
                        text = segment.text,
                        runs = listOf(
                            EditorCoreRenderRun(
                                text = segment.text,
                                x = 0f,
                                width = segment.width,
                            ),
                        ),
                    )
                }
                visualIndex += 1
            }
        }
        return LayoutSnapshot(
            documentVersion = documentStore.version,
            lines = lines,
            lineHeight = lineHeight,
            contentWidth = contentWidth,
            contentHeight = contentHeight,
        )
    }

    fun measurePositionRect(
        documentStore: DocumentStore,
        position: EditorCoreTextPosition,
        settings: LayoutSettings,
    ): EditorCoreRect {
        val lineHeight = resolveLineHeight(settings)
        val lineText = documentStore.getLineText(position.line)
        val x = measureTextWidth(
            text = lineText.take(position.column.coerceAtMost(lineText.length)),
            settings = settings,
        )
        val y = position.line * lineHeight
        return EditorCoreRect(
            x = x,
            y = y,
            width = 0f,
            height = lineHeight,
        )
    }

    fun buildSelectionRects(
        documentStore: DocumentStore,
        range: EditorCoreTextRange?,
        settings: LayoutSettings,
    ): List<EditorCoreRect> {
        val activeRange = range ?: return emptyList()
        if (activeRange.start == activeRange.end) {
            return emptyList()
        }
        val lineHeight = resolveLineHeight(settings)
        val rects = ArrayList<EditorCoreRect>()
        for (line in activeRange.start.line..activeRange.end.line) {
            val lineText = documentStore.getLineText(line)
            val startColumn = if (line == activeRange.start.line) activeRange.start.column else 0
            val endColumn = if (line == activeRange.end.line) activeRange.end.column else lineText.length
            if (endColumn <= startColumn) {
                continue
            }
            val startX = measureTextWidth(
                text = lineText.take(startColumn.coerceAtMost(lineText.length)),
                settings = settings,
            )
            val endX = measureTextWidth(
                text = lineText.take(endColumn.coerceAtMost(lineText.length)),
                settings = settings,
            )
            rects += EditorCoreRect(
                x = startX,
                y = line * lineHeight,
                width = (endX - startX).coerceAtLeast(0f),
                height = lineHeight,
            )
        }
        return rects
    }

    fun hitTest(
        documentStore: DocumentStore,
        x: Float,
        y: Float,
        settings: LayoutSettings,
    ): LayoutHitTestResult {
        val lineHeight = resolveLineHeight(settings)
        val targetLine = (y / lineHeight).toInt().coerceIn(
            minimumValue = 0,
            maximumValue = (documentStore.getLineCount() - 1).coerceAtLeast(0),
        )
        val lineText = documentStore.getLineText(targetLine)
        val normalized = normalizeText(lineText, settings.tabSize)
        val targetX = x.coerceAtLeast(0f)
        val column = findClosestColumn(
            text = normalized,
            x = targetX,
        )
        val resolvedPosition = EditorCoreTextPosition(
            line = targetLine,
            column = column.coerceAtMost(lineText.length),
        )
        val rect = measurePositionRect(
            documentStore = documentStore,
            position = resolvedPosition,
            settings = settings,
        )
        return LayoutHitTestResult(
            position = resolvedPosition,
            rect = rect,
        )
    }

    private fun wrapLine(
        text: String,
        viewportWidth: Int,
        settings: LayoutSettings,
    ): List<MeasuredSegment> {
        val normalized = normalizeText(text, settings.tabSize)
        if (normalized.isEmpty()) {
            return listOf(MeasuredSegment(text = "", width = 0f))
        }
        if (settings.wrapMode == EditorCoreWrapMode.None || viewportWidth <= 0) {
            return listOf(
                MeasuredSegment(
                    text = normalized,
                    width = measureNormalizedTextWidth(normalized),
                ),
            )
        }
        val maxWidth = viewportWidth.toFloat()
        val segments = ArrayList<MeasuredSegment>()
        var startIndex = 0
        while (startIndex < normalized.length) {
            var bestEnd = findLongestFittingEnd(
                text = normalized,
                startIndex = startIndex,
                maxWidth = maxWidth,
            )
            if (settings.wrapMode == EditorCoreWrapMode.WordBreak) {
                bestEnd = findWordBreakEnd(
                    text = normalized,
                    startIndex = startIndex,
                    fittedEnd = bestEnd,
                )
            }
            val segmentText = normalized.substring(startIndex, bestEnd)
            segments += MeasuredSegment(
                text = segmentText,
                width = measureNormalizedTextWidth(segmentText),
            )
            startIndex = bestEnd
        }
        return segments
    }

    private fun findLongestFittingEnd(
        text: String,
        startIndex: Int,
        maxWidth: Float,
    ): Int {
        var low = startIndex + 1
        var high = text.length
        var bestEnd = low
        while (low <= high) {
            val mid = (low + high).ushr(1)
            val candidateWidth = measureNormalizedTextWidth(text.substring(startIndex, mid))
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

    private fun measureTextWidth(
        text: String,
        settings: LayoutSettings,
    ): Float = measureNormalizedTextWidth(normalizeText(text, settings.tabSize))

    private fun measureNormalizedTextWidth(text: String): Float =
        textMeasurer.measureTextWidth(
            text = text,
            fontStyle = 0,
        )

    private fun findClosestColumn(
        text: String,
        x: Float,
    ): Int {
        if (text.isEmpty()) {
            return 0
        }
        var low = 0
        var high = text.length
        var best = 0
        while (low <= high) {
            val mid = (low + high).ushr(1)
            val width = measureNormalizedTextWidth(text.substring(0, mid))
            if (width <= x) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (best >= text.length) {
            return text.length
        }
        val leftWidth = measureNormalizedTextWidth(text.substring(0, best))
        val rightWidth = measureNormalizedTextWidth(text.substring(0, best + 1))
        return if (x - leftWidth <= rightWidth - x) best else best + 1
    }

    private fun normalizeText(text: String, tabSize: Int): String =
        if ('\t' !in text) {
            text
        } else {
            buildString(text.length) {
                text.forEach { char ->
                    if (char == '\t') {
                        repeat(tabSize.coerceAtLeast(1)) {
                            append(' ')
                        }
                    } else {
                        append(char)
                    }
                }
            }
        }
}

private data class MeasuredSegment(
    val text: String,
    val width: Float,
)
