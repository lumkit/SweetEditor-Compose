@file:OptIn(ExperimentalForeignApi::class)

package com.qiplat.compose.sweeteditor.bridge

import com.qiplat.compose.sweeteditor.nativeinterop.*
import kotlinx.cinterop.*

@kotlin.native.concurrent.ThreadLocal
private object IosMeasurerRegistry {
    val measurers: MutableMap<Long, NativeTextMeasurer> = mutableMapOf()
    var currentEditorHandle: Long = 0L
    var pendingMeasurer: NativeTextMeasurer? = null
}

internal object IosNativeBindings {
    fun nativeCreateDocumentFromUtf16(text: String): Long =
        text.withUtf16NullTerminated { utf16Pointer ->
            create_document_from_utf16(utf16Pointer).toLong()
        }

    fun nativeCreateDocumentFromFile(path: String): Long =
        create_document_from_file(path).toLong()

    fun nativeFreeDocument(handle: Long) {
        if (handle == 0L) {
            return
        }
        free_document(handle)
    }

    fun nativeGetDocumentLineCount(handle: Long): Int =
        get_document_line_count(handle).toInt()

    fun nativeGetDocumentLineText(handle: Long, line: Int): String =
        get_document_line_utf16(handle, line.toULong())
            ?.toKStringFromUtf16()
            .orEmpty()

    fun nativeCreateEditor(textMeasurer: NativeTextMeasurer, optionsData: ByteArray): Long = memScoped {
        val callbacks = alloc<text_measurer_t>()
        callbacks.measure_text_width = staticCFunction(::measureTextWidthCallback)
        callbacks.measure_inlay_hint_width = staticCFunction(::measureInlayHintWidthCallback)
        callbacks.measure_icon_width = staticCFunction(::measureIconWidthCallback)
        callbacks.get_font_metrics = staticCFunction(::getFontMetricsCallback)
        val handle = withEditorContext(
            editorHandle = IosMeasurerRegistry.currentEditorHandle,
            pendingMeasurer = textMeasurer,
        ) {
            optionsData.usePinned { optionsPinned ->
                val optionsPointer = if (optionsData.isEmpty()) null else optionsPinned.addressOf(0).reinterpret<UByteVar>()
                create_editor(
                    callbacks.readValue(),
                    optionsPointer,
                    optionsData.size.toULong(),
                ).toLong()
            }
        }
        if (handle != 0L) {
            IosMeasurerRegistry.measurers[handle] = textMeasurer
        }
        handle
    }

    fun nativeFreeEditor(handle: Long) {
        if (handle == 0L) {
            return
        }
        withEditorContext(handle) {
            free_editor(handle)
        }
        IosMeasurerRegistry.measurers.remove(handle)
    }

    fun nativeSetEditorDocument(editorHandle: Long, documentHandle: Long) {
        withEditorContext(editorHandle) {
            set_editor_document(editorHandle, documentHandle)
        }
    }

    fun nativeSetEditorViewport(editorHandle: Long, width: Int, height: Int) {
        withEditorContext(editorHandle) {
            set_editor_viewport(editorHandle, width.toShort(), height.toShort())
        }
    }

    fun nativeOnFontMetricsChanged(editorHandle: Long) {
        withEditorContext(editorHandle) {
            editor_on_font_metrics_changed(editorHandle)
        }
    }

    fun nativeSetFoldArrowMode(editorHandle: Long, mode: Int) {
        withEditorContext(editorHandle) {
            editor_set_fold_arrow_mode(editorHandle, mode)
        }
    }

    fun nativeSetWrapMode(editorHandle: Long, mode: Int) {
        withEditorContext(editorHandle) {
            editor_set_wrap_mode(editorHandle, mode)
        }
    }

    fun nativeSetTabSize(editorHandle: Long, tabSize: Int) {
        withEditorContext(editorHandle) {
            editor_set_tab_size(editorHandle, tabSize)
        }
    }

    fun nativeSetScale(editorHandle: Long, scale: Float) {
        withEditorContext(editorHandle) {
            editor_set_scale(editorHandle, scale)
        }
    }

    fun nativeSetLineSpacing(editorHandle: Long, add: Float, mult: Float) {
        withEditorContext(editorHandle) {
            editor_set_line_spacing(editorHandle, add, mult)
        }
    }

    fun nativeSetShowSplitLine(editorHandle: Long, show: Boolean) {
        withEditorContext(editorHandle) {
            editor_set_show_split_line(editorHandle, if (show) 1 else 0)
        }
    }

