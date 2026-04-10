package com.qiplat.compose.sweeteditor.core.render

import com.qiplat.compose.sweeteditor.core.EditorCoreCursorRect
import com.qiplat.compose.sweeteditor.core.EditorCoreRect
import com.qiplat.compose.sweeteditor.core.EditorCoreRenderModel
import com.qiplat.compose.sweeteditor.core.layout.LayoutSnapshot

data class RenderModelBuildInput(
    val layoutSnapshot: LayoutSnapshot,
    val cursorRect: EditorCoreCursorRect? = null,
    val selectionRects: List<EditorCoreRect> = emptyList(),
)

class RenderModelBuilder {
    fun build(input: RenderModelBuildInput): EditorCoreRenderModel =
        EditorCoreRenderModel(
            documentVersion = input.layoutSnapshot.documentVersion,
            lines = input.layoutSnapshot.lines,
            cursorRect = input.cursorRect,
            selectionRects = input.selectionRects,
            contentWidth = input.layoutSnapshot.contentWidth,
            contentHeight = input.layoutSnapshot.contentHeight,
            lineHeight = input.layoutSnapshot.lineHeight,
        )
}
