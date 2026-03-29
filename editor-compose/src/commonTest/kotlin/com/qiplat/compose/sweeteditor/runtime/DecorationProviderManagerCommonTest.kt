package com.qiplat.compose.sweeteditor.runtime

import com.qiplat.compose.sweeteditor.DecorationApplyMode
import com.qiplat.compose.sweeteditor.DecorationSet
import com.qiplat.compose.sweeteditor.DecorationUpdate
import com.qiplat.compose.sweeteditor.model.decoration.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecorationProviderManagerCommonTest {
    @Test
    fun mergeAndReplaceRangeProduceExpectedAggregate() {
        val manager = DecorationProviderManager()

        val firstGeneration = manager.beginGeneration("syntax")
        manager.commit(
            providerId = "syntax",
            generation = firstGeneration,
            update = DecorationUpdate(
                decorations = DecorationSet(
                    syntaxSpans = mapOf(
                        1 to listOf(StyleSpan(column = 0, length = 3, styleId = 1)),
                        2 to listOf(StyleSpan(column = 1, length = 2, styleId = 2)),
                    ),
                ),
                applyMode = DecorationApplyMode.ReplaceAll,
            ),
        )

        val secondGeneration = manager.beginGeneration("diagnostics")
        val batch = manager.commit(
            providerId = "diagnostics",
            generation = secondGeneration,
            update = DecorationUpdate(
                decorations = DecorationSet(
                    diagnostics = mapOf(
                        2 to listOf(DiagnosticItem(column = 0, length = 4, severity = DiagnosticSeverity.Warning)),
                    ),
                ),
                applyMode = DecorationApplyMode.ReplaceRange,
                lineRange = 2..3,
            ),
        )

        assertEquals(2, batch?.spansByLayer?.get(SpanLayer.Syntax)?.size)
        assertEquals(1, batch?.diagnostics?.size)
        assertEquals(1, batch?.diagnostics?.get(2)?.size)
    }

    @Test
    fun replaceRangeUpdatesFoldRegionsWithinRange() {
        val manager = DecorationProviderManager()

        val baseGeneration = manager.beginGeneration("folds")
        manager.commit(
            providerId = "folds",
            generation = baseGeneration,
            update = DecorationUpdate(
                decorations = DecorationSet(
                    foldRegions = listOf(
                        FoldRegion(startLine = 1, endLine = 3),
                        FoldRegion(startLine = 10, endLine = 12),
                    ),
                ),
                applyMode = DecorationApplyMode.ReplaceAll,
            ),
        )

        val nextGeneration = manager.beginGeneration("folds")
        val batch = manager.commit(
            providerId = "folds",
            generation = nextGeneration,
            update = DecorationUpdate(
                decorations = DecorationSet(
                    foldRegions = listOf(FoldRegion(startLine = 10, endLine = 14, collapsed = true)),
                ),
                applyMode = DecorationApplyMode.ReplaceRange,
                lineRange = 10..14,
            ),
        )

        requireNotNull(batch)
        assertEquals(2, batch.foldRegions.size)
        assertTrue(batch.foldRegions.any { it.startLine == 1 && it.endLine == 3 })
        assertTrue(batch.foldRegions.any { it.startLine == 10 && it.collapsed })
    }
}
