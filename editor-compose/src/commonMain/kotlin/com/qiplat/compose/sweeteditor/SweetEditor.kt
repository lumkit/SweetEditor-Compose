package com.qiplat.compose.sweeteditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import com.qiplat.compose.sweeteditor.model.foundation.CurrentLineRenderMode
import com.qiplat.compose.sweeteditor.model.foundation.EditorGestureEventType
import com.qiplat.compose.sweeteditor.model.foundation.GesturePoint
import com.qiplat.compose.sweeteditor.model.visual.*
import com.qiplat.compose.sweeteditor.runtime.EditorController
import com.qiplat.compose.sweeteditor.runtime.EditorState
import kotlin.math.min
import com.qiplat.compose.sweeteditor.model.decoration.TextStyle as EditorTextStyle

@OptIn(ExperimentalTextApi::class)
@Composable
fun SweetEditor(
    state: EditorState,
    controller: EditorController,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    gutterBackgroundColor: Color = Color(0xFFF7F7F9),
    selectionColor: Color = Color(0x332196F3),
    currentLineColor: Color = Color(0x11000000),
    cursorColor: Color = Color(0xFF1F1F1F),
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val textMeasurer = rememberTextMeasurer()
    val verticalScrollState = rememberScrollableState { delta ->
        controller.dispatchGestureEvent(
            type = EditorGestureEventType.DirectScroll,
            points = emptyList(),
            wheelDeltaY = -delta,
        )
        delta
    }
    val horizontalScrollState = rememberScrollableState { delta ->
        controller.dispatchGestureEvent(
            type = EditorGestureEventType.DirectScroll,
            points = emptyList(),
            wheelDeltaX = -delta,
        )
        delta
    }

    DisposableEffect(controller) {
        onDispose {
            controller.close()
        }
    }

    LaunchedEffect(controller, state.document) {
        if (state.document != null) {
            controller.refresh()
        }
    }

    LaunchedEffect(controller, state.lastGestureResult.needsAnimation) {
        while (state.lastGestureResult.needsAnimation) {
            withFrameNanos {
                controller.tickAnimations()
            }
        }
    }

    Box(
        modifier = modifier
            .background(backgroundColor)
            .clipToBounds()
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .scrollable(
                state = verticalScrollState,
                orientation = Orientation.Vertical,
            )
            .scrollable(
                state = horizontalScrollState,
                orientation = Orientation.Horizontal,
            )
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    controller.setViewport(size.width, size.height)
                }
            }
            .onPreviewKeyEvent { event ->
                controller.handleComposeKeyEvent(event)
            }
            .pointerInput(controller) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val renderModel = state.renderModel
                    val scrollMetrics = state.scrollMetrics
                    val hitRegion = renderModel?.hitTest(down.position)
                    focusRequester.requestFocus()
                    if (hitRegion == EditorHitRegion.VerticalScrollbarThumb) {
                        trackScrollbarDrag(
                            initialPosition = down.position,
                            renderModel = renderModel,
                            scrollMetrics = scrollMetrics,
                            vertical = true,
                            controller = controller,
                        )
                        return@awaitEachGesture
                    }
                    if (hitRegion == EditorHitRegion.HorizontalScrollbarThumb) {
                        trackScrollbarDrag(
                            initialPosition = down.position,
                            renderModel = renderModel,
                            scrollMetrics = scrollMetrics,
                            vertical = false,
                            controller = controller,
                        )
                        return@awaitEachGesture
                    }
                    controller.dispatchGestureEvent(
                        type = down.type.toDownEventType(),
                        points = listOf(down.position.toGesturePoint()),
                        modifiers = 0,
                    )
                    var active = true
                    while (active) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pressedPoints = event.changes
                            .filter { it.pressed }
                            .map { it.position.toGesturePoint() }
                        if (pressedPoints.isNotEmpty()) {
                            controller.dispatchGestureEvent(
                                type = down.type.toMoveEventType(),
                                points = pressedPoints,
                            )
                        }
                        val released = event.changes.firstOrNull { it.changedToUpIgnoreConsumed() }
                        if (released != null) {
                            controller.dispatchGestureEvent(
                                type = down.type.toUpEventType(),
                                points = listOf(released.position.toGesturePoint()),
                            )
                            active = false
                        }
                    }
                }
            },
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            drawEditorSurface(
                renderModel = state.renderModel,
                textMeasurer = textMeasurer,
                gutterBackgroundColor = gutterBackgroundColor,
                selectionColor = selectionColor,
                currentLineColor = currentLineColor,
                cursorColor = cursorColor,
            )
        }
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawEditorSurface(
    renderModel: EditorRenderModel?,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    gutterBackgroundColor: Color,
    selectionColor: Color,
    currentLineColor: Color,
    cursorColor: Color,
) {
    if (renderModel == null) {
        return
    }

    drawGutterBackground(renderModel, gutterBackgroundColor)
    drawSplitLine(renderModel)
    drawCurrentLine(renderModel, currentLineColor)

    renderModel.selectionRects.forEach { rect ->
        drawSelectionRect(renderModel, rect, selectionColor)
    }

    renderModel.guideSegments.forEach { guide ->
        drawGuide(renderModel, guide)
    }

    renderModel.diagnosticDecorations.forEach { decoration ->
        drawDiagnostic(renderModel, decoration)
    }

    renderModel.linkedEditingRects.forEach { rect ->
        drawLinkedEditing(renderModel, rect)
    }

    renderModel.bracketHighlightRects.forEach { rect ->
        drawRect(
            color = Color(0x2200C853),
            topLeft = Offset(
                x = rect.origin.x - renderModel.scrollX,
                y = rect.origin.y - renderModel.scrollY,
            ),
            size = Size(rect.width, rect.height),
        )
    }

    renderModel.lines.forEach { line ->
        drawLineNumber(renderModel, textMeasurer, line)
        drawRuns(renderModel, textMeasurer, line)
    }

    renderModel.gutterIcons.forEach { item ->
        drawGutterIcon(renderModel, item)
    }

    renderModel.foldMarkers.forEach { marker ->
        drawFoldMarker(renderModel, marker)
    }

    drawSelectionHandle(renderModel.selectionStartHandle.position, renderModel.selectionStartHandle.visible, renderModel)
    drawSelectionHandle(renderModel.selectionEndHandle.position, renderModel.selectionEndHandle.visible, renderModel)

    if (renderModel.cursor.visible) {
        drawRect(
            color = cursorColor,
            topLeft = Offset(
                x = renderModel.cursor.position.x - renderModel.scrollX,
                y = renderModel.cursor.position.y - renderModel.scrollY,
            ),
            size = Size(2f, renderModel.cursor.height.coerceAtLeast(1f)),
        )
    }

    drawScrollbar(renderModel.verticalScrollbar, renderModel)
    drawScrollbar(renderModel.horizontalScrollbar, renderModel)
}

