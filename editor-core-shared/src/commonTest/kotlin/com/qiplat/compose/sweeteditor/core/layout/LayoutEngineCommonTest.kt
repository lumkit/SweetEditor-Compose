package com.qiplat.compose.sweeteditor.core.layout

import com.qiplat.compose.sweeteditor.core.EditorCoreTextMeasurer
import com.qiplat.compose.sweeteditor.core.EditorCoreTextPosition
import com.qiplat.compose.sweeteditor.core.EditorCoreTextRange
import com.qiplat.compose.sweeteditor.core.EditorCoreWrapMode
import com.qiplat.compose.sweeteditor.core.document.DocumentStore
import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutEngineCommonTest {
    @Test
    fun layoutReturnsVisibleLinesForViewport() {
        val engine = LayoutEngine(FakeTextMeasurer())
        val store = DocumentStore("alpha\nbeta\ngamma")

        val snapshot = engine.layout(
            documentStore = store,
            viewportWidth = 200,
            viewportHeight = 25,
            scrollY = 0f,
            settings = LayoutSettings(),
        )

        assertEquals(3, snapshot.lines.size)
        assertEquals("alpha", snapshot.lines[0].text)
        assertEquals("beta", snapshot.lines[1].text)
        assertEquals(30f, snapshot.contentHeight)
    }

    @Test
    fun layoutWrapsLineWhenCharBreakEnabled() {
        val engine = LayoutEngine(FakeTextMeasurer())
        val store = DocumentStore("abcdef")

        val snapshot = engine.layout(
            documentStore = store,
            viewportWidth = 3,
            viewportHeight = 40,
            scrollY = 0f,
            settings = LayoutSettings(
                wrapMode = EditorCoreWrapMode.CharBreak,
            ),
        )

        assertEquals(2, snapshot.lines.size)
        assertEquals("abc", snapshot.lines[0].text)
        assertEquals("def", snapshot.lines[1].text)
    }

    @Test
    fun selectionRectsCoverSelectedColumns() {
        val engine = LayoutEngine(FakeTextMeasurer())
        val store = DocumentStore("alpha\nbeta")

        val rects = engine.buildSelectionRects(
            documentStore = store,
            range = EditorCoreTextRange(
                start = EditorCoreTextPosition(0, 1),
                end = EditorCoreTextPosition(1, 2),
            ),
            viewportWidth = 200,
            settings = LayoutSettings(),
        )

        assertEquals(2, rects.size)
        assertEquals(1f, rects[0].x)
        assertEquals(4f, rects[0].width)
        assertEquals(2f, rects[1].width)
    }

    @Test
    fun hitTestResolvesNearestPositionFromPoint() {
        val engine = LayoutEngine(FakeTextMeasurer())
        val store = DocumentStore("alpha\nbeta")

        val hit = engine.hitTest(
            documentStore = store,
            x = 2.6f,
            y = 11f,
            viewportWidth = 200,
            settings = LayoutSettings(),
        )

        assertEquals(EditorCoreTextPosition(1, 3), hit.position)
        assertEquals(10f, hit.rect.y)
    }

    @Test
    fun hitTestResolvesWrappedVisualLine() {
        val engine = LayoutEngine(FakeTextMeasurer())
        val store = DocumentStore("abcdef")

        val hit = engine.hitTest(
            documentStore = store,
            x = 1.2f,
            y = 12f,
            viewportWidth = 3,
            settings = LayoutSettings(
                wrapMode = EditorCoreWrapMode.CharBreak,
            ),
        )

        assertEquals(EditorCoreTextPosition(0, 4), hit.position)
        assertEquals(1, hit.visualLine.wrapIndex)
        assertEquals("def", hit.visualLine.text)
    }

    @Test
    fun measurePositionRectUsesWrappedVisualLineTop() {
        val engine = LayoutEngine(FakeTextMeasurer())
        val store = DocumentStore("abcdef")

        val rect = engine.measurePositionRect(
            documentStore = store,
            position = EditorCoreTextPosition(0, 4),
            viewportWidth = 3,
            settings = LayoutSettings(
                wrapMode = EditorCoreWrapMode.CharBreak,
            ),
        )

        assertEquals(10f, rect.y)
        assertEquals(1f, rect.x)
    }
}

private class FakeTextMeasurer : EditorCoreTextMeasurer {
    override fun measureTextWidth(text: String, fontStyle: Int): Float = text.length.toFloat()

    override fun measureInlayHintWidth(text: String): Float = text.length.toFloat()

    override fun measureIconWidth(iconId: Int): Float = iconId.toFloat()

    override fun getFontMetrics(): FloatArray = floatArrayOf(10f, 8f, 2f, 0f)
}
