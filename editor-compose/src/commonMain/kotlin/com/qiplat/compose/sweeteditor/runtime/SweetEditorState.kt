package com.qiplat.compose.sweeteditor.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.qiplat.compose.sweeteditor.CompletionItemRenderer
import com.qiplat.compose.sweeteditor.CompletionResult
import com.qiplat.compose.sweeteditor.EditorIconProvider
import com.qiplat.compose.sweeteditor.EditorMetadata
import com.qiplat.compose.sweeteditor.bridge.NativeBridgeFactory
import com.qiplat.compose.sweeteditor.bridge.platformNativeBridgeFactory
import com.qiplat.compose.sweeteditor.model.foundation.GestureResult
import com.qiplat.compose.sweeteditor.model.foundation.KeyEventResult
import com.qiplat.compose.sweeteditor.model.foundation.TextEditResult
import com.qiplat.compose.sweeteditor.model.visual.EditorRenderModel
import com.qiplat.compose.sweeteditor.model.visual.ScrollMetrics
import com.qiplat.compose.sweeteditor.theme.LanguageConfiguration

/**
 * Holds the mutable editor snapshot observed by Compose and updated by [NativeEditorController].
 *
 * The state object intentionally centralizes render data, interaction results, and dirty flags so that
 * the UI can coordinate rendering and side effects without exposing native bridge details.
 *
 * @property bridgeFactory factory used to create document and editor bridges.
 */
class SweetEditorState internal constructor(
    internal val bridgeFactory: NativeBridgeFactory,
) {
    /**
     * Creates an editor state backed by the default platform bridge factory.
     */
    constructor() : this(platformNativeBridgeFactory())

    /**
     * Active document currently attached to the editor.
     */
    var document: EditorDocument? by mutableStateOf(null)
        internal set

    /**
     * Latest render model built by the native editor kernel.
     */
    var renderModel: EditorRenderModel? by mutableStateOf(null)
        internal set

    /**
     * Latest scroll metrics snapshot returned by the native editor kernel.
     */
    var scrollMetrics: ScrollMetrics by mutableStateOf(ScrollMetrics())
        internal set

    /**
     * Latest text edit result produced by controller operations.
     */
    var lastEditResult: TextEditResult by mutableStateOf(TextEditResult.Empty)
        internal set

    /**
     * Latest decoded key event result returned by the kernel.
     */
    var lastKeyEventResult: KeyEventResult by mutableStateOf(KeyEventResult())
        internal set

    /**
     * Latest decoded gesture result returned by the kernel.
     */
    var lastGestureResult: GestureResult by mutableStateOf(GestureResult())
        internal set

    /**
     * Indicates whether a document is currently attached to the editor.
     */
    var isAttached: Boolean by mutableStateOf(false)
        internal set

    /**
     * Indicates whether the controller has already released its native resources.
     */
    var isDisposed: Boolean by mutableStateOf(false)
        internal set

    /**
     * Active language configuration used by decoration providers and higher-level features.
     */
    var languageConfiguration: LanguageConfiguration? by mutableStateOf(null)
        internal set

    var metadata: EditorMetadata? by mutableStateOf(null)
        internal set

    var editorIconProvider: EditorIconProvider? by mutableStateOf(null)
        internal set

    var completionResult: CompletionResult? by mutableStateOf(null)
        internal set

    var completionSelectedIndex: Int by mutableStateOf(0)
        internal set

    var completionItemRenderer: CompletionItemRenderer? by mutableStateOf(null)
        internal set

    /**
     * Marks whether the render model must be rebuilt before the next frame.
     */
    var isRenderModelDirty: Boolean = false
        internal set

    /**
     * Monotonic counter used to trigger render model refresh effects.
     */
    var renderModelRequestVersion: Int = 0
        internal set

    /**
     * Marks whether scroll metrics must be refreshed before the next frame.
     */
    var isScrollMetricsDirty: Boolean = false
        internal set

    /**
     * Monotonic counter used to trigger scroll metrics refresh effects.
     */
    var scrollMetricsRequestVersion: Int = 0
        internal set

    /**
     * Marks whether decoration providers should recompute their output.
     */
    var isDecorationDirty: Boolean = false
        internal set

    /**
     * Monotonic counter used to trigger decoration provider refresh effects.
     */
    var decorationRequestVersion: Int = 0
        internal set

    internal var frameRefreshSignal: Int by mutableIntStateOf(0)
        internal set

    internal var decorationRefreshSignal: Int by mutableIntStateOf(0)
        internal set
}

@Composable
fun rememberSweetEditorState(): SweetEditorState = remember {
    SweetEditorState()
}
