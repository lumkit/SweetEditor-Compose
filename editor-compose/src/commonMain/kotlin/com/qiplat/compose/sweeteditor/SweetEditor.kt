package com.qiplat.compose.sweeteditor

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.qiplat.compose.sweeteditor.model.foundation.*
import com.qiplat.compose.sweeteditor.model.visual.*
import com.qiplat.compose.sweeteditor.runtime.EditorController
import com.qiplat.compose.sweeteditor.runtime.EditorState
import com.qiplat.compose.sweeteditor.runtime.EditorTextMeasurer
import com.qiplat.compose.sweeteditor.runtime.InstallDecorationProviders
import com.qiplat.compose.sweeteditor.theme.EditorTheme
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.min
import com.qiplat.compose.sweeteditor.model.decoration.TextStyle as EditorTextStyle

@OptIn(ExperimentalTextApi::class)
@Composable
fun SweetEditor(
    controller: SweetEditorController,
    modifier: Modifier = Modifier,
    theme: EditorTheme = controller.getTheme(),
    settings: EditorSettings = controller.getSettings(),
    decorationProviders: List<DecorationProvider> = emptyList(),
    onGestureResult: (GestureResult) -> Unit = {},
    onHitTarget: (HitTarget) -> Unit = {},
    onContextMenuRequest: (EditorContextMenuRequest) -> Unit = {},
    onSelectionHandleDragStateChange: (EditorSelectionHandleDragState) -> Unit = {},
) {
    val mergedDecorationProviders = buildList {
        val providerIds = mutableSetOf<String>()
        decorationProviders.forEach { provider ->
            if (providerIds.add(provider.id)) {
                add(provider)
            }
        }
        controller.attachedDecorationProviders.forEach { provider ->
            if (providerIds.add(provider.id)) {
                add(provider)
            }
        }
    }
    DisposableEffect(controller) {
        controller.bind()
        onDispose {
            if (controller.isComposing()) {
                controller.compositionCancel()
            }
            controller.unbind()
        }
    }
    Box(modifier = modifier) {
        SweetEditor(
            state = controller.state,
            controller = controller.editorController,
            publicController = controller,
            modifier = Modifier.matchParentSize(),
            theme = theme,
            settings = settings,
            decorationProviders = mergedDecorationProviders,
            onGestureResult = { result ->
                controller.publishGestureEventFromComposable(result)
                onGestureResult(result)
            },
            onHitTarget = onHitTarget,
            onContextMenuRequest = onContextMenuRequest,
            onSelectionHandleDragStateChange = onSelectionHandleDragStateChange,
        )
        CompletionPopup(
            controller = controller,
            theme = theme,
        )
    }
}

/**
 * Renders the Compose editor surface backed by [EditorController] and [EditorState].
 *
 * This composable is responsible only for UI integration: input dispatch, IME installation,
 * render-model drawing, and side-effect orchestration. All editing logic stays inside the native
 * editor kernel and the controller layer.
 *
 * @param state editor state observed by the UI.
 * @param controller controller used to send commands and bridge events to the native kernel.
 * @param modifier Compose modifier applied to the editor canvas.
 * @param theme theme used for colors, fonts, and text styles.
 * @param settings high-level editor settings applied through the controller.
 * @param decorationProviders provider list used to compute additional editor decorations.
 * @param onGestureResult callback invoked after a gesture result is produced.
 * @param onHitTarget callback invoked when the gesture result reports a concrete hit target.
 * @param onContextMenuRequest callback invoked when a context menu gesture is detected.
 * @param onSelectionHandleDragStateChange callback invoked when selection handle drag state changes.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun SweetEditor(
    state: EditorState,
    controller: EditorController,
    publicController: SweetEditorController? = null,
    modifier: Modifier = Modifier,
    theme: EditorTheme = EditorTheme.dark(),
    settings: EditorSettings = EditorSettings(),
    decorationProviders: List<DecorationProvider> = emptyList(),
    onGestureResult: (GestureResult) -> Unit = {},
    onHitTarget: (HitTarget) -> Unit = {},
    onContextMenuRequest: (EditorContextMenuRequest) -> Unit = {},
    onSelectionHandleDragStateChange: (EditorSelectionHandleDragState) -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val textMeasurer = rememberTextMeasurer(cacheSize = 256)
    val renderModel = state.renderModel
    val renderScale = state.scrollMetrics.scale.takeIf { it > 0f } ?: 1f
    val scaledTheme = remember(theme, renderScale) {
        theme.scaled(renderScale)
    }
    val drawCache = remember(scaledTheme, LocalDensity.current) {
        EditorDrawCache(scaledTheme)
    }
    val iconPainter = remember(controller, state.editorIconProvider) {
        EditorGutterIconPainter(
            controller = controller,
            provider = state.editorIconProvider,
        )
    }
    val cursorTarget = renderModel?.cursor
    var lastCursorTextPosition by remember { mutableStateOf<TextPosition?>(null) }
    val shouldAnimateCursorMove = cursorTarget?.textPosition != null &&
        lastCursorTextPosition != null &&
        cursorTarget.textPosition != lastCursorTextPosition
    val animatedCursorX by animateFloatAsState(
        targetValue = cursorTarget?.position?.x ?: 0f,
        animationSpec = if (shouldAnimateCursorMove) {
            tween(
                durationMillis = 90,
                easing = FastOutSlowInEasing,
            )
        } else {
            snap()
        },
        label = "sweet_editor_cursor_x",
    )
    val animatedCursorY by animateFloatAsState(
        targetValue = cursorTarget?.position?.y ?: 0f,
        animationSpec = if (shouldAnimateCursorMove) {
            tween(
                durationMillis = 90,
                easing = FastOutSlowInEasing,
            )
        } else {
            snap()
        },
        label = "sweet_editor_cursor_y",
    )
    val animatedCursor = remember(cursorTarget, animatedCursorX, animatedCursorY) {
        AnimatedCursorRenderState(
            x = animatedCursorX,
            y = animatedCursorY,
            height = cursorTarget?.height ?: 0f,
            visible = cursorTarget?.visible == true,
        )
    }
    SideEffect {
        lastCursorTextPosition = cursorTarget?.textPosition
    }
    var isFocused by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        onDispose {
            if (controller.isComposing()) {
                controller.compositionCancel()
            }
        }
    }

    SweetEditorEffects(
        state = state,
        controller = controller,
        theme = theme,
        settings = settings,
        decorationProviders = decorationProviders,
        onGestureResult = onGestureResult,
        onHitTarget = onHitTarget,
        onContextMenuRequest = onContextMenuRequest,
        onSelectionHandleDragStateChange = onSelectionHandleDragStateChange,
    )

    val platformImeModifier = publicController?.let {
        InstallPlatformImeSession(
            controller = it,
            state = state,
            isFocused = isFocused,
            isReadOnly = settings.readOnly,
        )
    } ?: Modifier

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
                publicController?.handleComposeKeyEvent(event) ?: controller.handleComposeKeyEvent(event)
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
            renderModel = renderModel,
            textMeasurer = textMeasurer,
            drawCache = drawCache,
            iconPainter = iconPainter,
            animatedCursor = animatedCursor,
            theme = scaledTheme,
        )
    }
}

/**
 * Hosts side effects that should not force the main canvas composition to observe every editor signal.
 *
 * @param state editor state observed by effect handlers.
 * @param controller controller used to execute deferred refresh and bridge operations.
 * @param theme theme snapshot used when theme-dependent bridge state changes.
 * @param settings settings snapshot applied to the controller.
 * @param decorationProviders provider list installed into the decoration manager.
 * @param onGestureResult latest gesture callback.
 * @param onHitTarget latest hit-target callback.
 * @param onContextMenuRequest latest context-menu callback.
 * @param onSelectionHandleDragStateChange latest selection-handle callback.
 */
