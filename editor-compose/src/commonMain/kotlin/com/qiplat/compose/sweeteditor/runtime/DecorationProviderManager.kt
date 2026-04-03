package com.qiplat.compose.sweeteditor.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.qiplat.compose.sweeteditor.*
import com.qiplat.compose.sweeteditor.model.decoration.*
import kotlinx.coroutines.*

@Composable
internal fun InstallDecorationProviders(
    controller: EditorController,
    state: EditorState,
    providers: List<DecorationProvider>,
) {
    val manager = remember(controller) { DecorationProviderManager() }
    val document = state.document
    val renderModel = state.renderModel
    val scrollMetrics = state.scrollMetrics
    val lastEditResult = state.lastEditResult
    val languageConfiguration = state.languageConfiguration
    val metadata = state.metadata
    val decorationRequestVersion = state.decorationRequestVersion
    val providerIds = providers.map { it.id }
    val visibleLineRange = remember(renderModel, document) {
        document?.let { computeVisibleLineRange(renderModel, it) }
    }

    LaunchedEffect(controller, document, providerIds) {
        if (document == null) {
            manager.clearAll()?.let(controller::applyDecorationBatch)
            state.isDecorationDirty = false
            return@LaunchedEffect
        }
        manager.retainOnly(providerIds)?.let(controller::applyDecorationBatch)
        if (providerIds.isEmpty() && !manager.hasPendingRequests()) {
            state.isDecorationDirty = false
        }
    }

    providers.forEach { provider ->
        DisposableEffect(provider.id) {
            onDispose {
                manager.removeProvider(provider.id)?.let(controller::applyDecorationBatch)
                if (!manager.hasPendingRequests()) {
                    state.isDecorationDirty = false
                }
            }
        }

        LaunchedEffect(
            controller,
            document,
            provider.id,
            decorationRequestVersion,
            visibleLineRange,
            scrollMetrics.viewportWidth,
            scrollMetrics.viewportHeight,
            lastEditResult,
            languageConfiguration,
            metadata,
        ) {
            val effectScope = this
            val currentDocument = document
            val currentVisibleRange = visibleLineRange
            if (currentDocument == null || currentVisibleRange == null) {
                manager.removeProvider(provider.id)?.let(controller::applyDecorationBatch)
                if (!manager.hasPendingRequests()) {
                    state.isDecorationDirty = false
                }
                return@LaunchedEffect
            }
            val requestedLineRange = currentVisibleRange.expand(
                provider.overscanLines,
                currentDocument.getLineCount(),
            )
            val generation = manager.beginGeneration(provider.id)
            if (provider.debounceMillis > 0) {
                delay(provider.debounceMillis)
            }
            if (!manager.isCurrentGeneration(provider.id, generation)) {
                return@LaunchedEffect
            }
            val context = DecorationProviderContext(
                document = currentDocument,
                visibleLineRange = currentVisibleRange,
                requestedLineRange = requestedLineRange,
                renderModel = renderModel,
                scrollMetrics = scrollMetrics,
                lastEditResult = lastEditResult,
                languageConfiguration = languageConfiguration,
                editorMetadata = metadata,
            )
            val receiver = object : DecorationReceiver {
                override fun accept(result: DecorationResult): Boolean {
                    if (!manager.isCurrentGeneration(provider.id, generation)) {
                        return false
                    }
                    effectScope.launch {
                        manager.commitResult(
                            providerId = provider.id,
                            generation = generation,
                            result = result,
                            defaultLineRange = requestedLineRange,
                        )?.let(controller::applyDecorationBatch)
                    }
                    return true
                }

                override fun isCancelled(): Boolean = !manager.isCurrentGeneration(provider.id, generation)
            }
            try {
                withContext(Dispatchers.Default) {
                    runDecorationProviderSafely {
                        provider.provideDecorations(context, receiver)
                    }
                }
            } finally {
                if (manager.finishGeneration(provider.id, generation)) {
                    state.isDecorationDirty = false
                }
            }
        }
    }
}

internal class DecorationProviderManager {
    private data class ProviderEntry(
        val generation: Int = 0,
        val batch: DecorationBatch = DecorationBatch(),
        val pendingGeneration: Int? = null,
    )

