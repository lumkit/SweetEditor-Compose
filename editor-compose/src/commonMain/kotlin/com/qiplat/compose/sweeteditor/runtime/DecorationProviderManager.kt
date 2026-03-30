package com.qiplat.compose.sweeteditor.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.qiplat.compose.sweeteditor.*
import com.qiplat.compose.sweeteditor.model.decoration.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
    val decorationRequestVersion = state.decorationRequestVersion
    val providerIds = providers.map { it.id }
    val visibleLineRange = remember(renderModel, document) {
        document?.let { computeVisibleLineRange(renderModel, it) }
    }

    LaunchedEffect(controller, document, providerIds) {
        if (document == null) {
            manager.clearAll()?.let(controller::applyDecorationBatch)
            return@LaunchedEffect
        }
        manager.retainOnly(providerIds)?.let(controller::applyDecorationBatch)
    }

    providers.forEach { provider ->
        DisposableEffect(provider.id) {
            onDispose {
                manager.removeProvider(provider.id)?.let(controller::applyDecorationBatch)
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
        ) {
            val currentDocument = document
            val currentVisibleRange = visibleLineRange
            if (currentDocument == null || currentVisibleRange == null) {
                manager.removeProvider(provider.id)?.let(controller::applyDecorationBatch)
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
            val context = DecorationProviderContext(
                document = currentDocument,
                visibleLineRange = currentVisibleRange,
                requestedLineRange = requestedLineRange,
                renderModel = renderModel,
                scrollMetrics = scrollMetrics,
                lastEditResult = lastEditResult,
                languageConfiguration = languageConfiguration,
            )
            val update = withContext(Dispatchers.Default) {
                provider.provide(context)
            }
            manager.commit(provider.id, generation, update)?.let(controller::applyDecorationBatch)
            state.isDecorationDirty = false
        }
    }
}

internal class DecorationProviderManager {
    private data class ProviderEntry(
        val generation: Int = 0,
        val batch: DecorationBatch = DecorationBatch(),
    )

    private val providerEntries = linkedMapOf<String, ProviderEntry>()

    fun beginGeneration(providerId: String): Int {
        val nextGeneration = (providerEntries[providerId]?.generation ?: 0) + 1
        providerEntries[providerId] = providerEntries[providerId].orEmpty().copy(generation = nextGeneration)
        return nextGeneration
    }

    fun commit(
        providerId: String,
        generation: Int,
        update: DecorationUpdate?,
    ): DecorationBatch? {
        val current = providerEntries[providerId] ?: return null
        if (current.generation != generation) {
            return null
        }
        providerEntries[providerId] = current.copy(
            batch = applyUpdate(current.batch, update),
        )
        return buildBatch()
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

    internal fun buildBatch(): DecorationBatch {
        var textStyles: Map<Int, TextStyle> = emptyMap()
        var syntaxSpans: Map<Int, List<StyleSpan>> = emptyMap()
        var semanticSpans: Map<Int, List<StyleSpan>> = emptyMap()
        var inlayHints: Map<Int, List<InlayHint>> = emptyMap()
        var phantomTexts: Map<Int, List<PhantomText>> = emptyMap()
        var gutterIcons: Map<Int, List<GutterIcon>> = emptyMap()
        var diagnostics: Map<Int, List<DiagnosticItem>> = emptyMap()
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
            foldRegions = foldRegions,
        )
    }

    private fun applyUpdate(
        current: DecorationBatch,
        update: DecorationUpdate?,
    ): DecorationBatch {
        if (update == null) {
            return DecorationBatch()
        }
        val decorations = update.decorations
        return DecorationBatch(
            textStyles = applyTextStyles(current.textStyles, decorations.textStyles, update),
            spansByLayer = mapOf(
                SpanLayer.Syntax to applyLineMap(
                    current.spansByLayer[SpanLayer.Syntax].orEmpty(),
                    decorations.syntaxSpans,
                    update,
                ),
                SpanLayer.Semantic to applyLineMap(
                    current.spansByLayer[SpanLayer.Semantic].orEmpty(),
                    decorations.semanticSpans,
                    update,
                ),
            ),
            inlayHints = applyLineMap(current.inlayHints, decorations.inlayHints, update),
            phantomTexts = applyLineMap(current.phantomTexts, decorations.phantomTexts, update),
            gutterIcons = applyLineMap(current.gutterIcons, decorations.gutterIcons, update),
            diagnostics = applyLineMap(current.diagnostics, decorations.diagnostics, update),
            foldRegions = applyFoldRegions(current.foldRegions, decorations.foldRegions, update),
        )
    }

    private fun applyTextStyles(
        current: Map<Int, TextStyle>,
        next: Map<Int, TextStyle>?,
        update: DecorationUpdate,
    ): Map<Int, TextStyle> {
        if (next == null) {
            return current
        }
        return when (update.applyMode) {
            DecorationApplyMode.Merge,
            DecorationApplyMode.ReplaceRange,
            -> current + next

            DecorationApplyMode.ReplaceAll -> next
        }
    }

    private fun <T> applyLineMap(
        current: Map<Int, List<T>>,
        next: Map<Int, List<T>>?,
        update: DecorationUpdate,
    ): Map<Int, List<T>> {
        if (next == null) {
            return current
        }
        return when (update.applyMode) {
            DecorationApplyMode.Merge -> current.mergeValues(next)
            DecorationApplyMode.ReplaceAll -> next
            DecorationApplyMode.ReplaceRange -> {
                val lineRange = update.lineRange ?: inferLineRange(next.keys)
                if (lineRange == null) {
                    current
                } else {
                    current
                        .filterKeys { it !in lineRange }
                        .mergeValues(next)
                }
            }
        }
    }

    private fun applyFoldRegions(
        current: List<FoldRegion>,
        next: List<FoldRegion>?,
        update: DecorationUpdate,
    ): List<FoldRegion> {
        if (next == null) {
            return current
        }
        return when (update.applyMode) {
            DecorationApplyMode.Merge -> current + next
            DecorationApplyMode.ReplaceAll -> next
            DecorationApplyMode.ReplaceRange -> {
                val lineRange = update.lineRange ?: inferLineRange(next.map { it.startLine })
                if (lineRange == null) {
                    current
                } else {
                    current.filterNot { it.startLine in lineRange || it.endLine in lineRange } + next
                }
            }
        }
    }

    private fun ProviderEntry?.orEmpty(): ProviderEntry = this ?: ProviderEntry()
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
