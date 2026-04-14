package com.qiplat.compose.sweeteditor.protocol

import com.qiplat.compose.sweeteditor.model.decoration.TextStyle
import com.qiplat.compose.sweeteditor.model.foundation.*
import com.qiplat.compose.sweeteditor.model.visual.*

object ProtocolDecoder {
    fun decodeTextEditResult(data: ByteArray?): TextEditResult {
        if (data == null) {
            return TextEditResult.Empty
        }
        val reader = BinaryReader(data)
        val changed = reader.readBooleanAsInt()
        if (!changed) {
            return TextEditResult.Empty
        }
        val changeCount = reader.readInt()
        val changes = buildList(changeCount.coerceAtLeast(0)) {
            repeat(changeCount) {
                add(
                    TextChange(
                        range = readTextRange(reader),
                        newText = reader.readUtf8(),
                    ),
                )
            }
        }
        return TextEditResult(changed = true, changes = changes)
    }

    fun decodeKeyEventResult(data: ByteArray?): KeyEventResult {
        if (data == null) {
            return KeyEventResult()
        }
        val reader = BinaryReader(data)
        val handled = reader.readBooleanAsInt()
        val contentChanged = reader.readBooleanAsInt()
        val cursorChanged = reader.readBooleanAsInt()
        val selectionChanged = reader.readBooleanAsInt()
        val hasEdit = reader.readBooleanAsInt()
        val editResult = if (hasEdit) {
            decodeTextEditResultForReader(reader)
        } else {
            TextEditResult.Empty
        }
        return KeyEventResult(
            handled = handled,
            contentChanged = contentChanged,
            cursorChanged = cursorChanged,
            selectionChanged = selectionChanged,
            editResult = editResult,
        )
    }

    fun decodeGestureResult(data: ByteArray?): GestureResult {
        if (data == null) {
            return GestureResult()
        }
        val reader = BinaryReader(data)
        val type = reader.readInt().toGestureType()
        val tapPoint = when (type) {
            GestureType.Tap,
            GestureType.DoubleTap,
            GestureType.LongPress,
            GestureType.DragSelect,
            GestureType.ContextMenu,
            -> GesturePoint(
                x = reader.readFloat(),
                y = reader.readFloat(),
            )

            else -> GesturePoint()
        }

        val cursorPosition = readTextPosition(reader)
        val hasSelection = reader.readBooleanAsInt()
        val selection = readTextRange(reader)
        val viewScrollX = reader.readFloat()
        val viewScrollY = reader.readFloat()
        val viewScale = reader.readFloat()

        val hitTarget = if (reader.canRead(20)) {
            val hitType = reader.readInt().toHitTargetType()
            val hitTarget = HitTarget(
                type = hitType,
                line = reader.readInt(),
                column = reader.readInt(),
                iconId = reader.readInt(),
                colorValue = reader.readInt(),
            )
            if (hitType == HitTargetType.None) HitTarget.None else hitTarget
        } else {
            HitTarget.None
        }

        val needsEdgeScroll = if (reader.canRead(4)) reader.readBooleanAsInt() else false
        val needsFling = if (reader.canRead(4)) reader.readBooleanAsInt() else false
        val needsAnimation = if (reader.canRead(4)) reader.readBooleanAsInt() else false
        val isHandleDrag = if (reader.canRead(4)) reader.readBooleanAsInt() else false
        val pointerCursorType = if (reader.canRead(4)) reader.readInt().toPointerCursorType() else PointerCursorType.Default

        return GestureResult(
            type = type,
            tapPoint = tapPoint,
            cursorPosition = cursorPosition,
            hasSelection = hasSelection,
            selection = selection,
            viewScrollX = viewScrollX,
            viewScrollY = viewScrollY,
            viewScale = viewScale,
            hitTarget = hitTarget,
            needsEdgeScroll = needsEdgeScroll,
            needsFling = needsFling,
            needsAnimation = needsAnimation,
            isHandleDrag = isHandleDrag,
            pointerCursorType = pointerCursorType,
        )
    }

    fun decodeScrollMetrics(data: ByteArray?): ScrollMetrics {
        if (data == null) {
            return ScrollMetrics()
        }
        val reader = BinaryReader(data)
        if (!reader.canRead(52)) {
            return ScrollMetrics()
        }
        return ScrollMetrics(
            scale = reader.readFloat(),
            scrollX = reader.readFloat(),
            scrollY = reader.readFloat(),
            maxScrollX = reader.readFloat(),
            maxScrollY = reader.readFloat(),
            contentWidth = reader.readFloat(),
            contentHeight = reader.readFloat(),
            viewportWidth = reader.readFloat(),
            viewportHeight = reader.readFloat(),
            textAreaX = reader.readFloat(),
            textAreaWidth = reader.readFloat(),
            canScrollX = reader.readBooleanAsInt(),
            canScrollY = reader.readBooleanAsInt(),
        )
    }