    private val providerEntries = linkedMapOf<String, ProviderEntry>()

    fun beginGeneration(providerId: String): Int {
        val nextGeneration = (providerEntries[providerId]?.generation ?: 0) + 1
        providerEntries[providerId] = providerEntries[providerId].orEmpty().copy(
            generation = nextGeneration,
            pendingGeneration = nextGeneration,
        )
        return nextGeneration
    }

    fun isCurrentGeneration(
        providerId: String,
        generation: Int,
    ): Boolean = providerEntries[providerId]?.generation == generation

    fun commit(
        providerId: String,
        generation: Int,
        update: DecorationUpdate?,
    ): DecorationBatch? {
        val result = update?.toDecorationResult() ?: DecorationResult(
            textStyles = emptyMap(),
            textStylesMode = DecorationApplyMode.ReplaceAll,
            syntaxSpans = emptyMap(),
            syntaxSpansMode = DecorationApplyMode.ReplaceAll,
            semanticSpans = emptyMap(),
            semanticSpansMode = DecorationApplyMode.ReplaceAll,
            inlayHints = emptyMap(),
            inlayHintsMode = DecorationApplyMode.ReplaceAll,
            diagnostics = emptyMap(),
            diagnosticsMode = DecorationApplyMode.ReplaceAll,
            indentGuides = emptyList(),
            indentGuidesMode = DecorationApplyMode.ReplaceAll,
            bracketGuides = emptyList(),
            bracketGuidesMode = DecorationApplyMode.ReplaceAll,
            flowGuides = emptyList(),
            flowGuidesMode = DecorationApplyMode.ReplaceAll,
            separatorGuides = emptyList(),
            separatorGuidesMode = DecorationApplyMode.ReplaceAll,
            foldRegions = emptyList(),
            foldRegionsMode = DecorationApplyMode.ReplaceAll,
            gutterIcons = emptyMap(),
            gutterIconsMode = DecorationApplyMode.ReplaceAll,
            phantomTexts = emptyMap(),
            phantomTextsMode = DecorationApplyMode.ReplaceAll,
        )
        return commitResult(providerId, generation, result, update?.lineRange ?: IntRange.EMPTY)
    }

    fun commitResult(
        providerId: String,
        generation: Int,
        result: DecorationResult,
        defaultLineRange: IntRange,
    ): DecorationBatch? {
        val current = providerEntries[providerId] ?: return null
        if (current.generation != generation) {
            return null
        }
        providerEntries[providerId] = current.copy(
            batch = applyResult(current.batch, result, defaultLineRange),
        )
        return buildBatch()
    }

    fun finishGeneration(
        providerId: String,
        generation: Int,
    ): Boolean {
        val current = providerEntries[providerId] ?: return hasPendingRequests().not()
        if (current.generation != generation || current.pendingGeneration != generation) {
            return hasPendingRequests().not()
        }
        providerEntries[providerId] = current.copy(pendingGeneration = null)
        return hasPendingRequests().not()
    }

    fun removeProvider(providerId: String): DecorationBatch? {
        providerEntries.remove(providerId) ?: return null
        return if (providerEntries.isNotEmpty()) {
            buildBatch()
        } else {
            DecorationBatch()
        }
    }

    fun retainOnly(providerIds: List<String>): DecorationBatch? {
        val idSet = providerIds.toSet()
        val removed = providerEntries.keys.filterNot(idSet::contains)
        if (removed.isEmpty()) {
            return null
        }
        removed.forEach(providerEntries::remove)
        return buildBatch()
    }

    fun clearAll(): DecorationBatch? {
        if (providerEntries.isEmpty()) {
            return null
        }
        providerEntries.clear()
        return DecorationBatch()
    }

    fun hasPendingRequests(): Boolean = providerEntries.values.any { it.pendingGeneration != null }

