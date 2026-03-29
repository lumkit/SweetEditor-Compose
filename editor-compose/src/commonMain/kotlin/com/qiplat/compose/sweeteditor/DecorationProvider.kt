package com.qiplat.compose.sweeteditor

import com.qiplat.compose.sweeteditor.model.decoration.*
import com.qiplat.compose.sweeteditor.model.foundation.TextEditResult
import com.qiplat.compose.sweeteditor.model.visual.EditorRenderModel
import com.qiplat.compose.sweeteditor.model.visual.ScrollMetrics
import com.qiplat.compose.sweeteditor.runtime.EditorDocument
import com.qiplat.compose.sweeteditor.theme.LanguageConfiguration

/**
 * Defines how a provider result should be merged into the current decoration snapshot.
 */
enum class DecorationApplyMode {
    /**
     * Appends or overrides only the data that is present in the incoming update.
     */
    Merge,

    /**
     * Replaces existing decorations only inside the target line range.
     */
    ReplaceRange,

    /**
     * Replaces the entire decoration snapshot produced by the provider.
     */
    ReplaceAll,
}

/**
 * Contains all decoration payloads that can be produced by a provider run.
 *
 * Each property is optional so that a provider can update only the decoration types it owns.
 *
 * @property textStyles style definitions that should be registered before spans are applied.
 * @property syntaxSpans syntax highlight spans grouped by logical line.
 * @property semanticSpans semantic highlight spans grouped by logical line.
 * @property inlayHints inline hints grouped by logical line.
 * @property phantomTexts phantom texts grouped by logical line.
 * @property gutterIcons gutter icons grouped by logical line.
 * @property diagnostics diagnostics grouped by logical line.
 * @property foldRegions fold regions owned by the provider.
 */
data class DecorationSet(
    val textStyles: Map<Int, TextStyle>? = null,
    val syntaxSpans: Map<Int, List<StyleSpan>>? = null,
    val semanticSpans: Map<Int, List<StyleSpan>>? = null,
    val inlayHints: Map<Int, List<InlayHint>>? = null,
    val phantomTexts: Map<Int, List<PhantomText>>? = null,
    val gutterIcons: Map<Int, List<GutterIcon>>? = null,
    val diagnostics: Map<Int, List<DiagnosticItem>>? = null,
    val foldRegions: List<FoldRegion>? = null,
)

/**
 * Represents one provider refresh result.
 *
 * @property decorations decoration payload produced by the provider.
 * @property applyMode merge mode used by the manager when it applies the result.
 * @property lineRange affected logical line range. It is mainly used by replace-range updates.
 */
data class DecorationUpdate(
    val decorations: DecorationSet = DecorationSet(),
    val applyMode: DecorationApplyMode = DecorationApplyMode.Merge,
    val lineRange: IntRange? = null,
)

/**
 * Immutable input passed to a decoration provider.
 *
 * @property document current document instance. Providers can read line count and line text from it.
 * @property visibleLineRange logical line range that is currently visible in the viewport.
 * @property requestedLineRange logical line range requested after overscan is applied.
 * @property renderModel latest render model snapshot, or null when the editor has not rendered yet.
 * @property scrollMetrics latest scroll metrics snapshot.
 * @property lastEditResult latest edit result produced by the controller.
 * @property languageConfiguration active language configuration, or null when no language is attached.
 */
data class DecorationProviderContext(
    val document: EditorDocument,
    val visibleLineRange: IntRange,
    val requestedLineRange: IntRange,
    val renderModel: EditorRenderModel?,
    val scrollMetrics: ScrollMetrics,
    val lastEditResult: TextEditResult,
    val languageConfiguration: LanguageConfiguration?,
)

/**
 * Produces editor decorations for the current document snapshot.
 */
interface DecorationProvider {
    /**
     * Stable identifier used by the manager to keep provider cache and merge results.
     */
    val id: String

    /**
     * Additional lines requested before and after the visible range.
     *
     * A provider can use overscan to avoid visible pop-in when the viewport scrolls slightly.
     *
     * @return non-negative logical line count added to both sides of the visible range.
     */
    val overscanLines: Int
        get() = 0

    /**
     * Delay before the provider run starts.
     *
     * @return debounce interval in milliseconds.
     */
    val debounceMillis: Long
        get() = 0L

    /**
     * Produces a decoration update for the supplied editor snapshot.
     *
     * @param context immutable provider input that describes the current document, viewport, and edit state.
     * @return decoration update to merge into the editor, or null when the provider has nothing to contribute.
     */
    suspend fun provide(context: DecorationProviderContext): DecorationUpdate?
}

/**
 * Aggregated decoration payload ready to be flushed into the native bridge.
 */
internal data class DecorationBatch(
    val textStyles: Map<Int, TextStyle> = emptyMap(),
    val spansByLayer: Map<SpanLayer, Map<Int, List<StyleSpan>>> = emptyMap(),
    val inlayHints: Map<Int, List<InlayHint>> = emptyMap(),
    val phantomTexts: Map<Int, List<PhantomText>> = emptyMap(),
    val gutterIcons: Map<Int, List<GutterIcon>> = emptyMap(),
    val diagnostics: Map<Int, List<DiagnosticItem>> = emptyMap(),
    val foldRegions: List<FoldRegion> = emptyList(),
)