private fun DrawScope.drawGutterBackground(
    renderModel: EditorRenderModel,
    gutterBackgroundColor: Color,
) {
    if (!renderModel.gutterVisible) {
        return
    }
    drawRect(
        color = gutterBackgroundColor,
        topLeft = Offset.Zero,
        size = Size(renderModel.splitX.coerceAtLeast(0f), size.height),
    )
}

private fun DrawScope.drawSplitLine(
    renderModel: EditorRenderModel,
) {
    if (!renderModel.splitLineVisible) {
        return
    }
    drawLine(
        color = Color(0x14000000),
        start = Offset(renderModel.splitX, 0f),
        end = Offset(renderModel.splitX, size.height),
        strokeWidth = 1f,
    )
}

private fun DrawScope.drawCurrentLine(
    renderModel: EditorRenderModel,
    currentLineColor: Color,
) {
    val top = renderModel.currentLine.y - renderModel.scrollY
    val height = renderModel.cursor.height.takeIf { it > 0f } ?: 20f
    when (renderModel.currentLineRenderMode) {
        CurrentLineRenderMode.Background -> {
            drawRect(
                color = currentLineColor,
                topLeft = Offset(0f, top),
                size = Size(size.width, height),
            )
        }

        CurrentLineRenderMode.Border -> {
            drawRect(
                color = currentLineColor.copy(alpha = 0.5f),
                topLeft = Offset(0f, top),
                size = Size(size.width, height),
                style = Stroke(width = 1f),
            )
        }

        CurrentLineRenderMode.None -> Unit
    }
}

