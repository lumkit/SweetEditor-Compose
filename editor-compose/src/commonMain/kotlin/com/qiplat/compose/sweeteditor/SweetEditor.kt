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
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.qiplat.compose.sweeteditor.model.foundation.*
import com.qiplat.compose.sweeteditor.model.visual.*
import com.qiplat.compose.sweeteditor.runtime.EditorController
import com.qiplat.compose.sweeteditor.runtime.EditorState
import com.qiplat.compose.sweeteditor.runtime.InstallDecorationProviders
import com.qiplat.compose.sweeteditor.theme.EditorTheme
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.min
import com.qiplat.compose.sweeteditor.model.decoration.TextStyle as EditorTextStyle

@OptIn(ExperimentalTextApi::class)
@Composable
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
fun SweetEditor(
    state: EditorState,
    controller: EditorController,
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
    val drawCache = remember(scaledTheme) {
        EditorDrawCache(scaledTheme)
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

    val platformImeModifier = InstallPlatformImeSession(
        controller = controller,
        state = state,
        isFocused = isFocused,
        isReadOnly = settings.readOnly,
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
            renderModel = renderModel,
            textMeasurer = textMeasurer,
            drawCache = drawCache,
            theme = scaledTheme,
        )
    }
}

@Composable
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
/**
 * Draws the full editor frame from the native render model.
 *
 * @param renderModel render model returned by the native kernel, or null before the first refresh.
 * @param textMeasurer Compose text measurer used for drawing runs and line numbers.
 * @param drawCache cache used to reuse text styles, path effects, and layout results.
 * @param theme fully scaled editor theme used for the current frame.
 */
private fun DrawScope.drawEditorSurface(
    renderModel: EditorRenderModel?,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    drawCache: EditorDrawCache,
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
        drawGuide(renderModel, guide, theme.guideColor.toComposeColor(), drawCache)
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
        drawRuns(textMeasurer, line, theme, drawCache)
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
        drawLineNumber(renderModel, textMeasurer, line, drawCache)
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
    drawCache: EditorDrawCache,
) {
    val pathEffect = when (guide.style) {
        GuideStyle.Solid -> null
        GuideStyle.Dashed -> drawCache.dashedGuidePathEffect
        GuideStyle.Double -> drawCache.doubleGuidePathEffect
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
    drawCache: EditorDrawCache,
) {
    if (!renderModel.gutterVisible || line.wrapIndex != 0 || line.isPhantomLine) {
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
        ),
    )
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawRuns(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    line: VisualLine,
    theme: EditorTheme,
    drawCache: EditorDrawCache,
) {
    line.runs.forEach { run ->
        if (!run.shouldRenderText()) {
            return@forEach
        }
        drawBaselineText(
            textMeasurer = textMeasurer,
            drawCache = drawCache,
            text = run.text,
            x = run.x,
            baselineY = run.y,
            style = drawCache.runTextStyle(run.style),
        )
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawBaselineText(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
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

/**
 * Caches drawing artifacts that are expensive to rebuild on every frame.
 *
 * @property theme scaled theme snapshot used to create Compose text styles.
 */
private class EditorDrawCache(
    private val theme: EditorTheme,
) {
    private val runTextStyles = mutableMapOf<EditorTextStyle, TextStyle>()
    private val lineNumberTextStyles = mutableMapOf<Boolean, TextStyle>()
    private val textLayouts = object : LinkedHashMap<TextLayoutCacheKey, TextLayoutResult>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TextLayoutCacheKey, TextLayoutResult>?): Boolean =
            size > 256
    }

    val dashedGuidePathEffect: PathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
    val doubleGuidePathEffect: PathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 3f))

    /**
     * Returns a cached Compose text style for one editor text style.
     *
     * @param style editor text style produced by the native render model.
     * @return cached Compose text style.
     */
    fun runTextStyle(style: EditorTextStyle): TextStyle =
        runTextStyles.getOrPut(style) {
            style.toComposeTextStyle(theme)
        }

    /**
     * Returns a cached line number style.
     *
     * @param active true when the line number belongs to the current logical line.
     * @return cached Compose text style for the line number.
     */
    fun lineNumberStyle(active: Boolean): TextStyle =
        lineNumberTextStyles.getOrPut(active) {
            TextStyle(
                color = if (active) {
                    theme.currentLineNumberColor.toComposeColor()
                } else {
                    theme.lineNumberColor.toComposeColor()
                },
                fontFamily = theme.fontFamily,
                fontSize = theme.lineNumberFontSize,
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
        textMeasurer: androidx.compose.ui.text.TextMeasurer,
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
        return textLayouts.getOrPut(key) {
            textMeasurer.measure(
                text = text,
                style = style,
            )
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