    fun decodeRenderModel(data: ByteArray?): EditorRenderModel? {
        if (data == null) {
            return null
        }
        return decodeRenderModel(data, RenderModelLayout.Current)
            ?: decodeRenderModel(data, RenderModelLayout.Legacy)
    }

    private fun decodeTextEditResultForReader(reader: BinaryReader): TextEditResult {
        val changeCount = reader.readInt()
        val changes = buildList(changeCount.coerceAtLeast(0)) {
            repeat(changeCount) {
                add(
                    TextChange(
                        range = readTextRange(reader),
                        newText = reader.readUtf8(),
                    ),
                )
            }
        }
        return TextEditResult(changed = true, changes = changes)
    }

    private fun readTextPosition(reader: BinaryReader): TextPosition = TextPosition(
        line = reader.readInt(),
        column = reader.readInt(),
    )

    private fun readTextRange(reader: BinaryReader): TextRange = TextRange(
        start = readTextPosition(reader),
        end = readTextPosition(reader),
    )

    private fun readPoint(reader: BinaryReader): PointF = PointF(
        x = reader.readFloat(),
        y = reader.readFloat(),
    )

    private fun readTextStyle(reader: BinaryReader): TextStyle = TextStyle(
        color = reader.readInt(),
        backgroundColor = reader.readInt(),
        fontStyle = reader.readInt(),
    )

    private fun readVisualRun(reader: BinaryReader, layout: RenderModelLayout): VisualRun = VisualRun(
        type = reader.readInt().toVisualRunType(),
        x = reader.readFloat(),
        y = reader.readFloat(),
        text = reader.readUtf8(),
        style = readTextStyle(reader),
        iconId = reader.readInt(),
        colorValue = reader.readInt(),
        width = reader.readFloat(),
        padding = reader.readFloat(),
        margin = reader.readFloat(),
        active = if (layout == RenderModelLayout.Current) reader.readBooleanAsInt() else false,
    )

    private fun readVisualRuns(
        reader: BinaryReader,
        layout: RenderModelLayout,
    ): List<VisualRun> {
        val minBytesPerItem = if (layout == RenderModelLayout.Current) 48 else 44
        val count = readCount(reader, minBytesPerItem = minBytesPerItem, label = "visual runs")
        return buildList(count) {
            repeat(count) {
                add(readVisualRun(reader, layout))
            }
        }
    }

    private fun readVisualLine(
        reader: BinaryReader,
        layout: RenderModelLayout,
    ): VisualLine {
        val logicalLine = reader.readInt()
        val wrapIndex = reader.readInt()
        val lineNumberPosition = readPoint(reader)
        return if (layout == RenderModelLayout.Current) {
            VisualLine(
                logicalLine = logicalLine,
                wrapIndex = wrapIndex,
                lineNumberPosition = lineNumberPosition,
                kind = reader.readInt().toVisualLineKind(),
                ownsGutterSemantics = reader.readBooleanAsInt(),
                foldState = reader.readInt().toFoldState(),
                runs = readVisualRuns(reader, layout),
            )
        } else {
            val isPhantomLine = reader.readBooleanAsInt()
            VisualLine(
                logicalLine = logicalLine,
                wrapIndex = wrapIndex,
                lineNumberPosition = lineNumberPosition,
                kind = if (isPhantomLine) VisualLineKind.Phantom else VisualLineKind.Content,
                ownsGutterSemantics = wrapIndex == 0 && !isPhantomLine,
                foldState = reader.readInt().toFoldState(),
                runs = readVisualRuns(reader, layout),
            )
        }
    }

    private fun readVisualLines(
        reader: BinaryReader,
        layout: RenderModelLayout,
    ): List<VisualLine> {
        val minBytesPerItem = if (layout == RenderModelLayout.Current) 24 else 20
        val count = readCount(reader, minBytesPerItem = minBytesPerItem, label = "visual lines")
        return buildList(count) {
            repeat(count) {
                add(readVisualLine(reader, layout))
            }
        }
    }

    private fun readGutterIconRenderItem(reader: BinaryReader): GutterIconRenderItem = GutterIconRenderItem(
        logicalLine = reader.readInt(),
        iconId = reader.readInt(),
        origin = readPoint(reader),
        width = reader.readFloat(),
        height = reader.readFloat(),
    )

