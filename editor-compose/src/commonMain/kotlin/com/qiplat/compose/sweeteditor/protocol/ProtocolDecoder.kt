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
        val reader = BinaryReader(data)
        val splitX = reader.readFloat()
        val splitLineVisible = reader.readBooleanAsInt()
        val scrollX = reader.readFloat()
        val scrollY = reader.readFloat()
        val viewportWidth = reader.readFloat()
        val viewportHeight = reader.readFloat()
        val currentLine = readPoint(reader)
        val currentLineRenderMode = reader.readInt().toCurrentLineRenderMode()
        val lines = readVisualLines(reader)
        val gutterIcons = readGutterIconRenderItems(reader)
        val foldMarkers = readFoldMarkerRenderItems(reader)
        val cursor = readCursor(reader)
        val selectionRects = readSelectionRects(reader)
        val selectionStartHandle = readSelectionHandle(reader)
        val selectionEndHandle = readSelectionHandle(reader)
        val compositionDecoration = readCompositionDecoration(reader)
        val guideSegments = readGuideSegments(reader)
        val diagnosticDecorations = readDiagnosticDecorations(reader)
        val maxGutterIcons = reader.readInt()
        val linkedEditingRects = readLinkedEditingRects(reader)
        val bracketHighlightRects = readBracketHighlightRects(reader)

        var verticalScrollbar = ScrollbarModel()
        var horizontalScrollbar = ScrollbarModel()
        if (reader.canRead(44)) {
            verticalScrollbar = readScrollbarModel(reader)
        }
        if (reader.canRead(44)) {
            horizontalScrollbar = readScrollbarModel(reader)
        }

        val gutterSticky = if (reader.canRead(4)) reader.readBooleanAsInt() else true
        val gutterVisible = if (reader.canRead(4)) reader.readBooleanAsInt() else true

        return EditorRenderModel(
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
        )
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

    private fun readVisualRun(reader: BinaryReader): VisualRun = VisualRun(
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
    )

    private fun readVisualRuns(reader: BinaryReader): List<VisualRun> {
        val count = reader.readInt()
        return buildList(count.coerceAtLeast(0)) {
            repeat(count) {
                add(readVisualRun(reader))
            }
        }
    }

    private fun readVisualLine(reader: BinaryReader): VisualLine = VisualLine(
        logicalLine = reader.readInt(),
        wrapIndex = reader.readInt(),
        lineNumberPosition = readPoint(reader),
        isPhantomLine = reader.readBooleanAsInt(),
        foldState = reader.readInt().toFoldState(),
        runs = readVisualRuns(reader),
    )

    private fun readVisualLines(reader: BinaryReader): List<VisualLine> {
        val count = reader.readInt()
        return buildList(count.coerceAtLeast(0)) {
            repeat(count) {
                add(readVisualLine(reader))
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
        val count = reader.readInt()
        return buildList(count.coerceAtLeast(0)) {
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
        val count = reader.readInt()
        return buildList(count.coerceAtLeast(0)) {
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
        val count = reader.readInt()
        return buildList(count.coerceAtLeast(0)) {
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
        val count = reader.readInt()
        return buildList(count.coerceAtLeast(0)) {
            repeat(count) {
                add(readGuideSegment(reader))
            }
        }
    }

    private fun readDiagnosticDecoration(reader: BinaryReader): DiagnosticDecoration = DiagnosticDecoration(
        origin = readPoint(reader),
        width = reader.readFloat(),
        height = reader.readFloat(),
        severity = reader.readInt(),
        color = reader.readInt(),
    )

    private fun readDiagnosticDecorations(reader: BinaryReader): List<DiagnosticDecoration> {
        val count = reader.readInt()
        return buildList(count.coerceAtLeast(0)) {
            repeat(count) {
                add(readDiagnosticDecoration(reader))
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
        val count = reader.readInt()
        return buildList(count.coerceAtLeast(0)) {
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
        val count = reader.readInt()
        return buildList(count.coerceAtLeast(0)) {
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

    private fun readScrollbarModel(reader: BinaryReader): ScrollbarModel = ScrollbarModel(
        visible = reader.readBooleanAsInt(),
        alpha = reader.readFloat(),
        thumbActive = reader.readBooleanAsInt(),
        track = readScrollbarRect(reader),
        thumb = readScrollbarRect(reader),
    )
}
