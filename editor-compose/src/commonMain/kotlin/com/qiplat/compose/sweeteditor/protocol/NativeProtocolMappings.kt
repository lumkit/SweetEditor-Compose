package com.qiplat.compose.sweeteditor.protocol

import com.qiplat.compose.sweeteditor.model.decoration.DiagnosticSeverity
import com.qiplat.compose.sweeteditor.model.decoration.InlayType
import com.qiplat.compose.sweeteditor.model.decoration.SeparatorStyle
import com.qiplat.compose.sweeteditor.model.decoration.SpanLayer
import com.qiplat.compose.sweeteditor.model.foundation.*
import com.qiplat.compose.sweeteditor.model.visual.*

internal fun ScrollBehavior.toNativeValue(): Int = when (this) {
    ScrollBehavior.GoToTop -> 0
    ScrollBehavior.GoToCenter -> 1
    ScrollBehavior.GoToBottom -> 2
}

internal fun AutoIndentMode.toNativeValue(): Int = when (this) {
    AutoIndentMode.None -> 0
    AutoIndentMode.KeepIndent -> 1
}

internal fun WrapMode.toNativeValue(): Int = when (this) {
    WrapMode.None -> 0
    WrapMode.CharBreak -> 1
    WrapMode.WordBreak -> 2
}

internal fun CurrentLineRenderMode.toNativeValue(): Int = when (this) {
    CurrentLineRenderMode.Background -> 0
    CurrentLineRenderMode.Border -> 1
    CurrentLineRenderMode.None -> 2
}

internal fun FoldArrowMode.toNativeValue(): Int = when (this) {
    FoldArrowMode.Auto -> 0
    FoldArrowMode.Always -> 1
    FoldArrowMode.Hidden -> 2
}

internal fun SpanLayer.toNativeValue(): Int = when (this) {
    SpanLayer.Syntax -> 0
    SpanLayer.Semantic -> 1
}

internal fun InlayType.toNativeValue(): Int = when (this) {
    InlayType.Text -> 0
    InlayType.Icon -> 1
    InlayType.Color -> 2
}

internal fun DiagnosticSeverity.toNativeValue(): Int = when (this) {
    DiagnosticSeverity.Error -> 0
    DiagnosticSeverity.Warning -> 1
    DiagnosticSeverity.Info -> 2
    DiagnosticSeverity.Hint -> 3
}

internal fun SeparatorStyle.toNativeValue(): Int = when (this) {
    SeparatorStyle.Single -> 0
    SeparatorStyle.Double -> 1
}

internal fun Int.toGestureType(): GestureType = when (this) {
    1 -> GestureType.Tap
    2 -> GestureType.DoubleTap
    3 -> GestureType.LongPress
    4 -> GestureType.Scale
    5 -> GestureType.Scroll
    6 -> GestureType.FastScroll
    7 -> GestureType.DragSelect
    8 -> GestureType.ContextMenu
    else -> GestureType.Undefined
}

internal fun GestureType.toNativeValue(): Int = when (this) {
    GestureType.Undefined -> 0
    GestureType.Tap -> 1
    GestureType.DoubleTap -> 2
    GestureType.LongPress -> 3
    GestureType.Scale -> 4
    GestureType.Scroll -> 5
    GestureType.FastScroll -> 6
    GestureType.DragSelect -> 7
    GestureType.ContextMenu -> 8
}

internal fun Int.toHitTargetType(): HitTargetType = when (this) {
    1 -> HitTargetType.InlayHintText
    2 -> HitTargetType.InlayHintIcon
    3 -> HitTargetType.GutterIcon
    4 -> HitTargetType.FoldPlaceholder
    5 -> HitTargetType.FoldGutter
    6 -> HitTargetType.InlayHintColor
    else -> HitTargetType.None
}

internal fun Int.toCurrentLineRenderMode(): CurrentLineRenderMode = when (this) {
    1 -> CurrentLineRenderMode.Border
    2 -> CurrentLineRenderMode.None
    else -> CurrentLineRenderMode.Background
}

internal fun Int.toVisualRunType(): VisualRunType = when (this) {
    1 -> VisualRunType.Whitespace
    2 -> VisualRunType.Newline
    3 -> VisualRunType.InlayHint
    4 -> VisualRunType.PhantomText
    5 -> VisualRunType.FoldPlaceholder
    6 -> VisualRunType.Tab
    else -> VisualRunType.Text
}

internal fun Int.toFoldState(): FoldState = when (this) {
    1 -> FoldState.Expanded
    2 -> FoldState.Collapsed
    else -> FoldState.None
}

internal fun Int.toGuideDirection(): GuideDirection = when (this) {
    0 -> GuideDirection.Horizontal
    else -> GuideDirection.Vertical
}

internal fun Int.toGuideType(): GuideType = when (this) {
    1 -> GuideType.Bracket
    2 -> GuideType.Flow
    3 -> GuideType.Separator
    else -> GuideType.Indent
}

internal fun Int.toGuideStyle(): GuideStyle = when (this) {
    1 -> GuideStyle.Dashed
    2 -> GuideStyle.Double
    else -> GuideStyle.Solid
}