@Composable
private fun SweetEditorEffects(
    state: EditorState,
    controller: EditorController,
    theme: EditorTheme,
    settings: EditorSettings,
    decorationProviders: List<DecorationProvider>,
    onGestureResult: (GestureResult) -> Unit,
    onHitTarget: (HitTarget) -> Unit,
    onContextMenuRequest: (EditorContextMenuRequest) -> Unit,
    onSelectionHandleDragStateChange: (EditorSelectionHandleDragState) -> Unit,
) {
    val currentOnGestureResult by rememberUpdatedState(onGestureResult)
    val currentOnHitTarget by rememberUpdatedState(onHitTarget)
    val currentOnContextMenuRequest by rememberUpdatedState(onContextMenuRequest)
    val currentOnSelectionHandleDragStateChange by rememberUpdatedState(onSelectionHandleDragStateChange)

    val document = state.document
    val lastGestureResult = state.lastGestureResult
    val renderModel = state.renderModel
    val scrollMetrics = state.scrollMetrics
    val platformScale = lastGestureResult.viewScale
        .takeIf { it > 0f }
        ?: scrollMetrics.scale.takeIf { it > 0f }
        ?: 1f

    LaunchedEffect(controller, state) {
        snapshotFlow {
            Triple(
                state.renderModelRequestVersion,
                state.scrollMetricsRequestVersion,
                state.isRenderModelDirty || state.isScrollMetricsDirty,
            )
        }.collectLatest { (_, _, dirty) ->
            if (!dirty) {
                return@collectLatest
            }
            withFrameNanos {
                controller.refreshNow()
            }
        }
    }

    LaunchedEffect(
        controller,
        document,
        theme.fontFamily,
        theme.fontSize,
        theme.lineNumberFontSize,
        theme.inlayHintFontSize,
        platformScale,
    ) {
        if (document != null) {
            controller.syncPlatformScale(platformScale)
        }
    }

    LaunchedEffect(controller, document, theme) {
        if (document != null) {
            controller.applyTheme(theme)
        }
    }

    LaunchedEffect(controller, document, settings) {
        if (document != null) {
            controller.applySettings(settings)
        }
    }

    InstallDecorationProviders(
        controller = controller,
        state = state,
        providers = decorationProviders,
    )

    LaunchedEffect(controller, lastGestureResult.needsAnimation) {
        while (state.lastGestureResult.needsAnimation) {
            withFrameNanos {
                controller.tickAnimations()
                controller.refreshNow()
            }
        }
    }

    LaunchedEffect(lastGestureResult) {
        if (lastGestureResult.type != GestureType.Undefined) {
            currentOnGestureResult(lastGestureResult)
        }
        if (lastGestureResult.hitTarget.type != HitTargetType.None) {
            currentOnHitTarget(lastGestureResult.hitTarget)
        }
        if (lastGestureResult.type == GestureType.ContextMenu) {
            currentOnContextMenuRequest(
                EditorContextMenuRequest(
                    gestureResult = lastGestureResult,
                ),
            )
        }
    }

    LaunchedEffect(
        lastGestureResult.isHandleDrag,
        renderModel?.selectionStartHandle,
        renderModel?.selectionEndHandle,
    ) {
        val currentRenderModel = renderModel ?: return@LaunchedEffect
        currentOnSelectionHandleDragStateChange(
            EditorSelectionHandleDragState(
                active = lastGestureResult.isHandleDrag,
                gestureResult = lastGestureResult,
                startHandle = currentRenderModel.selectionStartHandle,
                endHandle = currentRenderModel.selectionEndHandle,
            ),
        )
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawEditorSurface(
    renderModel: EditorRenderModel?,
    textMeasurer: TextMeasurer,
    drawCache: EditorDrawCache,
    iconPainter: EditorGutterIconPainter,
    animatedCursor: AnimatedCursorRenderState,
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
    val viewportBounds = ViewportBounds(
        width = renderModel.viewportWidth.takeIf { it > 0f } ?: size.width,
        height = renderModel.viewportHeight.takeIf { it > 0f } ?: size.height,
    )
    val estimatedLineHeight = renderModel.cursor.height.takeIf { it > 0f } ?: 20f

    val cornerRadius: Float = density * theme.cornerRadius + .5f

    drawRect(
        color = theme.backgroundColor.toComposeColor(),
        topLeft = Offset.Zero,
        size = size,
    )
    drawCurrentLine(
        renderModel = renderModel,
        fillColor = theme.currentLineColor.toComposeColor(),
        borderColor = currentLineBorderColor(theme),
        left = 0f,
        width = renderModel.viewportWidth.takeIf { it > 0f } ?: size.width,
    )

    val visibleSelectionRects = renderModel.selectionRects.filter { rect ->
        viewportBounds.intersects(rect.origin.x, rect.origin.y, rect.width, rect.height)
    }
    if (visibleSelectionRects.isNotEmpty()) {
        drawSelectionRects(visibleSelectionRects, theme.selectionColor.toComposeColor(), cornerRadius)
    }

    renderModel.guideSegments.forEach { guide ->
        if (viewportBounds.intersectsGuide(guide)) {
            drawGuide(guide, theme, drawCache)
        }
    }

    renderModel.diagnosticDecorations.forEach { decoration ->
        if (viewportBounds.intersects(decoration.origin.x, decoration.origin.y, decoration.width, decoration.height)) {
            drawDiagnostic(decoration)
        }
    }

    if (
        viewportBounds.intersects(
            renderModel.compositionDecoration.origin.x,
            renderModel.compositionDecoration.origin.y,
            renderModel.compositionDecoration.width,
            renderModel.compositionDecoration.height,
        )
    ) {
        drawCompositionDecoration(renderModel, theme.compositionUnderlineColor.toComposeColor())
    }

    renderModel.linkedEditingRects.forEach { rect ->
        if (viewportBounds.intersects(rect.origin.x, rect.origin.y, rect.width, rect.height)) {
            drawLinkedEditing(
                rect = rect,
                activeColor = theme.linkedEditingActiveColor.toComposeColor(),
                inactiveColor = theme.linkedEditingInactiveColor.toComposeColor(),
            )
        }
    }

    renderModel.bracketHighlightRects.forEach { rect ->
        if (viewportBounds.intersects(rect.origin.x, rect.origin.y, rect.width, rect.height)) {
            drawRoundRect(
                color = theme.bracketHighlightBackgroundColor.toComposeColor(),
                topLeft = Offset(
                    x = rect.origin.x,
                    y = rect.origin.y,
                ),
                size = Size(rect.width, rect.height),
                cornerRadius = CornerRadius(cornerRadius),
            )
            drawRoundRect(
                color = theme.bracketHighlightBorderColor.toComposeColor(),
                topLeft = Offset(
                    x = rect.origin.x,
                    y = rect.origin.y,
                ),
                size = Size(rect.width, rect.height),
                style = Stroke(width = 1f),
                cornerRadius = CornerRadius(cornerRadius),
            )
        }
    }

    renderModel.lines.forEach { line ->
        if (viewportBounds.intersectsLine(line, estimatedLineHeight)) {
            drawRuns(textMeasurer, line, theme, drawCache, viewportBounds, estimatedLineHeight)
        }
    }

    drawCursor(
        theme = theme,
        cornerRadius = cornerRadius,
        animatedCursor = animatedCursor,
        renderModel = renderModel,
    )

    drawGutterBackground(
        renderModel = renderModel,
        gutterBackgroundColor = theme.gutterBackgroundColor.toComposeColor(),
        currentLineColor = theme.currentLineColor.toComposeColor(),
        currentLineBorderColor = currentLineBorderColor(theme),
        splitLineColor = theme.splitLineColor.toComposeColor(),
    )
    renderModel.lines.forEach { line ->
        if (viewportBounds.intersectsLine(line, estimatedLineHeight)) {
            drawLineNumber(renderModel, textMeasurer, line, drawCache, viewportBounds, estimatedLineHeight)
        }
    }

    val activeLineColor = currentLineAccentColor(theme)
    renderModel.gutterIcons.forEach { item ->
        if (viewportBounds.intersects(item.origin.x, item.origin.y, item.width, item.height)) {
            drawGutterIcon(
                item = item,
                painter = iconPainter,
                tint = if (item.logicalLine == renderModel.cursor.textPosition.line) activeLineColor else theme.inlayHintIconColor.toComposeColor(),
            )
        }
    }

    renderModel.foldMarkers.forEach { marker ->
        if (viewportBounds.intersects(marker.origin.x, marker.origin.y, marker.width, marker.height)) {
            drawFoldMarker(
                marker = marker,
                color = if (marker.logicalLine == renderModel.cursor.textPosition.line) activeLineColor else theme.lineNumberColor.toComposeColor(),
            )
        }
    }

    drawScrollbar(renderModel.verticalScrollbar, renderModel, theme)
    drawScrollbar(renderModel.horizontalScrollbar, renderModel, theme)

    drawSelectionHandle(
        alignment = Alignment.Start,
        position = renderModel.selectionStartHandle.position,
        handleHeight = renderModel.selectionStartHandle.height,
        visible = renderModel.selectionStartHandle.visible,
        color = theme.cursorColor.toComposeColor(),
    )
    drawSelectionHandle(
        alignment = Alignment.End,
        position = renderModel.selectionEndHandle.position,
        handleHeight = renderModel.selectionEndHandle.height,
        visible = renderModel.selectionEndHandle.visible,
        color = theme.cursorColor.toComposeColor(),
    )
}

private fun DrawScope.drawGutterBackground(
    renderModel: EditorRenderModel,
    gutterBackgroundColor: Color,
    currentLineColor: Color,
    currentLineBorderColor: Color,
    splitLineColor: Color,
) {
    if (!renderModel.gutterVisible) {
        return
    }
    val gutterWidth = renderModel.splitX.coerceAtLeast(0f)
    drawRect(
        color = gutterBackgroundColor,
        topLeft = Offset.Zero,
        size = Size(gutterWidth, size.height),
    )
    drawCurrentLine(
        renderModel = renderModel,
        fillColor = currentLineColor,
        borderColor = currentLineBorderColor,
        left = 0f,
        width = gutterWidth,
    )
    if (!renderModel.splitLineVisible) {
        return
    }
    drawLine(
        color = splitLineColor,
        start = Offset(renderModel.splitX, 0f),
        end = Offset(renderModel.splitX, size.height),
        strokeWidth = 1f,
    )
}

private fun DrawScope.drawCurrentLine(
    renderModel: EditorRenderModel,
    fillColor: Color,
    borderColor: Color,
    left: Float,
    width: Float,
) {
    val top = renderModel.currentLine.y
    val height = renderModel.cursor.height.takeIf { it > 0f } ?: 20f
    when (renderModel.currentLineRenderMode) {
        CurrentLineRenderMode.Background -> {
            drawRect(
                color = fillColor,
                topLeft = Offset(left, top),
                size = Size(width, height),
            )
        }

        CurrentLineRenderMode.Border -> {
            drawRect(
                color = borderColor,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 1f),
            )
        }

        CurrentLineRenderMode.None -> Unit
    }
}

private fun DrawScope.drawSelectionRects(
    rects: List<SelectionRect>,
    color: Color,
    cornerRadius: Float,
) {
    val bands = mergeSelectionBands(rects)
    val clusters = buildSelectionClusters(bands)
    clusters.forEach { cluster ->
        if (cluster.size == 1) {
            val band = cluster.first()
            drawRoundRect(
                color = color,
                topLeft = Offset(band.left, band.top),
                size = Size(band.right - band.left, band.bottom - band.top),
                cornerRadius = CornerRadius(cornerRadius),
            )
        } else {
            drawPath(
                path = buildRoundedSelectionPath(cluster, cornerRadius),
                color = color,
            )
        }
    }
}

private fun DrawScope.drawGuide(
    guide: GuideSegment,
    theme: EditorTheme,
    drawCache: EditorDrawCache,
) {
    val color = if (guide.type == GuideType.Separator) {
        theme.separatorLineColor.toComposeColor()
    } else {
        theme.guideColor.toComposeColor()
    }
    val pathEffect = when (guide.style) {
        GuideStyle.Solid -> null
        GuideStyle.Dashed -> drawCache.dashedGuidePathEffect
        GuideStyle.Double -> drawCache.doubleGuidePathEffect
    }
    val strokeWidth = if (guide.type == GuideType.Indent) 1f else 1.2f
    val start = Offset(guide.start.x, guide.start.y)
    val end = Offset(guide.end.x, guide.end.y)
    if (guide.arrowEnd) {
        val arrowTrim = 8f
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = kotlin.math.sqrt(dx * dx + dy * dy)
        val lineEnd = if (length > arrowTrim) {
            val ratio = (length - arrowTrim) / length
            Offset(start.x + dx * ratio, start.y + dy * ratio)
        } else {
            end
        }
        drawLine(
            color = color,
            start = start,
            end = lineEnd,
            strokeWidth = strokeWidth,
            pathEffect = pathEffect,
        )
        drawArrowHead(color = color, from = start, to = end, arrowLength = 9f)
        return
    }
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = strokeWidth,
        pathEffect = pathEffect,
    )
}