    fun nativeSetCurrentLineRenderMode(editorHandle: Long, mode: Int) {
        withEditorContext(editorHandle) {
            editor_set_current_line_render_mode(editorHandle, mode)
        }
    }

    fun nativeSetGutterSticky(editorHandle: Long, sticky: Boolean) {
        withEditorContext(editorHandle) {
            editor_set_gutter_sticky(editorHandle, if (sticky) 1 else 0)
        }
    }

    fun nativeSetGutterVisible(editorHandle: Long, visible: Boolean) {
        withEditorContext(editorHandle) {
            editor_set_gutter_visible(editorHandle, if (visible) 1 else 0)
        }
    }

    fun nativeSetReadOnly(editorHandle: Long, readOnly: Boolean) {
        withEditorContext(editorHandle) {
            editor_set_read_only(editorHandle, if (readOnly) 1 else 0)
        }
    }

    fun nativeIsReadOnly(editorHandle: Long): Boolean =
        withEditorContext(editorHandle) {
            editor_is_read_only(editorHandle) != 0
        }

    fun nativeSetCompositionEnabled(editorHandle: Long, enabled: Boolean) {
        withEditorContext(editorHandle) {
            editor_set_composition_enabled(editorHandle, if (enabled) 1 else 0)
        }
    }

    fun nativeIsCompositionEnabled(editorHandle: Long): Boolean =
        withEditorContext(editorHandle) {
            editor_is_composition_enabled(editorHandle) != 0
        }

    fun nativeSetAutoIndentMode(editorHandle: Long, mode: Int) {
        withEditorContext(editorHandle) {
            editor_set_auto_indent_mode(editorHandle, mode)
        }
    }

    fun nativeGetAutoIndentMode(editorHandle: Long): Int =
        withEditorContext(editorHandle) {
            editor_get_auto_indent_mode(editorHandle)
        }

    fun nativeSetCursorPosition(editorHandle: Long, line: Int, column: Int) {
        withEditorContext(editorHandle) {
            editor_set_cursor_position(editorHandle, line.toULong(), column.toULong())
        }
    }

    fun nativeSetSelection(
        editorHandle: Long,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
    ) {
        withEditorContext(editorHandle) {
            editor_set_selection(
                editorHandle,
                startLine.toULong(),
                startColumn.toULong(),
                endLine.toULong(),
                endColumn.toULong(),
            )
        }
    }

    fun nativeGetCursorPosition(editorHandle: Long): IntArray = memScoped {
        withEditorContext(editorHandle) {
            val outLine = alloc<ULongVar>()
            val outColumn = alloc<ULongVar>()
            editor_get_cursor_position(editorHandle, outLine.ptr, outColumn.ptr)
            intArrayOf(outLine.value.toInt(), outColumn.value.toInt())
        }
    }

    fun nativeGetSelection(editorHandle: Long): IntArray? = memScoped {
        withEditorContext(editorHandle) {
            val outStartLine = alloc<ULongVar>()
            val outStartColumn = alloc<ULongVar>()
            val outEndLine = alloc<ULongVar>()
            val outEndColumn = alloc<ULongVar>()
            val hasSelection = editor_get_selection(
                editorHandle,
                outStartLine.ptr,
                outStartColumn.ptr,
                outEndLine.ptr,
                outEndColumn.ptr,
            )
            if (hasSelection == 0) {
                null
            } else {
                intArrayOf(
                    outStartLine.value.toInt(),
                    outStartColumn.value.toInt(),
                    outEndLine.value.toInt(),
                    outEndColumn.value.toInt(),
                )
            }
        }
    }

    fun nativeBuildRenderModel(editorHandle: Long): ByteArray? =
        withBinaryPayload(editorHandle) { outSize ->
            build_editor_render_model(editorHandle, outSize)
        }

    fun nativeGetScrollMetrics(editorHandle: Long): ByteArray? =
        withBinaryPayload(editorHandle) { outSize ->
            editor_get_scroll_metrics(editorHandle, outSize)
        }

    fun nativeHandleGesture(
        editorHandle: Long,
        type: Int,
        points: FloatArray,
        modifiers: Int,
        wheelDeltaX: Float,
        wheelDeltaY: Float,
        directScale: Float,
    ): ByteArray? = withBinaryPayload(editorHandle) { outSize ->
        points.usePinned { pointsPinned ->
            val pointsPointer = if (points.isEmpty()) null else pointsPinned.addressOf(0)
            handle_editor_gesture_event_ex(
                editorHandle,
                type.toUByte(),
                (points.size / 2).toUByte(),
                pointsPointer,
                modifiers.toUByte(),
                wheelDeltaX,
                wheelDeltaY,
                directScale,
                outSize,
            )
        }
    }