private fun DrawScope.drawSelectionRect(
    renderModel: EditorRenderModel,
    rect: SelectionRect,
    color: Color,
) {
    drawRect(
        color = color,
        topLeft = Offset(
            x = rect.origin.x - renderModel.scrollX,
            y = rect.origin.y - renderModel.scrollY,
        ),
        size = Size(rect.width, rect.height),
    )
}

private fun DrawScope.drawGuide(
    renderModel: EditorRenderModel,
    guide: GuideSegment,
) {
    val pathEffect = when (guide.style) {
        GuideStyle.Solid -> null
        GuideStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        GuideStyle.Double -> PathEffect.dashPathEffect(floatArrayOf(1f, 3f))
    }
    drawLine(
        color = Color(0x33000000),
        start = Offset(
            x = guide.start.x - renderModel.scrollX,
            y = guide.start.y - renderModel.scrollY,
        ),
        end = Offset(
            x = guide.end.x - renderModel.scrollX,
            y = guide.end.y - renderModel.scrollY,
        ),
        strokeWidth = if (guide.direction == GuideDirection.Horizontal) 1f else 1.5f,
        pathEffect = pathEffect,
    )
}

private fun DrawScope.drawDiagnostic(
    renderModel: EditorRenderModel,
    decoration: DiagnosticDecoration,
) {
    drawRect(
        color = decoration.color.toComposeColor().copy(alpha = 0.18f),
        topLeft = Offset(
            x = decoration.origin.x - renderModel.scrollX,
            y = decoration.origin.y - renderModel.scrollY,
        ),
        size = Size(decoration.width, decoration.height),
    )
}

