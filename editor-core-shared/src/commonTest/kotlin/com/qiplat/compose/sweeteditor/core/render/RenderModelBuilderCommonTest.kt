package com.qiplat.compose.sweeteditor.core.render

import com.qiplat.compose.sweeteditor.core.EditorCoreCursorRect
import com.qiplat.compose.sweeteditor.core.EditorCoreRect
import com.qiplat.compose.sweeteditor.core.EditorCoreRenderLine
import com.qiplat.compose.sweeteditor.core.EditorCoreRenderRun
import com.qiplat.compose.sweeteditor.core.layout.LayoutSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class RenderModelBuilderCommonTest {
    @Test
    fun buildMapsLayoutSnapshotIntoRenderModel() {
        val builder = RenderModelBuilder()

        val renderModel = builder.build(
            RenderModelBuildInput(
                layoutSnapshot = LayoutSnapshot(
                    documentVersion = 7L,
                    lines = listOf(
                        EditorCoreRenderLine(
                            logicalLine = 1,
                            wrapIndex = 0,
                            top = 12f,
                            height = 10f,
                            text = "beta",
                            runs = listOf(
                                EditorCoreRenderRun(
                                    text = "beta",
                                    x = 0f,
                                    width = 4f,
                                ),
                            ),
                        ),
                    ),
                    lineHeight = 10f,
                    contentWidth = 40f,
                    contentHeight = 120f,
                ),
                cursorRect = EditorCoreCursorRect(
                    x = 3f,
                    y = 12f,
                    height = 10f,
                ),
                selectionRects = listOf(
                    EditorCoreRect(
                        x = 1f,
                        y = 12f,
                        width = 2f,
                        height = 10f,
                    ),
                ),
            ),
        )

        assertEquals(7L, renderModel.documentVersion)
        assertEquals(1, renderModel.lines.size)
        assertEquals("beta", renderModel.lines.first().text)
        assertEquals(40f, renderModel.contentWidth)
        assertEquals(120f, renderModel.contentHeight)
        assertEquals(10f, renderModel.lineHeight)
        assertEquals(1, renderModel.selectionRects.size)
        assertEquals(3f, renderModel.cursorRect?.x)
    }
}
