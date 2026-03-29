package com.qiplat.compose.sweeteditor.model.foundation

data class GestureResult(
    val type: GestureType = GestureType.Undefined,
    val tapPoint: GesturePoint = GesturePoint(),
    val cursorPosition: TextPosition = TextPosition.Zero,
    val hasSelection: Boolean = false,
    val selection: TextRange = TextRange(),
    val viewScrollX: Float = 0f,
    val viewScrollY: Float = 0f,
    val viewScale: Float = 1f,
    val hitTarget: HitTarget = HitTarget.None,
    val needsEdgeScroll: Boolean = false,
    val needsFling: Boolean = false,
    val needsAnimation: Boolean = false,
    val isHandleDrag: Boolean = false,
)

data class GesturePoint(
    val x: Float = 0f,
    val y: Float = 0f,
)

enum class GestureType {
    Undefined,
    Tap,
    DoubleTap,
    LongPress,
    Scale,
    Scroll,
    FastScroll,
    DragSelect,
    ContextMenu,
}

data class HitTarget(
    val type: HitTargetType,
    val line: Int = 0,
    val column: Int = 0,
    val iconId: Int = 0,
    val colorValue: Int = 0,
) {
    companion object {
        val None = HitTarget(type = HitTargetType.None)
    }
}

enum class HitTargetType {
    None,
    InlayHintText,
    InlayHintIcon,
    GutterIcon,
    FoldPlaceholder,
    FoldGutter,
    InlayHintColor,
}
