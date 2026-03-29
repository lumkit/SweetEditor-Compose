package com.qiplat.compose.sweeteditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import com.qiplat.compose.sweeteditor.model.foundation.*
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
    onGestureResult: (GestureResult) -> Unit = {},
    onHitTarget: (HitTarget) -> Unit = {},
    onContextMenuRequest: (EditorContextMenuRequest) -> Unit = {},
    onSelectionHandleDragStateChange: (EditorSelectionHandleDragState) -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val textMeasurer = rememberTextMeasurer()
    val platformScale = state.lastGestureResult.viewScale
        .takeIf { it > 0f }
        ?: state.scrollMetrics.scale.takeIf { it > 0f }
        ?: 1f
    val renderScale = state.scrollMetrics.scale.takeIf { it > 0f } ?: platformScale
    val scaledTheme = remember(theme, renderScale) {
        theme.scaled(renderScale)
    }
    var isFocused by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        onDispose {
            if (controller.isComposing()) {
                controller.compositionCancel()
            }
            controller.close()
        }
    }

    LaunchedEffect(controller, state.document) {
        if (state.document != null) {
            controller.syncPlatformScale(platformScale)
        }
    }

    LaunchedEffect(controller, platformScale) {
        if (state.document != null) {
            controller.syncPlatformScale(platformScale)
        }
    }

    LaunchedEffect(controller, theme.fontFamily, theme.fontSize, theme.lineNumberFontSize, theme.inlayHintFontSize, platformScale) {
        if (state.document != null) {
            controller.syncPlatformScale(platformScale)
        }
    }

    LaunchedEffect(controller, state.lastGestureResult.needsAnimation) {
        while (state.lastGestureResult.needsAnimation) {
            withFrameNanos {
                controller.tickAnimations()
            }
        }
    }

    LaunchedEffect(state.lastGestureResult) {
        val result = state.lastGestureResult
        if (result.type != GestureType.Undefined) {
            onGestureResult(result)
        }
        if (result.hitTarget.type != HitTargetType.None) {
            onHitTarget(result.hitTarget)
        }
        if (result.type == GestureType.ContextMenu) {
            onContextMenuRequest(
                EditorContextMenuRequest(
                    gestureResult = result,
                ),
            )
        }
    }

    LaunchedEffect(
        state.lastGestureResult.isHandleDrag,
        state.renderModel?.selectionStartHandle,
        state.renderModel?.selectionEndHandle,
    ) {
        val renderModel = state.renderModel ?: return@LaunchedEffect
        onSelectionHandleDragStateChange(
            EditorSelectionHandleDragState(
                active = state.lastGestureResult.isHandleDrag,
                gestureResult = state.lastGestureResult,
                startHandle = renderModel.selectionStartHandle,
                endHandle = renderModel.selectionEndHandle,
            ),
        )
    }

    val platformImeModifier = InstallPlatformImeSession(
        controller = controller,
        state = state,
        isFocused = isFocused,
    )

    Canvas(
        modifier = modifier
            .clipToBounds()
            .then(platformImeModifier)
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    controller.setViewport(size.width, size.height)
                }
            }
            .onPreviewKeyEvent { event ->
                controller.handleComposeKeyEvent(event)
            }
            .pointerInput(controller) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val scrollDelta = event.changes.firstOrNull()?.scrollDelta ?: Offset.Zero
                        if (scrollDelta != Offset.Zero) {
                            controller.dispatchGestureEvent(
                                type = EditorGestureEventType.MouseWheel,
                                points = emptyList(),
                                modifiers = event.toNativeModifiers(),
                                wheelDeltaX = scrollDelta.x,
                                wheelDeltaY = scrollDelta.y,
                            )
                        }
                    }
                }
            }
            .pointerInput(controller) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val eventModifiers = event.toNativeModifiers()
                        val allPoints = event.changes.map { it.position.toGesturePoint() }
                        val downChanges = event.changes.filter { it.changedToDownIgnoreConsumed() }
                        val upChanges = event.changes.filter { it.changedToUpIgnoreConsumed() }
                        val pressedChanges = event.changes.filter { it.pressed }
                        val movedPressedPoints = pressedChanges
                            .filter { it.position != it.previousPosition }
                            .map { it.position.toGesturePoint() }

                        if (downChanges.isNotEmpty()) {
                            val primaryDown = downChanges.first()
                            focusRequester.requestFocus()
                            if (primaryDown.type == PointerType.Touch && pressedChanges.size > 1 && allPoints.isNotEmpty()) {
                                controller.dispatchGestureEvent(
                                    type = EditorGestureEventType.TouchPointerDown,
                                    points = allPoints,
                                    modifiers = eventModifiers,
                                )
                            } else {
                                controller.dispatchGestureEvent(
                                    type = primaryDown.type.toDownEventType(
                                        isSecondaryPressed = event.buttons.isSecondaryPressed,
                                    ),
                                    points = listOf(primaryDown.position.toGesturePoint()),
                                    modifiers = eventModifiers,
                                )
                            }
                        } else if (upChanges.isNotEmpty()) {
                            val primaryUp = upChanges.first()
                            if (primaryUp.type == PointerType.Touch && pressedChanges.isNotEmpty() && allPoints.isNotEmpty()) {
                                controller.dispatchGestureEvent(
                                    type = EditorGestureEventType.TouchPointerUp,
                                    points = allPoints,
                                    modifiers = eventModifiers,
                                )
                            } else {
                                controller.dispatchGestureEvent(
                                    type = primaryUp.type.toUpEventType(),
                                    points = listOf(primaryUp.position.toGesturePoint()),
                                    modifiers = eventModifiers,
                                )
                            }
                        } else if (movedPressedPoints.isNotEmpty()) {
                            val pointerType = pressedChanges.firstOrNull()?.type ?: PointerType.Touch
                            val scaleDelta = if (pointerType == PointerType.Touch && pressedChanges.size >= 2) {
                                pressedChanges.calculateScaleDelta()
                            } else {
                                1f
                            }
                            val movePoints = if (pointerType == PointerType.Touch) {
                                pressedChanges.map { it.position.toGesturePoint() }
                            } else {
                                movedPressedPoints
                            }
                            controller.dispatchGestureEvent(
                                type = pointerType.toMoveEventType(),
                                points = movePoints,
                                modifiers = eventModifiers,
                            )
                            if (pointerType == PointerType.Touch && pressedChanges.size >= 2 && scaleDelta != 1f) {
                                controller.dispatchGestureEvent(
                                    type = EditorGestureEventType.DirectScale,
                                    points = listOf(pressedChanges.calculateCentroidPoint()),
                                    modifiers = eventModifiers,
                                    directScale = scaleDelta,
                                )
                            }
                        }
                    }
                }
            },
    ) {
        drawEditorSurface(
            renderModel = state.renderModel,
            textMeasurer = textMeasurer,
            theme = scaledTheme,
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
        drawRuns(textMeasurer, line, theme)
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

    drawGutterBackground(renderModel, theme.gutterBackgroundColor.toComposeColor())
    renderModel.lines.forEach { line ->
        drawLineNumber(renderModel, textMeasurer, line, theme)
    }

    renderModel.gutterIcons.forEach { item ->
        drawGutterIcon(renderModel, item, theme)
    }

    renderModel.foldMarkers.forEach { marker ->
        drawFoldMarker(renderModel, marker, theme)
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

private fun EditorTheme.scaled(scale: Float): EditorTheme {
    val normalizedScale = scale.coerceAtLeast(0.1f)
    return copy(
        fontSize = fontSize * normalizedScale,
        lineNumberFontSize = lineNumberFontSize * normalizedScale,
        inlayHintFontSize = inlayHintFontSize * normalizedScale,
    )
}

private fun PointerType.toDownEventType(isSecondaryPressed: Boolean): EditorGestureEventType = when (this) {
    PointerType.Mouse -> if (isSecondaryPressed) {
        EditorGestureEventType.MouseRightDown
    } else {
        EditorGestureEventType.MouseDown
    }
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

private fun List<androidx.compose.ui.input.pointer.PointerInputChange>.calculateCentroidPoint(): GesturePoint {
    if (isEmpty()) {
        return GesturePoint()
    }
    val centerX = sumOf { it.position.x.toDouble() }.toFloat() / size
    val centerY = sumOf { it.position.y.toDouble() }.toFloat() / size
    return GesturePoint(centerX, centerY)
}

private fun List<androidx.compose.ui.input.pointer.PointerInputChange>.calculateScaleDelta(): Float {
    if (size < 2) {
        return 1f
    }
    val currentCentroidX = sumOf { it.position.x.toDouble() }.toFloat() / size
    val currentCentroidY = sumOf { it.position.y.toDouble() }.toFloat() / size
    val previousCentroidX = sumOf { it.previousPosition.x.toDouble() }.toFloat() / size
    val previousCentroidY = sumOf { it.previousPosition.y.toDouble() }.toFloat() / size

    val currentRadius = map {
        val dx = it.position.x - currentCentroidX
        val dy = it.position.y - currentCentroidY
        kotlin.math.sqrt(dx * dx + dy * dy)
    }.average().toFloat()
    val previousRadius = map {
        val dx = it.previousPosition.x - previousCentroidX
        val dy = it.previousPosition.y - previousCentroidY
        kotlin.math.sqrt(dx * dx + dy * dy)
    }.average().toFloat()

    if (previousRadius <= 0.0001f || currentRadius <= 0.0001f) {
        return 1f
    }

    val scaleDelta = currentRadius / previousRadius
    return if (scaleDelta.isFinite() && kotlin.math.abs(scaleDelta - 1f) > 0.001f) {
        scaleDelta
    } else {
        1f
    }
}

private fun androidx.compose.ui.input.pointer.PointerEvent.toNativeModifiers(): Int {
    var value = 0
    if (keyboardModifiers.isShiftPressed) {
        value = value or 1
    }
    if (keyboardModifiers.isCtrlPressed) {
        value = value or 2
    }
    if (keyboardModifiers.isAltPressed) {
        value = value or 4
    }
    if (keyboardModifiers.isMetaPressed) {
        value = value or 8
    }
    return value
}

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
