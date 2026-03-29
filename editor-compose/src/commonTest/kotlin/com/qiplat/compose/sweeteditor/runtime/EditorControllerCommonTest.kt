package com.qiplat.compose.sweeteditor.runtime

import com.qiplat.compose.sweeteditor.bridge.NativeBridgeFactory
import com.qiplat.compose.sweeteditor.bridge.NativeDocumentBridge
import com.qiplat.compose.sweeteditor.bridge.NativeEditorBridge
import com.qiplat.compose.sweeteditor.bridge.NativeTextMeasurer
import com.qiplat.compose.sweeteditor.model.foundation.*
import com.qiplat.compose.sweeteditor.protocol.BinaryWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorControllerCommonTest {
    @Test
    fun setDocumentUpdatesStateAndBridge() {
        val editorBridge = FakeNativeEditorBridge()
        val controller = EditorController(
            state = EditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
            textMeasurer = FakeEditorTextMeasurer(),
        )

        val document = EditorDocuments.fromText(
            text = "hello",
            bridgeFactory = FakeNativeBridgeFactory(editorBridge),
        )

        controller.setDocument(document)

        assertEquals(document, controller.state.document)
        assertTrue(controller.state.isAttached)
        assertEquals(document.nativeBridge.handle, editorBridge.documentHandle)
    }

    @Test
    fun insertTextUpdatesLastEditResult() {
        val editorBridge = FakeNativeEditorBridge().apply {
            insertTextPayload = buildTextEditResultPayload()
        }
        val controller = EditorController(
            state = EditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
            textMeasurer = FakeEditorTextMeasurer(),
        )

        val result = controller.insertText("abc")

        assertTrue(result.changed)
        assertEquals(1, result.changes.size)
        assertEquals("abc", result.changes.first().newText)
        assertEquals(result, controller.state.lastEditResult)
    }

    @Test
    fun dispatchGestureEventPassesModifiersAndWheelData() {
        val editorBridge = FakeNativeEditorBridge()
        val controller = EditorController(
            state = EditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
            textMeasurer = FakeEditorTextMeasurer(),
        )

        controller.dispatchGestureEvent(
            type = EditorGestureEventType.DirectScroll,
            points = listOf(GesturePoint(10f, 20f)),
            modifiers = 3,
            wheelDeltaX = 5f,
            wheelDeltaY = -7f,
            directScale = 1.25f,
        )

        assertEquals(13, editorBridge.lastGestureType)
        assertEquals(floatArrayOf(10f, 20f).toList(), editorBridge.lastGesturePoints?.toList())
        assertEquals(3, editorBridge.lastGestureModifiers)
        assertEquals(5f, editorBridge.lastWheelDeltaX)
        assertEquals(-7f, editorBridge.lastWheelDeltaY)
        assertEquals(1.25f, editorBridge.lastDirectScale)
    }
}

private class FakeNativeBridgeFactory(
    private val editorBridge: FakeNativeEditorBridge,
) : NativeBridgeFactory {
    override fun createDocumentFromUtf16(text: String): NativeDocumentBridge =
        FakeNativeDocumentBridge(handle = 1L)

    override fun createDocumentFromFile(path: String): NativeDocumentBridge =
        FakeNativeDocumentBridge(handle = 2L)

    override fun createEditor(
        textMeasurer: NativeTextMeasurer,
        options: EditorOptions,
    ): NativeEditorBridge = editorBridge
}

private class FakeNativeDocumentBridge(
    override val handle: Long,
) : NativeDocumentBridge {
    override fun getLineCount(): Int = 0

    override fun getLineText(line: Int): String = ""

    override fun release() = Unit
}

private class FakeNativeEditorBridge : NativeEditorBridge {
    override val handle: Long = 10L
    var documentHandle: Long = 0L
    var insertTextPayload: ByteArray? = null
    var lastGestureType: Int = -1
    var lastGesturePoints: FloatArray? = null
    var lastGestureModifiers: Int = 0
    var lastWheelDeltaX: Float = 0f
    var lastWheelDeltaY: Float = 0f
    var lastDirectScale: Float = 1f

