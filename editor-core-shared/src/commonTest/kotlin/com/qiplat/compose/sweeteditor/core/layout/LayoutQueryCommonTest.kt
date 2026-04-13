package com.qiplat.compose.sweeteditor.core.layout

import com.qiplat.compose.sweeteditor.core.EditorCoreTextMeasurer
import com.qiplat.compose.sweeteditor.core.EditorCoreTextPosition
import com.qiplat.compose.sweeteditor.core.EditorCoreTextRange
import com.qiplat.compose.sweeteditor.core.EditorCoreWrapMode
import com.qiplat.compose.sweeteditor.core.document.DocumentStore
import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutQueryCommonTest {
    @Test
    fun layoutQueryReturnsWrappedVisualLineForPosition() {
        val engine = LayoutEngine(QueryFakeTextMeasurer())
        val query = engine.createQuery(
            documentStore = DocumentStore("abcdef"),
            viewportWidth = 3,
            settings = LayoutSettings(
                wrapMode = EditorCoreWrapMode.CharBreak,
            ),
        )

        val visualLine = query.findVisualLineForPosition(
            EditorCoreTextPosition(0, 4),
        )

        assertEquals(1, visualLine.wrapIndex)
        assertEquals("def", visualLine.text)
    }

    @Test
    fun layoutQueryMapsWrapEdgeToNextVisualLine() {
        val engine = LayoutEngine(QueryFakeTextMeasurer())
        val query = engine.createQuery(
            documentStore = DocumentStore("abcdef"),
            viewportWidth = 3,
            settings = LayoutSettings(
                wrapMode = EditorCoreWrapMode.CharBreak,
            ),
        )

        val result = query.queryPosition(
            EditorCoreTextPosition(0, 3),
        )

        assertEquals(1, result.visualLine.wrapIndex)
        assertEquals(0f, result.rect.x)
        assertEquals(10f, result.rect.y)
    }

    @Test
    fun layoutQueryReturnsPositionResultForWrappedColumn() {
        val engine = LayoutEngine(QueryFakeTextMeasurer())
        val query = engine.createQuery(
            documentStore = DocumentStore("abcdef"),
            viewportWidth = 3,
            settings = LayoutSettings(
                wrapMode = EditorCoreWrapMode.CharBreak,
            ),
        )

        val result = query.queryPosition(
            EditorCoreTextPosition(0, 4),
        )

        assertEquals(EditorCoreTextPosition(0, 4), result.position)
        assertEquals(1, result.visualLine.wrapIndex)
        assertEquals(1f, result.rect.x)
        assertEquals(10f, result.rect.y)
        assertEquals(1, result.localColumn)
        assertEquals(4, result.boundary.sourceColumn)
    }

    @Test
    fun layoutQueryReturnsUnifiedGeometryResult() {
        val engine = LayoutEngine(QueryFakeTextMeasurer())
        val query = engine.createQuery(
            documentStore = DocumentStore("abcdef"),
            viewportWidth = 3,
            settings = LayoutSettings(
                wrapMode = EditorCoreWrapMode.CharBreak,
            ),
        )

        val result = query.queryGeometry(
            cursor = EditorCoreTextPosition(0, 4),
            selection = EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 2),
                end = EditorCoreTextPosition(0, 5),
            ),
        )

        assertEquals(EditorCoreTextPosition(0, 4), result.cursor.position)
        assertEquals(1, result.cursor.visualLine.wrapIndex)
        assertEquals(2, result.selection.fragments.size)
        assertEquals(2, result.selection.fragments.first().startColumn)
        assertEquals(5, result.selection.fragments.last().endColumn)
    }

    @Test
    fun layoutQueryReturnsColumnMappingAffinity() {
        val engine = LayoutEngine(QueryFakeTextMeasurer())
        val query = engine.createQuery(
            documentStore = DocumentStore("abcdef"),
            viewportWidth = 3,
            settings = LayoutSettings(
                wrapMode = EditorCoreWrapMode.CharBreak,
            ),
        )

        val visualLine = query.getVisualLineForPoint(12f)
        val leadingMapping = query.queryColumn(
            visualLine = visualLine,
            x = 0.8f,
        )
        val trailingMapping = query.queryColumn(
            visualLine = visualLine,
            x = 1.2f,
        )

        assertEquals(LayoutColumnAffinity.Leading, leadingMapping.affinity)
        assertEquals(1, leadingMapping.localColumn)
        assertEquals(LayoutColumnAffinity.Trailing, trailingMapping.affinity)
        assertEquals(1, trailingMapping.localColumn)
    }

    @Test
    fun layoutQueryBuildsSelectionResultAcrossWrappedSegments() {
        val engine = LayoutEngine(QueryFakeTextMeasurer())
        val query = engine.createQuery(
            documentStore = DocumentStore("abcdef"),
            viewportWidth = 3,
            settings = LayoutSettings(
                wrapMode = EditorCoreWrapMode.CharBreak,
            ),
        )

        val result = query.querySelection(
            EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 2),
                end = EditorCoreTextPosition(0, 5),
            ),
        )

        assertEquals(2, result.rects.size)
        assertEquals(2, result.visualLines.size)
        assertEquals(2, result.fragments.size)
        assertEquals(2f, result.rects[0].x)
        assertEquals(1f, result.rects[0].width)
        assertEquals(0f, result.rects[1].x)
        assertEquals(2f, result.rects[1].width)
        assertEquals(0, result.visualLines[0].wrapIndex)
        assertEquals(1, result.visualLines[1].wrapIndex)
        assertEquals(2, result.fragments[0].startColumn)
        assertEquals(3, result.fragments[0].endColumn)
        assertEquals(3, result.fragments[1].startColumn)
        assertEquals(5, result.fragments[1].endColumn)
    }

    @Test
    fun hitTestCarriesColumnMappingResult() {
        val engine = LayoutEngine(QueryFakeTextMeasurer())
        val query = engine.createQuery(
            documentStore = DocumentStore("abcdef"),
            viewportWidth = 3,
            settings = LayoutSettings(
                wrapMode = EditorCoreWrapMode.CharBreak,
            ),
        )

        val result = query.hitTest(
            x = 1.2f,
            y = 12f,
        )

        assertEquals(EditorCoreTextPosition(0, 4), result.position)
        assertEquals(LayoutColumnAffinity.Trailing, result.columnMapping.affinity)
        assertEquals(1, result.columnMapping.localColumn)
        assertEquals(1, result.visualLine.wrapIndex)
        assertEquals(result.position, result.positionResult.position)
        assertEquals(result.rect, result.positionResult.rect)
    }

    @Test
    fun layoutQueryMapsTabToExpandedColumnWidth() {
        val engine = LayoutEngine(QueryFakeTextMeasurer())
        val query = engine.createQuery(
            documentStore = DocumentStore("\tab"),
            viewportWidth = 20,
            settings = LayoutSettings(
                tabSize = 4,
            ),
        )

        val positionResult = query.queryPosition(
            EditorCoreTextPosition(0, 1),
        )
        val columnMapping = query.queryColumn(
            visualLine = query.getVisualLineForPoint(0f),
            x = 3.6f,
        )

        assertEquals(4f, positionResult.rect.x)
        assertEquals(1, columnMapping.column)
        assertEquals(LayoutColumnAffinity.Leading, columnMapping.affinity)
    }

    @Test
    fun layoutQueryUsesMeasuredWidthForWideCharacters() {
        val engine = LayoutEngine(WideCharTextMeasurer())
        val query = engine.createQuery(
            documentStore = DocumentStore("a你b"),
            viewportWidth = 20,
            settings = LayoutSettings(),
        )

        val positionResult = query.queryPosition(
            EditorCoreTextPosition(0, 2),
        )
        val columnMapping = query.queryColumn(
            visualLine = query.getVisualLineForPoint(0f),
            x = 2.4f,
        )

        assertEquals(3f, positionResult.rect.x)
        assertEquals(2, columnMapping.column)
        assertEquals(LayoutColumnAffinity.Leading, columnMapping.affinity)
    }
}

private class QueryFakeTextMeasurer : EditorCoreTextMeasurer {
    override fun measureTextWidth(text: String, fontStyle: Int): Float = text.length.toFloat()

    override fun measureInlayHintWidth(text: String): Float = text.length.toFloat()

    override fun measureIconWidth(iconId: Int): Float = iconId.toFloat()

    override fun getFontMetrics(): FloatArray = floatArrayOf(10f, 8f, 2f, 0f)
}

private class WideCharTextMeasurer : EditorCoreTextMeasurer {
    override fun measureTextWidth(text: String, fontStyle: Int): Float =
        text.sumOf { char ->
            if (char == '你') 2.0 else 1.0
        }.toFloat()

    override fun measureInlayHintWidth(text: String): Float = measureTextWidth(text, 0)

    override fun measureIconWidth(iconId: Int): Float = iconId.toFloat()

    override fun getFontMetrics(): FloatArray = floatArrayOf(10f, 8f, 2f, 0f)
}
