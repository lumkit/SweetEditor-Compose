package com.qiplat.compose.sweeteditor.runtime

import com.qiplat.compose.sweeteditor.DecorationApplyMode
import com.qiplat.compose.sweeteditor.DecorationResult
import com.qiplat.compose.sweeteditor.DecorationSet
import com.qiplat.compose.sweeteditor.DecorationUpdate
import com.qiplat.compose.sweeteditor.model.decoration.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
}