    override fun release() = Unit

    override fun setDocument(document: NativeDocumentBridge?) {
        documentHandle = document?.handle ?: 0L
    }

    override fun setViewport(width: Int, height: Int) = Unit
    override fun onFontMetricsChanged() = Unit
    override fun setFoldArrowMode(mode: FoldArrowMode) = Unit
    override fun setWrapMode(mode: WrapMode) = Unit
    override fun setTabSize(tabSize: Int) = Unit
    override fun setScale(scale: Float) = Unit
    override fun setLineSpacing(add: Float, mult: Float) = Unit
    override fun setShowSplitLine(show: Boolean) = Unit
    override fun setCurrentLineRenderMode(mode: CurrentLineRenderMode) = Unit
    override fun setGutterSticky(sticky: Boolean) = Unit
    override fun setGutterVisible(visible: Boolean) = Unit
    override fun setReadOnly(readOnly: Boolean) = Unit
    override fun setCompositionEnabled(enabled: Boolean) = Unit
    override fun setCursorPosition(position: TextPosition) = Unit
    override fun setSelection(range: TextRange) = Unit
    override fun getCursorPosition(): TextPosition = TextPosition.Zero
    override fun getSelection(): TextRange? = null
    override fun buildRenderModel(): ByteArray? = null
    override fun getScrollMetrics(): ByteArray? = null
    override fun handleGesture(
        type: Int,
        points: FloatArray,
        modifiers: Int,
        wheelDeltaX: Float,
        wheelDeltaY: Float,
        directScale: Float,
    ): ByteArray? {
        lastGestureType = type
        lastGesturePoints = points
        lastGestureModifiers = modifiers
        lastWheelDeltaX = wheelDeltaX
        lastWheelDeltaY = wheelDeltaY
        lastDirectScale = directScale
        return null
    }
    override fun tickAnimations(): ByteArray? = null
    override fun handleKeyEvent(keyCode: Int, text: String?, modifiers: Int): ByteArray? = null
    override fun compositionStart() = Unit
    override fun compositionUpdate(text: String) = Unit
    override fun compositionEnd(committedText: String?): ByteArray? = null
    override fun compositionCancel() = Unit
    override fun isComposing(): Boolean = false
    override fun insertText(text: String): ByteArray? = insertTextPayload
    override fun replaceText(range: TextRange, text: String): ByteArray? = null
    override fun deleteText(range: TextRange): ByteArray? = null
    override fun backspace(): ByteArray? = null
    override fun deleteForward(): ByteArray? = null
    override fun registerBatchTextStyles(data: ByteArray) = Unit
    override fun setBatchLineSpans(data: ByteArray) = Unit
    override fun setBatchLineInlayHints(data: ByteArray) = Unit
    override fun setBatchLinePhantomTexts(data: ByteArray) = Unit
    override fun setBatchLineGutterIcons(data: ByteArray) = Unit
    override fun setBatchLineDiagnostics(data: ByteArray) = Unit
    override fun setFoldRegions(data: ByteArray) = Unit
    override fun setMaxGutterIcons(count: Int) = Unit
}

private class FakeEditorTextMeasurer : EditorTextMeasurer {
    override fun setScale(scale: Float) = Unit

    override fun measureTextWidth(text: String, fontStyle: Int): Float = text.length.toFloat()

    override fun measureInlayHintWidth(text: String): Float = text.length.toFloat()

    override fun measureIconWidth(iconId: Int): Float = iconId.toFloat()

    override fun getFontMetrics(): FloatArray = floatArrayOf(10f, 8f, 2f, 0f)
}

private fun buildTextEditResultPayload(): ByteArray {
    val writer = BinaryWriter()
    writer.writeBooleanAsInt(true)
    writer.writeInt(1)
    writer.writeInt(0)
    writer.writeInt(0)
    writer.writeInt(0)
    writer.writeInt(0)
    writer.writeUtf8("abc")
    return writer.toByteArray()
}
