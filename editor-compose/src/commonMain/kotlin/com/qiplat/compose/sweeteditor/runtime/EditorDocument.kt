package com.qiplat.compose.sweeteditor.runtime

import com.qiplat.compose.sweeteditor.bridge.NativeBridgeFactory
import com.qiplat.compose.sweeteditor.bridge.NativeDocumentBridge
import com.qiplat.compose.sweeteditor.bridge.platformNativeBridgeFactory

class EditorDocument internal constructor(
    internal val nativeBridge: NativeDocumentBridge,
) {
    fun close() {
        nativeBridge.release()
    }
}

object EditorDocuments {
    fun fromText(text: String): EditorDocument =
        fromText(
            text = text,
            bridgeFactory = platformNativeBridgeFactory(),
        )

    internal fun fromText(
        text: String,
        bridgeFactory: NativeBridgeFactory,
    ): EditorDocument = EditorDocument(
        nativeBridge = bridgeFactory.createDocumentFromUtf16(text),
    )

    fun fromFile(path: String): EditorDocument =
        fromFile(
            path = path,
            bridgeFactory = platformNativeBridgeFactory(),
        )

    internal fun fromFile(
        path: String,
        bridgeFactory: NativeBridgeFactory,
    ): EditorDocument = EditorDocument(
        nativeBridge = bridgeFactory.createDocumentFromFile(path),
    )
}
