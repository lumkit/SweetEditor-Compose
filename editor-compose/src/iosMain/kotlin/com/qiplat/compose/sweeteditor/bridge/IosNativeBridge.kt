package com.qiplat.compose.sweeteditor.bridge

import com.qiplat.compose.sweeteditor.model.foundation.EditorOptions

internal object IosNativeBridgeFactory : NativeBridgeFactory {
    override fun createDocumentFromUtf16(text: String): NativeDocumentBridge =
        unsupported()

    override fun createDocumentFromFile(path: String): NativeDocumentBridge =
        unsupported()

    override fun createEditor(
        textMeasurer: NativeTextMeasurer,
        options: EditorOptions,
    ): NativeEditorBridge = unsupported()

    private fun unsupported(): Nothing {
        error("Missing iOS native editor-core artifact. Add iOS cinterop bindings and native library before enabling the iOS bridge.")
    }
}