    private fun readGutterIconRenderItems(reader: BinaryReader): List<GutterIconRenderItem> {
        val count = readCount(reader, minBytesPerItem = 24, label = "gutter icon render items")
        return buildList(count) {
            repeat(count) {
                add(readGutterIconRenderItem(reader))
            }
        }
    }

    private fun readFoldMarkerRenderItem(reader: BinaryReader): FoldMarkerRenderItem = FoldMarkerRenderItem(
        logicalLine = reader.readInt(),
        foldState = reader.readInt().toFoldState(),
        origin = readPoint(reader),
        width = reader.readFloat(),
        height = reader.readFloat(),
    )

    private fun readFoldMarkerRenderItems(reader: BinaryReader): List<FoldMarkerRenderItem> {
        val count = readCount(reader, minBytesPerItem = 24, label = "fold marker render items")
        return buildList(count) {
            repeat(count) {
                add(readFoldMarkerRenderItem(reader))
            }
        }
    }

    private fun readCursor(reader: BinaryReader): Cursor = Cursor(
        textPosition = readTextPosition(reader),
        position = readPoint(reader),
        height = reader.readFloat(),
        visible = reader.readBooleanAsInt(),
        showDragger = reader.readBooleanAsInt(),
    )

    private fun readSelectionRect(reader: BinaryReader): SelectionRect = SelectionRect(
        origin = readPoint(reader),
        width = reader.readFloat(),
        height = reader.readFloat(),
    )

    private fun readSelectionRects(reader: BinaryReader): List<SelectionRect> {
        val count = readCount(reader, minBytesPerItem = 16, label = "selection rects")
        return buildList(count) {
            repeat(count) {
                add(readSelectionRect(reader))
            }
        }
    }

    private fun readSelectionHandle(reader: BinaryReader): SelectionHandle = SelectionHandle(
        position = readPoint(reader),
        height = reader.readFloat(),
        visible = reader.readBooleanAsInt(),
    )

    private fun readCompositionDecoration(reader: BinaryReader): CompositionDecoration = CompositionDecoration(
        active = reader.readBooleanAsInt(),
        origin = readPoint(reader),
        width = reader.readFloat(),
        height = reader.readFloat(),
    )

    private fun readGuideSegment(reader: BinaryReader): GuideSegment = GuideSegment(
        direction = reader.readInt().toGuideDirection(),
        type = reader.readInt().toGuideType(),
        style = reader.readInt().toGuideStyle(),
        start = readPoint(reader),
        end = readPoint(reader),
        arrowEnd = reader.readBooleanAsInt(),
    )

    private fun readGuideSegments(reader: BinaryReader): List<GuideSegment> {
        val count = readCount(reader, minBytesPerItem = 28, label = "guide segments")
        return buildList(count) {
            repeat(count) {
                add(readGuideSegment(reader))
            }
        }
    }

    private fun readDiagnosticDecoration(
        reader: BinaryReader,
        layout: RenderModelLayout,
    ): DiagnosticDecoration = DiagnosticDecoration(
        origin = readPoint(reader),
        width = reader.readFloat(),
        height = reader.readFloat(),
        severity = reader.readInt(),
        color = if (layout == RenderModelLayout.Legacy) reader.readInt() else 0,
    )

    private fun readDiagnosticDecorations(
        reader: BinaryReader,
        layout: RenderModelLayout,
    ): List<DiagnosticDecoration> {
        val minBytesPerItem = if (layout == RenderModelLayout.Current) 16 else 20
        val count = readCount(reader, minBytesPerItem = minBytesPerItem, label = "diagnostic decorations")
        return buildList(count) {
            repeat(count) {
                add(readDiagnosticDecoration(reader, layout))
            }
        }
    }

    private fun readLinkedEditingRect(reader: BinaryReader): LinkedEditingRect = LinkedEditingRect(
        origin = readPoint(reader),
        width = reader.readFloat(),
        height = reader.readFloat(),
        isActive = reader.readBooleanAsInt(),
    )

    private fun readLinkedEditingRects(reader: BinaryReader): List<LinkedEditingRect> {
        val count = readCount(reader, minBytesPerItem = 20, label = "linked editing rects")
        return buildList(count) {
            repeat(count) {
                add(readLinkedEditingRect(reader))
            }
        }
    }

    private fun readBracketHighlightRect(reader: BinaryReader): BracketHighlightRect = BracketHighlightRect(
        origin = readPoint(reader),
        width = reader.readFloat(),
        height = reader.readFloat(),
    )

