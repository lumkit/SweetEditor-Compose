package com.qiplat.compose.sweeteditor

import com.qiplat.compose.sweeteditor.model.foundation.AutoIndentMode
import com.qiplat.compose.sweeteditor.model.foundation.CurrentLineRenderMode
import com.qiplat.compose.sweeteditor.model.foundation.FoldArrowMode
import com.qiplat.compose.sweeteditor.model.foundation.WrapMode

data class EditorSettings(
    val wrapMode: WrapMode = WrapMode.None,
    val tabSize: Int = 4,
    val lineSpacingExtra: Float = 0f,
    val lineSpacingMultiplier: Float = 1f,
    val foldArrowMode: FoldArrowMode = FoldArrowMode.Auto,
    val gutterSticky: Boolean = true,
    val gutterVisible: Boolean = true,
    val currentLineRenderMode: CurrentLineRenderMode = CurrentLineRenderMode.Background,
    val readOnly: Boolean = false,
    val compositionEnabled: Boolean = true,
    val autoIndentMode: AutoIndentMode = AutoIndentMode.None,
)
