package com.qiplat.compose.sweeteditor.model.visual

import com.qiplat.compose.sweeteditor.model.decoration.TextStyle
import com.qiplat.compose.sweeteditor.model.foundation.CurrentLineRenderMode
import com.qiplat.compose.sweeteditor.model.foundation.TextPosition

data class PointF(
    val x: Float = 0f,
    val y: Float = 0f,
)

enum class VisualRunType {
    Text,
    Whitespace,
    Newline,
    InlayHint,
    PhantomText,
    FoldPlaceholder,
    Tab,
}

data class VisualRun(
    val type: VisualRunType = VisualRunType.Text,
    val x: Float = 0f,
    val y: Float = 0f,
    val text: String = "",
    val style: TextStyle = TextStyle(),
    val iconId: Int = 0,
    val colorValue: Int = 0,
    val width: Float = 0f,
    val padding: Float = 0f,
    val margin: Float = 0f,
)

enum class FoldState {
    None,
    Expanded,
    Collapsed,
}

data class VisualLine(
    val logicalLine: Int = 0,
    val wrapIndex: Int = 0,
    val lineNumberPosition: PointF = PointF(),
    val runs: List<VisualRun> = emptyList(),
    val isPhantomLine: Boolean = false,
    val foldState: FoldState = FoldState.None,
)

data class Cursor(
    val textPosition: TextPosition = TextPosition.Zero,
    val position: PointF = PointF(),
    val height: Float = 0f,
    val visible: Boolean = true,
    @Deprecated("showDragger status is deprecated, please change it to renderModel.selectionRects.")
    val showDragger: Boolean = false,
)

data class CursorRect(
    val x: Float = 0f,
    val y: Float = 0f,
    val height: Float = 0f,
)

data class SelectionRect(
    val origin: PointF = PointF(),
    val width: Float = 0f,
    val height: Float = 0f,
)

data class SelectionHandle(
    val position: PointF = PointF(),
    val height: Float = 0f,
    val visible: Boolean = false,
)

enum class GuideDirection {
    Horizontal,
    Vertical,
}

enum class GuideType {
    Indent,
    Bracket,
    Flow,
    Separator,
}

enum class GuideStyle {
    Solid,
    Dashed,
    Double,
}

data class GuideSegment(
    val direction: GuideDirection = GuideDirection.Vertical,
    val type: GuideType = GuideType.Indent,
    val style: GuideStyle = GuideStyle.Solid,
    val start: PointF = PointF(),
    val end: PointF = PointF(),
    val arrowEnd: Boolean = false,
)

data class CompositionDecoration(
    val active: Boolean = false,
    val origin: PointF = PointF(),
    val width: Float = 0f,
    val height: Float = 0f,
)

data class DiagnosticDecoration(
    val origin: PointF = PointF(),
    val width: Float = 0f,
    val height: Float = 0f,
    val severity: Int = 0,
    val color: Int = 0,
)

data class BracketHighlightRect(
    val origin: PointF = PointF(),
    val width: Float = 0f,
    val height: Float = 0f,
)

data class GutterIconRenderItem(
    val logicalLine: Int = 0,
    val iconId: Int = 0,
    val origin: PointF = PointF(),
    val width: Float = 0f,
    val height: Float = 0f,
)

data class FoldMarkerRenderItem(
    val logicalLine: Int = 0,
    val foldState: FoldState = FoldState.None,
    val origin: PointF = PointF(),
    val width: Float = 0f,
    val height: Float = 0f,
)

data class LinkedEditingRect(
    val origin: PointF = PointF(),
    val width: Float = 0f,
    val height: Float = 0f,
    val isActive: Boolean = false,
)

data class ScrollbarRect(
    val origin: PointF = PointF(),
    val width: Float = 0f,
    val height: Float = 0f,
)

data class ScrollbarModel(
    val visible: Boolean = false,
    val alpha: Float = 0f,
    val thumbActive: Boolean = false,
    val track: ScrollbarRect = ScrollbarRect(),
    val thumb: ScrollbarRect = ScrollbarRect(),
)

data class ScrollMetrics(
    val scale: Float = 1f,
    val scrollX: Float = 0f,
    val scrollY: Float = 0f,
    val maxScrollX: Float = 0f,
    val maxScrollY: Float = 0f,
    val contentWidth: Float = 0f,
    val contentHeight: Float = 0f,
    val viewportWidth: Float = 0f,
    val viewportHeight: Float = 0f,
    val textAreaX: Float = 0f,
    val textAreaWidth: Float = 0f,
    val canScrollX: Boolean = false,
    val canScrollY: Boolean = false,
)

data class EditorRenderModel(
    val splitX: Float = 0f,
    val splitLineVisible: Boolean = true,
    val scrollX: Float = 0f,
    val scrollY: Float = 0f,
    val viewportWidth: Float = 0f,
    val viewportHeight: Float = 0f,
    val currentLine: PointF = PointF(),
    val currentLineRenderMode: CurrentLineRenderMode = CurrentLineRenderMode.Background,
    val lines: List<VisualLine> = emptyList(),
    val cursor: Cursor = Cursor(),
    val selectionRects: List<SelectionRect> = emptyList(),
    val selectionStartHandle: SelectionHandle = SelectionHandle(),
    val selectionEndHandle: SelectionHandle = SelectionHandle(),
    val compositionDecoration: CompositionDecoration = CompositionDecoration(),
    val guideSegments: List<GuideSegment> = emptyList(),
    val diagnosticDecorations: List<DiagnosticDecoration> = emptyList(),
    val maxGutterIcons: Int = 0,
    val linkedEditingRects: List<LinkedEditingRect> = emptyList(),
    val bracketHighlightRects: List<BracketHighlightRect> = emptyList(),
    val gutterIcons: List<GutterIconRenderItem> = emptyList(),
    val foldMarkers: List<FoldMarkerRenderItem> = emptyList(),
    val verticalScrollbar: ScrollbarModel = ScrollbarModel(),
    val horizontalScrollbar: ScrollbarModel = ScrollbarModel(),
    val gutterSticky: Boolean = true,
    val gutterVisible: Boolean = true,
)
