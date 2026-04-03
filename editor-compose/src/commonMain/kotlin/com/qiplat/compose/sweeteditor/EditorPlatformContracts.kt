package com.qiplat.compose.sweeteditor

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.qiplat.compose.sweeteditor.model.visual.PointF

data class EditorMetadata(
    val entries: Map<String, String> = emptyMap(),
)

interface EditorIconProvider {
    fun paint(
        drawScope: DrawScope,
        iconId: Int,
        origin: PointF,
        size: Size,
        tint: Color,
    ): Boolean
}
