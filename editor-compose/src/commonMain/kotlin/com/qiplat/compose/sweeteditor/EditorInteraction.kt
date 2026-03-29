package com.qiplat.compose.sweeteditor

import com.qiplat.compose.sweeteditor.model.foundation.GestureResult
import com.qiplat.compose.sweeteditor.model.foundation.HitTarget
import com.qiplat.compose.sweeteditor.model.visual.SelectionHandle

data class EditorContextMenuRequest(
    val gestureResult: GestureResult,
    val hitTarget: HitTarget = gestureResult.hitTarget,
)

data class EditorSelectionHandleDragState(
    val active: Boolean,
    val gestureResult: GestureResult,
    val startHandle: SelectionHandle = SelectionHandle(),
    val endHandle: SelectionHandle = SelectionHandle(),
)