private fun DrawScope.drawLinkedEditing(
    renderModel: EditorRenderModel,
    rect: LinkedEditingRect,
) {
    drawRect(
        color = if (rect.isActive) Color(0x33007AFF) else Color(0x22007AFF),
        topLeft = Offset(
            x = rect.origin.x - renderModel.scrollX,
            y = rect.origin.y - renderModel.scrollY,
        ),
        size = Size(rect.width, rect.height),
        style = Stroke(width = 1f),
    )
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawLineNumber(
    renderModel: EditorRenderModel,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    line: VisualLine,
) {
    if (!renderModel.gutterVisible) {
        return
    }
    drawText(
        textMeasurer = textMeasurer,
        text = (line.logicalLine + 1).toString(),
        topLeft = Offset(
            x = line.lineNumberPosition.x,
            y = line.lineNumberPosition.y - renderModel.scrollY,
        ),
        style = TextStyle(color = Color(0x99000000)),
    )
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawRuns(
    renderModel: EditorRenderModel,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    line: VisualLine,
) {
    line.runs.forEach { run ->
        if (!run.shouldRenderText()) {
            return@forEach
        }
        drawText(
            textMeasurer = textMeasurer,
            text = run.text,
            topLeft = Offset(
                x = run.x - renderModel.scrollX,
                y = run.y - renderModel.scrollY,
            ),
            style = run.style.toComposeTextStyle(),
        )
    }
}

private fun DrawScope.drawGutterIcon(
    renderModel: EditorRenderModel,
    item: GutterIconRenderItem,
) {
    drawCircle(
        color = Color(0xFF5C6BC0),
        radius = min(item.width, item.height) / 2f,
        center = Offset(
            x = item.origin.x + item.width / 2f,
            y = item.origin.y - renderModel.scrollY + item.height / 2f,
        ),
    )
}

private fun DrawScope.drawFoldMarker(
    renderModel: EditorRenderModel,
    marker: FoldMarkerRenderItem,
) {
    val left = marker.origin.x
    val top = marker.origin.y - renderModel.scrollY
    val width = marker.width.coerceAtLeast(8f)
    val height = marker.height.coerceAtLeast(8f)
    drawRect(
        color = Color(0x22000000),
        topLeft = Offset(left, top),
        size = Size(width, height),
        style = Stroke(width = 1f),
    )
}

private fun DrawScope.drawSelectionHandle(
    position: PointF,
    visible: Boolean,
    renderModel: EditorRenderModel,
) {
    if (!visible) {
        return
    }
    drawCircle(
        color = Color(0xFF2196F3),
        radius = 6f,
        center = Offset(
            x = position.x - renderModel.scrollX,
            y = position.y - renderModel.scrollY,
        ),
    )
}

private fun DrawScope.drawScrollbar(
    scrollbar: ScrollbarModel,
    renderModel: EditorRenderModel,
) {
    if (!scrollbar.visible) {
        return
    }
    drawRect(
        color = Color.Black.copy(alpha = scrollbar.alpha * 0.12f),
        topLeft = Offset(
            x = scrollbar.track.origin.x - renderModel.scrollX,
            y = scrollbar.track.origin.y - renderModel.scrollY,
        ),
        size = Size(scrollbar.track.width, scrollbar.track.height),
    )
    drawRect(
        color = if (scrollbar.thumbActive) Color.Black.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.28f),
        topLeft = Offset(
            x = scrollbar.thumb.origin.x - renderModel.scrollX,
            y = scrollbar.thumb.origin.y - renderModel.scrollY,
        ),
        size = Size(scrollbar.thumb.width, scrollbar.thumb.height),
    )
}

private fun VisualRun.shouldRenderText(): Boolean = text.isNotEmpty()

private fun EditorTextStyle.toComposeTextStyle(): TextStyle {
    val decorations = buildList {
        if ((fontStyle and EditorTextStyle.Strikethrough) != 0) {
            add(TextDecoration.LineThrough)
        }
    }
    return TextStyle(
        color = color.toComposeColor(),
        background = backgroundColor.toComposeColor(),
        fontWeight = if ((fontStyle and EditorTextStyle.Bold) != 0) FontWeight.Bold else null,
        fontStyle = if ((fontStyle and EditorTextStyle.Italic) != 0) FontStyle.Italic else null,
        textDecoration = decorations.takeIf { it.isNotEmpty() }?.reduce(TextDecoration::plus),
    )
}

private fun PointerType.toDownEventType(): EditorGestureEventType = when (this) {
    PointerType.Mouse -> EditorGestureEventType.MouseDown
    else -> EditorGestureEventType.TouchDown
}

private fun PointerType.toMoveEventType(): EditorGestureEventType = when (this) {
    PointerType.Mouse -> EditorGestureEventType.MouseMove
    else -> EditorGestureEventType.TouchMove
}

private fun PointerType.toUpEventType(): EditorGestureEventType = when (this) {
    PointerType.Mouse -> EditorGestureEventType.MouseUp
    else -> EditorGestureEventType.TouchUp
}

private fun Offset.toGesturePoint(): GesturePoint = GesturePoint(x = x, y = y)

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.trackScrollbarDrag(
    initialPosition: Offset,
    renderModel: EditorRenderModel?,
    scrollMetrics: com.qiplat.compose.sweeteditor.model.visual.ScrollMetrics,
    vertical: Boolean,
    controller: EditorController,
) {
    if (renderModel == null) {
        return
    }
    val scrollbar = if (vertical) renderModel.verticalScrollbar else renderModel.horizontalScrollbar
    val thumb = scrollbar.thumb
    val track = scrollbar.track
    val initialThumbOrigin = if (vertical) thumb.origin.y else thumb.origin.x
    val initialPointer = if (vertical) initialPosition.y else initialPosition.x
    var active = true
    while (active) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val change = event.changes.firstOrNull()
        if (change == null) {
            continue
        }
        val pointerValue = if (vertical) change.position.y else change.position.x
        val delta = pointerValue - initialPointer
        val trackStart = if (vertical) track.origin.y else track.origin.x
        val trackSize = if (vertical) track.height else track.width
        val thumbSize = if (vertical) thumb.height else thumb.width
        val newThumbOrigin = (initialThumbOrigin + delta)
            .coerceIn(trackStart, trackStart + trackSize - thumbSize)
        val progress = if (trackSize <= thumbSize) {
            0f
        } else {
            (newThumbOrigin - trackStart) / (trackSize - thumbSize)
        }
        if (vertical) {
            controller.setScroll(
                scrollX = scrollMetrics.scrollX,
                scrollY = scrollMetrics.maxScrollY * progress,
            )
        } else {
            controller.setScroll(
                scrollX = scrollMetrics.maxScrollX * progress,
                scrollY = scrollMetrics.scrollY,
            )
        }
        if (change.changedToUpIgnoreConsumed()) {
            active = false
        }
    }
}

