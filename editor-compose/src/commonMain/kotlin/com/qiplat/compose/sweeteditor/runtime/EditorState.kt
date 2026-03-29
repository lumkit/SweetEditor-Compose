package com.qiplat.compose.sweeteditor.runtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.qiplat.compose.sweeteditor.bridge.NativeBridgeFactory
import com.qiplat.compose.sweeteditor.bridge.platformNativeBridgeFactory
import com.qiplat.compose.sweeteditor.model.foundation.GestureResult
import com.qiplat.compose.sweeteditor.model.foundation.KeyEventResult
import com.qiplat.compose.sweeteditor.model.foundation.TextEditResult
import com.qiplat.compose.sweeteditor.model.visual.EditorRenderModel
import com.qiplat.compose.sweeteditor.model.visual.ScrollMetrics

class EditorState internal constructor(
    internal val bridgeFactory: NativeBridgeFactory,
) {
    constructor() : this(platformNativeBridgeFactory())

    var document: EditorDocument? by mutableStateOf(null)
        internal set

    var renderModel: EditorRenderModel? by mutableStateOf(null)
        internal set

    var scrollMetrics: ScrollMetrics by mutableStateOf(ScrollMetrics())
        internal set

    var lastEditResult: TextEditResult by mutableStateOf(TextEditResult.Empty)
        internal set

    var lastKeyEventResult: KeyEventResult by mutableStateOf(KeyEventResult())
        internal set

    var lastGestureResult: GestureResult by mutableStateOf(GestureResult())
        internal set

    var isAttached: Boolean by mutableStateOf(false)
        internal set

    var isDisposed: Boolean by mutableStateOf(false)
        internal set
}
