package com.qiplat.compose.sweeteditor

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.qiplat.compose.sweeteditor.bridge.NativeHandleConfig
import com.qiplat.compose.sweeteditor.bridge.NativeScrollbarConfig
import com.qiplat.compose.sweeteditor.copilot.InlineSuggestionActionBar
import com.qiplat.compose.sweeteditor.model.foundation.*
import com.qiplat.compose.sweeteditor.model.visual.*
import com.qiplat.compose.sweeteditor.runtime.NativeEditorController
import com.qiplat.compose.sweeteditor.runtime.SweetEditorState
import com.qiplat.compose.sweeteditor.runtime.EditorTextMeasurer
import com.qiplat.compose.sweeteditor.runtime.InstallDecorationProviders
import com.qiplat.compose.sweeteditor.model.decoration.SpanStyle as DecorationTextStyle
import com.qiplat.compose.sweeteditor.theme.SweetEditorColors
import com.qiplat.compose.sweeteditor.theme.SweetEditorSpanColors
import com.qiplat.compose.sweeteditor.theme.SweetEditorSpanStyles
import com.qiplat.compose.sweeteditor.theme.SweetEditorTheme
import com.qiplat.compose.sweeteditor.theme.SweetEditorThemeScheme
import com.qiplat.compose.sweeteditor.theme.SweetEditorTypography
import com.qiplat.compose.sweeteditor.theme.tokens.ColorDarkTokens
import com.qiplat.compose.sweeteditor.theme.tokens.ColorLightTokens
import com.qiplat.compose.sweeteditor.theme.tokens.SpanColorDarkTokens
import com.qiplat.compose.sweeteditor.theme.tokens.SpanColorLightTokens
import com.qiplat.compose.sweeteditor.model.decoration.SpanFontStyle
import com.qiplat.compose.sweeteditor.theme.rememberSweetEditorTheme
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import com.qiplat.compose.sweeteditor.model.decoration.SpanStyle as EditorTextStyle