private enum class EditorHitRegion {
    VerticalScrollbarThumb,
    HorizontalScrollbarThumb,
    SelectionStartHandle,
    SelectionEndHandle,
    FoldMarker,
    GutterIcon,
    Content,
}

private fun EditorRenderModel.hitTest(position: Offset): EditorHitRegion {
    if (verticalScrollbar.visible && verticalScrollbar.thumb.contains(position)) {
        return EditorHitRegion.VerticalScrollbarThumb
    }
    if (horizontalScrollbar.visible && horizontalScrollbar.thumb.contains(position)) {
        return EditorHitRegion.HorizontalScrollbarThumb
    }
    if (selectionStartHandle.visible && selectionStartHandle.position.containsHandle(position)) {
        return EditorHitRegion.SelectionStartHandle
    }
    if (selectionEndHandle.visible && selectionEndHandle.position.containsHandle(position)) {
        return EditorHitRegion.SelectionEndHandle
    }
    if (foldMarkers.any { it.contains(position, scrollY) }) {
        return EditorHitRegion.FoldMarker
    }
    if (gutterIcons.any { it.contains(position, scrollY) }) {
        return EditorHitRegion.GutterIcon
    }
    return EditorHitRegion.Content
}

private fun com.qiplat.compose.sweeteditor.model.visual.ScrollbarRect.contains(position: Offset): Boolean =
    position.x in origin.x..(origin.x + width) &&
        position.y in origin.y..(origin.y + height)

private fun PointF.containsHandle(position: Offset, radius: Float = 16f): Boolean =
    position.x in (x - radius)..(x + radius) &&
        position.y in (y - radius)..(y + radius)

private fun FoldMarkerRenderItem.contains(position: Offset, scrollY: Float): Boolean =
    position.x in origin.x..(origin.x + width) &&
        position.y in (origin.y - scrollY)..(origin.y - scrollY + height)

private fun GutterIconRenderItem.contains(position: Offset, scrollY: Float): Boolean =
    position.x in origin.x..(origin.x + width) &&
        position.y in (origin.y - scrollY)..(origin.y - scrollY + height)

private fun EditorController.handleComposeKeyEvent(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) {
        return false
    }
    val text = event.toInsertedText()
    val result = handleKeyEvent(
        keyCode = event.key.keyCode.toInt(),
        text = text,
        modifiers = event.toNativeModifiers(),
    )
    return result.handled || text != null
}

private fun KeyEvent.toNativeModifiers(): Int {
    var value = 0
    if (isShiftPressed) {
        value = value or 1
    }
    if (isCtrlPressed) {
        value = value or 2
    }
    if (isAltPressed) {
        value = value or 4
    }
    if (isMetaPressed) {
        value = value or 8
    }
    return value
}

private fun KeyEvent.toInsertedText(): String? {
    val codePoint = utf16CodePoint
    if (codePoint <= 0 || codePoint < 32) {
        return null
    }
    return codePoint.toChar().toString()
}

private fun Int.toComposeColor(): Color = Color(this)