    fun nativeTickAnimations(editorHandle: Long): ByteArray? =
        withBinaryPayload(editorHandle) { outSize ->
            editor_tick_animations(editorHandle, outSize)
        }

    fun nativeHandleKeyEvent(
        editorHandle: Long,
        keyCode: Int,
        text: String?,
        modifiers: Int,
    ): ByteArray? = withBinaryPayload(editorHandle) { outSize ->
        handle_editor_key_event(
            editorHandle,
            keyCode.toUShort(),
            text,
            modifiers.toUByte(),
            outSize,
        )
    }

    fun nativeCompositionStart(editorHandle: Long) {
        withEditorContext(editorHandle) {
            editor_composition_start(editorHandle)
        }
    }

    fun nativeCompositionUpdate(editorHandle: Long, text: String) {
        withEditorContext(editorHandle) {
            editor_composition_update(editorHandle, text)
        }
    }

    fun nativeCompositionEnd(editorHandle: Long, committedText: String?): ByteArray? =
        withBinaryPayload(editorHandle) { outSize ->
            editor_composition_end(editorHandle, committedText, outSize)
        }

    fun nativeCompositionCancel(editorHandle: Long) {
        withEditorContext(editorHandle) {
            editor_composition_cancel(editorHandle)
        }
    }

    fun nativeIsComposing(editorHandle: Long): Boolean =
        withEditorContext(editorHandle) {
            editor_is_composing(editorHandle) != 0
        }

    fun nativeInsertText(editorHandle: Long, text: String): ByteArray? =
        withBinaryPayload(editorHandle) { outSize ->
            editor_insert_text(editorHandle, text, outSize)
        }

    fun nativeReplaceText(
        editorHandle: Long,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
        text: String,
    ): ByteArray? = withBinaryPayload(editorHandle) { outSize ->
        editor_replace_text(
            editorHandle,
            startLine.toULong(),
            startColumn.toULong(),
            endLine.toULong(),
            endColumn.toULong(),
            text,
            outSize,
        )
    }

    fun nativeDeleteText(
        editorHandle: Long,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
    ): ByteArray? = withBinaryPayload(editorHandle) { outSize ->
        editor_delete_text(
            editorHandle,
            startLine.toULong(),
            startColumn.toULong(),
            endLine.toULong(),
            endColumn.toULong(),
            outSize,
        )
    }

    fun nativeBackspace(editorHandle: Long): ByteArray? =
        withBinaryPayload(editorHandle) { outSize ->
            editor_backspace(editorHandle, outSize)
        }

    fun nativeDeleteForward(editorHandle: Long): ByteArray? =
        withBinaryPayload(editorHandle) { outSize ->
            editor_delete_forward(editorHandle, outSize)
        }

    fun nativeInsertSnippet(editorHandle: Long, template: String): ByteArray? =
        withBinaryPayload(editorHandle) { outSize ->
            editor_insert_snippet(editorHandle, template, outSize)
        }

