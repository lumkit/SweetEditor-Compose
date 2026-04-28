package com.qiplat.compose.sweeteditor

import androidx.compose.ui.graphics.Color
import com.qiplat.compose.sweeteditor.model.visual.FoldMarkerRenderItem
import com.qiplat.compose.sweeteditor.model.visual.FoldState
import com.qiplat.compose.sweeteditor.model.visual.GutterIconRenderItem
import com.qiplat.compose.sweeteditor.model.visual.VisualRunType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import com.qiplat.compose.sweeteditor.model.decoration.SpanStyle as EditorTextStyle

class SweetEditorDrawCacheCommonTest {
    @Test
    fun lineNumberStyleKeyChangesWhenBaselineShiftChanges() {
        val firstShift = computeLineNumberBaselineShift(
            baselineY = 10f,
            estimatedLineHeight = 20f,
        )
        val repeatedShift = computeLineNumberBaselineShift(
            baselineY = 30f,
            estimatedLineHeight = 20f,
        )
        val shiftedBaseline = computeLineNumberBaselineShift(
            baselineY = 15f,
            estimatedLineHeight = 20f,
        )

        val firstKey = computeLineNumberTextStyleKey(active = false, baselineShift = firstShift)
        val repeatedKey = computeLineNumberTextStyleKey(active = false, baselineShift = repeatedShift)
        val shiftedKey = computeLineNumberTextStyleKey(active = false, baselineShift = shiftedBaseline)

        assertEquals(firstShift, repeatedShift)
        assertEquals(firstKey, repeatedKey)
        assertNotEquals(firstShift, shiftedBaseline)
        assertNotEquals(firstKey, shiftedKey)
    }

    @Test
    fun gutterIconsAreGroupedByLogicalLineInInsertionOrder() {
        val grouped = buildGutterIconsByLine(
            listOf(
                GutterIconRenderItem(logicalLine = 4, iconId = 10),
                GutterIconRenderItem(logicalLine = 2, iconId = 20),
                GutterIconRenderItem(logicalLine = 4, iconId = 30),
            ),
        )

        assertEquals(listOf(4, 2), grouped.keys.toList())
        assertEquals(listOf(10, 30), grouped.getValue(4).map { it.iconId })
        assertEquals(listOf(20), grouped.getValue(2).map { it.iconId })
    }

    @Test
    fun foldMarkerIndexKeepsLatestMarkerPerLogicalLine() {
        val markers = buildFoldMarkerByLine(
            listOf(
                FoldMarkerRenderItem(logicalLine = 1, foldState = FoldState.Expanded),
                FoldMarkerRenderItem(logicalLine = 3, foldState = FoldState.Collapsed),
                FoldMarkerRenderItem(logicalLine = 1, foldState = FoldState.Collapsed),
            ),
        )

        assertEquals(listOf(1, 3), markers.keys.toList())
        assertEquals(FoldState.Collapsed, markers.getValue(1).foldState)
        assertEquals(FoldState.Collapsed, markers.getValue(3).foldState)
    }

    @Test
    fun runTextLayoutCacheIdentityDependsOnTextAndStyleKey() {
        val style = EditorTextStyle(color = 0x112233)
        val same = computeRunTextLayoutCacheIdentity("abc", style, VisualRunType.Text)
        val differentType = computeRunTextLayoutCacheIdentity("abc", style, VisualRunType.InlayHint)
        val differentText = computeRunTextLayoutCacheIdentity("abcd", style, VisualRunType.Text)

        assertEquals(same, computeRunTextLayoutCacheIdentity("abc", style, VisualRunType.Text))
        assertNotEquals(same, differentType)
        assertNotEquals(same, differentText)
    }

    @Test
    fun lineNumberLayoutCacheIdentityDependsOnBaselineBucket() {
        val first = computeLineNumberTextLayoutCacheIdentity(
            text = "10",
            active = false,
            baselineY = 10f,
            estimatedLineHeight = 20f,
        )
        val repeated = computeLineNumberTextLayoutCacheIdentity(
            text = "10",
            active = false,
            baselineY = 30f,
            estimatedLineHeight = 20f,
        )
        val shifted = computeLineNumberTextLayoutCacheIdentity(
            text = "10",
            active = false,
            baselineY = 15f,
            estimatedLineHeight = 20f,
        )

        assertEquals(first, repeated)
        assertNotEquals(first, shifted)
    }

    @Test
    fun reusableTextLayoutCacheIsDisabledOnAndroidOnly() {
        assertEquals(false, supportsReusableTextLayoutCache(PlatformType.Android))
        assertEquals(true, supportsReusableTextLayoutCache(PlatformType.IOS))
        assertEquals(true, supportsReusableTextLayoutCache(PlatformType.Desktop))
        assertEquals(true, supportsReusableTextLayoutCache(PlatformType.Web))
    }

    @Test
    fun resolveEditorColorsUsesCurrentLineHelpers() {
        val theme = SweetEditorDefaults.theme().darkTheme.let { base ->
            base.copy(
                colors = base.colors.copy(
                    currentLine = Color(0x11010203),
                    currentLineNumber = Color(0xFFAABBCC),
                ),
            )
        }

        val colors = resolveEditorColors(theme)

        assertTrue(colors.currentLineBorderColor.alpha >= 0.63f)
        assertEquals(1f, colors.currentLineAccentColor.alpha)
        assertEquals(theme.colors.currentLineNumber, colors.currentLineAccentColor)
    }
}