private fun DrawScope.drawDiagnostic(
    decoration: DiagnosticDecoration,
) {
    val color = decoration.color.toComposeColor()
    val startX = decoration.origin.x
    val endX = startX + decoration.width
    val baseY = decoration.origin.y + decoration.height - 1f
    if (decoration.severity == 3) {
        drawLine(
            color = color,
            start = Offset(startX, baseY),
            end = Offset(endX, baseY),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 2f)),
        )
        return
    }
    val halfWave = 7f
    val amplitude = 3.5f
    var x = startX
    var step = 0
    while (x < endX) {
        val nextX = minOf(x + halfWave, endX)
        val midX = (x + nextX) / 2f
        val peakY = if (step % 2 == 0) baseY - amplitude else baseY + amplitude
        drawLine(
            color = color,
            start = Offset(x, baseY),
            end = Offset(midX, peakY),
            strokeWidth = 2f,
        )
        drawLine(
            color = color,
            start = Offset(midX, peakY),
            end = Offset(nextX, baseY),
            strokeWidth = 2f,
        )
        x = nextX
        step++
    }
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
    rect: LinkedEditingRect,
    activeColor: Color,
    inactiveColor: Color,
) {
    if (rect.isActive) {
        drawRect(
            color = activeColor.copy(alpha = 32f / 255f),
            topLeft = Offset(rect.origin.x, rect.origin.y),
            size = Size(rect.width, rect.height),
        )
    }
    drawRect(
        color = if (rect.isActive) activeColor else inactiveColor,
        topLeft = Offset(
            x = rect.origin.x,
            y = rect.origin.y,
        ),
        size = Size(rect.width, rect.height),
        style = Stroke(width = if (rect.isActive) 2f else 1f),
    )
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawLineNumber(
    renderModel: EditorRenderModel,
    textMeasurer: TextMeasurer,
    line: VisualLine,
    drawCache: EditorDrawCache,
    viewportBounds: ViewportBounds,
    estimatedLineHeight: Float,
) {
    if (!renderModel.gutterVisible || line.wrapIndex != 0 || line.isPhantomLine) {
        return
    }
    if (!viewportBounds.intersectsLine(line, estimatedLineHeight)) {
        return
    }
    drawBaselineText(
        textMeasurer = textMeasurer,
        drawCache = drawCache,
        text = (line.logicalLine + 1).toString(),
        x = line.lineNumberPosition.x,
        baselineY = line.lineNumberPosition.y,
        style = drawCache.lineNumberStyle(
            active = line.logicalLine == renderModel.cursor.textPosition.line,
            baselineY = line.lineNumberPosition.y,
            estimatedLineHeight = estimatedLineHeight,
        ),
    )
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawRuns(
    textMeasurer: TextMeasurer,
    line: VisualLine,
    theme: EditorTheme,
    drawCache: EditorDrawCache,
    viewportBounds: ViewportBounds,
    estimatedLineHeight: Float,
) {
    line.runs.forEach { run ->
        if (!run.shouldRenderText()) {
            return@forEach
        }
        if (!viewportBounds.intersectsRun(run, estimatedLineHeight)) {
            return@forEach
        }
        drawRunBackground(run, theme, estimatedLineHeight)
        drawBaselineText(
            textMeasurer = textMeasurer,
            drawCache = drawCache,
            text = run.text,
            x = runTextX(run),
            baselineY = run.y,
            style = drawCache.runTextStyle(run.style, run.type, theme),
        )
    }
}

private fun DrawScope.drawRunBackground(
    run: VisualRun,
    theme: EditorTheme,
    estimatedLineHeight: Float,
) {
    val top = run.y - estimatedLineHeight * 0.8f
    val height = estimatedLineHeight
    when (run.type) {
        VisualRunType.FoldPlaceholder -> {
            val margin = run.margin
            val left = run.x + margin
            val width = (run.width - margin * 2f).coerceAtLeast(0f)
            val radius = height * 0.2f
            drawRoundRect(
                color = theme.foldPlaceholderBackgroundColor.toComposeColor(),
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(radius, radius),
            )
        }

        VisualRunType.InlayHint -> {
            if (run.colorValue != 0) {
                drawRect(
                    color = run.colorValue.toComposeColor(),
                    topLeft = Offset(run.x + run.margin, top),
                    size = Size(height, height),
                )
            } else {
                val margin = run.margin
                val left = run.x + margin
                val width = (run.width - margin * 2f).coerceAtLeast(0f)
                val radius = height * 0.2f
                drawRoundRect(
                    color = theme.inlayHintBackgroundColor.toComposeColor(),
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
        }

        else -> Unit
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawBaselineText(
    textMeasurer: TextMeasurer,
    drawCache: EditorDrawCache,
    text: String,
    x: Float,
    baselineY: Float,
    style: TextStyle,
) {
    val layoutResult = drawCache.measureText(textMeasurer, text, style)
    drawText(
        textLayoutResult = layoutResult,
        topLeft = Offset(
            x = x,
            y = baselineY - layoutResult.firstBaseline,
        ),
    )
}

private fun DrawScope.drawGutterIcon(
    item: GutterIconRenderItem,
    painter: EditorGutterIconPainter,
    tint: Color,
) {
    painter.paint(
        drawScope = this,
        iconId = item.iconId,
        origin = item.origin,
        width = item.width,
        height = item.height,
        tint = tint,
    )
}

private fun DrawScope.drawFoldMarker(
    marker: FoldMarkerRenderItem,
    color: Color,
) {
    if (marker.foldState == FoldState.None) {
        return
    }
    val centerX = marker.origin.x + marker.width * 0.5f
    val centerY = marker.origin.y + marker.height * 0.5f
    val halfSize = min(marker.width, marker.height).coerceAtLeast(8f) * 0.28f
    val strokeWidth = (marker.height * 0.1f).coerceAtLeast(1f)
    if (marker.foldState == FoldState.Collapsed) {
        drawLine(
            color = color,
            start = Offset(centerX - halfSize * 0.5f, centerY - halfSize),
            end = Offset(centerX + halfSize * 0.5f, centerY),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(centerX + halfSize * 0.5f, centerY),
            end = Offset(centerX - halfSize * 0.5f, centerY + halfSize),
            strokeWidth = strokeWidth,
        )
    } else {
        drawLine(
            color = color,
            start = Offset(centerX - halfSize, centerY - halfSize * 0.5f),
            end = Offset(centerX, centerY + halfSize * 0.5f),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(centerX, centerY + halfSize * 0.5f),
            end = Offset(centerX + halfSize, centerY - halfSize * 0.5f),
            strokeWidth = strokeWidth,
        )
    }
}

private fun DrawScope.drawCursor(
    theme: EditorTheme,
    cornerRadius: Float,
    animatedCursor: AnimatedCursorRenderState,
    renderModel: EditorRenderModel,
) {
    val cursor = renderModel.cursor

    if (animatedCursor.visible) {
        drawRoundRect(
            color = theme.cursorColor.toComposeColor(),
            topLeft = Offset(
                x = animatedCursor.x,
                y = animatedCursor.y + cornerRadius,
            ),
            size = Size(
                1.2f * density + .5f,
                (animatedCursor.height.coerceAtLeast(1f) - cornerRadius * 2f).coerceAtLeast(1f),
            ),
            cornerRadius = CornerRadius(cornerRadius),
        )
    }

    if (cursor.showDragger) {
        val handleHeight = renderModel.selectionEndHandle.height

        val path = Path().apply {
            moveTo(0f, 0f)
            cubicTo(
                -0.2f * handleHeight, 0.2f * handleHeight,
                -0.5f * handleHeight, 0.4f * handleHeight,
                -0.5f * handleHeight, 0.7f * handleHeight
            )
            cubicTo(
                -0.5f * handleHeight, 0.9f * handleHeight,
                -0.24f * handleHeight, 1f * handleHeight,
                0f, 1f * handleHeight
            )
            cubicTo(
                0.24f * handleHeight, 1f * handleHeight,
                0.5f * handleHeight, 0.9f * handleHeight,
                0.5f * handleHeight, 0.7f * handleHeight
            )
            cubicTo(
                0.5f * handleHeight, 0.4f * handleHeight,
                0.2f * handleHeight, 0.2f * handleHeight,
                0f, 0f
            )

            close()
        }
        drawPath(
            path = path,
            color = theme.cursorColor.toComposeColor(),
        )
    }
}

private fun DrawScope.drawSelectionHandle(
    alignment: Alignment.Horizontal,
    position: PointF,
    handleHeight: Float,
    visible: Boolean,
    color: Color,
) {
    if (!visible && alignment in listOf(Alignment.Start, Alignment.End)) {
        return
    }
    val stemHeight = handleHeight.coerceAtLeast(10f)

    drawPath(
         path =selectionHandlePath(alignment, position, stemHeight),
        color = color,
    )
}

private fun selectionHandlePath(
    alignment: Alignment.Horizontal,
    position: PointF,
    handleHeight: Float,
): Path {
    val path = Path()
    when (alignment) {
        Alignment.Start -> {
            path.apply {
                moveTo(position.x, position.y + handleHeight)
                lineTo(position.x - handleHeight, position.y + handleHeight)
                arcTo(
                    rect = Rect(
                        left = position.x - handleHeight,
                        top = position.y + handleHeight,
                        right = position.x,
                        bottom = position.y + handleHeight * 2
                    ),
                    startAngleDegrees = -90f,
                    sweepAngleDegrees = -270f,
                    forceMoveTo = false
                )
                close()
            }
        }
        Alignment.End -> {
            path.apply {
                moveTo(position.x, position.y + handleHeight)
                lineTo(position.x + handleHeight, position.y + handleHeight)
                arcTo(
                    rect = Rect(
                        left = position.x,
                        top = position.y + handleHeight,
                        right = position.x + handleHeight,
                        bottom = position.y + handleHeight * 2
                    ),
                    startAngleDegrees = -90f,
                    sweepAngleDegrees = 270f,
                    forceMoveTo = false
                )
                close()
            }
        }
    }
    return path
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

private data class SelectionBand(
    val top: Float,
    val bottom: Float,
    val left: Float,
    val right: Float,
)

private data class AnimatedCursorRenderState(
    val x: Float,
    val y: Float,
    val height: Float,
    val visible: Boolean,
)

private fun mergeSelectionBands(rects: List<SelectionRect>): List<SelectionBand> {
    val sortedRects = rects.sortedWith(compareBy<SelectionRect> { it.origin.y }.thenBy { it.origin.x })
    val merged = mutableListOf<SelectionBand>()
    sortedRects.forEach { rect ->
        if (rect.width <= 0f || rect.height <= 0f) {
            return@forEach
        }
        val top = rect.origin.y
        val bottom = rect.origin.y + rect.height
        val left = rect.origin.x
        val right = rect.origin.x + rect.width
        val last = merged.lastOrNull()
        if (
            last != null &&
            approximatelyEqual(last.top, top) &&
            approximatelyEqual(last.bottom, bottom) &&
            left <= last.right + SELECTION_BAND_EPSILON
        ) {
            merged[merged.lastIndex] = last.copy(
                left = minOf(last.left, left),
                right = maxOf(last.right, right),
            )
        } else {
            merged += SelectionBand(
                top = top,
                bottom = bottom,
                left = left,
                right = right,
            )
        }
    }
    return merged
}

private fun buildSelectionClusters(bands: List<SelectionBand>): List<List<SelectionBand>> {
    if (bands.isEmpty()) {
        return emptyList()
    }
    val clusters = mutableListOf<MutableList<SelectionBand>>()
    var currentCluster = mutableListOf(bands.first())
    for (index in 1 until bands.size) {
        val previous = bands[index - 1]
        val current = bands[index]
        val isConnected = approximatelyEqual(previous.bottom, current.top) &&
            current.left <= previous.right + SELECTION_BAND_EPSILON &&
            current.right >= previous.left - SELECTION_BAND_EPSILON
        if (isConnected) {
            currentCluster += current
        } else {
            clusters += currentCluster
            currentCluster = mutableListOf(current)
        }
    }
    clusters += currentCluster
    return clusters
}

private fun buildRoundedSelectionPath(
    bands: List<SelectionBand>,
    cornerRadius: Float,
): Path {
    val points = buildSelectionPolygonPoints(bands)
    val path = Path()
    if (points.isEmpty()) {
        return path
    }
    val effectiveRadius = cornerRadius.coerceAtLeast(0f)
    if (points.size < 3 || effectiveRadius <= 0f) {
        path.moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { point ->
            path.lineTo(point.x, point.y)
        }
        path.close()
        return path
    }
    val startCorner = roundedCorner(points.last(), points.first(), points[1], effectiveRadius)
    path.moveTo(startCorner.entry.x, startCorner.entry.y)
    points.indices.forEach { index ->
        val previous = points[(index - 1 + points.size) % points.size]
        val current = points[index]
        val next = points[(index + 1) % points.size]
        val corner = roundedCorner(previous, current, next, effectiveRadius)
        path.lineTo(corner.entry.x, corner.entry.y)
        path.quadraticTo(current.x, current.y, corner.exit.x, corner.exit.y)
    }
    path.close()
    return path
}

private data class RoundedCorner(
    val entry: Offset,
    val exit: Offset,
)

private fun roundedCorner(
    previous: Offset,
    current: Offset,
    next: Offset,
    radius: Float,
): RoundedCorner {
    val previousVector = Offset(previous.x - current.x, previous.y - current.y)
    val nextVector = Offset(next.x - current.x, next.y - current.y)
    val previousLength = distance(previousVector)
    val nextLength = distance(nextVector)
    if (previousLength <= SELECTION_BAND_EPSILON || nextLength <= SELECTION_BAND_EPSILON) {
        return RoundedCorner(current, current)
    }
    val cut = minOf(radius, previousLength / 2f, nextLength / 2f)
    val previousDirection = Offset(previousVector.x / previousLength, previousVector.y / previousLength)
    val nextDirection = Offset(nextVector.x / nextLength, nextVector.y / nextLength)
    return RoundedCorner(
        entry = Offset(
            x = current.x + previousDirection.x * cut,
            y = current.y + previousDirection.y * cut,
        ),
        exit = Offset(
            x = current.x + nextDirection.x * cut,
            y = current.y + nextDirection.y * cut,
        ),
    )
}

private fun buildSelectionPolygonPoints(bands: List<SelectionBand>): List<Offset> {
    if (bands.isEmpty()) {
        return emptyList()
    }
    val points = mutableListOf<Offset>()
    val first = bands.first()
    points += Offset(first.left, first.top)
    points += Offset(first.right, first.top)

    var currentRight = first.right
    var currentBottom = first.bottom
    points += Offset(currentRight, currentBottom)
    for (index in 1 until bands.size) {
        val band = bands[index]
        if (!approximatelyEqual(currentBottom, band.top)) {
            points += Offset(currentRight, band.top)
        }
        if (!approximatelyEqual(currentRight, band.right)) {
            points += Offset(currentRight, band.top)
            points += Offset(band.right, band.top)
        }
        currentRight = band.right
        currentBottom = band.bottom
        points += Offset(currentRight, currentBottom)
    }

    val last = bands.last()
    points += Offset(last.left, last.bottom)

    var currentLeft = last.left
    var currentTop = last.top
    points += Offset(currentLeft, currentTop)
    for (index in bands.lastIndex - 1 downTo 0) {
        val band = bands[index]
        if (!approximatelyEqual(currentTop, band.bottom)) {
            points += Offset(currentLeft, band.bottom)
        }
        if (!approximatelyEqual(currentLeft, band.left)) {
            points += Offset(currentLeft, band.bottom)
            points += Offset(band.left, band.bottom)
        }
        currentLeft = band.left
        currentTop = band.top
        points += Offset(currentLeft, currentTop)
    }
    return simplifyPolygonPoints(points)
}

private fun simplifyPolygonPoints(points: List<Offset>): List<Offset> {
    if (points.size < 3) {
        return points
    }
    val simplified = mutableListOf<Offset>()
    points.forEach { point ->
        if (simplified.lastOrNull()?.approximatelyEquals(point) != true) {
            simplified += point
        }
    }
    if (simplified.size < 3) {
        return simplified
    }
    var index = 0
    while (index < simplified.size) {
        val previous = simplified[(index - 1 + simplified.size) % simplified.size]
        val current = simplified[index]
        val next = simplified[(index + 1) % simplified.size]
        if (isCollinear(previous, current, next)) {
            simplified.removeAt(index)
            if (simplified.size < 3) {
                break
            }
        } else {
            index++
        }
    }
    return simplified
}

private fun isCollinear(
    a: Offset,
    b: Offset,
    c: Offset,
): Boolean = approximatelyEqual((b.x - a.x) * (c.y - b.y), (b.y - a.y) * (c.x - b.x))

private fun Offset.approximatelyEquals(other: Offset): Boolean =
    approximatelyEqual(x, other.x) && approximatelyEqual(y, other.y)

private fun distance(offset: Offset): Float =
    kotlin.math.sqrt(offset.x * offset.x + offset.y * offset.y)

private fun approximatelyEqual(
    first: Float,
    second: Float,
): Boolean = kotlin.math.abs(first - second) <= SELECTION_BAND_EPSILON

private const val SELECTION_BAND_EPSILON = 0.5f

private fun DrawScope.drawArrowHead(
    color: Color,
    from: Offset,
    to: Offset,
    arrowLength: Float,
) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = kotlin.math.sqrt(dx * dx + dy * dy)
    if (length < 1f) {
        return
    }
    val ux = dx / length
    val uy = dy / length
    val arrowAngle = (PI * 28.0 / 180.0).toFloat()
    val cosA = kotlin.math.cos(arrowAngle)
    val sinA = kotlin.math.sin(arrowAngle)
    val ax1 = to.x - arrowLength * (ux * cosA - uy * sinA)
    val ay1 = to.y - arrowLength * (uy * cosA + ux * sinA)
    val ax2 = to.x - arrowLength * (ux * cosA + uy * sinA)
    val ay2 = to.y - arrowLength * (uy * cosA - ux * sinA)
    drawLine(color = color, start = to, end = Offset(ax1, ay1), strokeWidth = 1.2f)
    drawLine(color = color, start = to, end = Offset(ax2, ay2), strokeWidth = 1.2f)
}

private fun currentLineBorderColor(theme: EditorTheme): Color {
    val color = theme.currentLineColor.toComposeColor()
    return if (color.alpha < 0.63f) {
        color.copy(alpha = 0.63f)
    } else {
        color
    }
}

private fun currentLineAccentColor(theme: EditorTheme): Color {
    val accent = theme.currentLineNumberColor.toComposeColor()
    return accent.copy(alpha = 1f)
}

private fun runTextX(run: VisualRun): Float = when (run.type) {
    VisualRunType.FoldPlaceholder,
    VisualRunType.InlayHint,
    -> run.x + run.margin + run.padding

    else -> run.x
}

private class EditorGutterIconPainter(
    controller: EditorController,
    private val provider: EditorIconProvider?,
) {
    private val textMeasurer: EditorTextMeasurer = controller.textMeasurer()

    fun paint(
        drawScope: DrawScope,
        iconId: Int,
        origin: PointF,
        width: Float,
        height: Float,
        tint: Color,
    ) {
        if (
            provider?.paint(
                drawScope = drawScope,
                iconId = iconId,
                origin = origin,
                size = Size(width, height),
                tint = tint,
            ) == true
        ) {
            return
        }
        val iconSize = min(width, height)
        val center = Offset(
            x = origin.x + width / 2f,
            y = origin.y + height / 2f,
        )
        when (iconId) {
            1 -> {
                drawScope.drawRoundRect(
                    color = tint.copy(alpha = 0.14f),
                    topLeft = Offset(center.x - iconSize / 2f, center.y - iconSize / 2f),
                    size = Size(iconSize, iconSize),
                    cornerRadius = CornerRadius(iconSize * 0.22f, iconSize * 0.22f),
                )
                val strokeWidth = (iconSize * 0.12f).coerceAtLeast(1f)
                drawScope.drawRoundRect(
                    color = tint,
                    topLeft = Offset(center.x - iconSize * 0.34f, center.y - iconSize * 0.36f),
                    size = Size(iconSize * 0.68f, iconSize * 0.72f),
                    cornerRadius = CornerRadius(iconSize * 0.14f, iconSize * 0.14f),
                    style = Stroke(width = strokeWidth),
                )
                drawScope.drawLine(
                    color = tint,
                    start = Offset(center.x - iconSize * 0.2f, center.y - iconSize * 0.05f),
                    end = Offset(center.x + iconSize * 0.2f, center.y - iconSize * 0.05f),
                    strokeWidth = strokeWidth,
                )
                drawScope.drawLine(
                    color = tint,
                    start = Offset(center.x - iconSize * 0.2f, center.y + iconSize * 0.14f),
                    end = Offset(center.x + iconSize * 0.12f, center.y + iconSize * 0.14f),
                    strokeWidth = strokeWidth,
                )
            }

            else -> {
                val radius = iconSize / 2f
                drawScope.drawCircle(
                    color = tint.copy(alpha = 0.16f),
                    radius = radius,
                    center = center,
                )
                drawScope.drawCircle(
                    color = tint,
                    radius = radius * 0.58f,
                    center = center,
                    style = Stroke(width = (textMeasurer.measureIconWidth(iconId) * 0.08f).coerceAtLeast(1f)),
                )
            }
        }
    }
}

private data class ViewportBounds(
    val width: Float,
    val height: Float,
) {
    fun intersects(
        x: Float,
        y: Float,
        itemWidth: Float,
        itemHeight: Float,
    ): Boolean {
        if (itemWidth <= 0f || itemHeight <= 0f) {
            return false
        }
        val right = x + itemWidth
        val bottom = y + itemHeight
        return right >= 0f && bottom >= 0f && x <= width && y <= height
    }

    fun intersectsGuide(guide: GuideSegment): Boolean {
        val left = minOf(guide.start.x, guide.end.x)
        val top = minOf(guide.start.y, guide.end.y)
        val rectWidth = kotlin.math.abs(guide.end.x - guide.start.x).coerceAtLeast(1f)
        val rectHeight = kotlin.math.abs(guide.end.y - guide.start.y).coerceAtLeast(1f)
        return intersects(left, top, rectWidth, rectHeight)
    }

    fun intersectsLine(line: VisualLine, estimatedLineHeight: Float): Boolean {
        val baseline = line.firstBaseline()
        val top = baseline - estimatedLineHeight
        return top <= height && baseline + estimatedLineHeight * 0.5f >= 0f
    }

    fun intersectsRun(run: VisualRun, estimatedLineHeight: Float): Boolean {
        val runWidth = run.width.takeIf { it > 0f } ?: (run.text.length * estimatedLineHeight * 0.5f)
        return intersects(
            x = run.x,
            y = run.y - estimatedLineHeight,
            itemWidth = runWidth,
            itemHeight = estimatedLineHeight * 1.5f,
        )
    }
}

private fun VisualLine.firstBaseline(): Float =
    runs.firstOrNull()?.y ?: lineNumberPosition.y

/**
 * Caches drawing artifacts that are expensive to rebuild on every frame.
 *
 * @property theme scaled theme snapshot used to create Compose text styles.
 */
private class EditorDrawCache(
    private val theme: EditorTheme,
) {
    private val runTextStyles = mutableMapOf<RunTextStyleKey, TextStyle>()
    private val lineNumberTextStyles = mutableMapOf<Boolean, TextStyle>()
    private val textLayouts = SimpleLruCache<TextLayoutCacheKey, TextLayoutResult>(maxSize = 256)

    val dashedGuidePathEffect: PathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
    val doubleGuidePathEffect: PathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 3f))

    /**
     * Returns a cached Compose text style for one editor text style.
     *
     * @param style editor text style produced by the native render model.
     * @return cached Compose text style.
     */
    fun runTextStyle(
        style: EditorTextStyle,
        type: VisualRunType,
        theme: EditorTheme,
    ): TextStyle =
        runTextStyles.getOrPut(RunTextStyleKey(style, type)) {
            style.toComposeTextStyle(theme, type)
        }

    /**
     * Returns a cached line number style.
     *
     * @param active true when the line number belongs to the current logical line.
     * @return cached Compose text style for the line number.
     */
    fun lineNumberStyle(
        active: Boolean,
        baselineY: Float,
        estimatedLineHeight: Float,
    ): TextStyle =
        lineNumberTextStyles.getOrPut(active) {
            TextStyle(
                color = if (active) {
                    theme.currentLineNumberColor.toComposeColor()
                } else {
                    theme.lineNumberColor.toComposeColor()
                },
                fontFamily = theme.fontFamily,
                fontSize = theme.lineNumberFontSize,
                baselineShift = androidx.compose.ui.text.style.BaselineShift(
                    ((baselineY % estimatedLineHeight) / estimatedLineHeight - 0.5f) * 0.03f,
                ),
            )
        }

    /**
     * Measures text with a bounded cache to avoid repeated layout work.
     *
     * @param textMeasurer Compose text measurer.
     * @param text raw text to measure.
     * @param style Compose text style used for measurement.
     * @return measured text layout result.
     */
    fun measureText(
        textMeasurer: TextMeasurer,
        text: String,
        style: TextStyle,
    ): TextLayoutResult {
        if (text.length > 256) {
            return textMeasurer.measure(
                text = text,
                style = style,
            )
        }
        val key = TextLayoutCacheKey(text = text, style = style)
        textLayouts[key]?.let { return it }
        return textMeasurer.measure(
            text = text,
            style = style,
        ).also { layoutResult ->
            textLayouts[key] = layoutResult
        }
    }
}

private class SimpleLruCache<K, V>(
    private val maxSize: Int,
) {
    private val values = mutableMapOf<K, V>()
    private val accessOrder = ArrayDeque<K>()

    operator fun get(key: K): V? {
        val value = values[key] ?: return null
        accessOrder.remove(key)
        accessOrder.addLast(key)
        return value
    }

    operator fun set(key: K, value: V) {
        if (values.containsKey(key)) {
            accessOrder.remove(key)
        }
        values[key] = value
        accessOrder.addLast(key)
        while (values.size > maxSize) {
            val eldestKey = accessOrder.removeFirstOrNull() ?: break
            values.remove(eldestKey)
        }
    }
}

/**
 * Cache key used by [EditorDrawCache] for measured text layouts.
 */
private data class TextLayoutCacheKey(
    val text: String,
    val style: TextStyle,
)

private data class RunTextStyleKey(
    val style: EditorTextStyle,
    val type: VisualRunType,
)

private fun EditorTextStyle.toComposeTextStyle(
    theme: EditorTheme,
    type: VisualRunType,
): TextStyle {
    val decorations = buildList {
        if ((fontStyle and EditorTextStyle.Strikethrough) != 0) {
            add(TextDecoration.LineThrough)
        }
    }
    return TextStyle(
        color = when {
            type == VisualRunType.FoldPlaceholder -> theme.foldPlaceholderTextColor.toComposeColor()
            type == VisualRunType.PhantomText -> theme.phantomTextColor.toComposeColor()
            color != 0 -> color.toComposeColor()
            else -> theme.textColor.toComposeColor()
        },
        background = when {
            type == VisualRunType.FoldPlaceholder || type == VisualRunType.InlayHint -> Color.Transparent
            backgroundColor != 0 -> backgroundColor.toComposeColor()
            else -> Color.Transparent
        },
        fontWeight = if ((fontStyle and EditorTextStyle.Bold) != 0) FontWeight.Bold else null,
        fontStyle = if ((fontStyle and EditorTextStyle.Italic) != 0) FontStyle.Italic else null,
        textDecoration = decorations.takeIf { it.isNotEmpty() }?.reduce(TextDecoration::plus),
        fontFamily = theme.fontFamily,
        fontSize = if (type == VisualRunType.InlayHint) theme.inlayHintFontSize else theme.fontSize,
    )
}

private fun EditorTheme.scaled(scale: Float): EditorTheme {
    val normalizedScale = scale.coerceAtLeast(0.1f)
    return copy(
        fontSize = fontSize * normalizedScale,
        lineNumberFontSize = lineNumberFontSize * normalizedScale,
        inlayHintFontSize = inlayHintFontSize * normalizedScale,
        cornerRadius = cornerRadius * normalizedScale,
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

private fun List<PointerInputChange>.calculateCentroidPoint(): GesturePoint {
    if (isEmpty()) {
        return GesturePoint()
    }
    val centerX = sumOf { it.position.x.toDouble() }.toFloat() / size
    val centerY = sumOf { it.position.y.toDouble() }.toFloat() / size
    return GesturePoint(centerX, centerY)
}

private fun List<PointerInputChange>.calculateScaleDelta(): Float {
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

private fun PointerEvent.toNativeModifiers(): Int {
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

@Composable
private fun CompletionPopup(
    controller: SweetEditorController,
    theme: EditorTheme,
) {
    val result = controller.state.completionResult ?: return
    if (result.items.isEmpty()) {
        return
    }
    val cursor = controller.state.renderModel?.cursor ?: return
    val selectedIndex = controller.state.completionSelectedIndex
    val renderer = controller.state.completionItemRenderer
    val backgroundColor = theme.gutterBackgroundColor.toComposeColor()
    val borderColor = theme.scrollbarThumbColor.toComposeColor()
    val selectedColor = theme.selectionColor.toComposeColor()
    val textColor = theme.textColor.toComposeColor()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(0.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = cursor.position.x.toInt(),
                        y = (cursor.position.y + cursor.height).toInt(),
                    )
                }
                .background(backgroundColor)
                .border(1.dp, borderColor)
                .padding(vertical = 4.dp),
        ) {
            result.items.take(8).forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                androidx.compose.foundation.text.BasicText(
                    text = renderer?.render(item) ?: item.detail?.let { "${item.label}  $it" } ?: item.label,
                    modifier = Modifier
                        .background(if (isSelected) selectedColor else Color.Transparent)
                        .clickable {
                            controller.selectCompletionItem(index)
                            controller.applySelectedCompletionItem()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    style = TextStyle(
                        color = textColor,
                        fontFamily = theme.fontFamily,
                        fontSize = theme.fontSize,
                    ),
                )
            }
        }
    }
}

private fun SweetEditorController.handleComposeKeyEvent(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) {
        return false
    }
    when (event.key) {
        Key.DirectionDown -> {
            if (hasVisibleCompletion()) {
                selectNextCompletionItem()
                return true
            }
        }

        Key.DirectionUp -> {
            if (hasVisibleCompletion()) {
                selectPreviousCompletionItem()
                return true
            }
        }

        Key.Enter,
        Key.NumPadEnter,
        -> {
            if (hasVisibleCompletion()) {
                applySelectedCompletionItem()
            } else {
                performNewLineAction()
            }
            return true
        }

        Key.Tab -> {
            if (isInLinkedEditing()) {
                if (event.isShiftPressed) {
                    linkedEditingPrev()
                } else {
                    linkedEditingNext()
                }
                return true
            }
            if (hasVisibleCompletion()) {
                applySelectedCompletionItem()
                return true
            }
        }

        Key.Escape -> {
            if (isInLinkedEditing()) {
                cancelLinkedEditing()
                return true
            }
            if (hasVisibleCompletion()) {
                dismissCompletion()
                return true
            }
        }
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
