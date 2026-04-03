package com.qiplat.compose.sweeteditor.runtime

import com.qiplat.compose.sweeteditor.*
import com.qiplat.compose.sweeteditor.bridge.NativeBridgeFactory
import com.qiplat.compose.sweeteditor.bridge.NativeDocumentBridge
import com.qiplat.compose.sweeteditor.bridge.NativeEditorBridge
import com.qiplat.compose.sweeteditor.bridge.NativeTextMeasurer
import com.qiplat.compose.sweeteditor.model.foundation.*
import com.qiplat.compose.sweeteditor.model.snippet.LinkedEditingModel
import com.qiplat.compose.sweeteditor.model.snippet.TabStopGroup
import com.qiplat.compose.sweeteditor.model.visual.CursorRect
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

    @Test
    fun applySettingsUpdatesNativeBridge() {
        val editorBridge = FakeNativeEditorBridge()
        val controller = EditorController(
            state = EditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
            textMeasurer = FakeEditorTextMeasurer(),
        )

        controller.applySettings(
            EditorSettings(
                wrapMode = WrapMode.WordBreak,
                tabSize = 2,
                lineSpacingExtra = 3f,
                lineSpacingMultiplier = 1.2f,
                foldArrowMode = FoldArrowMode.Always,
                gutterSticky = false,
                gutterVisible = false,
                currentLineRenderMode = CurrentLineRenderMode.Border,
                readOnly = true,
                compositionEnabled = false,
                autoIndentMode = AutoIndentMode.KeepIndent,
            ),
        )

        assertEquals(WrapMode.WordBreak, editorBridge.appliedWrapMode)
        assertEquals(2, editorBridge.appliedTabSize)
        assertEquals(3f, editorBridge.lineSpacingAdd)
        assertEquals(1.2f, editorBridge.lineSpacingMult)
        assertEquals(FoldArrowMode.Always, editorBridge.appliedFoldArrowMode)
        assertEquals(false, editorBridge.appliedGutterSticky)
        assertEquals(false, editorBridge.appliedGutterVisible)
        assertEquals(CurrentLineRenderMode.Border, editorBridge.appliedCurrentLineRenderMode)
        assertEquals(true, editorBridge.appliedReadOnly)
        assertEquals(false, editorBridge.appliedCompositionEnabled)
        assertEquals(AutoIndentMode.KeepIndent, editorBridge.appliedAutoIndentMode)
        assertEquals(WrapMode.WordBreak, controller.getWrapMode())
        assertEquals(2, controller.getTabSize())
        assertEquals(FoldArrowMode.Always, controller.getFoldArrowMode())
        assertEquals(LineSpacing(extra = 3f, multiplier = 1.2f), controller.getLineSpacing())
        assertEquals(CurrentLineRenderMode.Border, controller.getCurrentLineRenderMode())
        assertEquals(false, controller.isGutterSticky())
        assertEquals(false, controller.isGutterVisible())
    }

    @Test
    fun individualSettingSettersUpdateQueryableState() {
        val editorBridge = FakeNativeEditorBridge()
        val controller = EditorController(
            state = EditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
            textMeasurer = FakeEditorTextMeasurer(),
        )

        controller.setWrapMode(WrapMode.CharBreak)
        controller.setTabSize(8)
        controller.setFoldArrowMode(FoldArrowMode.Hidden)
        controller.setLineSpacing(4f, 1.5f)
        controller.setShowSplitLine(false)
        controller.setCurrentLineRenderMode(CurrentLineRenderMode.None)
        controller.setScale(1.25f)
        controller.setGutterSticky(false)
        controller.setGutterVisible(false)

        assertEquals(WrapMode.CharBreak, controller.getWrapMode())
        assertEquals(8, controller.getTabSize())
        assertEquals(FoldArrowMode.Hidden, controller.getFoldArrowMode())
        assertEquals(LineSpacing(extra = 4f, multiplier = 1.5f), controller.getLineSpacing())
        assertEquals(false, controller.isShowSplitLine())
        assertEquals(CurrentLineRenderMode.None, controller.getCurrentLineRenderMode())
        assertEquals(1.25f, controller.getScale())
        assertEquals(false, controller.isGutterSticky())
        assertEquals(false, controller.isGutterVisible())
    }

    @Test
    fun completionProvidersPopulateAndApplySelectedItem() {
        val editorBridge = FakeNativeEditorBridge().apply {
            fakeCursorPosition = TextPosition(0, 3)
            wordRange = TextRange(
                start = TextPosition(0, 0),
                end = TextPosition(0, 3),
            )
            replaceTextPayload = buildTextEditResultPayload()
        }
        val controller = SweetEditorController(
            textMeasurer = FakeEditorTextMeasurer(),
            state = EditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
        )
        controller.loadDocument(
            EditorDocument(
                FakeNativeDocumentBridge(
                    handle = 1L,
                    lines = listOf("pri"),
                ),
            ),
        )
        controller.addCompletionProvider(
            object : CompletionProvider {
                override suspend fun provideCompletions(
                    context: com.qiplat.compose.sweeteditor.CompletionContext,
                    receiver: com.qiplat.compose.sweeteditor.CompletionReceiver,
                ) {
                    assertEquals(CompletionTriggerKind.Invoked, context.triggerKind)
                    receiver.accept(
                        CompletionResult(
                            items = listOf(
                                CompletionItem(
                                    label = "println",
                                    insertText = "println",
                                ),
                            ),
                        ),
                    )
                }
            },
        )

        controller.triggerCompletion()
        kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeout(1_000) {
                while (controller.getCompletionResult() == null) {
                    kotlinx.coroutines.delay(10)
                }
            }
        }

        assertEquals(1, controller.getCompletionResult()?.items?.size)
        assertEquals(0, controller.getSelectedCompletionIndex())

        controller.applySelectedCompletionItem()

        assertEquals("println", editorBridge.lastReplacedText)
        assertEquals(null, controller.getCompletionResult())
    }

    @Test
    fun newLineActionProviderOverridesEnterInsertion() {
        val editorBridge = FakeNativeEditorBridge().apply {
            insertTextPayload = buildTextEditResultPayload()
        }
        val controller = SweetEditorController(
            textMeasurer = FakeEditorTextMeasurer(),
            state = EditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
        )
        controller.loadDocument(
            EditorDocument(
                FakeNativeDocumentBridge(
                    handle = 1L,
                    lines = listOf("    if (ready)"),
                ),
            ),
        )
        editorBridge.fakeCursorPosition = TextPosition(0, 14)
        controller.addNewLineActionProvider(
            NewLineActionProvider {
                NewLineAction("\n        ")
            },
        )

        controller.performNewLineAction()

        assertEquals("\n        ", editorBridge.lastInsertedText)
    }

    @Test
    fun snippetAndLinkedEditingApisDelegateToNativeBridge() {
        val editorBridge = FakeNativeEditorBridge().apply {
            insertSnippetPayload = buildTextEditResultPayload()
            linkedEditingActive = true
            linkedEditingNextResult = true
            linkedEditingPrevResult = false
        }
        val controller = SweetEditorController(
            textMeasurer = FakeEditorTextMeasurer(),
            state = EditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
        )
        controller.loadDocument(
            EditorDocument(
                FakeNativeDocumentBridge(
                    handle = 1L,
                    lines = listOf("value"),
                ),
            ),
        )
        val model = LinkedEditingModel(
            groups = listOf(
                TabStopGroup(
                    index = 1,
                    ranges = listOf(
                        TextRange(
                            start = TextPosition(0, 0),
                            end = TextPosition(0, 5),
                        ),
                    ),
                    defaultText = "value",
                ),
            ),
        )

        controller.insertSnippet("const ${'$'}1 = ${'$'}0")
        controller.startLinkedEditing(model)
        assertEquals(true, controller.isInLinkedEditing())
        val movedNext = controller.linkedEditingNext()
        val movedPrev = controller.linkedEditingPrev()
        controller.cancelLinkedEditing()

        assertEquals("const ${'$'}1 = ${'$'}0", editorBridge.lastInsertedSnippetTemplate)
        assertEquals(model, editorBridge.lastLinkedEditingModel)
        assertEquals(true, movedNext)
        assertEquals(false, movedPrev)
        assertEquals(1, editorBridge.linkedEditingCancelCount)
    }

    @Test
    fun linkedEditingSuppressesAndDismissesCompletion() {
        val editorBridge = FakeNativeEditorBridge().apply {
            linkedEditingActive = true
        }
        val controller = SweetEditorController(
            textMeasurer = FakeEditorTextMeasurer(),
            state = EditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
        )
        controller.loadDocument(
            EditorDocument(
                FakeNativeDocumentBridge(
                    handle = 1L,
                    lines = listOf("value"),
                ),
            ),
        )
        controller.showCompletionItems(
            listOf(
                CompletionItem(label = "ignored"),
            ),
        )

        assertEquals(null, controller.getCompletionResult())

        editorBridge.linkedEditingActive = false
        controller.showCompletionItems(
            listOf(
                CompletionItem(label = "visible"),
            ),
        )
        editorBridge.linkedEditingActive = true

        controller.triggerCompletion()
        controller.linkedEditingNext()

        assertEquals(null, controller.getCompletionResult())
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
    private val lines: List<String> = emptyList(),
) : NativeDocumentBridge {
    override fun getLineCount(): Int = lines.size

    override fun getLineText(line: Int): String = lines.getOrElse(line) { "" }

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
    var appliedFoldArrowMode: FoldArrowMode = FoldArrowMode.Auto
    var appliedWrapMode: WrapMode = WrapMode.None
    var appliedTabSize: Int = 4
    var lineSpacingAdd: Float = 0f
    var lineSpacingMult: Float = 1f
    var appliedCurrentLineRenderMode: CurrentLineRenderMode = CurrentLineRenderMode.Background
    var appliedGutterSticky: Boolean = true
    var appliedGutterVisible: Boolean = true
    var appliedReadOnly: Boolean = false
    var appliedCompositionEnabled: Boolean = true
    var appliedAutoIndentMode: AutoIndentMode = AutoIndentMode.None
    var fakeCursorPosition: TextPosition = TextPosition.Zero
    var selectionRange: TextRange? = null
    var wordRange: TextRange = TextRange()
    var replaceTextPayload: ByteArray? = null
    var insertSnippetPayload: ByteArray? = null
    var lastInsertedText: String? = null
    var lastReplacedText: String? = null
    var lastInsertedSnippetTemplate: String? = null
    var lastLinkedEditingModel: LinkedEditingModel? = null
    var linkedEditingActive: Boolean = false
    var linkedEditingNextResult: Boolean = false
    var linkedEditingPrevResult: Boolean = false
    var linkedEditingCancelCount: Int = 0

    override fun release() = Unit

    override fun setDocument(document: NativeDocumentBridge?) {
        documentHandle = document?.handle ?: 0L
    }

    override fun setViewport(width: Int, height: Int) = Unit
    override fun onFontMetricsChanged() = Unit
    override fun setFoldArrowMode(mode: FoldArrowMode) {
        appliedFoldArrowMode = mode
    }
    override fun setWrapMode(mode: WrapMode) {
        appliedWrapMode = mode
    }
    override fun setTabSize(tabSize: Int) {
        appliedTabSize = tabSize
    }
    override fun setScale(scale: Float) = Unit
    override fun setLineSpacing(add: Float, mult: Float) {
        lineSpacingAdd = add
        lineSpacingMult = mult
    }
    override fun setShowSplitLine(show: Boolean) = Unit
    override fun setCurrentLineRenderMode(mode: CurrentLineRenderMode) {
        appliedCurrentLineRenderMode = mode
    }
    override fun setGutterSticky(sticky: Boolean) {
        appliedGutterSticky = sticky
    }
    override fun setGutterVisible(visible: Boolean) {
        appliedGutterVisible = visible
    }
    override fun setReadOnly(readOnly: Boolean) {
        appliedReadOnly = readOnly
    }
    override fun isReadOnly(): Boolean = appliedReadOnly
    override fun setCompositionEnabled(enabled: Boolean) {
        appliedCompositionEnabled = enabled
    }
    override fun isCompositionEnabled(): Boolean = appliedCompositionEnabled
    override fun setAutoIndentMode(mode: AutoIndentMode) {
        appliedAutoIndentMode = mode
    }
    override fun getAutoIndentMode(): AutoIndentMode = appliedAutoIndentMode
    override fun setCursorPosition(position: TextPosition) {
        fakeCursorPosition = position
    }
    override fun setSelection(range: TextRange) {
        selectionRange = range
    }
    override fun getCursorPosition(): TextPosition = fakeCursorPosition
    override fun getSelection(): TextRange? = selectionRange
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
    override fun insertText(text: String): ByteArray? {
        lastInsertedText = text
        return insertTextPayload
    }
    override fun replaceText(range: TextRange, text: String): ByteArray? {
        lastReplacedText = text
        return replaceTextPayload
    }
    override fun deleteText(range: TextRange): ByteArray? = null
    override fun backspace(): ByteArray? = null
    override fun deleteForward(): ByteArray? = null
    override fun insertSnippet(template: String): ByteArray? {
        lastInsertedSnippetTemplate = template
        return insertSnippetPayload
    }
    override fun startLinkedEditing(model: LinkedEditingModel) {
        lastLinkedEditingModel = model
            linkedEditingActive = true
    }
    override fun isInLinkedEditing(): Boolean = linkedEditingActive
    override fun linkedEditingNext(): Boolean = linkedEditingNextResult
    override fun linkedEditingPrev(): Boolean = linkedEditingPrevResult
    override fun cancelLinkedEditing() {
        linkedEditingActive = false
        linkedEditingCancelCount += 1
    }
    override fun moveLineUp(): ByteArray? = null
    override fun moveLineDown(): ByteArray? = null
    override fun copyLineUp(): ByteArray? = null
    override fun copyLineDown(): ByteArray? = null
    override fun deleteLine(): ByteArray? = null
    override fun insertLineAbove(): ByteArray? = null
    override fun insertLineBelow(): ByteArray? = null
    override fun undo(): ByteArray? = null
    override fun redo(): ByteArray? = null
    override fun canUndo(): Boolean = false
    override fun canRedo(): Boolean = false
    override fun selectAll() = Unit
    override fun getSelectedText(): String? = null
    override fun getWordRangeAtCursor(): TextRange = wordRange
    override fun getWordAtCursor(): String? = null
    override fun moveCursorLeft(extendSelection: Boolean) = Unit
    override fun moveCursorRight(extendSelection: Boolean) = Unit
    override fun moveCursorUp(extendSelection: Boolean) = Unit
    override fun moveCursorDown(extendSelection: Boolean) = Unit
    override fun moveCursorToLineStart(extendSelection: Boolean) = Unit
    override fun moveCursorToLineEnd(extendSelection: Boolean) = Unit
    override fun scrollToLine(line: Int, behavior: ScrollBehavior) = Unit
    override fun gotoPosition(line: Int, column: Int) = Unit
    override fun setScroll(scrollX: Float, scrollY: Float) = Unit
    override fun getPositionRect(line: Int, column: Int) = CursorRect()
    override fun getCursorRect() = CursorRect()
    override fun registerBatchTextStyles(data: ByteArray) = Unit
    override fun setBatchLineSpans(data: ByteArray) = Unit
    override fun setBatchLineInlayHints(data: ByteArray) = Unit
    override fun setBatchLinePhantomTexts(data: ByteArray) = Unit
    override fun setBatchLineGutterIcons(data: ByteArray) = Unit
    override fun setBatchLineDiagnostics(data: ByteArray) = Unit
    override fun clearInlayHints() = Unit
    override fun clearPhantomTexts() = Unit
    override fun clearGutterIcons() = Unit
    override fun clearDiagnostics() = Unit
    override fun setIndentGuides(data: ByteArray) = Unit
    override fun setBracketGuides(data: ByteArray) = Unit
    override fun setFlowGuides(data: ByteArray) = Unit
    override fun setSeparatorGuides(data: ByteArray) = Unit
    override fun clearGuides() = Unit
    override fun setFoldRegions(data: ByteArray) = Unit
    override fun clearAllDecorations() = Unit
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
