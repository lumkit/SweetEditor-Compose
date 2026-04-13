package com.qiplat.compose.sweeteditor.core.state

import com.qiplat.compose.sweeteditor.core.EditorCoreRect
import com.qiplat.compose.sweeteditor.core.EditorCoreScrollBehavior
import com.qiplat.compose.sweeteditor.core.EditorCoreTextPosition
import com.qiplat.compose.sweeteditor.core.layout.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ViewportStateCommonTest {
    @Test
    fun ensureRectVisibleAdjustsScrollToRevealRect() {
        val state = ViewportState(
            width = 10,
            height = 10,
            scrollX = 0f,
            scrollY = 0f,
        )

        val nextState = state.ensureRectVisible(
            rect = EditorCoreRect(
                x = 0f,
                y = 25f,
                width = 0f,
                height = 10f,
            ),
            maxScrollX = 0f,
            maxScrollY = 40f,
        )

        assertEquals(25f, nextState.scrollY)
    }

    @Test
    fun scrollToLineSupportsCenterBehavior() {
        val state = ViewportState(
            width = 10,
            height = 20,
            scrollX = 0f,
            scrollY = 0f,
        )

        val nextState = state.scrollToLine(
            targetTop = 60f,
            lineHeight = 10f,
            behavior = EditorCoreScrollBehavior.GoToCenter,
            maxScrollY = 100f,
        )

        assertEquals(50f, nextState.scrollY)
    }

    @Test
    fun ensureGeometryVisibleUsesCursorRect() {
        val state = ViewportState(
            width = 10,
            height = 10,
            scrollX = 0f,
            scrollY = 0f,
        )

        val nextState = state.ensureGeometryVisible(
            geometry = LayoutGeometryQueryResult(
                cursor = LayoutPositionQueryResult(
                    position = EditorCoreTextPosition(0, 0),
                    rect = EditorCoreRect(
                        x = 0f,
                        y = 25f,
                        width = 0f,
                        height = 10f,
                    ),
                    visualLine = fakeVisualLine(top = 25f),
                    localColumn = 0,
                    boundary = fakeVisualLine(top = 25f).columnBoundaries.first(),
                ),
                selection = LayoutSelectionQueryResult(
                    range = null,
                    fragments = emptyList(),
                ),
            ),
            maxScrollX = 0f,
            maxScrollY = 40f,
        )

        assertEquals(25f, nextState.scrollY)
    }

    @Test
    fun scrollToLineSupportsVisualLineQuery() {
        val state = ViewportState(
            width = 10,
            height = 20,
            scrollX = 0f,
            scrollY = 0f,
        )

        val nextState = state.scrollToLine(
            lineQuery = LayoutLineQueryResult(
                logicalLine = 2,
                visualLine = fakeVisualLine(top = 60f),
                rect = EditorCoreRect(
                    x = 0f,
                    y = 60f,
                    width = 10f,
                    height = 10f,
                ),
            ),
            behavior = EditorCoreScrollBehavior.GoToCenter,
            maxScrollY = 100f,
        )

        assertEquals(50f, nextState.scrollY)
    }
}

private fun fakeVisualLine(top: Float) = LayoutVisualLine(
    logicalLine = 0,
    wrapIndex = 0,
    startColumn = 0,
    endColumn = 0,
    top = top,
    height = 10f,
    width = 0f,
    text = "",
    columnBoundaries = listOf(
        com.qiplat.compose.sweeteditor.core.layout.LayoutColumnBoundary(
            sourceColumn = 0,
            x = 0f,
        ),
    ),
)