    fun nativeStartLinkedEditing(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            data.withByteArrayPointer { dataPointer, dataSize ->
                editor_start_linked_editing(editorHandle, dataPointer, dataSize)
            }
        }
    }

    fun nativeIsInLinkedEditing(editorHandle: Long): Boolean =
        withEditorContext(editorHandle) {
            editor_is_in_linked_editing(editorHandle) != 0
        }

    fun nativeLinkedEditingNext(editorHandle: Long): Boolean =
        withEditorContext(editorHandle) {
            editor_linked_editing_next(editorHandle) != 0
        }

    fun nativeLinkedEditingPrev(editorHandle: Long): Boolean =
        withEditorContext(editorHandle) {
            editor_linked_editing_prev(editorHandle) != 0
        }

    fun nativeCancelLinkedEditing(editorHandle: Long) {
        withEditorContext(editorHandle) {
            editor_cancel_linked_editing(editorHandle)
        }
    }

    fun nativeMoveLineUp(editorHandle: Long): ByteArray? =
        withBinaryPayload(editorHandle) { outSize ->
            editor_move_line_up(editorHandle, outSize)
        }

    fun nativeMoveLineDown(editorHandle: Long): ByteArray? =
        withBinaryPayload(editorHandle) { outSize ->
            editor_move_line_down(editorHandle, outSize)
        }

    fun nativeCopyLineUp(editorHandle: Long): ByteArray? =
        withBinaryPayload(editorHandle) { outSize ->
            editor_copy_line_up(editorHandle, outSize)
        }

    fun nativeCopyLineDown(editorHandle: Long): ByteArray? =
        withBinaryPayload(editorHandle) { outSize ->
            editor_copy_line_down(editorHandle, outSize)
        }

    fun nativeDeleteLine(editorHandle: Long): ByteArray? =
        withBinaryPayload(editorHandle) { outSize ->
            editor_delete_line(editorHandle, outSize)
        }

    fun nativeInsertLineAbove(editorHandle: Long): ByteArray? =
        withBinaryPayload(editorHandle) { outSize ->
            editor_insert_line_above(editorHandle, outSize)
        }

    fun nativeInsertLineBelow(editorHandle: Long): ByteArray? =
        withBinaryPayload(editorHandle) { outSize ->
            editor_insert_line_below(editorHandle, outSize)
        }

    fun nativeUndo(editorHandle: Long): ByteArray? =
        withBinaryPayload(editorHandle) { outSize ->
            editor_undo(editorHandle, outSize)
        }

    fun nativeRedo(editorHandle: Long): ByteArray? =
        withBinaryPayload(editorHandle) { outSize ->
            editor_redo(editorHandle, outSize)
        }

    fun nativeCanUndo(editorHandle: Long): Boolean =
        withEditorContext(editorHandle) {
            editor_can_undo(editorHandle) != 0
        }

    fun nativeCanRedo(editorHandle: Long): Boolean =
        withEditorContext(editorHandle) {
            editor_can_redo(editorHandle) != 0
        }

    fun nativeSelectAll(editorHandle: Long) {
        withEditorContext(editorHandle) {
            editor_select_all(editorHandle)
        }
    }

    fun nativeGetSelectedText(editorHandle: Long): String? =
        withEditorContext(editorHandle) {
            editor_get_selected_text(editorHandle)?.toKString()
        }

    fun nativeGetWordRangeAtCursor(editorHandle: Long): IntArray = memScoped {
        withEditorContext(editorHandle) {
            val outStartLine = alloc<ULongVar>()
            val outStartColumn = alloc<ULongVar>()
            val outEndLine = alloc<ULongVar>()
            val outEndColumn = alloc<ULongVar>()
            editor_get_word_range_at_cursor(
                editorHandle,
                outStartLine.ptr,
                outStartColumn.ptr,
                outEndLine.ptr,
                outEndColumn.ptr,
            )
            intArrayOf(
                outStartLine.value.toInt(),
                outStartColumn.value.toInt(),
                outEndLine.value.toInt(),
                outEndColumn.value.toInt(),
            )
        }
    }

    fun nativeGetWordAtCursor(editorHandle: Long): String? =
        withEditorContext(editorHandle) {
            editor_get_word_at_cursor(editorHandle)?.toKString()
        }

    fun nativeMoveCursorLeft(editorHandle: Long, extendSelection: Boolean) {
        withEditorContext(editorHandle) {
            editor_move_cursor_left(editorHandle, if (extendSelection) 1 else 0)
        }
    }

    fun nativeMoveCursorRight(editorHandle: Long, extendSelection: Boolean) {
        withEditorContext(editorHandle) {
            editor_move_cursor_right(editorHandle, if (extendSelection) 1 else 0)
        }
    }

    fun nativeMoveCursorUp(editorHandle: Long, extendSelection: Boolean) {
        withEditorContext(editorHandle) {
            editor_move_cursor_up(editorHandle, if (extendSelection) 1 else 0)
        }
    }

    fun nativeMoveCursorDown(editorHandle: Long, extendSelection: Boolean) {
        withEditorContext(editorHandle) {
            editor_move_cursor_down(editorHandle, if (extendSelection) 1 else 0)
        }
    }

    fun nativeMoveCursorToLineStart(editorHandle: Long, extendSelection: Boolean) {
        withEditorContext(editorHandle) {
            editor_move_cursor_to_line_start(editorHandle, if (extendSelection) 1 else 0)
        }
    }

    fun nativeMoveCursorToLineEnd(editorHandle: Long, extendSelection: Boolean) {
        withEditorContext(editorHandle) {
            editor_move_cursor_to_line_end(editorHandle, if (extendSelection) 1 else 0)
        }
    }

    fun nativeScrollToLine(editorHandle: Long, line: Int, behavior: Int) {
        withEditorContext(editorHandle) {
            editor_scroll_to_line(editorHandle, line.toULong(), behavior.toUByte())
        }
    }

    fun nativeGotoPosition(editorHandle: Long, line: Int, column: Int) {
        withEditorContext(editorHandle) {
            editor_goto_position(editorHandle, line.toULong(), column.toULong())
        }
    }

    fun nativeSetScroll(editorHandle: Long, scrollX: Float, scrollY: Float) {
        withEditorContext(editorHandle) {
            editor_set_scroll(editorHandle, scrollX, scrollY)
        }
    }

    fun nativeGetPositionRect(editorHandle: Long, line: Int, column: Int): FloatArray = memScoped {
        withEditorContext(editorHandle) {
            val outX = alloc<FloatVar>()
            val outY = alloc<FloatVar>()
            val outHeight = alloc<FloatVar>()
            editor_get_position_rect(
                editorHandle,
                line.toULong(),
                column.toULong(),
                outX.ptr,
                outY.ptr,
                outHeight.ptr,
            )
            floatArrayOf(outX.value, outY.value, outHeight.value)
        }
    }

    fun nativeGetCursorRect(editorHandle: Long): FloatArray = memScoped {
        withEditorContext(editorHandle) {
            val outX = alloc<FloatVar>()
            val outY = alloc<FloatVar>()
            val outHeight = alloc<FloatVar>()
            editor_get_cursor_rect(editorHandle, outX.ptr, outY.ptr, outHeight.ptr)
            floatArrayOf(outX.value, outY.value, outHeight.value)
        }
    }

    fun nativeRegisterBatchTextStyles(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            data.withByteArrayPointer { dataPointer, dataSize ->
                editor_register_batch_text_styles(editorHandle, dataPointer, dataSize)
            }
        }
    }

    fun nativeSetBatchLineSpans(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            data.withByteArrayPointer { dataPointer, dataSize ->
                editor_set_batch_line_spans(editorHandle, dataPointer, dataSize)
            }
        }
    }

    fun nativeSetBatchLineInlayHints(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            data.withByteArrayPointer { dataPointer, dataSize ->
                editor_set_batch_line_inlay_hints(editorHandle, dataPointer, dataSize)
            }
        }
    }

    fun nativeSetBatchLinePhantomTexts(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            data.withByteArrayPointer { dataPointer, dataSize ->
                editor_set_batch_line_phantom_texts(editorHandle, dataPointer, dataSize)
            }
        }
    }

    fun nativeSetBatchLineGutterIcons(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            data.withByteArrayPointer { dataPointer, dataSize ->
                editor_set_batch_line_gutter_icons(editorHandle, dataPointer, dataSize)
            }
        }
    }

    fun nativeSetBatchLineDiagnostics(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            data.withByteArrayPointer { dataPointer, dataSize ->
                editor_set_batch_line_diagnostics(editorHandle, dataPointer, dataSize)
            }
        }
    }

    fun nativeClearInlayHints(editorHandle: Long) {
        withEditorContext(editorHandle) {
            editor_clear_inlay_hints(editorHandle)
        }
    }

    fun nativeClearPhantomTexts(editorHandle: Long) {
        withEditorContext(editorHandle) {
            editor_clear_phantom_texts(editorHandle)
        }
    }

    fun nativeClearGutterIcons(editorHandle: Long) {
        withEditorContext(editorHandle) {
            editor_clear_gutter_icons(editorHandle)
        }
    }

    fun nativeClearDiagnostics(editorHandle: Long) {
        withEditorContext(editorHandle) {
            editor_clear_diagnostics(editorHandle)
        }
    }

    fun nativeSetIndentGuides(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            data.withByteArrayPointer { dataPointer, dataSize ->
                editor_set_indent_guides(editorHandle, dataPointer, dataSize)
            }
        }
    }

    fun nativeSetBracketGuides(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            data.withByteArrayPointer { dataPointer, dataSize ->
                editor_set_bracket_guides(editorHandle, dataPointer, dataSize)
            }
        }
    }

    fun nativeSetFlowGuides(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            data.withByteArrayPointer { dataPointer, dataSize ->
                editor_set_flow_guides(editorHandle, dataPointer, dataSize)
            }
        }
    }

    fun nativeSetSeparatorGuides(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            data.withByteArrayPointer { dataPointer, dataSize ->
                editor_set_separator_guides(editorHandle, dataPointer, dataSize)
            }
        }
    }

    fun nativeClearGuides(editorHandle: Long) {
        withEditorContext(editorHandle) {
            editor_clear_guides(editorHandle)
        }
    }

    fun nativeSetFoldRegions(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            data.withByteArrayPointer { dataPointer, dataSize ->
                editor_set_fold_regions(editorHandle, dataPointer, dataSize)
            }
        }
    }

    fun nativeClearAllDecorations(editorHandle: Long) {
        withEditorContext(editorHandle) {
            editor_clear_all_decorations(editorHandle)
        }
    }

    fun nativeSetMaxGutterIcons(editorHandle: Long, count: Int) {
        withEditorContext(editorHandle) {
            editor_set_max_gutter_icons(editorHandle, count.toUInt())
        }
    }
}

