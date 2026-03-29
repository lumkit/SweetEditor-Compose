package com.qiplat.compose.sweeteditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.qiplat.compose.sweeteditor.theme.EditorTheme
import kotlin.math.min
import com.qiplat.compose.sweeteditor.model.decoration.TextStyle as EditorTextStyle

@OptIn(ExperimentalTextApi::class)
@Composable
fun SweetEditor(
    state: EditorState,
    controller: EditorController,
    modifier: Modifier = Modifier,
    theme: EditorTheme = EditorTheme.dark(),
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val textMeasurer = rememberTextMeasurer()

    DisposableEffect(controller) {
        onDispose {
            controller.close()
        }
    }

    LaunchedEffect(controller, state.document) {
        if (state.document != null) {
            controller.onFontMetricsChanged()
            controller.refresh()
        }
    }

    LaunchedEffect(controller, theme.fontFamily, theme.fontSize, theme.lineNumberFontSize, theme.inlayHintFontSize) {
        controller.onFontMetricsChanged()
    }

    LaunchedEffect(controller, state.lastGestureResult.needsAnimation) {
        while (state.lastGestureResult.needsAnimation) {
            withFrameNanos {
                controller.tickAnimations()
            }
        }
    }

    Canvas(
        modifier = modifier
            .clipToBounds()
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
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
        drawEditorSurface(
            renderModel = state.renderModel,
            textMeasurer = textMeasurer,
            theme = theme,
        )
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawEditorSurface(
    renderModel: EditorRenderModel?,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    theme: EditorTheme,
) {
    if (renderModel == null) {
        drawRect(
            color = theme.backgroundColor.toComposeColor(),
            topLeft = Offset.Zero,
            size = size,
        )
        return
    }

    drawRect(
        color = theme.backgroundColor.toComposeColor(),
        topLeft = Offset.Zero,
        size = size,
    )
    drawGutterBackground(renderModel, theme.gutterBackgroundColor.toComposeColor())
    drawSplitLine(renderModel, theme.splitLineColor.toComposeColor())
    drawCurrentLine(renderModel, theme.currentLineColor.toComposeColor())

    renderModel.selectionRects.forEach { rect ->
        drawSelectionRect(renderModel, rect, theme.selectionColor.toComposeColor())
    }

    renderModel.guideSegments.forEach { guide ->
        drawGuide(renderModel, guide, theme.guideColor.toComposeColor())
    }

    renderModel.diagnosticDecorations.forEach { decoration ->
        drawDiagnostic(renderModel, decoration)
    }

    drawCompositionDecoration(renderModel, theme.compositionUnderlineColor.toComposeColor())

    renderModel.linkedEditingRects.forEach { rect ->
        drawLinkedEditing(
            renderModel = renderModel,
            rect = rect,
            activeColor = theme.linkedEditingActiveColor.toComposeColor(),
            inactiveColor = theme.linkedEditingInactiveColor.toComposeColor(),
        )
    }

    renderModel.bracketHighlightRects.forEach { rect ->
        drawRect(
            color = theme.bracketHighlightBackgroundColor.toComposeColor(),
            topLeft = Offset(
                x = rect.origin.x,
                y = rect.origin.y,
            ),
            size = Size(rect.width, rect.height),
        )
        drawRect(
            color = theme.bracketHighlightBorderColor.toComposeColor(),
            topLeft = Offset(
                x = rect.origin.x,
                y = rect.origin.y,
            ),
            size = Size(rect.width, rect.height),
            style = Stroke(width = 1f),
        )
    }

    renderModel.lines.forEach { line ->
        drawLineNumber(renderModel, textMeasurer, line, theme)
        drawRuns(textMeasurer, line, theme)
    }

    renderModel.gutterIcons.forEach { item ->
        drawGutterIcon(renderModel, item, theme)
    }

    renderModel.foldMarkers.forEach { marker ->
        drawFoldMarker(renderModel, marker, theme)
    }

    drawSelectionHandle(
        position = renderModel.selectionStartHandle.position,
        visible = renderModel.selectionStartHandle.visible,
        renderModel = renderModel,
        color = theme.cursorColor.toComposeColor(),
    )
    drawSelectionHandle(
        position = renderModel.selectionEndHandle.position,
        visible = renderModel.selectionEndHandle.visible,
        renderModel = renderModel,
        color = theme.cursorColor.toComposeColor(),
    )

    if (renderModel.cursor.visible) {
        drawRect(
            color = theme.cursorColor.toComposeColor(),
            topLeft = Offset(
                x = renderModel.cursor.position.x,
                y = renderModel.cursor.position.y,
            ),
            size = Size(2f, renderModel.cursor.height.coerceAtLeast(1f)),
        )
    }

    drawScrollbar(renderModel.verticalScrollbar, renderModel, theme)
    drawScrollbar(renderModel.horizontalScrollbar, renderModel, theme)
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
    color: Color,
) {
    if (!renderModel.splitLineVisible) {
        return
    }
    drawLine(
        color = color,
        start = Offset(renderModel.splitX, 0f),
        end = Offset(renderModel.splitX, size.height),
        strokeWidth = 1f,
    )
}

private fun DrawScope.drawCurrentLine(
    renderModel: EditorRenderModel,
    currentLineColor: Color,
) {
    val top = renderModel.currentLine.y
    val height = renderModel.cursor.height.takeIf { it > 0f } ?: 20f
    val width = renderModel.viewportWidth.takeIf { it > 0f } ?: size.width
    when (renderModel.currentLineRenderMode) {
        CurrentLineRenderMode.Background -> {
            drawRect(
                color = currentLineColor,
                topLeft = Offset(0f, top),
                size = Size(width, height),
            )
        }

        CurrentLineRenderMode.Border -> {
            drawRect(
                color = currentLineColor.copy(alpha = 0.5f),
                topLeft = Offset(0f, top),
                size = Size(width, height),
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
            x = rect.origin.x,
            y = rect.origin.y,
        ),
        size = Size(rect.width, rect.height),
    )
}

private fun DrawScope.drawGuide(
    renderModel: EditorRenderModel,
    guide: GuideSegment,
    color: Color,
) {
    val pathEffect = when (guide.style) {
        GuideStyle.Solid -> null
        GuideStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        GuideStyle.Double -> PathEffect.dashPathEffect(floatArrayOf(1f, 3f))
    }
    drawLine(
        color = color,
        start = Offset(
            x = guide.start.x,
            y = guide.start.y,
        ),
        end = Offset(
            x = guide.end.x,
            y = guide.end.y,
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
            x = decoration.origin.x,
            y = decoration.origin.y,
        ),
        size = Size(decoration.width, decoration.height),
    )
}

private fun DrawScope.drawCompositionDecoration(
    renderModel: EditorRenderModel,
    color: Color,
) {
    val decoration = renderModel.compositionDecoration
    if (!decoration.active || decoration.width <= 0f || decoration.height <= 0f) {
        return
    }
    val underlineY = decoration.origin.y + decoration.height - 1f
    drawLine(
        color = color,
        start = Offset(decoration.origin.x, underlineY),
        end = Offset(decoration.origin.x + decoration.width, underlineY),
        strokeWidth = 1.5f,
    )
}

private fun DrawScope.drawLinkedEditing(
    renderModel: EditorRenderModel,
    rect: LinkedEditingRect,
    activeColor: Color,
    inactiveColor: Color,
) {
    drawRect(
        color = if (rect.isActive) activeColor else inactiveColor,
        topLeft = Offset(
            x = rect.origin.x,
            y = rect.origin.y,
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
    theme: EditorTheme,
) {
    if (!renderModel.gutterVisible || line.wrapIndex != 0 || line.isPhantomLine) {
        return
    }
    val style = TextStyle(
        color = if (line.logicalLine == renderModel.cursor.textPosition.line) {
            theme.currentLineNumberColor.toComposeColor()
        } else {
            theme.lineNumberColor.toComposeColor()
        },
        fontFamily = theme.fontFamily,
        fontSize = theme.lineNumberFontSize,
    )
    drawBaselineText(
        textMeasurer = textMeasurer,
        text = (line.logicalLine + 1).toString(),
        x = line.lineNumberPosition.x,
        baselineY = line.lineNumberPosition.y,
        style = style,
    )
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawRuns(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    line: VisualLine,
    theme: EditorTheme,
) {
    line.runs.forEach { run ->
        if (!run.shouldRenderText()) {
            return@forEach
        }
        drawBaselineText(
            textMeasurer = textMeasurer,
            text = run.text,
            x = run.x,
            baselineY = run.y,
            style = run.style.toComposeTextStyle(theme),
        )
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawBaselineText(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    x: Float,
    baselineY: Float,
    style: TextStyle,
) {
    val layoutResult = textMeasurer.measure(
        text = text,
        style = style,
    )
    drawText(
        textLayoutResult = layoutResult,
        topLeft = Offset(
            x = x,
            y = baselineY - layoutResult.firstBaseline,
        ),
    )
}

private fun DrawScope.drawGutterIcon(
    renderModel: EditorRenderModel,
    item: GutterIconRenderItem,
    theme: EditorTheme,
) {
    drawCircle(
        color = theme.inlayHintIconColor.toComposeColor(),
        radius = min(item.width, item.height) / 2f,
        center = Offset(
            x = item.origin.x + item.width / 2f,
            y = item.origin.y + item.height / 2f,
        ),
    )
}

private fun DrawScope.drawFoldMarker(
    renderModel: EditorRenderModel,
    marker: FoldMarkerRenderItem,
    theme: EditorTheme,
) {
    val left = marker.origin.x
    val top = marker.origin.y
    val width = marker.width.coerceAtLeast(8f)
    val height = marker.height.coerceAtLeast(8f)
    drawRect(
        color = theme.foldPlaceholderTextColor.toComposeColor().copy(alpha = 0.35f),
        topLeft = Offset(left, top),
        size = Size(width, height),
        style = Stroke(width = 1f),
    )
}

private fun DrawScope.drawSelectionHandle(
    position: PointF,
    visible: Boolean,
    renderModel: EditorRenderModel,
    color: Color,
) {
    if (!visible) {
        return
    }
    drawCircle(
        color = color,
        radius = 6f,
        center = Offset(
            x = position.x,
            y = position.y,
        ),
    )
}

private fun DrawScope.drawScrollbar(
    scrollbar: ScrollbarModel,
    renderModel: EditorRenderModel,
    theme: EditorTheme,
) {
    if (!scrollbar.visible) {
        return
    }
    drawRect(
        color = theme.scrollbarTrackColor.toComposeColor().copy(alpha = scrollbar.alpha.coerceIn(0f, 1f)),
        topLeft = Offset(
            x = scrollbar.track.origin.x,
            y = scrollbar.track.origin.y,
        ),
        size = Size(scrollbar.track.width, scrollbar.track.height),
    )
    drawRect(
        color = if (scrollbar.thumbActive) {
            theme.scrollbarThumbActiveColor.toComposeColor()
        } else {
            theme.scrollbarThumbColor.toComposeColor()
        },
        topLeft = Offset(
            x = scrollbar.thumb.origin.x,
            y = scrollbar.thumb.origin.y,
        ),
        size = Size(scrollbar.thumb.width, scrollbar.thumb.height),
    )
}

private fun VisualRun.shouldRenderText(): Boolean = text.isNotEmpty()

private fun EditorTextStyle.toComposeTextStyle(theme: EditorTheme): TextStyle {
    val decorations = buildList {
        if ((fontStyle and EditorTextStyle.Strikethrough) != 0) {
            add(TextDecoration.LineThrough)
        }
    }
    return TextStyle(
        color = if (color != 0) color.toComposeColor() else theme.textColor.toComposeColor(),
        background = if (backgroundColor != 0) backgroundColor.toComposeColor() else Color.Transparent,
        fontWeight = if ((fontStyle and EditorTextStyle.Bold) != 0) FontWeight.Bold else null,
        fontStyle = if ((fontStyle and EditorTextStyle.Italic) != 0) FontStyle.Italic else null,
        textDecoration = decorations.takeIf { it.isNotEmpty() }?.reduce(TextDecoration::plus),
        fontFamily = theme.fontFamily,
        fontSize = theme.fontSize,
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
    if (foldMarkers.any { it.contains(position) }) {
        return EditorHitRegion.FoldMarker
    }
    if (gutterIcons.any { it.contains(position) }) {
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

private fun FoldMarkerRenderItem.contains(position: Offset): Boolean =
    position.x in origin.x..(origin.x + width) &&
        position.y in origin.y..(origin.y + height)

private fun GutterIconRenderItem.contains(position: Offset): Boolean =
    position.x in origin.x..(origin.x + width) &&
        position.y in origin.y..(origin.y + height)

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