    internal fun buildBatch(): DecorationBatch {
        var textStyles: Map<Int, TextStyle> = emptyMap()
        var syntaxSpans: Map<Int, List<StyleSpan>> = emptyMap()
        var semanticSpans: Map<Int, List<StyleSpan>> = emptyMap()
        var inlayHints: Map<Int, List<InlayHint>> = emptyMap()
        var phantomTexts: Map<Int, List<PhantomText>> = emptyMap()
        var gutterIcons: Map<Int, List<GutterIcon>> = emptyMap()
        var diagnostics: Map<Int, List<DiagnosticItem>> = emptyMap()
        var indentGuides: List<IndentGuide> = emptyList()
        var bracketGuides: List<BracketGuide> = emptyList()
        var flowGuides: List<FlowGuide> = emptyList()
        var separatorGuides: List<SeparatorGuide> = emptyList()
        var foldRegions: List<FoldRegion> = emptyList()

        providerEntries.values.forEach { entry ->
            val batch = entry.batch
            textStyles = textStyles + batch.textStyles
            syntaxSpans = syntaxSpans.mergeValues(batch.spansByLayer[SpanLayer.Syntax].orEmpty())
            semanticSpans = semanticSpans.mergeValues(batch.spansByLayer[SpanLayer.Semantic].orEmpty())
            inlayHints = inlayHints.mergeValues(batch.inlayHints)
            phantomTexts = phantomTexts.mergeValues(batch.phantomTexts)
            gutterIcons = gutterIcons.mergeValues(batch.gutterIcons)
            diagnostics = diagnostics.mergeValues(batch.diagnostics)
            indentGuides = indentGuides + batch.indentGuides
            bracketGuides = bracketGuides + batch.bracketGuides
            flowGuides = flowGuides + batch.flowGuides
            separatorGuides = separatorGuides + batch.separatorGuides
            foldRegions = foldRegions + batch.foldRegions
        }

        return DecorationBatch(
            textStyles = textStyles,
            spansByLayer = mapOf(
                SpanLayer.Syntax to syntaxSpans,
                SpanLayer.Semantic to semanticSpans,
            ),
            inlayHints = inlayHints,
            phantomTexts = phantomTexts,
            gutterIcons = gutterIcons,
            diagnostics = diagnostics,
            indentGuides = indentGuides,
            bracketGuides = bracketGuides,
            flowGuides = flowGuides,
            separatorGuides = separatorGuides,
            foldRegions = foldRegions,
        )
    }

    private fun applyResult(
        current: DecorationBatch,
        result: DecorationResult,
        defaultLineRange: IntRange,
    ): DecorationBatch {
        return DecorationBatch(
            textStyles = applyTextStyles(current.textStyles, result.textStyles, result.textStylesMode),
            spansByLayer = mapOf(
                SpanLayer.Syntax to applyLineMap(
                    current.spansByLayer[SpanLayer.Syntax].orEmpty(),
                    result.syntaxSpans,
                    result.syntaxSpansMode,
                    result.lineRange ?: defaultLineRange,
                ),
                SpanLayer.Semantic to applyLineMap(
                    current.spansByLayer[SpanLayer.Semantic].orEmpty(),
                    result.semanticSpans,
                    result.semanticSpansMode,
                    result.lineRange ?: defaultLineRange,
                ),
            ),
            inlayHints = applyLineMap(current.inlayHints, result.inlayHints, result.inlayHintsMode, result.lineRange ?: defaultLineRange),
            phantomTexts = applyLineMap(current.phantomTexts, result.phantomTexts, result.phantomTextsMode, result.lineRange ?: defaultLineRange),
            gutterIcons = applyLineMap(current.gutterIcons, result.gutterIcons, result.gutterIconsMode, result.lineRange ?: defaultLineRange),
            diagnostics = applyLineMap(current.diagnostics, result.diagnostics, result.diagnosticsMode, result.lineRange ?: defaultLineRange),
            indentGuides = applyGuideList(current.indentGuides, result.indentGuides, result.indentGuidesMode, result.lineRange ?: defaultLineRange) { it.start.line },
            bracketGuides = applyGuideList(current.bracketGuides, result.bracketGuides, result.bracketGuidesMode, result.lineRange ?: defaultLineRange) { it.parent.line },
            flowGuides = applyGuideList(current.flowGuides, result.flowGuides, result.flowGuidesMode, result.lineRange ?: defaultLineRange) { it.start.line },
            separatorGuides = applyGuideList(current.separatorGuides, result.separatorGuides, result.separatorGuidesMode, result.lineRange ?: defaultLineRange) { it.line },
            foldRegions = applyFoldRegions(current.foldRegions, result.foldRegions, result.foldRegionsMode, result.lineRange ?: defaultLineRange),
        )
    }