@OptIn(ExperimentalTextApi::class)
@Composable
fun SweetEditor(
    controller: SweetEditorController,
    modifier: Modifier = Modifier,
    theme: SweetEditorThemeScheme = controller.getTheme(),
    settings: SweetEditorSettings = controller.getSettings(),
    decorationProviders: List<DecorationProvider> = emptyList(),
    onGestureResult: (GestureResult) -> Unit = {},
    onHitTarget: (HitTarget) -> Unit = {},
    onContextMenuRequest: (EditorContextMenuRequest) -> Unit = {},
    onSelectionHandleDragStateChange: (SelectionHandleDragState) -> Unit = {},
    completions: (@Composable (selectedIndex: Int, items: List<CompletionItem>, render: CompletionItemRenderer?) -> Unit)? = null,
) {
    var editorWindowOffset by remember { mutableStateOf(IntOffset.Zero) }
    val mergedDecorationProviders = remember(decorationProviders, controller.attachedDecorationProviders) {
        buildList {
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
    LaunchedEffect(controller, theme.typography) {
        controller.updateTypography(theme.typography)
    }
    SweetEditor(
        state = controller.state,
        controller = controller,
        modifier = modifier,
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
        onPositionInWindowChanged = { editorWindowOffset = it },
    )
    CompletionPopup(
        completions = completions,
        controller = controller,
        editorWindowOffset = editorWindowOffset,
    )
    InlineSuggestionActionBar(
        suggestion = controller.inlineSuggestions().suggestionState.value,
        cursor = controller.state.renderModel?.cursor,
        theme = controller.themeState.value,
        editorWindowOffset = editorWindowOffset,
        onAccept = { controller.inlineSuggestions().accept() },
        onDismiss = { controller.inlineSuggestions().dismiss() },
    )
}

/**
 * Renders the Compose editor surface backed by [NativeEditorController] and [SweetEditorState].
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
    state: SweetEditorState,
    controller: SweetEditorController,
    modifier: Modifier = Modifier,
    theme: SweetEditorThemeScheme = rememberSweetEditorTheme(),
    settings: SweetEditorSettings = SweetEditorSettings(),
    decorationProviders: List<DecorationProvider> = emptyList(),
    onGestureResult: (GestureResult) -> Unit = {},
    onHitTarget: (HitTarget) -> Unit = {},
    onContextMenuRequest: (EditorContextMenuRequest) -> Unit = {},
    onSelectionHandleDragStateChange: (SelectionHandleDragState) -> Unit = {},
    onPositionInWindowChanged: (IntOffset) -> Unit = {},
) {
    val platformType = LocalPlatformType.current
    val preferDesktopIme = platformType == PlatformType.Desktop
    val mobilePlatformTypes = remember { listOf(PlatformType.Android, PlatformType.IOS) }
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val textMeasurer = rememberTextMeasurer(cacheSize = 256)
    val density = LocalDensity.current.density
    val renderModel = state.renderModel
    val scrollMetrics = state.scrollMetrics
    val controllerScale = controller.scaleState.value
    val renderScale = state.scrollMetrics.scale.takeIf { it > 0f } ?: controllerScale.takeIf { it > 0f } ?: 1f
    val scaledTheme = remember(theme, renderScale) {
        theme.scaled(renderScale)
    }
    val enableTextLayoutCache = remember(platformType) {
        supportsReusableTextLayoutCache(platformType)
    }
    val drawCache = remember(scaledTheme, LocalDensity.current, enableTextLayoutCache) {
        SweetEditorDrawCache(
            theme = scaledTheme,
            enableTextLayoutCache = enableTextLayoutCache,
        )
    }
    val resolvedColors = remember(scaledTheme) {
        resolveEditorColors(scaledTheme)
    }
    val selectionCornerRadius = density * scaledTheme.cornerRadius + .5f
    val renderSurfaceCache = remember(
        renderModel?.selectionRects,
        renderModel?.guideSegments,
        renderModel?.diagnosticDecorations,
        renderModel?.gutterIcons,
        renderModel?.foldMarkers,
        selectionCornerRadius,
    ) {
        buildRenderSurfaceCache(renderModel, selectionCornerRadius)
    }
    val iconPainter = remember(controller, state.editorIconProvider) {
        EditorGutterIconPainter(
            controller = controller.editorController,
            provider = state.editorIconProvider,
        )
    }
    val cursorTarget = renderModel?.cursor
    var lastCursorTextPosition by remember { mutableStateOf<TextPosition?>(null) }
    val shouldAnimateCursorMove by remember(cursorTarget, lastCursorTextPosition) {
        derivedStateOf {
            cursorTarget?.textPosition != null &&
                    lastCursorTextPosition != null &&
                    cursorTarget.textPosition != lastCursorTextPosition
        }
    }
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
    var hoverPosition by remember { mutableStateOf<Offset?>(null) }
    val pointerHoverIcon = remember(renderModel, scrollMetrics, hoverPosition) {
        resolveEditorPointerIcon(
            renderModel = renderModel,
            scrollMetrics = scrollMetrics,
            hoverPosition = hoverPosition,
        )
    }

    DisposableEffect(controller) {
        onDispose {
            if (controller.isComposing()) {
                controller.compositionCancel()
            }
        }
    }

    SweetEditorEffects(
        state = state,
        controller = controller.editorController,
        theme = theme,
        settings = settings,
        decorationProviders = decorationProviders,
        onGestureResult = onGestureResult,
        onHitTarget = onHitTarget,
        onContextMenuRequest = onContextMenuRequest,
        onSelectionHandleDragStateChange = onSelectionHandleDragStateChange,
    )

    Canvas(
        modifier = modifier
            .clipToBounds()
            .then(
                InstallPlatformImeSession(
                    controller = controller,
                    state = state,
                    isFocused = isFocused,
                    isReadOnly = settings.readOnly,
                )
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable(interactionSource = interactionSource)
            .pointerHoverIcon(pointerHoverIcon)
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    controller.setViewport(size.width, size.height)
                }
            }
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInWindow()
                onPositionInWindowChanged(
                    IntOffset(
                        x = position.x.roundToInt(),
                        y = position.y.roundToInt(),
                    ),
                )
            }
            .onPreviewKeyEvent { event ->
                controller.handleComposeKeyEvent(
                    event = event,
                    preferIme = preferDesktopIme,
                )
            }
            .onKeyEvent { event ->
                controller.handleComposeTextInputFallback(event)
            }
            .pointerInput(controller) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.firstOrNull { it.type == PointerType.Mouse }?.let { change ->
                            hoverPosition = change.position
                        }
                        val eventModifiers = event.toNativeModifiers()
                        val plan = buildPointerDispatchPlan(
                            scrollDelta = normalizeMouseWheelScrollDelta(
                                event.changes.firstOrNull()?.scrollDelta ?: Offset.Zero,
                            ),
                            isSecondaryPressed = event.buttons.isSecondaryPressed,
                            changes = event.changes.map { change ->
                                PointerChangeSnapshot(
                                    type = change.type,
                                    position = change.position.toGesturePoint(),
                                    previousPosition = change.previousPosition.toGesturePoint(),
                                    pressed = change.pressed,
                                    changedToDown = change.changedToDownIgnoreConsumed(),
                                    changedToUp = change.changedToUpIgnoreConsumed(),
                                )
                            },
                        )
                        if (plan.requestFocus) {
                            focusRequester.requestFocus()
                        }
                        plan.dispatches.forEach { dispatch ->
                            controller.dispatchGestureEvent(
                                type = dispatch.type,
                                points = dispatch.points,
                                modifiers = eventModifiers,
                                wheelDeltaX = dispatch.wheelDeltaX,
                                wheelDeltaY = dispatch.wheelDeltaY,
                                directScale = dispatch.directScale,
                            )
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
            renderSurfaceCache = renderSurfaceCache,
            animatedCursor = animatedCursor,
            theme = scaledTheme,
            colors = resolvedColors,
            platformType = platformType,
            mobilePlatformTypes = mobilePlatformTypes,
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
    state: SweetEditorState,
    controller: NativeEditorController,
    theme: SweetEditorThemeScheme,
    settings: SweetEditorSettings,
    decorationProviders: List<DecorationProvider>,
    onGestureResult: (GestureResult) -> Unit,
    onHitTarget: (HitTarget) -> Unit,
    onContextMenuRequest: (EditorContextMenuRequest) -> Unit,
    onSelectionHandleDragStateChange: (SelectionHandleDragState) -> Unit,
) {
    val currentOnGestureResult by rememberUpdatedState(onGestureResult)
    val currentOnHitTarget by rememberUpdatedState(onHitTarget)
    val currentOnContextMenuRequest by rememberUpdatedState(onContextMenuRequest)
    val currentOnSelectionHandleDragStateChange by rememberUpdatedState(onSelectionHandleDragStateChange)
    val density = LocalDensity.current.density
    val platformType = LocalPlatformType.current
    val platformScrollbarConfig = remember(platformType, density) {
        buildDefaultScrollbarConfig(platformType, density)
    }
    val platformHandleConfig = remember(platformType, density) {
        buildDefaultHandleConfig(platformType, density)
    }

    val document = state.document
    val lastGestureResult = state.lastGestureResult
    val renderModel = state.renderModel
    val scrollMetrics = state.scrollMetrics
    val platformScale = scrollMetrics.scale.takeIf { it > 0f }
        ?: controller.getScale().takeIf { it > 0f }
        ?: 1f

    LaunchedEffect(controller, state) {
        snapshotFlow {
            state.frameRefreshSignal
        }.collectLatest {
            withFrameNanos {
                controller.refreshNow()
            }
        }
    }

    LaunchedEffect(
        controller,
        document,
        theme.typography.fontFamily,
        theme.typography.fontSize,
        theme.typography.lineNumberFontSize,
        theme.typography.inlayHintFontSize,
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

    LaunchedEffect(controller, document, platformScrollbarConfig) {
        if (document != null) {
            controller.setScrollbarConfig(platformScrollbarConfig)
        }
    }

    LaunchedEffect(controller, document, platformHandleConfig) {
        if (document != null && platformHandleConfig != null) {
            controller.setHandleConfig(platformHandleConfig)
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
            SelectionHandleDragState(
                active = lastGestureResult.isHandleDrag,
                gestureResult = lastGestureResult,
                startHandle = currentRenderModel.selectionStartHandle,
                endHandle = currentRenderModel.selectionEndHandle,
            ),
        )
    }
}

object SweetEditorDefaults {

    fun theme(
        darkTheme: SweetEditorThemeScheme = SweetEditorThemeScheme(
            colors = darkColors(),
            typography = SweetEditorTypography(),
            spanStyles = spanStyles(darkSpanColors()),
            cornerRadius = 1.5f,
        ),
        lightTheme: SweetEditorThemeScheme = SweetEditorThemeScheme(
            colors = lightColors(),
            typography = SweetEditorTypography(),
            spanStyles = spanStyles(lightSpanColors()),
            cornerRadius = 1.5f,
        ),
    ): SweetEditorTheme = SweetEditorTheme(
        darkTheme = darkTheme,
        lightTheme = lightTheme,
    )

    fun lightColors(
        background: Color = ColorLightTokens.Background,
        text: Color = ColorLightTokens.Text,
        cursor: Color = ColorLightTokens.Cursor,
        selection: Color = ColorLightTokens.Selection,
        lineNumber: Color = ColorLightTokens.LineNumber,
        currentLineNumber: Color = ColorLightTokens.CurrentLineNumber,
        currentLine: Color = ColorLightTokens.CurrentLine,
        guide: Color = ColorLightTokens.Guide,
        separatorLine: Color = ColorLightTokens.SeparatorLine,
        splitLine: Color = ColorLightTokens.SplitLine,
        scrollbarTrack: Color = ColorLightTokens.ScrollbarTrack,
        scrollbarThumb: Color = ColorLightTokens.ScrollbarThumb,
        scrollbarThumbActive: Color = ColorLightTokens.ScrollbarThumbActive,
        compositionUnderline: Color = ColorLightTokens.CompositionUnderline,
        inlayHintBackground: Color = ColorLightTokens.InlayHintBackground,
        inlayHintText: Color = ColorLightTokens.InlayHintText,
        foldPlaceholderBackground: Color = ColorLightTokens.FoldPlaceholderBackground,
        foldPlaceholderText: Color = ColorLightTokens.FoldPlaceholderText,
        phantomText: Color = ColorLightTokens.PhantomText,
        inlayHintIcon: Color = ColorLightTokens.InlayHintIcon,
        diagnosticError: Color = ColorLightTokens.DiagnosticError,
        diagnosticWarning: Color = ColorLightTokens.DiagnosticWarning,
        diagnosticInfo: Color = ColorLightTokens.DiagnosticInfo,
        diagnosticHint: Color = ColorLightTokens.DiagnosticHint,
        linkedEditingActive: Color = ColorLightTokens.LinkedEditingActive,
        linkedEditingInactive: Color = ColorLightTokens.LinkedEditingInactive,
        bracketHighlightBorder: Color = ColorLightTokens.BracketHighlightBorder,
        bracketHighlightBackground: Color = ColorLightTokens.BracketHighlightBackground,
        gutterBackground: Color = ColorLightTokens.GutterBackground,
    ) = SweetEditorColors(
        background = background,
        text = text,
        cursor = cursor,
        selection = selection,
        lineNumber = lineNumber,
        currentLineNumber = currentLineNumber,
        currentLine = currentLine,
        guide = guide,
        separatorLine = separatorLine,
        splitLine = splitLine,
        scrollbarTrack = scrollbarTrack,
        scrollbarThumb = scrollbarThumb,
        scrollbarThumbActive = scrollbarThumbActive,
        compositionUnderline = compositionUnderline,
        inlayHintBackground = inlayHintBackground,
        inlayHintText = inlayHintText,
        foldPlaceholderBackground = foldPlaceholderBackground,
        foldPlaceholderText = foldPlaceholderText,
        phantomText = phantomText,
        inlayHintIcon = inlayHintIcon,
        diagnosticError = diagnosticError,
        diagnosticWarning = diagnosticWarning,
        diagnosticInfo = diagnosticInfo,
        diagnosticHint = diagnosticHint,
        linkedEditingActive = linkedEditingActive,
        linkedEditingInactive = linkedEditingInactive,
        bracketHighlightBorder = bracketHighlightBorder,
        bracketHighlightBackground = bracketHighlightBackground,
        gutterBackground = gutterBackground,
    )

    fun darkColors(
        background: Color = ColorDarkTokens.Background,
        text: Color = ColorDarkTokens.Text,
        cursor: Color = ColorDarkTokens.Cursor,
        selection: Color = ColorDarkTokens.Selection,
        lineNumber: Color = ColorDarkTokens.LineNumber,
        currentLineNumber: Color = ColorDarkTokens.CurrentLineNumber,
        currentLine: Color = ColorDarkTokens.CurrentLine,
        guide: Color = ColorDarkTokens.Guide,
        separatorLine: Color = ColorDarkTokens.SeparatorLine,
        splitLine: Color = ColorDarkTokens.SplitLine,
        scrollbarTrack: Color = ColorDarkTokens.ScrollbarTrack,
        scrollbarThumb: Color = ColorDarkTokens.ScrollbarThumb,
        scrollbarThumbActive: Color = ColorDarkTokens.ScrollbarThumbActive,
        compositionUnderline: Color = ColorDarkTokens.CompositionUnderline,
        inlayHintBackground: Color = ColorDarkTokens.InlayHintBackground,
        inlayHintText: Color = ColorDarkTokens.InlayHintText,
        foldPlaceholderBackground: Color = ColorDarkTokens.FoldPlaceholderBackground,
        foldPlaceholderText: Color = ColorDarkTokens.FoldPlaceholderText,
        phantomText: Color = ColorDarkTokens.PhantomText,
        inlayHintIcon: Color = ColorDarkTokens.InlayHintIcon,
        diagnosticError: Color = ColorDarkTokens.DiagnosticError,
        diagnosticWarning: Color = ColorDarkTokens.DiagnosticWarning,
        diagnosticInfo: Color = ColorDarkTokens.DiagnosticInfo,
        diagnosticHint: Color = ColorDarkTokens.DiagnosticHint,
        linkedEditingActive: Color = ColorDarkTokens.LinkedEditingActive,
        linkedEditingInactive: Color = ColorDarkTokens.LinkedEditingInactive,
        bracketHighlightBorder: Color = ColorDarkTokens.BracketHighlightBorder,
        bracketHighlightBackground: Color = ColorDarkTokens.BracketHighlightBackground,
        gutterBackground: Color = ColorDarkTokens.GutterBackground,
    ) = SweetEditorColors(
        background = background,
        text = text,
        cursor = cursor,
        selection = selection,
        lineNumber = lineNumber,
        currentLineNumber = currentLineNumber,
        currentLine = currentLine,
        guide = guide,
        separatorLine = separatorLine,
        splitLine = splitLine,
        scrollbarTrack = scrollbarTrack,
        scrollbarThumb = scrollbarThumb,
        scrollbarThumbActive = scrollbarThumbActive,
        compositionUnderline = compositionUnderline,
        inlayHintBackground = inlayHintBackground,
        inlayHintText = inlayHintText,
        foldPlaceholderBackground = foldPlaceholderBackground,
        foldPlaceholderText = foldPlaceholderText,
        phantomText = phantomText,
        inlayHintIcon = inlayHintIcon,
        diagnosticError = diagnosticError,
        diagnosticWarning = diagnosticWarning,
        diagnosticInfo = diagnosticInfo,
        diagnosticHint = diagnosticHint,
        linkedEditingActive = linkedEditingActive,
        linkedEditingInactive = linkedEditingInactive,
        bracketHighlightBorder = bracketHighlightBorder,
        bracketHighlightBackground = bracketHighlightBackground,
        gutterBackground = gutterBackground,
    )

    fun lightSpanColors(
        keyword: Color = SpanColorLightTokens.Keyword,
        string: Color = SpanColorLightTokens.String,
        comment: Color = SpanColorLightTokens.Comment,
        number: Color = SpanColorLightTokens.Number,
        builtin: Color = SpanColorLightTokens.Builtin,
        type: Color = SpanColorLightTokens.Type,
        className: Color = SpanColorLightTokens.Class,
        function: Color = SpanColorLightTokens.Function,
        variable: Color = SpanColorLightTokens.Variable,
        property: Color = SpanColorLightTokens.Property,
        parameter: Color = SpanColorLightTokens.Parameter,
        constant: Color = SpanColorLightTokens.Constant,
        field: Color = SpanColorLightTokens.Field,
        namespace: Color = SpanColorLightTokens.Namespace,
        enumMember: Color = SpanColorLightTokens.EnumMember,
        operator: Color = SpanColorLightTokens.Operator,
        punctuation: Color = SpanColorLightTokens.Punctuation,
        annotation: Color = SpanColorLightTokens.Annotation,
        preprocessor: Color = SpanColorLightTokens.Preprocessor,
    ) = SweetEditorSpanColors(
        keyword = keyword,
        string = string,
        comment = comment,
        number = number,
        builtin = builtin,
        type = type,
        className = className,
        function = function,
        variable = variable,
        property = property,
        parameter = parameter,
        constant = constant,
        field = field,
        namespace = namespace,
        enumMember = enumMember,
        operator = operator,
        punctuation = punctuation,
        annotation = annotation,
        preprocessor = preprocessor,
    )

    fun darkSpanColors(
        keyword: Color = SpanColorDarkTokens.Keyword,
        string: Color = SpanColorDarkTokens.String,
        comment: Color = SpanColorDarkTokens.Comment,
        number: Color = SpanColorDarkTokens.Number,
        builtin: Color = SpanColorDarkTokens.Builtin,
        type: Color = SpanColorDarkTokens.Type,
        className: Color = SpanColorDarkTokens.Class,
        function: Color = SpanColorDarkTokens.Function,
        variable: Color = SpanColorDarkTokens.Variable,
        property: Color = SpanColorDarkTokens.Property,
        parameter: Color = SpanColorDarkTokens.Parameter,
        constant: Color = SpanColorDarkTokens.Constant,
        field: Color = SpanColorDarkTokens.Field,
        namespace: Color = SpanColorDarkTokens.Namespace,
        enumMember: Color = SpanColorDarkTokens.EnumMember,
        operator: Color = SpanColorDarkTokens.Operator,
        punctuation: Color = SpanColorDarkTokens.Punctuation,
        annotation: Color = SpanColorDarkTokens.Annotation,
        preprocessor: Color = SpanColorDarkTokens.Preprocessor,
    ) = SweetEditorSpanColors(
        keyword = keyword,
        string = string,
        comment = comment,
        number = number,
        builtin = builtin,
        type = type,
        className = className,
        function = function,
        variable = variable,
        property = property,
        parameter = parameter,
        constant = constant,
        field = field,
        namespace = namespace,
        enumMember = enumMember,
        operator = operator,
        punctuation = punctuation,
        annotation = annotation,
        preprocessor = preprocessor,
    )

    fun spanStyles(spanColors: SweetEditorSpanColors): SweetEditorSpanStyles = SweetEditorSpanStyles(
        keyword = DecorationTextStyle(
            color = spanColors.keyword,
            fontStyle = SpanFontStyle.Bold,
        ),
        string = DecorationTextStyle(spanColors.string),
        comment = DecorationTextStyle(
            color = spanColors.comment,
            fontStyle = SpanFontStyle.Italic,
        ),
        number = DecorationTextStyle(spanColors.number),
        builtin = DecorationTextStyle(spanColors.builtin),
        type = DecorationTextStyle(spanColors.type),
        className = DecorationTextStyle(
            color = spanColors.className,
            fontStyle = SpanFontStyle.Bold,
        ),
        interfaceName = DecorationTextStyle(
            color = spanColors.className,
            fontStyle = SpanFontStyle.Bold,
        ),
        enumName = DecorationTextStyle(
            color = spanColors.className,
            fontStyle = SpanFontStyle.Bold,
        ),
        struct = DecorationTextStyle(
            color = spanColors.className,
            fontStyle = SpanFontStyle.Bold,
        ),
        function = DecorationTextStyle(spanColors.function),
        variable = DecorationTextStyle(spanColors.variable),
        property = DecorationTextStyle(spanColors.property),
        parameter = DecorationTextStyle(spanColors.parameter),
        constant = DecorationTextStyle(spanColors.constant),
        field = DecorationTextStyle(spanColors.field),
        namespace = DecorationTextStyle(spanColors.namespace),
        enumMember = DecorationTextStyle(spanColors.enumMember),
        operator = DecorationTextStyle(spanColors.operator),
        punctuation = DecorationTextStyle(spanColors.punctuation),
        annotation = DecorationTextStyle(spanColors.annotation),
        preprocessor = DecorationTextStyle(spanColors.preprocessor),
    )

    val cornerRadius: Float = 1.5f

}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawEditorSurface(
    renderModel: EditorRenderModel?,
    textMeasurer: TextMeasurer,
    drawCache: SweetEditorDrawCache,
    iconPainter: EditorGutterIconPainter,
    renderSurfaceCache: RenderSurfaceCache,
    animatedCursor: AnimatedCursorRenderState,
    theme: SweetEditorThemeScheme,
    colors: ResolvedEditorColors,
    platformType: PlatformType,
    mobilePlatformTypes: List<PlatformType>,
) {
    if (renderModel == null) {
        drawRect(
            color = colors.background,
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
    val cornerRadius = density * theme.cornerRadius + .5f

    drawRect(
        color = colors.background,
        topLeft = Offset.Zero,
        size = size,
    )
    drawCurrentLine(
        renderModel = renderModel,
        fillColor = colors.currentLine,
        borderColor = colors.currentLineBorderColor,
        left = 0f,
        width = renderModel.viewportWidth.takeIf { it > 0f } ?: size.width,
    )

    if (renderSurfaceCache.selectionItems.isNotEmpty()) {
        drawSelectionItems(renderSurfaceCache.selectionItems, colors.selection)
    }

    if (renderSurfaceCache.guideGroups.isNotEmpty()) {
        drawGuideGroups(renderSurfaceCache.guideGroups, colors, drawCache)
    }

    if (renderSurfaceCache.diagnosticGroups.isNotEmpty()) {
        drawDiagnosticGroups(renderSurfaceCache.diagnosticGroups, colors, drawCache)
    }

    if (
        viewportBounds.intersects(
            renderModel.compositionDecoration.origin.x,
            renderModel.compositionDecoration.origin.y,
            renderModel.compositionDecoration.width,
            renderModel.compositionDecoration.height,
        )
    ) {
        drawCompositionDecoration(renderModel, colors.compositionUnderline)
    }

    renderModel.linkedEditingRects.forEach { rect ->
        if (viewportBounds.intersects(rect.origin.x, rect.origin.y, rect.width, rect.height)) {
            drawLinkedEditing(
                rect = rect,
                activeColor = colors.linkedEditingActive,
                inactiveColor = colors.linkedEditingInactive,
            )
        }
    }

    renderModel.bracketHighlightRects.forEach { rect ->
        if (viewportBounds.intersects(rect.origin.x, rect.origin.y, rect.width, rect.height)) {
            drawRoundRect(
                color = colors.bracketHighlightBackground,
                topLeft = Offset(
                    x = rect.origin.x,
                    y = rect.origin.y,
                ),
                size = Size(rect.width, rect.height),
                cornerRadius = CornerRadius(cornerRadius),
            )
            drawRoundRect(
                color = colors.bracketHighlightBorder,
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
            drawRuns(textMeasurer, line, theme, colors, drawCache, viewportBounds, estimatedLineHeight)
        }
    }

    drawCursor(
        cornerRadius = cornerRadius,
        animatedCursor = animatedCursor,
        renderModel = renderModel,
        drawCache = drawCache,
        cursorColor = colors.cursor,
    )

    drawGutterBackground(
        renderModel = renderModel,
        gutterBackground = colors.gutterBackground,
        currentLine = colors.currentLine,
        currentLineBorderColor = colors.currentLineBorderColor,
        splitLine = colors.splitLine,
    )
    val activeLineColor = colors.currentLineAccentColor
    val overlayMode = renderModel.maxGutterIcons == 0
    var currentDrawingLineNumber = -1
    renderModel.lines.forEach { line ->
        if (!line.ownsGutterSemantics) {
            return@forEach
        }
        if (!viewportBounds.intersectsLine(line, estimatedLineHeight)) {
            return@forEach
        }
        val logicalLine = line.logicalLine
        val lineNumber = logicalLine + 1
        val iconItems = renderSurfaceCache.gutterIconsByLine[logicalLine].orEmpty()
        val hasIcons = iconItems.isNotEmpty()
        val lineIconTint = if (logicalLine == renderModel.cursor.textPosition.line) {
            activeLineColor
        } else {
            colors.inlayHintIcon
        }

        if (overlayMode && hasIcons) {
            val item = iconItems.first()
            if (viewportBounds.intersects(item.origin.x, item.origin.y, item.width, item.height)) {
                drawGutterIcon(
                    item = item,
                    painter = iconPainter,
                    tint = lineIconTint,
                )
            }
            currentDrawingLineNumber = lineNumber
        } else if (lineNumber != currentDrawingLineNumber) {
            drawLineNumber(renderModel, textMeasurer, line, drawCache, viewportBounds, estimatedLineHeight)
            currentDrawingLineNumber = lineNumber
        }

        if (!overlayMode && hasIcons) {
            iconItems.forEach { item ->
                if (viewportBounds.intersects(item.origin.x, item.origin.y, item.width, item.height)) {
                    drawGutterIcon(
                        item = item,
                        painter = iconPainter,
                        tint = lineIconTint,
                    )
                }
            }
        }

        val marker = renderSurfaceCache.foldMarkerByLine[logicalLine]
        if (marker != null && viewportBounds.intersects(
                marker.origin.x,
                marker.origin.y,
                marker.width,
                marker.height
            )
        ) {
            drawFoldMarker(
                marker = marker,
                color = if (marker.logicalLine == renderModel.cursor.textPosition.line) activeLineColor else colors.lineNumber,
            )
        }
    }

    drawScrollbar(renderModel.verticalScrollbar, colors)
    drawScrollbar(renderModel.horizontalScrollbar, colors)

    if (platformType in mobilePlatformTypes) {
        drawSelectionHandle(
            alignment = Alignment.Start,
            position = renderModel.selectionStartHandle.position,
            handleHeight = renderModel.selectionStartHandle.height,
            visible = renderModel.selectionStartHandle.visible,
            color = colors.cursor,
            drawCache = drawCache,
        )
        drawSelectionHandle(
            alignment = Alignment.End,
            position = renderModel.selectionEndHandle.position,
            handleHeight = renderModel.selectionEndHandle.height,
            visible = renderModel.selectionEndHandle.visible,
            color = colors.cursor,
            drawCache = drawCache,
        )
    }
}

private fun buildCursorDraggerPath(handleHeight: Float): Path = Path().apply {
    moveTo(0f, 0f)
    cubicTo(
        -0.2f * handleHeight, 0.2f * handleHeight,
        -0.5f * handleHeight, 0.4f * handleHeight,
        -0.5f * handleHeight, 0.7f * handleHeight,
    )
    cubicTo(
        -0.5f * handleHeight, 0.9f * handleHeight,
        -0.24f * handleHeight, 1f * handleHeight,
        0f, 1f * handleHeight,
    )
    cubicTo(
        0.24f * handleHeight, 1f * handleHeight,
        0.5f * handleHeight, 0.9f * handleHeight,
        0.5f * handleHeight, 0.7f * handleHeight,
    )
    cubicTo(
        0.5f * handleHeight, 0.4f * handleHeight,
        0.2f * handleHeight, 0.2f * handleHeight,
        0f, 0f,
    )
    close()
}

private fun DrawScope.drawGutterBackground(
    renderModel: EditorRenderModel,
    gutterBackground: Color,
    currentLine: Color,
    currentLineBorderColor: Color,
    splitLine: Color,
) {
    if (!renderModel.gutterVisible) {
        return
    }
    val gutterWidth = renderModel.splitX.coerceAtLeast(0f)
    drawRect(
        color = gutterBackground,
        topLeft = Offset.Zero,
        size = Size(gutterWidth, size.height),
    )
    drawCurrentLine(
        renderModel = renderModel,
        fillColor = currentLine,
        borderColor = currentLineBorderColor,
        left = 0f,
        width = gutterWidth,
    )
    if (!renderModel.splitLineVisible) {
        return
    }
    drawLine(
        color = splitLine,
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

private fun DrawScope.drawSelectionItems(
    items: List<SelectionRenderItem>,
    color: Color,
) {
    items.forEach { item ->
        val rect = item.rect
        if (rect != null) {
            drawRoundRect(
                color = color,
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = item.cornerRadius,
            )
        } else if (item.path != null) {
            drawPath(
                path = item.path,
                color = color,
            )
        }
    }
}

private fun DrawScope.drawGuideGroups(
    guideGroups: List<GuideRenderGroup>,
    colors: ResolvedEditorColors,
    drawCache: SweetEditorDrawCache,
) {
    guideGroups.forEach { group ->
        drawPath(
            path = group.path,
            color = if (group.type == GuideType.Separator) {
                colors.separatorLine
            } else {
                colors.guide
            },
            style = Stroke(
                width = group.strokeWidth,
                pathEffect = when (group.style) {
                    GuideStyle.Solid -> null
                    GuideStyle.Dashed -> drawCache.dashedGuidePathEffect
                    GuideStyle.Double -> drawCache.doubleGuidePathEffect
                },
            ),
        )
    }
}

private fun DrawScope.drawDiagnosticGroups(
    diagnosticGroups: List<DiagnosticRenderGroup>,
    colors: ResolvedEditorColors,
    drawCache: SweetEditorDrawCache,
) {
    diagnosticGroups.forEach { group ->
        drawPath(
            path = group.path,
            color = group.colorValue.takeIf { it != 0 }?.toComposeColor() ?: when (group.severity) {
                0 -> colors.diagnosticError
                1 -> colors.diagnosticWarning
                2 -> colors.diagnosticInfo
                else -> colors.diagnosticHint
            },
            style = Stroke(
                width = 2f,
                pathEffect = if (group.severity == 3) {
                    drawCache.hintDiagnosticPathEffect
                } else {
                    null
                },
            ),
        )
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
    drawCache: SweetEditorDrawCache,
    viewportBounds: ViewportBounds,
    estimatedLineHeight: Float,
) {
    if (!renderModel.gutterVisible || !line.ownsGutterSemantics) {
        return
    }
    if (!viewportBounds.intersectsLine(line, estimatedLineHeight)) {
        return
    }
    val layoutResult = drawCache.measureLineNumberText(
        textMeasurer = textMeasurer,
        text = (line.logicalLine + 1).toString(),
        active = line.logicalLine == renderModel.cursor.textPosition.line,
        baselineY = line.lineNumberPosition.y,
        estimatedLineHeight = estimatedLineHeight,
    )
    drawMeasuredText(
        layoutResult = layoutResult,
        x = line.lineNumberPosition.x,
        baselineY = line.lineNumberPosition.y,
    )
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawRuns(
    textMeasurer: TextMeasurer,
    line: VisualLine,
    theme: SweetEditorThemeScheme,
    colors: ResolvedEditorColors,
    drawCache: SweetEditorDrawCache,
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
        drawRunBackground(run, colors, estimatedLineHeight)
        val layoutResult = drawCache.measureRunText(
            textMeasurer = textMeasurer,
            text = run.text,
            style = run.style,
            type = run.type,
            theme = theme,
        )
        drawMeasuredText(
            layoutResult = layoutResult,
            x = runTextX(run),
            baselineY = run.y,
        )
    }
}

private fun DrawScope.drawRunBackground(
    run: VisualRun,
    colors: ResolvedEditorColors,
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
                color = colors.foldPlaceholderBackground,
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
                    color = colors.inlayHintBackground,
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
private fun DrawScope.drawMeasuredText(
    layoutResult: TextLayoutResult,
    x: Float,
    baselineY: Float,
) {
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
    cornerRadius: Float,
    animatedCursor: AnimatedCursorRenderState,
    renderModel: EditorRenderModel,
    drawCache: SweetEditorDrawCache,
    cursorColor: Color,
) {
    val cursor = renderModel.cursor

    if (animatedCursor.visible) {
        drawRoundRect(
            color = cursorColor,
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
        drawPath(
            path = drawCache.cursorDraggerPath(handleHeight),
            color = cursorColor,
        )
    }
}

private fun DrawScope.drawSelectionHandle(
    alignment: Alignment.Horizontal,
    position: PointF,
    handleHeight: Float,
    visible: Boolean,
    color: Color,
    drawCache: SweetEditorDrawCache,
) {
    if (!visible && alignment in listOf(Alignment.Start, Alignment.End)) {
        return
    }
    val stemHeight = handleHeight.coerceAtLeast(18f * density)

    drawPath(
        path = drawCache.selectionHandlePath(
            alignment = alignment,
            position = position,
            stemHeight = stemHeight,
        ),
        color = color,
    )
}

private fun buildSelectionHandlePath(
    alignment: Alignment.Horizontal,
    position: PointF,
    stemHeight: Float,
): Path {
    val path = Path()
    when (alignment) {
        Alignment.Start -> {
            path.apply {
                moveTo(position.x, position.y + stemHeight)
                lineTo(position.x - stemHeight, position.y + stemHeight)
                arcTo(
                    rect = Rect(
                        left = position.x - stemHeight,
                        top = position.y + stemHeight,
                        right = position.x,
                        bottom = position.y + stemHeight * 2
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
                moveTo(position.x, position.y + stemHeight)
                lineTo(position.x + stemHeight, position.y + stemHeight)
                arcTo(
                    rect = Rect(
                        left = position.x,
                        top = position.y + stemHeight,
                        right = position.x + stemHeight,
                        bottom = position.y + stemHeight * 2
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

private fun DrawScope.drawScrollbar(scrollbar: ScrollbarModel, colors: ResolvedEditorColors) {
    if (!scrollbar.visible) {
        return
    }
    drawRect(
        color = colors.scrollbarTrack.copy(alpha = scrollbar.alpha.coerceIn(0f, 1f)),
        topLeft = Offset(
            x = scrollbar.track.origin.x,
            y = scrollbar.track.origin.y,
        ),
        size = Size(scrollbar.track.width, scrollbar.track.height),
    )
    drawRect(
        color = if (scrollbar.thumbActive) {
            colors.scrollbarThumbActive
        } else {
            colors.scrollbarThumb
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

private data class RenderSurfaceCache(
    val selectionItems: List<SelectionRenderItem> = emptyList(),
    val guideGroups: List<GuideRenderGroup> = emptyList(),
    val diagnosticGroups: List<DiagnosticRenderGroup> = emptyList(),
    val gutterIconsByLine: Map<Int, List<GutterIconRenderItem>> = emptyMap(),
    val foldMarkerByLine: Map<Int, FoldMarkerRenderItem> = emptyMap(),
)

private data class SelectionRenderItem(
    val rect: Rect? = null,
    val cornerRadius: CornerRadius = CornerRadius.Zero,
    val path: Path? = null,
)

private data class GuideRenderGroup(
    val type: GuideType,
    val style: GuideStyle,
    val strokeWidth: Float,
    val path: Path,
)

private data class DiagnosticRenderGroup(
    val severity: Int,
    val colorValue: Int,
    val path: Path,
)

private fun buildRenderSurfaceCache(
    renderModel: EditorRenderModel?,
    cornerRadius: Float,
): RenderSurfaceCache {
    if (renderModel == null) {
        return RenderSurfaceCache()
    }
    return RenderSurfaceCache(
        selectionItems = buildSelectionRenderItems(renderModel.selectionRects, cornerRadius),
        guideGroups = buildGuideRenderGroups(renderModel.guideSegments),
        diagnosticGroups = buildDiagnosticRenderGroups(renderModel.diagnosticDecorations),
        gutterIconsByLine = buildGutterIconsByLine(renderModel.gutterIcons),
        foldMarkerByLine = buildFoldMarkerByLine(renderModel.foldMarkers),
    )
}

internal fun buildGutterIconsByLine(
    gutterIcons: List<GutterIconRenderItem>,
): Map<Int, List<GutterIconRenderItem>> {
    if (gutterIcons.isEmpty()) {
        return emptyMap()
    }
    val groupedIcons = linkedMapOf<Int, MutableList<GutterIconRenderItem>>()
    gutterIcons.forEach { item ->
        groupedIcons.getOrPut(item.logicalLine) { mutableListOf() }.add(item)
    }
    return groupedIcons
}

internal fun buildFoldMarkerByLine(
    foldMarkers: List<FoldMarkerRenderItem>,
): Map<Int, FoldMarkerRenderItem> {
    if (foldMarkers.isEmpty()) {
        return emptyMap()
    }
    val markersByLine = linkedMapOf<Int, FoldMarkerRenderItem>()
    foldMarkers.forEach { marker ->
        markersByLine[marker.logicalLine] = marker
    }
    return markersByLine
}

private data class GuideRenderGroupKey(
    val type: GuideType,
    val style: GuideStyle,
    val strokeWidth: Float,
)

private fun buildGuideRenderGroups(
    guideSegments: List<GuideSegment>,
): List<GuideRenderGroup> {
    if (guideSegments.isEmpty()) {
        return emptyList()
    }
    val groupedPaths = linkedMapOf<GuideRenderGroupKey, Path>()
    guideSegments.forEach { guide ->
        val key = GuideRenderGroupKey(
            type = guide.type,
            style = guide.style,
            strokeWidth = if (guide.type == GuideType.Indent) 1f else 1.2f,
        )
        val path = groupedPaths.getOrPut(key) { Path() }
        appendGuideToPath(path, guide)
    }
    return groupedPaths.map { (key, path) ->
        GuideRenderGroup(
            type = key.type,
            style = key.style,
            strokeWidth = key.strokeWidth,
            path = path,
        )
    }
}

private fun appendGuideToPath(
    path: Path,
    guide: GuideSegment,
) {
    val startX = guide.start.x
    val startY = guide.start.y
    val endX = guide.end.x
    val endY = guide.end.y
    if (guide.arrowEnd) {
        val arrowTrim = 8f
        val dx = endX - startX
        val dy = endY - startY
        val length = kotlin.math.sqrt(dx * dx + dy * dy)
        val lineEndX: Float
        val lineEndY: Float
        if (length > arrowTrim) {
            val ratio = (length - arrowTrim) / length
            lineEndX = startX + dx * ratio
            lineEndY = startY + dy * ratio
        } else {
            lineEndX = endX
            lineEndY = endY
        }
        path.moveTo(startX, startY)
        path.lineTo(lineEndX, lineEndY)
        appendArrowHeadToPath(path, startX, startY, endX, endY, 9f)
        return
    }
    path.moveTo(startX, startY)
    path.lineTo(endX, endY)
}

private fun appendArrowHeadToPath(
    path: Path,
    fromX: Float,
    fromY: Float,
    toX: Float,
    toY: Float,
    arrowLength: Float,
) {
    val dx = toX - fromX
    val dy = toY - fromY
    val length = kotlin.math.sqrt(dx * dx + dy * dy)
    if (length < 1f) {
        return
    }
    val ux = dx / length
    val uy = dy / length
    val arrowAngle = (PI * 28.0 / 180.0).toFloat()
    val cosA = kotlin.math.cos(arrowAngle)
    val sinA = kotlin.math.sin(arrowAngle)
    val ax1 = toX - arrowLength * (ux * cosA - uy * sinA)
    val ay1 = toY - arrowLength * (uy * cosA + ux * sinA)
    val ax2 = toX - arrowLength * (ux * cosA + uy * sinA)
    val ay2 = toY - arrowLength * (uy * cosA - ux * sinA)
    path.moveTo(toX, toY)
    path.lineTo(ax1, ay1)
    path.moveTo(toX, toY)
    path.lineTo(ax2, ay2)
}

private fun buildSelectionRenderItems(
    selectionRects: List<SelectionRect>,
    cornerRadius: Float,
): List<SelectionRenderItem> {
    val clusters = buildSelectionClusters(mergeSelectionBands(selectionRects))
    if (clusters.isEmpty()) {
        return emptyList()
    }
    val roundedCorner = CornerRadius(cornerRadius)
    return clusters.map { cluster ->
        if (cluster.size == 1) {
            val band = cluster.first()
            SelectionRenderItem(
                rect = Rect(
                    left = band.left,
                    top = band.top,
                    right = band.right,
                    bottom = band.bottom,
                ),
                cornerRadius = roundedCorner,
            )
        } else {
            SelectionRenderItem(
                path = buildRoundedSelectionPath(cluster, cornerRadius),
            )
        }
    }
}

private fun buildDiagnosticRenderGroups(
    decorations: List<DiagnosticDecoration>,
): List<DiagnosticRenderGroup> {
    if (decorations.isEmpty()) {
        return emptyList()
    }
    val groupedPaths = linkedMapOf<Pair<Int, Int>, Path>()
    decorations.forEach { decoration ->
        val key = decoration.severity to decoration.color
        val path = groupedPaths.getOrPut(key) { Path() }
        appendDiagnosticToPath(path, decoration)
    }
    return groupedPaths.map { (key, path) ->
        DiagnosticRenderGroup(
            severity = key.first,
            colorValue = key.second,
            path = path,
        )
    }
}

private fun appendDiagnosticToPath(
    path: Path,
    decoration: DiagnosticDecoration,
) {
    val startX = decoration.origin.x
    val endX = startX + decoration.width
    val baseY = decoration.origin.y + decoration.height - 1f
    if (decoration.severity == 3) {
        path.moveTo(startX, baseY)
        path.lineTo(endX, baseY)
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
        path.moveTo(x, baseY)
        path.lineTo(midX, peakY)
        path.lineTo(nextX, baseY)
        x = nextX
        step++
    }
}

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

private fun currentLineBorderColor(theme: SweetEditorThemeScheme): Color {
    val color = theme.colors.currentLine
    return if (color.alpha < 0.63f) {
        color.copy(alpha = 0.63f)
    } else {
        color
    }
}

private fun currentLineAccentColor(theme: SweetEditorThemeScheme): Color {
    val accent = theme.colors.currentLineNumber
    return accent.copy(alpha = 1f)
}

internal data class ResolvedEditorColors(
    val background: Color,
    val currentLine: Color,
    val currentLineBorderColor: Color,
    val currentLineAccentColor: Color,
    val selection: Color,
    val guide: Color,
    val separatorLine: Color,
    val compositionUnderline: Color,
    val linkedEditingActive: Color,
    val linkedEditingInactive: Color,
    val bracketHighlightBackground: Color,
    val bracketHighlightBorder: Color,
    val gutterBackground: Color,
    val splitLine: Color,
    val inlayHintBackground: Color,
    val foldPlaceholderBackground: Color,
    val inlayHintIcon: Color,
    val lineNumber: Color,
    val cursor: Color,
    val diagnosticError: Color,
    val diagnosticWarning: Color,
    val diagnosticInfo: Color,
    val diagnosticHint: Color,
    val scrollbarTrack: Color,
    val scrollbarThumb: Color,
    val scrollbarThumbActive: Color,
)

internal fun resolveEditorColors(theme: SweetEditorThemeScheme): ResolvedEditorColors = ResolvedEditorColors(
    background = theme.colors.background,
    currentLine = theme.colors.currentLine,
    currentLineBorderColor = currentLineBorderColor(theme),
    currentLineAccentColor = currentLineAccentColor(theme),
    selection = theme.colors.selection,
    guide = theme.colors.guide,
    separatorLine = theme.colors.separatorLine,
    compositionUnderline = theme.colors.compositionUnderline,
    linkedEditingActive = theme.colors.linkedEditingActive,
    linkedEditingInactive = theme.colors.linkedEditingInactive,
    bracketHighlightBackground = theme.colors.bracketHighlightBackground,
    bracketHighlightBorder = theme.colors.bracketHighlightBorder,
    gutterBackground = theme.colors.gutterBackground,
    splitLine = theme.colors.splitLine,
    inlayHintBackground = theme.colors.inlayHintBackground,
    foldPlaceholderBackground = theme.colors.foldPlaceholderBackground,
    inlayHintIcon = theme.colors.inlayHintIcon,
    lineNumber = theme.colors.lineNumber,
    cursor = theme.colors.cursor,
    diagnosticError = theme.colors.diagnosticError,
    diagnosticWarning = theme.colors.diagnosticWarning,
    diagnosticInfo = theme.colors.diagnosticInfo,
    diagnosticHint = theme.colors.diagnosticHint,
    scrollbarTrack = theme.colors.scrollbarTrack,
    scrollbarThumb = theme.colors.scrollbarThumb,
    scrollbarThumbActive = theme.colors.scrollbarThumbActive,
)

private fun runTextX(run: VisualRun): Float = when (run.type) {
    VisualRunType.FoldPlaceholder,
    VisualRunType.InlayHint,
        -> run.x + run.margin + run.padding

    else -> run.x
}

private class EditorGutterIconPainter(
    controller: NativeEditorController,
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
internal class SweetEditorDrawCache(
    private val theme: SweetEditorThemeScheme,
    private val enableTextLayoutCache: Boolean,
) {
    private val runTextStyles = mutableMapOf<RunTextStyleKey, TextStyle>()
    private val lineNumberTextStyles = mutableMapOf<LineNumberTextStyleKey, TextStyle>()
    private val selectionHandlePaths = mutableMapOf<SelectionHandlePathKey, Path>()
    private val cursorDraggerPaths = mutableMapOf<Int, Path>()
    private val runTextLayouts = SimpleLruCache<RunTextLayoutCacheKey, TextLayoutResult>(maxSize = 1024)
    private val lineNumberTextLayouts = SimpleLruCache<LineNumberTextLayoutCacheKey, TextLayoutResult>(maxSize = 256)

    val dashedGuidePathEffect: PathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
    val doubleGuidePathEffect: PathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 3f))
    val hintDiagnosticPathEffect: PathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 2f))

    /**
     * Returns a cached Compose text style for one editor text style.
     *
     * @param style editor text style produced by the native render model.
     * @return cached Compose text style.
     */
    fun runTextStyle(
        style: EditorTextStyle,
        type: VisualRunType,
        theme: SweetEditorThemeScheme,
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
    ): TextStyle {
        val baselineShift = computeLineNumberBaselineShift(baselineY, estimatedLineHeight)
        val key = computeLineNumberTextStyleKey(active, baselineShift)
        return lineNumberTextStyles.getOrPut(key) {
            TextStyle(
                color = if (active) {
                    theme.colors.currentLineNumber
                } else {
                    theme.colors.lineNumber
                },
                fontFamily = theme.typography.fontFamily,
                fontSize = theme.typography.lineNumberFontSize,
                baselineShift = androidx.compose.ui.text.style.BaselineShift(baselineShift),
            )
        }
    }

    /**
     * Measures text with a bounded cache to avoid repeated layout work.
     *
     * @param textMeasurer Compose text measurer.
     * @param text raw text to measure.
     * @param style Compose text style used for measurement.
     * @return measured text layout result.
     */
    fun measureRunText(
        textMeasurer: TextMeasurer,
        text: String,
        style: EditorTextStyle,
        type: VisualRunType,
        theme: SweetEditorThemeScheme,
    ): TextLayoutResult {
        val resolvedStyle = runTextStyle(style, type, theme)
        if (!enableTextLayoutCache || text.length > 2048) {
            return textMeasurer.measure(
                text = text,
                style = resolvedStyle,
            )
        }
        val key = RunTextLayoutCacheKey(
            text = text,
            style = RunTextStyleKey(style, type),
        )
        runTextLayouts[key]?.let { return it }
        return textMeasurer.measure(
            text = text,
            style = resolvedStyle,
        ).also { layoutResult ->
            runTextLayouts[key] = layoutResult
        }
    }

    fun measureLineNumberText(
        textMeasurer: TextMeasurer,
        text: String,
        active: Boolean,
        baselineY: Float,
        estimatedLineHeight: Float,
    ): TextLayoutResult {
        val styleKey = computeLineNumberTextStyleKey(
            active = active,
            baselineShift = computeLineNumberBaselineShift(baselineY, estimatedLineHeight),
        )
        val style = lineNumberStyle(
            active = active,
            baselineY = baselineY,
            estimatedLineHeight = estimatedLineHeight,
        )
        val key = LineNumberTextLayoutCacheKey(
            text = text,
            style = styleKey,
        )
        if (!enableTextLayoutCache) {
            return textMeasurer.measure(
                text = text,
                style = style,
            )
        }
        lineNumberTextLayouts[key]?.let { return it }
        return textMeasurer.measure(
            text = text,
            style = style,
        ).also { layoutResult ->
            lineNumberTextLayouts[key] = layoutResult
        }
    }

    fun cursorDraggerPath(handleHeight: Float): Path {
        val key = (handleHeight * 100f).roundToInt()
        return cursorDraggerPaths.getOrPut(key) {
            buildCursorDraggerPath(handleHeight)
        }
    }

    fun selectionHandlePath(
        alignment: Alignment.Horizontal,
        position: PointF,
        stemHeight: Float,
    ): Path {
        val key = SelectionHandlePathKey(
            alignment = alignment,
            xBucket = (position.x * 100f).roundToInt(),
            yBucket = (position.y * 100f).roundToInt(),
            stemHeightBucket = (stemHeight * 100f).roundToInt(),
        )
        return selectionHandlePaths.getOrPut(key) {
            buildSelectionHandlePath(alignment, position, stemHeight)
        }
    }
}

private class SimpleLruCache<K, V>(
    private val maxSize: Int,
) {
    private val values = mutableMapOf<K, V>()
    private val accessOrder = linkedSetOf<K>()

    operator fun get(key: K): V? {
        val value = values[key] ?: return null
        accessOrder.remove(key)
        accessOrder.add(key)
        return value
    }

    operator fun set(key: K, value: V) {
        if (values.containsKey(key)) {
            accessOrder.remove(key)
        }
        values[key] = value
        accessOrder.add(key)
        while (values.size > maxSize) {
            val eldestKey = accessOrder.firstOrNull() ?: break
            accessOrder.remove(eldestKey)
            values.remove(eldestKey)
        }
    }
}

internal fun supportsReusableTextLayoutCache(platformType: PlatformType): Boolean =
    platformType != PlatformType.Android

private fun resolveEditorPointerIcon(
    renderModel: EditorRenderModel?,
    scrollMetrics: ScrollMetrics,
    hoverPosition: Offset?,
): PointerIcon {
    if (renderModel == null || hoverPosition == null) {
        return PointerIcon.Default
    }
    if (renderModel.isInGutterArea(hoverPosition) || renderModel.isInScrollbarArea(hoverPosition)) {
        return PointerIcon.Default
    }
    if (scrollMetrics.isInTextArea(hoverPosition)) {
        return textInputPointerIcon()
    }
    return when (renderModel.pointerCursorType) {
        PointerCursorType.Hand -> PointerIcon.Hand
        else -> PointerIcon.Default
    }
}

private fun EditorRenderModel.isInGutterArea(position: Offset): Boolean {
    if (!gutterVisible) {
        return false
    }
    val gutterWidth = splitX.coerceAtLeast(0f)
    return position.x in 0f..gutterWidth
}

private fun EditorRenderModel.isInScrollbarArea(position: Offset): Boolean =
    verticalScrollbar.track.contains(position) || horizontalScrollbar.track.contains(position)

private fun ScrollMetrics.isInTextArea(position: Offset): Boolean {
    val textLeft = textAreaX.coerceAtLeast(0f)
    val textWidth = textAreaWidth.takeIf { it > 0f } ?: viewportWidth
    val textRight = (textLeft + textWidth).coerceAtLeast(textLeft)
    val textBottom = viewportHeight.takeIf { it > 0f } ?: Float.MAX_VALUE
    return position.x in textLeft..textRight && position.y in 0f..textBottom
}

private fun ScrollbarRect.contains(position: Offset): Boolean {
    if (width <= 0f || height <= 0f) {
        return false
    }
    val left = origin.x
    val top = origin.y
    val right = left + width
    val bottom = top + height
    return position.x in left..right && position.y in top..bottom
}

/**
 * Cache key used by [SweetEditorDrawCache] for measured text layouts.
 */
private data class RunTextLayoutCacheKey(
    val text: String,
    val style: RunTextStyleKey,
)

private data class LineNumberTextLayoutCacheKey(
    val text: String,
    val style: LineNumberTextStyleKey,
)

internal fun computeRunTextLayoutCacheIdentity(
    text: String,
    style: EditorTextStyle,
    type: VisualRunType,
): Pair<String, RunTextStyleKey> = text to RunTextStyleKey(style, type)

internal fun computeLineNumberTextLayoutCacheIdentity(
    text: String,
    active: Boolean,
    baselineY: Float,
    estimatedLineHeight: Float,
): Pair<String, LineNumberTextStyleKey> = text to computeLineNumberTextStyleKey(
    active = active,
    baselineShift = computeLineNumberBaselineShift(
        baselineY = baselineY,
        estimatedLineHeight = estimatedLineHeight,
    ),
)

private data class SelectionHandlePathKey(
    val alignment: Alignment.Horizontal,
    val xBucket: Int,
    val yBucket: Int,
    val stemHeightBucket: Int,
)

internal data class LineNumberTextStyleKey(
    val active: Boolean,
    val baselineShiftBucket: Int,
)

internal fun computeLineNumberTextStyleKey(
    active: Boolean,
    baselineShift: Float,
): LineNumberTextStyleKey = LineNumberTextStyleKey(
    active = active,
    baselineShiftBucket = (baselineShift * 10_000f).roundToInt(),
)

internal fun computeLineNumberBaselineShift(
    baselineY: Float,
    estimatedLineHeight: Float,
): Float {
    if (estimatedLineHeight <= 0f) {
        return 0f
    }
    val normalizedBaseline = (baselineY % estimatedLineHeight) / estimatedLineHeight
    return (normalizedBaseline - 0.5f) * 0.03f
}

private fun buildDefaultScrollbarConfig(
    platformType: PlatformType,
    density: Float,
): NativeScrollbarConfig {
    val isMobile = platformType == PlatformType.Android || platformType == PlatformType.IOS
    val thicknessDp = if (isMobile) 8f else 8f
    val minThumbDp = if (isMobile) 36f else 24f
    val hitPaddingDp = if (isMobile) 8f else 6f
    val showMode = if (isMobile) 1 else 0
    return NativeScrollbarConfig(
        thickness = thicknessDp * density,
        minThumb = minThumbDp * density,
        thumbHitPadding = hitPaddingDp * density,
        mode = showMode,
        thumbDraggable = true,
        trackTapMode = 0,
        fadeDelayMillis = if (isMobile) 900 else 600,
        fadeDurationMillis = if (isMobile) 260 else 220,
    )
}

private fun buildDefaultHandleConfig(
    platformType: PlatformType,
    density: Float,
): NativeHandleConfig? {
    val isMobile = platformType == PlatformType.Android || platformType == PlatformType.IOS
    if (!isMobile) {
        return null
    }
    val angle = (PI / 4.0).toFloat()
    val cos = kotlin.math.cos(angle)
    val sin = kotlin.math.sin(angle)
    val radius = 10f
    val distance = 24f
    val points = listOf(
        0f to 0f,
        -radius to distance,
        radius to distance,
        0f to (distance + radius),
        0f to (distance - radius * 0.8f),
    )
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    points.forEach { (x, y) ->
        val rx = x * cos - y * sin
        val ry = x * sin + y * cos
        minX = minOf(minX, rx)
        minY = minOf(minY, ry)
        maxX = maxOf(maxX, rx)
        maxY = maxOf(maxY, ry)
    }
    val pad = 8f
    return NativeHandleConfig(
        startLeft = (minX - pad) * density,
        startTop = (minY - pad) * density,
        startRight = (maxX + pad) * density,
        startBottom = (maxY + pad) * density,
        endLeft = (-maxX - pad) * density,
        endTop = (minY - pad) * density,
        endRight = (-minX + pad) * density,
        endBottom = (maxY + pad) * density,
    )
}

internal data class RunTextStyleKey(
    val style: EditorTextStyle,
    val type: VisualRunType,
)

private fun EditorTextStyle.toComposeTextStyle(
    theme: SweetEditorThemeScheme,
    type: VisualRunType,
): TextStyle {
    val resolvedColor = when {
        color == Color.Unspecified -> null
        else -> color
    }
    val resolvedBackgroundColor = when {
        backgroundColor == Color.Unspecified -> null
        else -> backgroundColor
    }
    val decorations = buildList {
        if (fontStyle.contains(SpanFontStyle.Strikethrough)) {
            add(TextDecoration.LineThrough)
        }
    }
    return TextStyle(
        color = when (type) {
            VisualRunType.FoldPlaceholder -> theme.colors.foldPlaceholderText
            VisualRunType.PhantomText -> theme.colors.phantomText
            else -> resolvedColor ?: theme.colors.text
        },
        background = when {
            type == VisualRunType.FoldPlaceholder || type == VisualRunType.InlayHint -> Color.Transparent
            else -> resolvedBackgroundColor ?: Color.Transparent
        },
        fontWeight = if (fontStyle.contains(SpanFontStyle.Bold)) FontWeight.Bold else null,
        fontStyle = if (fontStyle.contains(SpanFontStyle.Italic)) FontStyle.Italic else null,
        textDecoration = decorations.takeIf { it.isNotEmpty() }?.reduce(TextDecoration::plus),
        fontFamily = theme.typography.fontFamily,
        fontSize = if (type == VisualRunType.InlayHint) theme.typography.inlayHintFontSize else theme.typography.fontSize,
    )
}

private fun SweetEditorThemeScheme.scaled(scale: Float): SweetEditorThemeScheme {
    val normalizedScale = scale.coerceAtLeast(0.1f)
    return copy(
        typography = typography.copy(
            fontSize = typography.fontSize * normalizedScale,
            lineNumberFontSize = typography.lineNumberFontSize * normalizedScale,
            inlayHintFontSize = typography.inlayHintFontSize * normalizedScale,
            iconSize = typography.iconSize * normalizedScale,
        ),
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

internal data class PointerChangeSnapshot(
    val type: PointerType,
    val position: GesturePoint,
    val previousPosition: GesturePoint,
    val pressed: Boolean,
    val changedToDown: Boolean,
    val changedToUp: Boolean,
)

internal data class PointerGestureDispatch(
    val type: EditorGestureEventType,
    val points: List<GesturePoint>,
    val wheelDeltaX: Float = 0f,
    val wheelDeltaY: Float = 0f,
    val directScale: Float = 1f,
)

internal data class PointerDispatchPlan(
    val requestFocus: Boolean = false,
    val dispatches: List<PointerGestureDispatch> = emptyList(),
)

internal fun normalizeMouseWheelScrollDelta(
    scrollDelta: Offset,
): Offset = normalizePlatformMouseWheelScrollDelta(scrollDelta)

internal fun buildPointerDispatchPlan(
    scrollDelta: Offset,
    isSecondaryPressed: Boolean,
    changes: List<PointerChangeSnapshot>,
): PointerDispatchPlan {
    val dispatches = mutableListOf<PointerGestureDispatch>()
    if (scrollDelta != Offset.Zero) {
        dispatches += PointerGestureDispatch(
            type = EditorGestureEventType.MouseWheel,
            points = emptyList(),
            wheelDeltaX = scrollDelta.x,
            wheelDeltaY = scrollDelta.y,
        )
    }

    val allPoints = changes.map { it.position }
    val downChanges = changes.filter { it.changedToDown }
    val upChanges = changes.filter { it.changedToUp }
    val pressedChanges = changes.filter { it.pressed }
    val movedPressedPoints = pressedChanges
        .filter { it.position != it.previousPosition }
        .map { it.position }

    if (downChanges.isNotEmpty()) {
        val primaryDown = downChanges.first()
        dispatches += if (primaryDown.type == PointerType.Touch && pressedChanges.size > 1 && allPoints.isNotEmpty()) {
            PointerGestureDispatch(
                type = EditorGestureEventType.TouchPointerDown,
                points = allPoints,
            )
        } else {
            PointerGestureDispatch(
                type = primaryDown.type.toDownEventType(
                    isSecondaryPressed = isSecondaryPressed,
                ),
                points = listOf(primaryDown.position),
            )
        }
        return PointerDispatchPlan(
            requestFocus = true,
            dispatches = dispatches,
        )
    }

    if (upChanges.isNotEmpty()) {
        val primaryUp = upChanges.first()
        dispatches += if (primaryUp.type == PointerType.Touch && pressedChanges.isNotEmpty() && allPoints.isNotEmpty()) {
            PointerGestureDispatch(
                type = EditorGestureEventType.TouchPointerUp,
                points = allPoints,
            )
        } else {
            PointerGestureDispatch(
                type = primaryUp.type.toUpEventType(),
                points = listOf(primaryUp.position),
            )
        }
        return PointerDispatchPlan(dispatches = dispatches)
    }

    if (movedPressedPoints.isNotEmpty()) {
        val pointerType = pressedChanges.firstOrNull()?.type ?: PointerType.Touch
        val scaleDelta = if (pointerType == PointerType.Touch && pressedChanges.size >= 2) {
            pressedChanges.calculateScaleDelta()
        } else {
            1f
        }
        val movePoints = if (pointerType == PointerType.Touch) {
            pressedChanges.map { it.position }
        } else {
            movedPressedPoints
        }
        dispatches += PointerGestureDispatch(
            type = pointerType.toMoveEventType(),
            points = movePoints,
        )
        if (pointerType == PointerType.Touch && pressedChanges.size >= 2 && scaleDelta != 1f) {
            dispatches += PointerGestureDispatch(
                type = EditorGestureEventType.DirectScale,
                points = listOf(pressedChanges.calculateCentroidPoint()),
                directScale = scaleDelta,
            )
        }
    }

    return PointerDispatchPlan(dispatches = dispatches)
}

private fun List<PointerChangeSnapshot>.calculateCentroidPoint(): GesturePoint {
    if (isEmpty()) {
        return GesturePoint()
    }
    val centerX = sumOf { it.position.x.toDouble() }.toFloat() / size
    val centerY = sumOf { it.position.y.toDouble() }.toFloat() / size
    return GesturePoint(centerX, centerY)
}

private fun List<PointerChangeSnapshot>.calculateScaleDelta(): Float {
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

@Composable
private fun CompletionPopup(
    completions: (@Composable (selectedIndex: Int, items: List<CompletionItem>, render: CompletionItemRenderer?) -> Unit)?,
    controller: SweetEditorController,
    editorWindowOffset: IntOffset,
) {
    completions?.also {
        val result = controller.state.completionResult ?: return
        val cursor = controller.state.renderModel?.cursor ?: return
        val selectedIndex = controller.state.completionSelectedIndex
        val renderer = controller.state.completionItemRenderer

        if (result.items.isNotEmpty()) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(
                    x = editorWindowOffset.x + cursor.position.x.roundToInt(),
                    y = editorWindowOffset.y + (cursor.position.y + cursor.height).roundToInt(),
                ),
                onDismissRequest = {
                    controller.dismissCompletion()
                },
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    clippingEnabled = true
                )
            ) {
                Column(
                    modifier = Modifier.sizeIn(
                        minWidth = 180.dp,
                        maxWidth = 300.dp,
                        minHeight = 240.dp,
                        maxHeight = 500.dp
                    )
                ) {
                    it(selectedIndex, result.items, renderer)
                }
            }
        }
    }
}

private fun SweetEditorController.handleComposeKeyEvent(
    event: KeyEvent,
    preferIme: Boolean,
): Boolean {
    if (event.type != KeyEventType.KeyDown) {
        return false
    }
    if (isComposing() && event.key != Key.Escape) {
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
            handleEnterAction()
            return true
        }

        Key.Tab -> {
            if (inlineSuggestions().handleKeyEvent(event.key)) {
                return true
            }
            if (handleTabAction(reverse = event.isShiftPressed)) {
                return true
            }
        }

        Key.Escape -> {
            if (inlineSuggestions().handleKeyEvent(event.key)) {
                return true
            }
            if (handleEscapeAction()) {
                return true
            }
        }
    }
    val mappedKeyCode = event.key.toEditorKeyCode()
    if (mappedKeyCode != 0) {
        val result = handleKeyEvent(
            keyCode = mappedKeyCode,
            text = null,
            modifiers = event.toNativeModifiers(),
        )
        return result.handled
    }
    if (preferIme) {
        val shortcutKeyCode = event.key.keyCode.toInt().takeIf {
            (event.isCtrlPressed || event.isMetaPressed) && event.key.isCtrlShortcutKey()
        } ?: return false
        val result = handleKeyEvent(
            keyCode = shortcutKeyCode,
            text = null,
            modifiers = event.toNativeModifiers(),
        )
        return result.handled
    }
    val text = event.toInsertedText()
    if (text != null && event.shouldInsertDirectText()) {
        insertText(text)
        return true
    }
    val shortcutKeyCode = event.key.keyCode.toInt().takeIf {
        (event.isCtrlPressed || event.isMetaPressed) && event.key.isCtrlShortcutKey()
    } ?: return false
    val result = handleKeyEvent(
        keyCode = shortcutKeyCode,
        text = null,
        modifiers = event.toNativeModifiers(),
    )
    return result.handled
}

