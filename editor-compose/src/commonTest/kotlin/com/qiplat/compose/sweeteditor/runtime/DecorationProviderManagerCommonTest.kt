package com.qiplat.compose.sweeteditor.runtime

import com.qiplat.compose.sweeteditor.DecorationApplyMode
import com.qiplat.compose.sweeteditor.DecorationResult
import com.qiplat.compose.sweeteditor.DecorationSet
import com.qiplat.compose.sweeteditor.DecorationUpdate
import androidx.compose.ui.graphics.Color
import com.qiplat.compose.sweeteditor.model.decoration.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

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

    @Test
    fun finishGenerationTracksPendingRequests() {
        val manager = DecorationProviderManager()

        val syntaxGeneration = manager.beginGeneration("syntax")
        val diagnosticsGeneration = manager.beginGeneration("diagnostics")

        assertTrue(manager.hasPendingRequests())
        assertEquals(false, manager.finishGeneration("syntax", syntaxGeneration))
        assertTrue(manager.hasPendingRequests())
        assertEquals(true, manager.finishGeneration("diagnostics", diagnosticsGeneration))
        assertEquals(false, manager.hasPendingRequests())
    }

    @Test
    fun staleGenerationDoesNotClearCurrentPendingRequest() {
        val manager = DecorationProviderManager()

        val firstGeneration = manager.beginGeneration("syntax")
        val secondGeneration = manager.beginGeneration("syntax")

        assertEquals(false, manager.finishGeneration("syntax", firstGeneration))
        assertTrue(manager.hasPendingRequests())
        assertEquals(true, manager.finishGeneration("syntax", secondGeneration))
        assertEquals(false, manager.hasPendingRequests())
    }

    @Test
    fun multipleSnapshotsInSameGenerationMergeIncrementally() {
        val manager = DecorationProviderManager()

        val generation = manager.beginGeneration("syntax")
        manager.commitResult(
            providerId = "syntax",
            generation = generation,
            result = DecorationResult(
                syntaxSpans = mapOf(
                    1 to listOf(StyleSpan(column = 0, length = 2, styleId = 1)),
                ),
                syntaxSpansMode = DecorationApplyMode.Merge,
            ),
            defaultLineRange = 1..1,
        )
        val batch = manager.commitResult(
            providerId = "syntax",
            generation = generation,
            result = DecorationResult(
                syntaxSpans = mapOf(
                    2 to listOf(StyleSpan(column = 1, length = 3, styleId = 2)),
                ),
                syntaxSpansMode = DecorationApplyMode.Merge,
            ),
            defaultLineRange = 2..2,
        )

        requireNotNull(batch)
        assertEquals(2, batch.spansByLayer.getValue(SpanLayer.Syntax).size)
        assertTrue(batch.spansByLayer.getValue(SpanLayer.Syntax).containsKey(1))
        assertTrue(batch.spansByLayer.getValue(SpanLayer.Syntax).containsKey(2))
    }

    @Test
    fun replaceRangeSnapshotOverridesOnlyTargetLinesWithinSameGeneration() {
        val manager = DecorationProviderManager()

        val generation = manager.beginGeneration("syntax")
        manager.commitResult(
            providerId = "syntax",
            generation = generation,
            result = DecorationResult(
                syntaxSpans = mapOf(
                    1 to listOf(StyleSpan(column = 0, length = 2, styleId = 1)),
                    2 to listOf(StyleSpan(column = 0, length = 2, styleId = 2)),
                ),
                syntaxSpansMode = DecorationApplyMode.Merge,
            ),
            defaultLineRange = 1..2,
        )
        val batch = manager.commitResult(
            providerId = "syntax",
            generation = generation,
            result = DecorationResult(
                syntaxSpans = mapOf(
                    2 to listOf(StyleSpan(column = 3, length = 1, styleId = 9)),
                ),
                syntaxSpansMode = DecorationApplyMode.ReplaceRange,
                lineRange = 2..2,
            ),
            defaultLineRange = 2..2,
        )

        requireNotNull(batch)
        val syntaxSpans = batch.spansByLayer.getValue(SpanLayer.Syntax)
        assertEquals(2, syntaxSpans.size)
        assertEquals(listOf(StyleSpan(column = 0, length = 2, styleId = 1)), syntaxSpans.getValue(1))
        assertEquals(listOf(StyleSpan(column = 3, length = 1, styleId = 9)), syntaxSpans.getValue(2))
    }

    @Test
    fun providerFailureDoesNotCrashRuntimeHelper() {
        runBlocking {
            val completed = runDecorationProviderSafely {
                error("boom")
            }

            assertEquals(false, completed)
        }
    }

    @Test
    fun providerCancellationIsRethrown() {
        runBlocking {
            assertFailsWith<CancellationException> {
                runDecorationProviderSafely {
                    throw CancellationException("cancelled")
                }
            }
        }
    }

    @Test
    fun failedGenerationKeepsPreviousProviderBatch() {
        val manager = DecorationProviderManager()

        val firstGeneration = manager.beginGeneration("syntax")
        manager.commitResult(
            providerId = "syntax",
            generation = firstGeneration,
            result = DecorationResult(
                syntaxSpans = mapOf(
                    3 to listOf(StyleSpan(column = 0, length = 4, styleId = 7)),
                ),
                syntaxSpansMode = DecorationApplyMode.ReplaceAll,
            ),
            defaultLineRange = 3..3,
        )
        manager.finishGeneration("syntax", firstGeneration)

        val failedGeneration = manager.beginGeneration("syntax")
        manager.finishGeneration("syntax", failedGeneration)

        val syntaxSpans = manager.buildBatch().spansByLayer.getValue(SpanLayer.Syntax)
        assertEquals(1, syntaxSpans.size)
        assertEquals(listOf(StyleSpan(column = 0, length = 4, styleId = 7)), syntaxSpans.getValue(3))
    }

    @Test
    fun unchangedProviderResultDoesNotRebuildAggregateBatch() {
        val manager = DecorationProviderManager()

        val generation = manager.beginGeneration("syntax")
        val firstBatch = manager.commitResult(
            providerId = "syntax",
            generation = generation,
            result = DecorationResult(
                syntaxSpans = mapOf(
                    1 to listOf(StyleSpan(column = 0, length = 2, styleId = 1)),
                ),
                syntaxSpansMode = DecorationApplyMode.ReplaceAll,
            ),
            defaultLineRange = 1..1,
        )
        val secondBatch = manager.commitResult(
            providerId = "syntax",
            generation = generation,
            result = DecorationResult(
                syntaxSpans = mapOf(
                    1 to listOf(StyleSpan(column = 0, length = 2, styleId = 1)),
                ),
                syntaxSpansMode = DecorationApplyMode.ReplaceAll,
            ),
            defaultLineRange = 1..1,
        )

        requireNotNull(firstBatch)
        assertEquals(null, secondBatch)
    }

    @Test
    fun mergeIndentGuidesDoesNotAccumulateDuplicates() {
        val manager = DecorationProviderManager()

        val guide = IndentGuide(
            start = com.qiplat.compose.sweeteditor.model.foundation.TextPosition(1, 4),
            end = com.qiplat.compose.sweeteditor.model.foundation.TextPosition(8, 4),
        )
        val generation = manager.beginGeneration("guides")
        manager.commitResult(
            providerId = "guides",
            generation = generation,
            result = DecorationResult(
                indentGuides = listOf(guide),
                indentGuidesMode = DecorationApplyMode.Merge,
            ),
            defaultLineRange = 1..8,
        )
        val batch = manager.commitResult(
            providerId = "guides",
            generation = generation,
            result = DecorationResult(
                indentGuides = listOf(guide),
                indentGuidesMode = DecorationApplyMode.Merge,
            ),
            defaultLineRange = 1..8,
        )

        assertEquals(null, batch)
        assertEquals(listOf(guide), manager.buildBatch().indentGuides)
    }

    @Test
    fun replaceRangeRemovesIndentGuidesThatOverlapRange() {
        val manager = DecorationProviderManager()

        val baseGeneration = manager.beginGeneration("guides")
        manager.commitResult(
            providerId = "guides",
            generation = baseGeneration,
            result = DecorationResult(
                indentGuides = listOf(
                    IndentGuide(
                        start = com.qiplat.compose.sweeteditor.model.foundation.TextPosition(1, 4),
                        end = com.qiplat.compose.sweeteditor.model.foundation.TextPosition(10, 4),
                    ),
                ),
                indentGuidesMode = DecorationApplyMode.ReplaceAll,
            ),
            defaultLineRange = 1..10,
        )
        val nextGeneration = manager.beginGeneration("guides")
        val replacement = IndentGuide(
            start = com.qiplat.compose.sweeteditor.model.foundation.TextPosition(3, 8),
            end = com.qiplat.compose.sweeteditor.model.foundation.TextPosition(6, 8),
        )
        val batch = manager.commitResult(
            providerId = "guides",
            generation = nextGeneration,
            result = DecorationResult(
                indentGuides = listOf(replacement),
                indentGuidesMode = DecorationApplyMode.ReplaceRange,
                lineRange = 4..5,
            ),
            defaultLineRange = 4..5,
        )

        requireNotNull(batch)
        assertEquals(listOf(replacement), batch.indentGuides)
    }

    @Test
    fun buildBatchProjectsIndentGuidesToVisibleRange() {
        val manager = DecorationProviderManager()

        val generation = manager.beginGeneration("guides")
        manager.commitResult(
            providerId = "guides",
            generation = generation,
            result = DecorationResult(
                indentGuides = listOf(
                    IndentGuide(
                        start = com.qiplat.compose.sweeteditor.model.foundation.TextPosition(1, 4),
                        end = com.qiplat.compose.sweeteditor.model.foundation.TextPosition(20, 4),
                    ),
                    IndentGuide(
                        start = com.qiplat.compose.sweeteditor.model.foundation.TextPosition(400, 4),
                        end = com.qiplat.compose.sweeteditor.model.foundation.TextPosition(460, 4),
                    ),
                ),
                indentGuidesMode = DecorationApplyMode.ReplaceAll,
            ),
            defaultLineRange = 0..500,
            visibleLineRange = 10..30,
        )

        val batch = manager.buildBatch(10..30)

        assertEquals(1, batch.indentGuides.size)
        assertEquals(1, batch.indentGuides.single().start.line)
    }

    @Test
    fun buildBatchProjectsLineBasedDecorationsToVisibleRange() {
        val manager = DecorationProviderManager()

        val generation = manager.beginGeneration("decorations")
        manager.commitResult(
            providerId = "decorations",
            generation = generation,
            result = DecorationResult(
                syntaxSpans = mapOf(
                    5 to listOf(StyleSpan(column = 0, length = 2, styleId = 1)),
                    120 to listOf(StyleSpan(column = 1, length = 3, styleId = 2)),
                ),
                syntaxSpansMode = DecorationApplyMode.ReplaceAll,
                diagnostics = mapOf(
                    150 to listOf(DiagnosticItem(column = 2, length = 2, severity = DiagnosticSeverity.Warning)),
                ),
                diagnosticsMode = DecorationApplyMode.ReplaceAll,
                inlayHints = mapOf(
                    15 to listOf(InlayHint(column = 0, text = "a")),
                    160 to listOf(InlayHint(column = 1, text = "b")),
                ),
                inlayHintsMode = DecorationApplyMode.ReplaceAll,
            ),
            defaultLineRange = 0..200,
            visibleLineRange = 10..30,
        )

        val batch = manager.buildBatch(10..30)

        assertEquals(setOf(5), batch.spansByLayer.getValue(SpanLayer.Syntax).keys)
        assertEquals(setOf(15), batch.inlayHints.keys)
        assertEquals(emptySet(), batch.diagnostics.keys)
    }

    @Test
    fun buildBatchKeepsFoldRegionsOutsideVisibleRange() {
        val manager = DecorationProviderManager()

        val generation = manager.beginGeneration("folds")
        manager.commitResult(
            providerId = "folds",
            generation = generation,
            result = DecorationResult(
                foldRegions = listOf(
                    FoldRegion(startLine = 1, endLine = 3),
                    FoldRegion(startLine = 200, endLine = 240),
                ),
                foldRegionsMode = DecorationApplyMode.ReplaceAll,
            ),
            defaultLineRange = 0..300,
            visibleLineRange = 10..30,
        )

        val batch = manager.buildBatch(10..30)

        assertEquals(2, batch.foldRegions.size)
    }

    @Test
    fun buildBatchProjectsTextStylesToVisibleSpanUsage() {
        val manager = DecorationProviderManager()

        val generation = manager.beginGeneration("styles")
        manager.commitResult(
            providerId = "styles",
            generation = generation,
            result = DecorationResult(
                spanStyles = mapOf(
                    1 to SpanStyle(color = Color(0xFF0000)),
                    2 to SpanStyle(color = Color(0x00FF00)),
                    3 to SpanStyle(color = Color(0x0000FF)),
                ),
                spanStylesMode = DecorationApplyMode.ReplaceAll,
                syntaxSpans = mapOf(
                    15 to listOf(StyleSpan(column = 0, length = 2, styleId = 1)),
                    200 to listOf(StyleSpan(column = 0, length = 2, styleId = 2)),
                ),
                syntaxSpansMode = DecorationApplyMode.ReplaceAll,
                semanticSpans = mapOf(
                    20 to listOf(StyleSpan(column = 1, length = 1, styleId = 3)),
                ),
                semanticSpansMode = DecorationApplyMode.ReplaceAll,
            ),
            defaultLineRange = 0..240,
            visibleLineRange = 10..30,
        )

        val batch = manager.buildBatch(10..30)

        assertEquals(setOf(1, 3), batch.spanStyles.keys)
    }

    @Test
    fun buildBatchDropsTextStylesWhenNoVisibleSpansUseThem() {
        val manager = DecorationProviderManager()

        val generation = manager.beginGeneration("styles")
        manager.commitResult(
            providerId = "styles",
            generation = generation,
            result = DecorationResult(
                spanStyles = mapOf(
                    5 to SpanStyle(color = Color(0xFF0000)),
                ),
                spanStylesMode = DecorationApplyMode.ReplaceAll,
                syntaxSpans = mapOf(
                    400 to listOf(StyleSpan(column = 0, length = 2, styleId = 5)),
                ),
                syntaxSpansMode = DecorationApplyMode.ReplaceAll,
            ),
            defaultLineRange = 0..500,
            visibleLineRange = 10..30,
        )

        val batch = manager.buildBatch(10..30)

        assertEquals(emptySet(), batch.spanStyles.keys)
    }

    @Test
    fun buildBatchReusesProjectedBatchForSameVisibleRange() {
        val manager = DecorationProviderManager()

        val generation = manager.beginGeneration("syntax")
        manager.commitResult(
            providerId = "syntax",
            generation = generation,
            result = DecorationResult(
                syntaxSpans = mapOf(
                    12 to listOf(StyleSpan(column = 0, length = 2, styleId = 1)),
                ),
                syntaxSpansMode = DecorationApplyMode.ReplaceAll,
            ),
            defaultLineRange = 10..20,
            visibleLineRange = 10..20,
        )

        val first = manager.buildBatch(10..20)
        val second = manager.buildBatch(10..20)

        assertSame(first, second)
    }

    @Test
    fun buildBatchInvalidatesProjectedCacheWhenAggregateChanges() {
        val manager = DecorationProviderManager()

        val generation = manager.beginGeneration("syntax")
        manager.commitResult(
            providerId = "syntax",
            generation = generation,
            result = DecorationResult(
                syntaxSpans = mapOf(
                    12 to listOf(StyleSpan(column = 0, length = 2, styleId = 1)),
                ),
                syntaxSpansMode = DecorationApplyMode.ReplaceAll,
            ),
            defaultLineRange = 10..20,
            visibleLineRange = 10..20,
        )

        val first = manager.buildBatch(10..20)

        manager.commitResult(
            providerId = "syntax",
            generation = generation,
            result = DecorationResult(
                syntaxSpans = mapOf(
                    12 to listOf(StyleSpan(column = 0, length = 3, styleId = 1)),
                ),
                syntaxSpansMode = DecorationApplyMode.ReplaceAll,
            ),
            defaultLineRange = 10..20,
            visibleLineRange = 10..20,
        )

        val second = manager.buildBatch(10..20)

        assertNotSame(first, second)
        assertEquals(3, second.spansByLayer.getValue(SpanLayer.Syntax).getValue(12).single().length)
    }
}
