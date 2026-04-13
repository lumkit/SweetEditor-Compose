package com.qiplat.compose.sweeteditor.core.state

import com.qiplat.compose.sweeteditor.core.EditorCoreRect
import com.qiplat.compose.sweeteditor.core.EditorCoreScrollBehavior
import com.qiplat.compose.sweeteditor.core.EditorCoreScrollMetrics
import com.qiplat.compose.sweeteditor.core.layout.LayoutGeometryQueryResult
import com.qiplat.compose.sweeteditor.core.layout.LayoutLineQueryResult

data class ViewportState(
    val width: Int = 0,
    val height: Int = 0,
    val scrollX: Float = 0f,
    val scrollY: Float = 0f,
) {
    fun withViewport(
        width: Int,
        height: Int,
    ): ViewportState = copy(
        width = width,
        height = height,
    )

    fun withScroll(
        scrollX: Float,
        scrollY: Float,
        maxScrollX: Float,
        maxScrollY: Float,
    ): ViewportState = copy(
        scrollX = scrollX.coerceIn(0f, maxScrollX),
        scrollY = scrollY.coerceIn(0f, maxScrollY),
    )

    fun ensureRectVisible(
        rect: EditorCoreRect,
        maxScrollX: Float,
        maxScrollY: Float,
    ): ViewportState {
        val nextScrollX = when {
            width <= 0 -> scrollX
            rect.x < scrollX -> rect.x
            rect.x > scrollX + width -> rect.x - width
            else -> scrollX
        }
        val nextScrollY = when {
            height <= 0 -> scrollY
            rect.y < scrollY -> rect.y
            rect.y + rect.height > scrollY + height -> rect.y + rect.height - height
            else -> scrollY
        }
        return withScroll(
            scrollX = nextScrollX,
            scrollY = nextScrollY,
            maxScrollX = maxScrollX,
            maxScrollY = maxScrollY,
        )
    }

    fun ensureGeometryVisible(
        geometry: LayoutGeometryQueryResult,
        maxScrollX: Float,
        maxScrollY: Float,
    ): ViewportState =
        ensureRectVisible(
            rect = geometry.cursor.rect,
            maxScrollX = maxScrollX,
            maxScrollY = maxScrollY,
        )

    fun scrollToLine(
        targetTop: Float,
        lineHeight: Float,
        behavior: EditorCoreScrollBehavior,
        maxScrollY: Float,
    ): ViewportState {
        val nextScrollY = when (behavior) {
            EditorCoreScrollBehavior.GoToTop -> targetTop
            EditorCoreScrollBehavior.GoToCenter -> targetTop - height / 2f
            EditorCoreScrollBehavior.GoToBottom -> targetTop - height + lineHeight
        }
        return withScroll(
            scrollX = scrollX,
            scrollY = nextScrollY,
            maxScrollX = Float.MAX_VALUE,
            maxScrollY = maxScrollY,
        )
    }
    fun scrollBy(
        dx: Float,
        dy: Float,
        maxScrollX: Float,
        maxScrollY: Float,
    ): ViewportState =
        withScroll(
            scrollX = scrollX + dx,
            scrollY = scrollY + dy,
            maxScrollX = maxScrollX,
            maxScrollY = maxScrollY,
        )


    fun scrollToLine(
        lineQuery: LayoutLineQueryResult,
        behavior: EditorCoreScrollBehavior,
        maxScrollY: Float,
    ): ViewportState =
        scrollToLine(
            targetTop = lineQuery.rect.y,
            lineHeight = lineQuery.rect.height,
            behavior = behavior,
            maxScrollY = maxScrollY,
        )

    fun toScrollMetrics(
        contentWidth: Float,
        contentHeight: Float,
    ): EditorCoreScrollMetrics =
        EditorCoreScrollMetrics(
            scrollX = scrollX,
            scrollY = scrollY,
            maxScrollX = (contentWidth - width).coerceAtLeast(0f),
            maxScrollY = (contentHeight - height).coerceAtLeast(0f),
        )
}