    private fun applyTextStyles(
        current: Map<Int, TextStyle>,
        next: Map<Int, TextStyle>?,
        mode: DecorationApplyMode,
    ): Map<Int, TextStyle> {
        if (next == null) {
            return current
        }
        return when (mode) {
            DecorationApplyMode.Merge,
            DecorationApplyMode.ReplaceRange,
            -> current + next

            DecorationApplyMode.ReplaceAll -> next
        }
    }

    private fun <T> applyLineMap(
        current: Map<Int, List<T>>,
        next: Map<Int, List<T>>?,
        mode: DecorationApplyMode,
        lineRange: IntRange,
    ): Map<Int, List<T>> {
        if (next == null) {
            return current
        }
        return when (mode) {
            DecorationApplyMode.Merge -> current.mergeValues(next)
            DecorationApplyMode.ReplaceAll -> next
            DecorationApplyMode.ReplaceRange -> {
                current
                    .filterKeys { it !in lineRange }
                    .mergeValues(next)
            }
        }
    }

    private fun <T> applyGuideList(
        current: List<T>,
        next: List<T>?,
        mode: DecorationApplyMode,
        lineRange: IntRange,
        lineSelector: (T) -> Int,
    ): List<T> {
        if (next == null) {
            return current
        }
        return when (mode) {
            DecorationApplyMode.Merge -> current + next
            DecorationApplyMode.ReplaceAll -> next
            DecorationApplyMode.ReplaceRange -> current.filterNot { lineSelector(it) in lineRange } + next
        }
    }

    private fun applyFoldRegions(
        current: List<FoldRegion>,
        next: List<FoldRegion>?,
        mode: DecorationApplyMode,
        lineRange: IntRange,
    ): List<FoldRegion> {
        if (next == null) {
            return current
        }
        return when (mode) {
            DecorationApplyMode.Merge -> current + next
            DecorationApplyMode.ReplaceAll -> next
            DecorationApplyMode.ReplaceRange -> current.filterNot { it.startLine in lineRange || it.endLine in lineRange } + next
        }
    }

    private fun ProviderEntry?.orEmpty(): ProviderEntry = this ?: ProviderEntry()
}

internal suspend fun runDecorationProviderSafely(
    block: suspend () -> Unit,
): Boolean = try {
    block()
    true
} catch (error: CancellationException) {
    throw error
} catch (_: Throwable) {
    false
}

private fun computeVisibleLineRange(
    renderModel: com.qiplat.compose.sweeteditor.model.visual.EditorRenderModel?,
    document: EditorDocument,
): IntRange {
    val logicalLines = renderModel?.lines
        ?.map { it.logicalLine }
        ?.distinct()
        .orEmpty()
    if (logicalLines.isEmpty()) {
        val lastLine = (document.getLineCount() - 1).coerceAtLeast(0)
        return 0..lastLine
    }
    return logicalLines.first()..logicalLines.last()
}

private fun IntRange.expand(overscanLines: Int, lineCount: Int): IntRange {
    if (lineCount <= 0) {
        return IntRange.EMPTY
    }
    val safeOverscan = overscanLines.coerceAtLeast(0)
    return (first - safeOverscan).coerceAtLeast(0)..(last + safeOverscan).coerceAtMost(lineCount - 1)
}

private fun inferLineRange(lines: Collection<Int>): IntRange? {
    if (lines.isEmpty()) {
        return null
    }
    return lines.min()..lines.max()
}

private fun <T> Map<Int, List<T>>.mergeValues(
    next: Map<Int, List<T>>,
): Map<Int, List<T>> = buildMap {
    putAll(this@mergeValues)
    next.forEach { (line, values) ->
        val mergedValues = get(line).orEmpty() + values
        put(line, mergedValues)
    }
}