private inline fun <T> withEditorContext(
    editorHandle: Long,
    pendingMeasurer: NativeTextMeasurer? = null,
    block: () -> T,
): T {
    val previousEditorHandle = IosMeasurerRegistry.currentEditorHandle
    val previousPendingMeasurer = IosMeasurerRegistry.pendingMeasurer
    IosMeasurerRegistry.currentEditorHandle = editorHandle
    IosMeasurerRegistry.pendingMeasurer = pendingMeasurer
    return try {
        block()
    } finally {
        IosMeasurerRegistry.currentEditorHandle = previousEditorHandle
        IosMeasurerRegistry.pendingMeasurer = previousPendingMeasurer
    }
}

private inline fun withBinaryPayload(
    editorHandle: Long,
    loader: (CPointer<ULongVar>) -> CPointer<UByteVar>?,
): ByteArray? = memScoped {
    withEditorContext(editorHandle) {
        val outSize = alloc<ULongVar>()
        val data = loader(outSize.ptr)
        val size = outSize.value.toInt()
        if (data == null || size == 0) {
            if (data != null) {
                free_binary_data(data.rawValue.toLong())
            }
            null
        } else {
            val bytes = ByteArray(size) { index -> data[index].toByte() }
            free_binary_data(data.rawValue.toLong())
            bytes
        }
    }
}

