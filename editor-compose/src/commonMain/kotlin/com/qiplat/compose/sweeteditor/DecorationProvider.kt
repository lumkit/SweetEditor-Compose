package com.qiplat.compose.sweeteditor

import com.qiplat.compose.sweeteditor.model.decoration.*
import com.qiplat.compose.sweeteditor.model.foundation.TextEditResult
import com.qiplat.compose.sweeteditor.model.visual.EditorRenderModel
import com.qiplat.compose.sweeteditor.model.visual.ScrollMetrics
import com.qiplat.compose.sweeteditor.runtime.EditorDocument
import com.qiplat.compose.sweeteditor.theme.LanguageConfiguration

enum class DecorationApplyMode {
    Merge,
    ReplaceRange,
    ReplaceAll,
}

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

data class DecorationUpdate(
    val decorations: DecorationSet = DecorationSet(),
    val applyMode: DecorationApplyMode = DecorationApplyMode.Merge,
    val lineRange: IntRange? = null,
)

data class DecorationProviderContext(
    val document: EditorDocument,
    val visibleLineRange: IntRange,
    val requestedLineRange: IntRange,
    val renderModel: EditorRenderModel?,
    val scrollMetrics: ScrollMetrics,
    val lastEditResult: TextEditResult,
    val languageConfiguration: LanguageConfiguration?,
)

interface DecorationProvider {
    val id: String

    val overscanLines: Int
        get() = 0

    val debounceMillis: Long
        get() = 0L

    suspend fun provide(context: DecorationProviderContext): DecorationUpdate?
}

internal data class DecorationBatch(
    val textStyles: Map<Int, TextStyle> = emptyMap(),
    val spansByLayer: Map<SpanLayer, Map<Int, List<StyleSpan>>> = emptyMap(),
    val inlayHints: Map<Int, List<InlayHint>> = emptyMap(),
    val phantomTexts: Map<Int, List<PhantomText>> = emptyMap(),
    val gutterIcons: Map<Int, List<GutterIcon>> = emptyMap(),
    val diagnostics: Map<Int, List<DiagnosticItem>> = emptyMap(),
    val foldRegions: List<FoldRegion> = emptyList(),
)