private fun SweetEditorController.handleComposeTextInputFallback(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) {
        return false
    }
    if (isComposing()) {
        return false
    }
    val text = event.toInsertedText() ?: return false
    if (!event.shouldInsertDirectText()) {
        return false
    }
    insertText(text)
    return true
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
    if (key.isModifierKey()) {
        return null
    }
    val codePoint = utf16CodePoint
    if (
        codePoint <= 0 ||
        codePoint < 32 ||
        codePoint == 0x7F ||
        codePoint in 0xE000..0xF8FF ||
        codePoint in 0xF0000..0xFFFFD ||
        codePoint in 0x100000..0x10FFFD ||
        codePoint > 0x10FFFF
    ) {
        return null
    }
    return if (codePoint <= 0xFFFF) {
        codePoint.toChar().toString()
    } else {
        val value = codePoint - 0x10000
        val high = (value / 0x400 + 0xD800).toChar()
        val low = (value % 0x400 + 0xDC00).toChar()
        "$high$low"
    }
}

private fun KeyEvent.shouldInsertDirectText(): Boolean =
    !isCtrlPressed &&
            !isAltPressed &&
            !isMetaPressed &&
            !key.isModifierKey()

private fun Key.toEditorKeyCode(): Int = when (this) {
    Key.Backspace -> 8
    Key.Tab -> 9
    Key.Enter, Key.NumPadEnter -> 13
    Key.Escape -> 27
    Key.Delete -> 46
    Key.DirectionLeft -> 37
    Key.DirectionUp -> 38
    Key.DirectionRight -> 39
    Key.DirectionDown -> 40
    Key.MoveHome -> 36
    Key.MoveEnd -> 35
    Key.PageUp -> 33
    Key.PageDown -> 34
    else -> 0
}

private fun Key.isCtrlShortcutKey(): Boolean = when (this) {
    Key.A,
    Key.C,
    Key.V,
    Key.X,
    Key.Y,
    Key.Z,
    Key.Spacebar,
        -> true

    else -> false
}

private fun Key.isModifierKey(): Boolean = when (this) {
    Key.CtrlLeft,
    Key.CtrlRight,
    Key.ShiftLeft,
    Key.ShiftRight,
    Key.AltLeft,
    Key.AltRight,
    Key.MetaLeft,
    Key.MetaRight,
        -> true

    else -> false
}

fun Int.toComposeColor(): Color = Color(this)