private inline fun ByteArray.withByteArrayPointer(
    block: (CPointer<UByteVar>?, ULong) -> Unit,
) {
    if (isEmpty()) {
        block(null, 0u)
        return
    }
    usePinned { pinned ->
        block(pinned.addressOf(0).reinterpret(), size.toULong())
    }
}

private inline fun <T> String.withUtf16NullTerminated(block: (CPointer<UShortVar>) -> T): T {
    val utf16 = UShortArray(length + 1)
    for (index in indices) {
        utf16[index] = this[index].code.toUShort()
    }
    utf16[length] = 0u
    return utf16.usePinned { pinned ->
        block(pinned.addressOf(0))
    }
}

private fun CPointer<UShortVar>.toKStringFromUtf16(): String {
    val chars = ArrayList<Char>()
    var index = 0
    while (true) {
        val value = this[index].toInt()
        if (value == 0) {
            break
        }
        chars.add(value.toChar())
        index += 1
    }
    return buildString(chars.size) {
        chars.forEach(::append)
    }
}

private fun resolveCurrentMeasurer(): NativeTextMeasurer? =
    IosMeasurerRegistry.measurers[IosMeasurerRegistry.currentEditorHandle]
        ?: IosMeasurerRegistry.pendingMeasurer

private fun measureTextWidthCallback(text: CPointer<UShortVar>?, fontStyle: Int): Float {
    val measurer = resolveCurrentMeasurer() ?: return 0f
    val value = text?.toKStringFromUtf16().orEmpty()
    return measurer.measureTextWidth(value, fontStyle)
}

private fun measureInlayHintWidthCallback(text: CPointer<UShortVar>?): Float {
    val measurer = resolveCurrentMeasurer() ?: return 0f
    val value = text?.toKStringFromUtf16().orEmpty()
    return measurer.measureInlayHintWidth(value)
}

private fun measureIconWidthCallback(iconId: Int): Float {
    val measurer = resolveCurrentMeasurer() ?: return 0f
    return measurer.measureIconWidth(iconId)
}

private fun getFontMetricsCallback(values: CPointer<FloatVar>?, length: ULong) {
    val size = length.toInt()
    if (values == null || size <= 0) {
        return
    }
    for (index in 0 until size) {
        values[index] = 0f
    }
    val measurer = resolveCurrentMeasurer() ?: return
    val metrics = measurer.getFontMetrics()
    val copyLength = minOf(size, metrics.size)
    for (index in 0 until copyLength) {
        values[index] = metrics[index]
    }
}