    private fun readBracketHighlightRects(reader: BinaryReader): List<BracketHighlightRect> {
        val count = readCount(reader, minBytesPerItem = 16, label = "bracket highlight rects")
        return buildList(count) {
            repeat(count) {
                add(readBracketHighlightRect(reader))
            }
        }
    }

    private fun readScrollbarRect(reader: BinaryReader): ScrollbarRect = ScrollbarRect(
        origin = readPoint(reader),
        width = reader.readFloat(),
        height = reader.readFloat(),
    )

    private fun readScrollbarModel(
        reader: BinaryReader,
        layout: RenderModelLayout,
    ): ScrollbarModel = ScrollbarModel(
        visible = reader.readBooleanAsInt(),
        alpha = reader.readFloat(),
        thumbActive = if (layout == RenderModelLayout.Legacy) reader.readBooleanAsInt() else false,
        track = readScrollbarRect(reader),
        thumb = readScrollbarRect(reader),
    )

    private fun decodeRenderModel(
        data: ByteArray,
        layout: RenderModelLayout,
    ): EditorRenderModel? = try {
        val reader = BinaryReader(data)
        val splitX = reader.readFloat()
        val splitLineVisible = reader.readBooleanAsInt()
        val scrollX = reader.readFloat()
        val scrollY = reader.readFloat()
        val viewportWidth = reader.readFloat()
        val viewportHeight = reader.readFloat()
        val currentLine = readPoint(reader)
        val currentLineRenderMode = reader.readInt().toCurrentLineRenderMode()
        val lines = readVisualLines(reader, layout)
        val gutterIcons = readGutterIconRenderItems(reader)
        val foldMarkers = readFoldMarkerRenderItems(reader)
        val cursor = readCursor(reader)
        val selectionRects = readSelectionRects(reader)
        val selectionStartHandle = readSelectionHandle(reader)
        val selectionEndHandle = readSelectionHandle(reader)
        val compositionDecoration = readCompositionDecoration(reader)
        val guideSegments = readGuideSegments(reader)
        val diagnosticDecorations = readDiagnosticDecorations(reader, layout)
        val maxGutterIcons = reader.readInt()
        val linkedEditingRects = readLinkedEditingRects(reader)
        val bracketHighlightRects = readBracketHighlightRects(reader)

        var verticalScrollbar = ScrollbarModel()
        var horizontalScrollbar = ScrollbarModel()
        val scrollbarSize = if (layout == RenderModelLayout.Current) 40 else 44
        if (reader.canRead(scrollbarSize)) {
            verticalScrollbar = readScrollbarModel(reader, layout)
        }
        if (reader.canRead(scrollbarSize)) {
            horizontalScrollbar = readScrollbarModel(reader, layout)
        }

        val gutterSticky = if (reader.canRead(4)) reader.readBooleanAsInt() else true
        val gutterVisible = if (reader.canRead(4)) reader.readBooleanAsInt() else true
        val pointerCursorType = if (layout == RenderModelLayout.Current && reader.canRead(4)) {
            reader.readInt().toPointerCursorType()
        } else {
            PointerCursorType.Default
        }

        EditorRenderModel(
            splitX = splitX,
            splitLineVisible = splitLineVisible,
            scrollX = scrollX,
            scrollY = scrollY,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            currentLine = currentLine,
            currentLineRenderMode = currentLineRenderMode,
            lines = lines,
            cursor = cursor,
            selectionRects = selectionRects,
            selectionStartHandle = selectionStartHandle,
            selectionEndHandle = selectionEndHandle,
            compositionDecoration = compositionDecoration,
            guideSegments = guideSegments,
            diagnosticDecorations = diagnosticDecorations,
            maxGutterIcons = maxGutterIcons,
            linkedEditingRects = linkedEditingRects,
            bracketHighlightRects = bracketHighlightRects,
            gutterIcons = gutterIcons,
            foldMarkers = foldMarkers,
            verticalScrollbar = verticalScrollbar,
            horizontalScrollbar = horizontalScrollbar,
            gutterSticky = gutterSticky,
            gutterVisible = gutterVisible,
            pointerCursorType = pointerCursorType,
        )
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun readCount(
        reader: BinaryReader,
        minBytesPerItem: Int,
        label: String,
    ): Int {
        val count = reader.readInt()
        require(count >= 0) {
            "Negative $label count: $count"
        }
        if (count == 0) {
            return 0
        }
        require(minBytesPerItem <= 0 || count <= reader.remaining / minBytesPerItem) {
            "Invalid $label count: $count remaining=${reader.remaining}"
        }
        return count
    }

    private enum class RenderModelLayout {
        Current,
        Legacy,
    }
}
