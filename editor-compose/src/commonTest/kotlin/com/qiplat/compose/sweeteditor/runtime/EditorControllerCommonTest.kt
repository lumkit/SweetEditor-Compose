package com.qiplat.compose.sweeteditor.runtime

import androidx.compose.ui.graphics.Color
import com.qiplat.compose.sweeteditor.*
import com.qiplat.compose.sweeteditor.bridge.*
import com.qiplat.compose.sweeteditor.model.decoration.*
import com.qiplat.compose.sweeteditor.model.foundation.*
import com.qiplat.compose.sweeteditor.model.snippet.LinkedEditingModel
import com.qiplat.compose.sweeteditor.model.snippet.TabStopGroup
import com.qiplat.compose.sweeteditor.model.visual.CursorRect
import com.qiplat.compose.sweeteditor.protocol.BinaryWriter
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EditorControllerCommonTest {
    @Test
    fun setDocumentUpdatesStateAndBridge() {
        val editorBridge = FakeNativeEditorBridge()
        val controller = NativeEditorController(
            state = SweetEditorState(
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
        val controller = NativeEditorController(
            state = SweetEditorState(
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
        val controller = NativeEditorController(
            state = SweetEditorState(
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
        val controller = NativeEditorController(
            state = SweetEditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
            textMeasurer = FakeEditorTextMeasurer(),
        )

        controller.applySettings(
            SweetEditorSettings(
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
    fun applyDecorationBatchCoalescesUntilRefresh() {
        val editorBridge = FakeNativeEditorBridge()
        val controller = NativeEditorController(
            state = SweetEditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
            textMeasurer = FakeEditorTextMeasurer(),
        )
        val batch = DecorationBatch(
            spanStyles = mapOf(1 to SpanStyle(color = Color(0xFF00FF))),
            spansByLayer = mapOf(
                SpanLayer.Syntax to mapOf(
                    0 to listOf(StyleSpan(column = 0, length = 2, styleId = 1)),
                ),
            ),
        )

        controller.applyDecorationBatch(batch)
        controller.applyDecorationBatch(batch)

        assertEquals(0, editorBridge.registerBatchTextStylesCallCount)
        assertEquals(0, editorBridge.setBatchLineSpansCallCount)

        controller.refreshNow()

        assertEquals(1, editorBridge.registerBatchTextStylesCallCount)
        assertEquals(1, editorBridge.setBatchLineSpansCallCount)
        assertEquals(1, editorBridge.buildRenderModelCallCount)

        controller.applyDecorationBatch(batch)
        controller.refreshNow()

        assertEquals(1, editorBridge.registerBatchTextStylesCallCount)
        assertEquals(1, editorBridge.setBatchLineSpansCallCount)
        assertEquals(1, editorBridge.buildRenderModelCallCount)
    }

    @Test
    fun refreshNowReusesDecodedSnapshotsWhenNativePayloadIsUnchanged() {
        val editorBridge = FakeNativeEditorBridge().apply {
            renderModelPayload = buildRenderModelPayloadForControllerTest()
            scrollMetricsPayload = buildScrollMetricsPayloadForControllerTest()
        }
        val controller = NativeEditorController(
            state = SweetEditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
            textMeasurer = FakeEditorTextMeasurer(),
        )

        controller.refresh()
        controller.refreshNow()
        val firstRenderModel = requireNotNull(controller.state.renderModel)
        val firstScrollMetrics = controller.state.scrollMetrics

        controller.refresh()
        controller.refreshNow()

        assertSame(firstRenderModel, controller.state.renderModel)
        assertSame(firstScrollMetrics, controller.state.scrollMetrics)
        assertEquals(2, editorBridge.buildRenderModelCallCount)
        assertEquals(2, editorBridge.getScrollMetricsCallCount)
    }

    @Test
    fun refreshRequestsOnlyBumpVersionsOnDirtyEdge() {
        val editorBridge = FakeNativeEditorBridge()
        val controller = NativeEditorController(
            state = SweetEditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
            textMeasurer = FakeEditorTextMeasurer(),
        )

        controller.refresh()
        controller.refresh()
        controller.requestDecorationRefresh()
        controller.requestDecorationRefresh()

        assertEquals(1, controller.state.renderModelRequestVersion)
        assertEquals(1, controller.state.scrollMetricsRequestVersion)
        assertEquals(1, controller.state.decorationRequestVersion)
        assertEquals(1, controller.state.frameRefreshSignal)
        assertEquals(1, controller.state.decorationRefreshSignal)
    }

    @Test
    fun setViewportSkipsDuplicateSize() {
        val editorBridge = FakeNativeEditorBridge()
        val controller = NativeEditorController(
            state = SweetEditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
            textMeasurer = FakeEditorTextMeasurer(),
        )

        controller.setViewport(800, 600)
        controller.setViewport(800, 600)
        controller.setViewport(1024, 600)

        assertEquals(2, editorBridge.setViewportCallCount)
        assertEquals(1_024, editorBridge.lastViewportWidth)
        assertEquals(600, editorBridge.lastViewportHeight)
    }

    @Test
    fun ensureCursorVisibleDelegatesToNativeBridgeApi() {
        val editorBridge = FakeNativeEditorBridge()
        val controller = NativeEditorController(
            state = SweetEditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
            textMeasurer = FakeEditorTextMeasurer(),
        )

        controller.ensureCursorVisible()

        assertEquals(1, editorBridge.ensureCursorVisibleCallCount)
        assertEquals(0, editorBridge.gotoPositionCallCount)
    }

    @Test
    fun syncPlatformScaleUpdatesMeasurerAndNativeScale() {
        val editorBridge = FakeNativeEditorBridge()
        val textMeasurer = FakeEditorTextMeasurer()
        val controller = NativeEditorController(
            state = SweetEditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
            textMeasurer = textMeasurer,
        )

        controller.syncPlatformScale(1.5f)

        assertEquals(1.5f, textMeasurer.lastScale)
        assertEquals(1.5f, editorBridge.lastScale)
        assertEquals(1, editorBridge.onFontMetricsChangedCallCount)
    }

    @Test
    fun compositionUpdatesDoNotTriggerDecorationRefresh() {
        val editorBridge = FakeNativeEditorBridge()
        val controller = NativeEditorController(
            state = SweetEditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
            textMeasurer = FakeEditorTextMeasurer(),
        )

        controller.compositionStart()
        controller.compositionUpdate("hel")
        controller.compositionCancel()

        assertEquals(1, controller.state.renderModelRequestVersion)
        assertEquals(0, controller.state.scrollMetricsRequestVersion)
        assertEquals(0, controller.state.decorationRequestVersion)
        assertEquals(1, controller.state.frameRefreshSignal)
        assertEquals(0, controller.state.decorationRefreshSignal)
        assertEquals(1, editorBridge.compositionStartCallCount)
        assertEquals(listOf("hel"), editorBridge.compositionUpdates)
        assertEquals(1, editorBridge.compositionCancelCallCount)
    }

    @Test
    fun compositionEndStillTriggersDecorationRefresh() {
        val editorBridge = FakeNativeEditorBridge().apply {
            compositionEndPayload = buildTextEditResultPayload()
        }
        val controller = NativeEditorController(
            state = SweetEditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
            textMeasurer = FakeEditorTextMeasurer(),
        )

        controller.compositionEnd("ok")

        assertEquals(1, controller.state.renderModelRequestVersion)
        assertEquals(1, controller.state.scrollMetricsRequestVersion)
        assertEquals(1, controller.state.decorationRequestVersion)
        assertEquals("ok", editorBridge.lastCompositionCommittedText)
    }

    @Test
    fun compositionUpdateSkipsDuplicateNativeUpdate() {
        val editorBridge = FakeNativeEditorBridge()
        val controller = NativeEditorController(
            state = SweetEditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
            textMeasurer = FakeEditorTextMeasurer(),
        )

        controller.compositionStart()
        controller.compositionUpdate("same")
        controller.compositionUpdate("same")

        assertEquals(listOf("same"), editorBridge.compositionUpdates)
        assertEquals(1, controller.state.renderModelRequestVersion)
        assertEquals(1, controller.state.frameRefreshSignal)
    }

    @Test
    fun compositionUpdateAllowsSameTextAfterCompositionRestart() {
        val editorBridge = FakeNativeEditorBridge().apply {
            compositionEndPayload = buildTextEditResultPayload()
        }
        val controller = NativeEditorController(
            state = SweetEditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
            textMeasurer = FakeEditorTextMeasurer(),
        )

        controller.compositionStart()
        controller.compositionUpdate("same")
        controller.compositionEnd(null)
        controller.compositionStart()
        controller.compositionUpdate("same")

        assertEquals(listOf("same", "same"), editorBridge.compositionUpdates)
    }

    @Test
    fun setCompositionEnabledSkipsDuplicateNativeCall() {
        val editorBridge = FakeNativeEditorBridge()
        val controller = NativeEditorController(
            state = SweetEditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
            textMeasurer = FakeEditorTextMeasurer(),
        )

        controller.setCompositionEnabled(false)
        controller.setCompositionEnabled(false)

        assertEquals(1, editorBridge.setCompositionEnabledCallCount)
        assertEquals(1, controller.state.renderModelRequestVersion)
        assertEquals(1, controller.state.frameRefreshSignal)
    }

    @Test
    fun applyDecorationBatchOnlyFlushesChangedCategories() {
        val editorBridge = FakeNativeEditorBridge()
        val controller = NativeEditorController(
            state = SweetEditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
            textMeasurer = FakeEditorTextMeasurer(),
        )
        val indentGuide = IndentGuide(
            start = TextPosition(1, 4),
            end = TextPosition(8, 4),
        )
        val firstBatch = DecorationBatch(
            indentGuides = listOf(indentGuide),
        )
        val secondBatch = DecorationBatch(
            indentGuides = listOf(indentGuide),
            diagnostics = mapOf(
                2 to listOf(DiagnosticItem(column = 0, length = 4, severity = DiagnosticSeverity.Warning)),
            ),
        )

        controller.applyDecorationBatch(firstBatch)
        controller.refreshNow()
        controller.applyDecorationBatch(secondBatch)
        controller.refreshNow()

        assertEquals(1, editorBridge.setIndentGuidesCallCount)
        assertEquals(2, editorBridge.setBatchLineDiagnosticsCallCount)
    }

    @Test
    fun applyDecorationBatchReusesEncodedPayloadAfterInvalidation() {
        val editorBridge = FakeNativeEditorBridge()
        val controller = NativeEditorController(
            state = SweetEditorState(
                bridgeFactory = FakeNativeBridgeFactory(editorBridge),
            ),
            textMeasurer = FakeEditorTextMeasurer(),
        )
        val batch = DecorationBatch(
            spanStyles = mapOf(1 to SpanStyle(color = Color(0xFF00FF))),
            spansByLayer = mapOf(
                SpanLayer.Syntax to mapOf(
                    0 to listOf(StyleSpan(column = 0, length = 2, styleId = 1)),
                ),
            ),
        )

        controller.applyDecorationBatch(batch)
        controller.refreshNow()
        controller.clearAllDecorations()
        controller.applyDecorationBatch(batch)
        controller.refreshNow()

        assertEquals(2, editorBridge.registerBatchTextStylesCallCount)
        assertEquals(2, editorBridge.setBatchLineSpansCallCount)
        assertSame(
            editorBridge.registerBatchTextStylesPayloads[0],
            editorBridge.registerBatchTextStylesPayloads[1],
        )
        assertSame(
            editorBridge.setBatchLineSpansPayloads[0],
            editorBridge.setBatchLineSpansPayloads[1],
        )
    }

    @Test
    fun individualSettingSettersUpdateQueryableState() {
        val editorBridge = FakeNativeEditorBridge()
        val controller = NativeEditorController(
            state = SweetEditorState(
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
            state = SweetEditorState(
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
        GlobalScope.launch {
            withTimeout(1_000) {
                while (controller.getCompletionResult() == null) {
                    delay(10)
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
            state = SweetEditorState(
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
    fun snippetCompletionItemUsesSnippetInsertionAndReplacementRange() {
        val editorBridge = FakeNativeEditorBridge().apply {
            insertSnippetPayload = buildTextEditResultPayload()
            fakeCursorPosition = TextPosition(0, 3)
            wordRange = TextRange(
                start = TextPosition(0, 0),
                end = TextPosition(0, 3),
            )
        }
        val controller = SweetEditorController(
            textMeasurer = FakeEditorTextMeasurer(),
            state = SweetEditorState(
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

        controller.showCompletionItems(
            listOf(
                CompletionItem(
                    label = "println",
                    insertText = "println(${ '$' }1)",
                    insertTextFormat = CompletionItem.SNIPPET,
                ),
            ),
        )

        controller.applySelectedCompletionItem()

        assertEquals("println(${ '$' }1)", editorBridge.lastInsertedSnippetTemplate)
        assertEquals(
            TextRange(
                start = TextPosition(0, 0),
                end = TextPosition(0, 3),
            ),
            editorBridge.selectionRange,
        )
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
            state = SweetEditorState(
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
            state = SweetEditorState(
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

    @Test
    fun linkedEditingEnterCancelsSessionBeforeNewLineInsertion() {
        val editorBridge = FakeNativeEditorBridge().apply {
            linkedEditingActive = true
            insertTextPayload = buildTextEditResultPayload()
        }
        val controller = SweetEditorController(
            textMeasurer = FakeEditorTextMeasurer(),
            state = SweetEditorState(
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
        controller.addNewLineActionProvider(
            NewLineActionProvider {
                NewLineAction("\n  ")
            },
        )

        controller.handleEnterAction()

        assertEquals(false, controller.isInLinkedEditing())
        assertEquals(1, editorBridge.linkedEditingCancelCount)
        assertEquals("\n  ", editorBridge.lastInsertedText)
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
    var buildRenderModelCallCount: Int = 0
    var getScrollMetricsCallCount: Int = 0
    var registerBatchTextStylesCallCount: Int = 0
    var setBatchLineSpansCallCount: Int = 0
    val registerBatchTextStylesPayloads = mutableListOf<ByteArray>()
    val setBatchLineSpansPayloads = mutableListOf<ByteArray>()
    var renderModelPayload: ByteArray? = null
    var scrollMetricsPayload: ByteArray? = null
    var setViewportCallCount: Int = 0
    var lastViewportWidth: Int = 0
    var lastViewportHeight: Int = 0
    var ensureCursorVisibleCallCount: Int = 0
    var gotoPositionCallCount: Int = 0
    var lastScale: Float = 1f
    var onFontMetricsChangedCallCount: Int = 0
    var setBatchLineDiagnosticsCallCount: Int = 0
    var setIndentGuidesCallCount: Int = 0
    var compositionStartCallCount: Int = 0
    val compositionUpdates = mutableListOf<String>()
    var compositionCancelCallCount: Int = 0
    var lastCompositionCommittedText: String? = null
    var compositionEndPayload: ByteArray? = null
    var composing: Boolean = false
    var setCompositionEnabledCallCount: Int = 0
    var appliedScrollbarConfig: NativeScrollbarConfig? = null
    var appliedHandleConfig: NativeHandleConfig? = null

    override fun release() = Unit

    override fun setDocument(document: NativeDocumentBridge?) {
        documentHandle = document?.handle ?: 0L
    }

    override fun setViewport(width: Int, height: Int) {
        setViewportCallCount += 1
        lastViewportWidth = width
        lastViewportHeight = height
    }
    override fun onFontMetricsChanged() {
        onFontMetricsChangedCallCount += 1
    }
    override fun setFoldArrowMode(mode: FoldArrowMode) {
        appliedFoldArrowMode = mode
    }
    override fun setWrapMode(mode: WrapMode) {
        appliedWrapMode = mode
    }
    override fun setTabSize(tabSize: Int) {
        appliedTabSize = tabSize
    }
    override fun setScale(scale: Float) {
        lastScale = scale
    }
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
    override fun setHandleConfig(config: NativeHandleConfig) {
        appliedHandleConfig = config
    }
    override fun setScrollbarConfig(config: NativeScrollbarConfig) {
        appliedScrollbarConfig = config
    }
    override fun setReadOnly(readOnly: Boolean) {
        appliedReadOnly = readOnly
    }
    override fun isReadOnly(): Boolean = appliedReadOnly
    override fun setCompositionEnabled(enabled: Boolean) {
        setCompositionEnabledCallCount += 1
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
    override fun buildRenderModel(): ByteArray? {
        buildRenderModelCallCount += 1
        return renderModelPayload
    }
    override fun getScrollMetrics(): ByteArray? {
        getScrollMetricsCallCount += 1
        return scrollMetricsPayload
    }
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
    override fun compositionStart() {
        compositionStartCallCount += 1
        composing = true
    }
    override fun compositionUpdate(text: String) {
        compositionUpdates += text
    }
    override fun compositionEnd(committedText: String?): ByteArray? {
        lastCompositionCommittedText = committedText
        composing = false
        return compositionEndPayload
    }
    override fun compositionCancel() {
        compositionCancelCallCount += 1
        composing = false
    }
    override fun isComposing(): Boolean = composing
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
    override fun gotoPosition(line: Int, column: Int) {
        gotoPositionCallCount += 1
    }
    override fun ensureCursorVisible() {
        ensureCursorVisibleCallCount += 1
    }
    override fun setScroll(scrollX: Float, scrollY: Float) = Unit
    override fun getPositionRect(line: Int, column: Int) = CursorRect()
    override fun getCursorRect() = CursorRect()
    override fun registerBatchTextStyles(data: ByteArray) {
        registerBatchTextStylesCallCount += 1
        registerBatchTextStylesPayloads += data
    }
    override fun setBatchLineSpans(data: ByteArray) {
        setBatchLineSpansCallCount += 1
        setBatchLineSpansPayloads += data
    }
    override fun setBatchLineInlayHints(data: ByteArray) = Unit
    override fun setBatchLinePhantomTexts(data: ByteArray) = Unit
    override fun setBatchLineGutterIcons(data: ByteArray) = Unit
    override fun setBatchLineDiagnostics(data: ByteArray) {
        setBatchLineDiagnosticsCallCount += 1
    }
    override fun clearInlayHints() = Unit
    override fun clearPhantomTexts() = Unit
    override fun clearGutterIcons() = Unit
    override fun clearDiagnostics() = Unit
    override fun setIndentGuides(data: ByteArray) {
        setIndentGuidesCallCount += 1
    }
    override fun setBracketGuides(data: ByteArray) = Unit
    override fun setFlowGuides(data: ByteArray) = Unit
    override fun setSeparatorGuides(data: ByteArray) = Unit
    override fun clearGuides() = Unit
    override fun setFoldRegions(data: ByteArray) = Unit
    override fun clearAllDecorations() = Unit
    override fun setMaxGutterIcons(count: Int) = Unit
}

private class FakeEditorTextMeasurer : EditorTextMeasurer {
    var lastScale: Float = 1f

    override fun setScale(scale: Float) {
        lastScale = scale
    }

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

private fun buildRenderModelPayloadForControllerTest(): ByteArray {
    val writer = BinaryWriter()
    writer.writeFloat(1f)
    writer.writeBooleanAsInt(true)
    writer.writeFloat(2f)
    writer.writeFloat(3f)
    writer.writeFloat(100f)
    writer.writeFloat(200f)
    writer.writeFloat(4f)
    writer.writeFloat(5f)
    writer.writeInt(0)
    writer.writeInt(0)
    writer.writeInt(0)
    writer.writeInt(0)
    writer.writeInt(0)
    writer.writeInt(0)
    writer.writeFloat(10f)
    writer.writeFloat(20f)
    writer.writeFloat(18f)
    writer.writeBooleanAsInt(true)
    writer.writeBooleanAsInt(false)
    writer.writeInt(0)
    repeat(2) {
        writer.writeFloat(0f)
        writer.writeFloat(0f)
        writer.writeFloat(0f)
        writer.writeBooleanAsInt(false)
    }
    writer.writeBooleanAsInt(false)
    writer.writeFloat(0f)
    writer.writeFloat(0f)
    writer.writeFloat(0f)
    writer.writeFloat(0f)
    writer.writeInt(0)
    writer.writeInt(0)
    writer.writeInt(0)
    writer.writeInt(0)
    writer.writeInt(0)
    writer.writeBooleanAsInt(false)
    writer.writeFloat(0f)
    repeat(2) {
        writer.writeFloat(0f)
        writer.writeFloat(0f)
        writer.writeFloat(0f)
        writer.writeFloat(0f)
    }
    writer.writeBooleanAsInt(false)
    writer.writeFloat(0f)
    repeat(2) {
        writer.writeFloat(0f)
        writer.writeFloat(0f)
        writer.writeFloat(0f)
        writer.writeFloat(0f)
    }
    writer.writeBooleanAsInt(true)
    writer.writeBooleanAsInt(true)
    writer.writeInt(0)
    return writer.toByteArray()
}

private fun buildScrollMetricsPayloadForControllerTest(): ByteArray {
    val writer = BinaryWriter()
    writer.writeFloat(1f)
    writer.writeFloat(2f)
    writer.writeFloat(3f)
    writer.writeFloat(10f)
    writer.writeFloat(20f)
    writer.writeFloat(300f)
    writer.writeFloat(400f)
    writer.writeFloat(500f)
    writer.writeFloat(600f)
    writer.writeFloat(16f)
    writer.writeFloat(484f)
    writer.writeBooleanAsInt(true)
    writer.writeBooleanAsInt(true)
    return writer.toByteArray()
}
