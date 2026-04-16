package com.qiplat.compose.sweeteditor.bridge

import com.qiplat.compose.sweeteditor.core.EditorNative
import java.lang.foreign.Arena
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles

internal object DesktopNativeBindings {
    init {
        // EditorNative will automatically load the native library
    }

    // Map to store measurer references for each editor instance
    private val editorMeasurers = mutableMapOf<Long, NativeTextMeasurer>()

    // Map to store arena references for each editor instance (to keep callbacks alive)
    private val editorArenas = mutableMapOf<Long, Arena>()

    // Current editor handle to allow callbacks to find the correct measurer
    @Volatile
    @JvmStatic
    var currentEditorHandle: Long = 0L

    // Static measurer reference for fallback (for older implementations)
    @Volatile
    @JvmStatic
    var currentMeasurer: NativeTextMeasurer? = null

    /**
     * Helper function to resolve the current measurer for the active editor
     * This mirrors the C++ get_current_measurer() function behavior with an important difference:
     * In C++, get_current_measurer() only uses g_current_editor_handle to look up in g_measurers.
     * In JVM, we also fall back to currentMeasurer to handle cases where the editor is being created
     * or when the context hasn't been set yet (e.g., during editor initialization).
     */
    private fun resolveCurrentMeasurer(): NativeTextMeasurer? {
        // First try to find the measurer by currentEditorHandle (matches C++ behavior)
        val measurerByHandle = editorMeasurers[currentEditorHandle]
        if (measurerByHandle != null) {
            return measurerByHandle
        }

        // Fall back to currentMeasurer for cases where the editor is being created
        // or when the context hasn't been set yet (e.g., during editor initialization)
        // This is necessary because JVM cannot set the context before create_editor returns
        return currentMeasurer
    }

    /**
     * Context function similar to iOS/Android implementation to ensure proper editor context
     */
    private inline fun <T> withEditorContext(editorHandle: Long, measurer: NativeTextMeasurer? = null, block: () -> T): T {
        val previousEditorHandle = currentEditorHandle
        val previousMeasurer = currentMeasurer
        currentEditorHandle = editorHandle
        if (measurer != null) {
            currentMeasurer = measurer
        }
        return try {
            block()
        } finally {
            currentEditorHandle = previousEditorHandle
            if (measurer != null) {
                currentMeasurer = previousMeasurer
            }
        }
    }

    // Static callback functions that will be called from native code
    // These functions attempt to find the appropriate measurer for the current editor
    @JvmStatic
    fun measureTextWidthCallback(textPtr: MemorySegment, fontStyle: Int): Float {
        val measurer = resolveCurrentMeasurer() ?:
        // If no measurer is set, return 0 to indicate measurement failure
        // This will cause rendering issues but prevents crashes
        return 0f

        if (textPtr == MemorySegment.NULL) return 0f
        val text = EditorNative.readUtf16String(textPtr) ?: ""
        return measurer.measureTextWidth(text, fontStyle)
    }

    @JvmStatic
    fun measureInlayHintWidthCallback(textPtr: MemorySegment): Float {
        val measurer = resolveCurrentMeasurer() ?:
        // If no measurer is set, return 0 to indicate measurement failure
        return 0f

        if (textPtr == MemorySegment.NULL) return 0f
        val text = EditorNative.readUtf16String(textPtr) ?: ""
        return measurer.measureInlayHintWidth(text)
    }

    @JvmStatic
    fun measureIconWidthCallback(iconId: Int): Float {
        val measurer = resolveCurrentMeasurer() ?:
        // If no measurer is set, return 0 to indicate measurement failure
        return 0f

        return measurer.measureIconWidth(iconId)
    }

    @JvmStatic
    fun getFontMetricsCallback(outPtr: MemorySegment, length: Long) {
        val measurer = resolveCurrentMeasurer() ?:
        // If no measurer is set, return without writing to avoid corruption
        return

        if (outPtr == MemorySegment.NULL || length <= 0) return

        // When called from native callbacks, the pointer may have byteSize=0
        // We should use the length parameter as the guide for how much to write
        try {
            val actualSize = outPtr.byteSize()

            // Determine effective size: if byteSize is 0 (raw pointer from callback),
            // use length * sizeof(float) as the effective size
            val effectiveSize = if (actualSize == 0L) {
                length * ValueLayout.JAVA_FLOAT.byteSize()
            } else {
                actualSize
            }

            val metrics = measurer.getFontMetrics()
            val maxFloats = (effectiveSize / ValueLayout.JAVA_FLOAT.byteSize()).toInt()
            val copyLength = minOf(minOf(length.toInt(), metrics.size), maxFloats)

            // Reinterpret the pointer with effective size if needed
            val targetPtr = if (actualSize == 0L) {
                outPtr.reinterpret(effectiveSize)
            } else {
                outPtr
            }

            for (i in 0 until copyLength) {
                targetPtr.set(ValueLayout.JAVA_FLOAT, i * ValueLayout.JAVA_FLOAT.byteSize(), metrics[i])
            }
            // Fill remaining with zeros
            for (i in copyLength until minOf(length.toInt(), maxFloats)) {
                targetPtr.set(ValueLayout.JAVA_FLOAT, i * ValueLayout.JAVA_FLOAT.byteSize(), 0f)
            }
        } catch (_: Exception) {
            // Catch any memory access exceptions
            return
        }
    }

    fun nativeCreateDocumentFromUtf16(text: String): Long =
        Arena.ofConfined().use { arena ->
            EditorNative.createDocument(arena, text)
        }

    fun nativeCreateDocumentFromFile(path: String): Long =
        Arena.ofConfined().use { arena ->
            EditorNative.createDocumentFromFile(arena, path)
        }

    fun nativeFreeDocument(handle: Long) {
        if (handle == 0L) {
            return
        }
        EditorNative.freeDocument(handle)
    }

    fun nativeGetDocumentLineCount(handle: Long): Int =
        EditorNative.getDocumentLineCount(handle).toInt()

    fun nativeGetDocumentLineText(handle: Long, line: Int): String =
        EditorNative.getDocumentLineText(handle, line)

    fun nativeCreateEditor(textMeasurer: NativeTextMeasurer, optionsData: ByteArray): Long {
        // Use shared arena to keep callbacks alive
        val arena = Arena.ofShared()
        try {
            // Create measurer callbacks - these will use the global state when called
            val measurerSegment = createMeasurerCallbacks(arena)

            // Store the measurer in a temporary location for callbacks during creation
            val previousMeasurer = currentMeasurer
            currentMeasurer = textMeasurer

            // Create the editor with the measurer segment
            // During creation, callbacks will use currentMeasurer (which we just set)
            val handle = EditorNative.createEditor(measurerSegment, optionsData, arena)

            // Restore previous measurer
            currentMeasurer = previousMeasurer

            if (handle != 0L) {
                // Store the measurer and arena for this editor instance
                editorMeasurers[handle] = textMeasurer
                editorArenas[handle] = arena
            } else {
                // If editor creation failed, close the arena
                arena.close()
            }

            return handle
        } catch (e: Exception) {
            arena.close()
            throw e
        }
    }

    fun nativeFreeEditor(handle: Long) {
        if (handle == 0L) {
            return
        }
        withEditorContext(handle) {
            EditorNative.freeEditor(handle)
        }

        // Remove the measurer for this editor instance
        editorMeasurers.remove(handle)

        // Close and remove the arena for this editor instance
        editorArenas.remove(handle)?.close()
    }

    fun nativeSetEditorDocument(editorHandle: Long, documentHandle: Long) {
        withEditorContext(editorHandle) {
            EditorNative.setEditorDocument(editorHandle, documentHandle)
        }
    }

    fun nativeSetEditorViewport(editorHandle: Long, width: Int, height: Int) {
        withEditorContext(editorHandle) {
            EditorNative.setViewport(editorHandle, width, height)
        }
    }

    fun nativeOnFontMetricsChanged(editorHandle: Long) {
        withEditorContext(editorHandle) {
            EditorNative.onFontMetricsChanged(editorHandle)
        }
    }

    fun nativeSetFoldArrowMode(editorHandle: Long, mode: Int) {
        EditorNative.setFoldArrowMode(editorHandle, mode)
    }

    fun nativeSetWrapMode(editorHandle: Long, mode: Int) {
        withEditorContext(editorHandle) {
            EditorNative.setWrapMode(editorHandle, mode)
        }
    }

    fun nativeSetTabSize(editorHandle: Long, tabSize: Int) {
        withEditorContext(editorHandle) {
            EditorNative.setTabSize(editorHandle, tabSize)
        }
    }

    fun nativeSetScale(editorHandle: Long, scale: Float) {
        withEditorContext(editorHandle) {
            EditorNative.setScale(editorHandle, scale)
        }
    }

    fun nativeSetLineSpacing(editorHandle: Long, add: Float, mult: Float) {
        withEditorContext(editorHandle) {
            EditorNative.setLineSpacing(editorHandle, add, mult)
        }
    }

    fun nativeSetShowSplitLine(editorHandle: Long, show: Boolean) {
        withEditorContext(editorHandle) {
            EditorNative.setShowSplitLine(editorHandle, show)
        }
    }

    fun nativeSetCurrentLineRenderMode(editorHandle: Long, mode: Int) {
        withEditorContext(editorHandle) {
            EditorNative.setCurrentLineRenderMode(editorHandle, mode)
        }
    }

    fun nativeSetGutterSticky(editorHandle: Long, sticky: Boolean) {
        withEditorContext(editorHandle) {
            EditorNative.setGutterSticky(editorHandle, sticky)
        }
    }

    fun nativeSetGutterVisible(editorHandle: Long, visible: Boolean) {
        withEditorContext(editorHandle) {
            EditorNative.setGutterVisible(editorHandle, visible)
        }
    }

    fun nativeSetHandleConfig(
        editorHandle: Long,
        startLeft: Float,
        startTop: Float,
        startRight: Float,
        startBottom: Float,
        endLeft: Float,
        endTop: Float,
        endRight: Float,
        endBottom: Float,
    ) {
        withEditorContext(editorHandle) {
            EditorNative.setHandleConfig(
                editorHandle = editorHandle,
                startLeft = startLeft,
                startTop = startTop,
                startRight = startRight,
                startBottom = startBottom,
                endLeft = endLeft,
                endTop = endTop,
                endRight = endRight,
                endBottom = endBottom,
            )
        }
    }

    fun nativeSetScrollbarConfig(
        editorHandle: Long,
        thickness: Float,
        minThumb: Float,
        thumbHitPadding: Float,
        mode: Int,
        thumbDraggable: Boolean,
        trackTapMode: Int,
        fadeDelayMillis: Int,
        fadeDurationMillis: Int,
    ) {
        withEditorContext(editorHandle) {
            EditorNative.setScrollbarConfig(
                editorHandle = editorHandle,
                thickness = thickness,
                minThumb = minThumb,
                thumbHitPadding = thumbHitPadding,
                mode = mode,
                thumbDraggable = thumbDraggable,
                trackTapMode = trackTapMode,
                fadeDelayMillis = fadeDelayMillis,
                fadeDurationMillis = fadeDurationMillis,
            )
        }
    }

    fun nativeSetReadOnly(editorHandle: Long, readOnly: Boolean) {
        withEditorContext(editorHandle) {
            EditorNative.setReadOnly(editorHandle, readOnly)
        }
    }

    fun nativeIsReadOnly(editorHandle: Long): Boolean =
        withEditorContext(editorHandle) {
            EditorNative.isReadOnly(editorHandle)
        }

    fun nativeSetCompositionEnabled(editorHandle: Long, enabled: Boolean) {
        withEditorContext(editorHandle) {
            EditorNative.setCompositionEnabled(editorHandle, enabled)
        }
    }

    fun nativeIsCompositionEnabled(editorHandle: Long): Boolean =
        withEditorContext(editorHandle) {
            EditorNative.isCompositionEnabled(editorHandle)
        }

    fun nativeSetAutoIndentMode(editorHandle: Long, mode: Int) {
        withEditorContext(editorHandle) {
            EditorNative.setAutoIndentMode(editorHandle, mode)
        }
    }

    fun nativeGetAutoIndentMode(editorHandle: Long): Int =
        withEditorContext(editorHandle) {
            EditorNative.getAutoIndentMode(editorHandle)
        }

    fun nativeSetCursorPosition(editorHandle: Long, line: Int, column: Int) {
        withEditorContext(editorHandle) {
            EditorNative.setCursorPosition(editorHandle, line, column)
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
            EditorNative.setSelection(editorHandle, startLine, startColumn, endLine, endColumn)
        }
    }

    fun nativeGetCursorPosition(editorHandle: Long): IntArray =
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                EditorNative.getCursorPosition(editorHandle, arena)
            }
        }

    fun nativeGetSelection(editorHandle: Long): IntArray? =
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                EditorNative.getSelection(editorHandle, arena)
            }
        }

    fun nativeBuildRenderModel(editorHandle: Long): ByteArray? =
        withEditorContext(editorHandle) {
            val result = EditorNative.buildRenderModel(editorHandle)
            try {
                if (result.hasData()) {
                    val buffer = result.asByteBuffer()
                    if (buffer != null) {
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        bytes
                    } else {
                        null
                    }
                } else {
                    null
                }
            } finally {
                result.free()
            }
        }

    fun nativeGetScrollMetrics(editorHandle: Long): ByteArray? =
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                val result = EditorNative.getScrollMetrics(editorHandle, arena)
                try {
                    if (result.hasData()) {
                        val buffer = result.asByteBuffer()
                        if (buffer != null) {
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            bytes
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } finally {
                    result.free()
                }
            }
        }

    fun nativeHandleGesture(
        editorHandle: Long,
        type: Int,
        points: FloatArray,
        modifiers: Int,
        wheelDeltaX: Float,
        wheelDeltaY: Float,
        directScale: Float,
    ): ByteArray? =
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                val result = EditorNative.handleGestureEventEx(
                    editorHandle, type, points.size / 2, arena, points,
                    modifiers, wheelDeltaX, wheelDeltaY, directScale
                )
                try {
                    if (result.hasData()) {
                        val buffer = result.asByteBuffer()
                        if (buffer != null) {
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            bytes
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } finally {
                    result.free()
                }
            }
        }

    fun nativeHandleKeyEvent(
        editorHandle: Long,
        keyCode: Int,
        text: String?,
        modifiers: Int,
    ): ByteArray? =
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                val result = EditorNative.handleKeyEvent(editorHandle, keyCode, text, modifiers, arena)
                try {
                    if (result.hasData()) {
                        val buffer = result.asByteBuffer()
                        if (buffer != null) {
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            bytes
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } finally {
                    result.free()
                }
            }
        }

    fun nativeInsertText(editorHandle: Long, text: String): ByteArray? =
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                val result = EditorNative.insertText(editorHandle, text, arena)
                try {
                    if (result.hasData()) {
                        val buffer = result.asByteBuffer()
                        if (buffer != null) {
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            bytes
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } finally {
                    result.free()
                }
            }
        }

    fun nativeReplaceText(
        editorHandle: Long,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
        text: String,
    ): ByteArray? =
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                val result = EditorNative.replaceText(editorHandle, startLine, startColumn, endLine, endColumn, text, arena)
                try {
                    if (result.hasData()) {
                        val buffer = result.asByteBuffer()
                        if (buffer != null) {
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            bytes
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } finally {
                    result.free()
                }
            }
        }

    fun nativeDeleteText(
        editorHandle: Long,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
    ): ByteArray? =
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                val result = EditorNative.deleteText(editorHandle, startLine, startColumn, endLine, endColumn, arena)
                try {
                    if (result.hasData()) {
                        val buffer = result.asByteBuffer()
                        if (buffer != null) {
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            bytes
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } finally {
                    result.free()
                }
            }
        }

    fun nativeBackspace(editorHandle: Long): ByteArray? =
        withEditorContext(editorHandle) {
            val result = EditorNative.backspace(editorHandle)
            try {
                if (result.hasData()) {
                    val buffer = result.asByteBuffer()
                    if (buffer != null) {
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        bytes
                    } else {
                        null
                    }
                } else {
                    null
                }
            } finally {
                result.free()
            }
        }

    fun nativeDeleteForward(editorHandle: Long): ByteArray? =
        withEditorContext(editorHandle) {
            val result = EditorNative.deleteForward(editorHandle)
            try {
                if (result.hasData()) {
                    val buffer = result.asByteBuffer()
                    if (buffer != null) {
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        bytes
                    } else {
                        null
                    }
                } else {
                    null
                }
            } finally {
                result.free()
            }
        }

    fun nativeInsertSnippet(editorHandle: Long, template: String): ByteArray? =
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                val result = EditorNative.insertSnippet(editorHandle, template, arena)
                try {
                    if (result.hasData()) {
                        val buffer = result.asByteBuffer()
                        if (buffer != null) {
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            bytes
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } finally {
                    result.free()
                }
            }
        }

    fun nativeStartLinkedEditing(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                EditorNative.startLinkedEditing(editorHandle, data, arena)
            }
        }
    }

    fun nativeIsInLinkedEditing(editorHandle: Long): Boolean =
        withEditorContext(editorHandle) {
            EditorNative.isInLinkedEditing(editorHandle)
        }

    fun nativeLinkedEditingNext(editorHandle: Long): Boolean =
        withEditorContext(editorHandle) {
            EditorNative.linkedEditingNext(editorHandle)
        }

    fun nativeLinkedEditingPrev(editorHandle: Long): Boolean =
        withEditorContext(editorHandle) {
            EditorNative.linkedEditingPrev(editorHandle)
        }

    fun nativeCancelLinkedEditing(editorHandle: Long) {
        withEditorContext(editorHandle) {
            EditorNative.cancelLinkedEditing(editorHandle)
        }
    }

    fun nativeMoveLineUp(editorHandle: Long): ByteArray? =
        withEditorContext(editorHandle) {
            val result = EditorNative.moveLineUp(editorHandle)
            try {
                if (result.hasData()) {
                    val buffer = result.asByteBuffer()
                    if (buffer != null) {
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        bytes
                    } else {
                        null
                    }
                } else {
                    null
                }
            } finally {
                result.free()
            }
        }

    fun nativeMoveLineDown(editorHandle: Long): ByteArray? =
        withEditorContext(editorHandle) {
            val result = EditorNative.moveLineDown(editorHandle)
            try {
                if (result.hasData()) {
                    val buffer = result.asByteBuffer()
                    if (buffer != null) {
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        bytes
                    } else {
                        null
                    }
                } else {
                    null
                }
            } finally {
                result.free()
            }
        }

    fun nativeCopyLineUp(editorHandle: Long): ByteArray? =
        withEditorContext(editorHandle) {
            val result = EditorNative.copyLineUp(editorHandle)
            try {
                if (result.hasData()) {
                    val buffer = result.asByteBuffer()
                    if (buffer != null) {
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        bytes
                    } else {
                        null
                    }
                } else {
                    null
                }
            } finally {
                result.free()
            }
        }

    fun nativeCopyLineDown(editorHandle: Long): ByteArray? =
        withEditorContext(editorHandle) {
            val result = EditorNative.copyLineDown(editorHandle)
            try {
                if (result.hasData()) {
                    val buffer = result.asByteBuffer()
                    if (buffer != null) {
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        bytes
                    } else {
                        null
                    }
                } else {
                    null
                }
            } finally {
                result.free()
            }
        }

    fun nativeDeleteLine(editorHandle: Long): ByteArray? =
        withEditorContext(editorHandle) {
            val result = EditorNative.deleteLine(editorHandle)
            try {
                if (result.hasData()) {
                    val buffer = result.asByteBuffer()
                    if (buffer != null) {
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        bytes
                    } else {
                        null
                    }
                } else {
                    null
                }
            } finally {
                result.free()
            }
        }

    fun nativeInsertLineAbove(editorHandle: Long): ByteArray? =
        withEditorContext(editorHandle) {
            val result = EditorNative.insertLineAbove(editorHandle)
            try {
                if (result.hasData()) {
                    val buffer = result.asByteBuffer()
                    if (buffer != null) {
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        bytes
                    } else {
                        null
                    }
                } else {
                    null
                }
            } finally {
                result.free()
            }
        }

    fun nativeInsertLineBelow(editorHandle: Long): ByteArray? =
        withEditorContext(editorHandle) {
            val result = EditorNative.insertLineBelow(editorHandle)
            try {
                if (result.hasData()) {
                    val buffer = result.asByteBuffer()
                    if (buffer != null) {
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        bytes
                    } else {
                        null
                    }
                } else {
                    null
                }
            } finally {
                result.free()
            }
        }

    fun nativeUndo(editorHandle: Long): ByteArray? =
        withEditorContext(editorHandle) {
            val result = EditorNative.undo(editorHandle)
            try {
                if (result.hasData()) {
                    val buffer = result.asByteBuffer()
                    if (buffer != null) {
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        bytes
                    } else {
                        null
                    }
                } else {
                    null
                }
            } finally {
                result.free()
            }
        }

    fun nativeRedo(editorHandle: Long): ByteArray? =
        withEditorContext(editorHandle) {
            val result = EditorNative.redo(editorHandle)
            try {
                if (result.hasData()) {
                    val buffer = result.asByteBuffer()
                    if (buffer != null) {
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        bytes
                    } else {
                        null
                    }
                } else {
                    null
                }
            } finally {
                result.free()
            }
        }

    fun nativeCanUndo(editorHandle: Long): Boolean =
        withEditorContext(editorHandle) {
            EditorNative.canUndo(editorHandle)
        }

    fun nativeCanRedo(editorHandle: Long): Boolean =
        withEditorContext(editorHandle) {
            EditorNative.canRedo(editorHandle)
        }

    fun nativeSelectAll(editorHandle: Long) {
        withEditorContext(editorHandle) {
            EditorNative.selectAll(editorHandle)
        }
    }

    fun nativeGetSelectedText(editorHandle: Long): String =
        withEditorContext(editorHandle) {
            EditorNative.getSelectedText(editorHandle)
        }

    fun nativeGetWordRangeAtCursor(editorHandle: Long): IntArray =
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                EditorNative.getWordRangeAtCursor(editorHandle, arena)
            }
        }

    fun nativeGetWordAtCursor(editorHandle: Long): String =
        withEditorContext(editorHandle) {
            EditorNative.getWordAtCursor(editorHandle)
        }

    fun nativeMoveCursorLeft(editorHandle: Long, extendSelection: Boolean) {
        withEditorContext(editorHandle) {
            EditorNative.moveCursorLeft(editorHandle, extendSelection)
        }
    }

    fun nativeMoveCursorRight(editorHandle: Long, extendSelection: Boolean) {
        withEditorContext(editorHandle) {
            EditorNative.moveCursorRight(editorHandle, extendSelection)
        }
    }

    fun nativeMoveCursorUp(editorHandle: Long, extendSelection: Boolean) {
        withEditorContext(editorHandle) {
            EditorNative.moveCursorUp(editorHandle, extendSelection)
        }
    }

    fun nativeMoveCursorDown(editorHandle: Long, extendSelection: Boolean) {
        withEditorContext(editorHandle) {
            EditorNative.moveCursorDown(editorHandle, extendSelection)
        }
    }

    fun nativeMoveCursorToLineStart(editorHandle: Long, extendSelection: Boolean) {
        withEditorContext(editorHandle) {
            EditorNative.moveCursorToLineStart(editorHandle, extendSelection)
        }
    }

    fun nativeMoveCursorToLineEnd(editorHandle: Long, extendSelection: Boolean) {
        withEditorContext(editorHandle) {
            EditorNative.moveCursorToLineEnd(editorHandle, extendSelection)
        }
    }

    fun nativeScrollToLine(editorHandle: Long, line: Int, behavior: Int) {
        withEditorContext(editorHandle) {
            EditorNative.scrollToLine(editorHandle, line, behavior)
        }
    }

    fun nativeGotoPosition(editorHandle: Long, line: Int, column: Int) {
        withEditorContext(editorHandle) {
            EditorNative.gotoPosition(editorHandle, line, column)
        }
    }

    fun nativeEnsureCursorVisible(editorHandle: Long) {
        withEditorContext(editorHandle) {
            EditorNative.ensureCursorVisible(editorHandle)
        }
    }

    fun nativeSetScroll(editorHandle: Long, scrollX: Float, scrollY: Float) {
        withEditorContext(editorHandle) {
            EditorNative.setScroll(editorHandle, scrollX, scrollY)
        }
    }

    fun nativeGetPositionRect(editorHandle: Long, line: Int, column: Int): FloatArray =
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                EditorNative.getPositionRect(editorHandle, line, column, arena)
            }
        }

    fun nativeGetCursorRect(editorHandle: Long): FloatArray =
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                EditorNative.getCursorRect(editorHandle, arena)
            }
        }

    fun nativeRegisterBatchTextStyles(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                EditorNative.registerBatchTextStyles(editorHandle, data, arena)
            }
        }
    }

    fun nativeSetBatchLineSpans(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                EditorNative.setBatchLineSpans(editorHandle, data, arena)
            }
        }
    }

    fun nativeSetBatchLineInlayHints(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                EditorNative.setBatchLineInlayHints(editorHandle, data, arena)
            }
        }
    }

    fun nativeSetBatchLinePhantomTexts(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                EditorNative.setBatchLinePhantomTexts(editorHandle, data, arena)
            }
        }
    }

    fun nativeSetBatchLineGutterIcons(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                EditorNative.setBatchLineGutterIcons(editorHandle, data, arena)
            }
        }
    }

    fun nativeSetBatchLineDiagnostics(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                EditorNative.setBatchLineDiagnostics(editorHandle, data, arena)
            }
        }
    }

    fun nativeClearInlayHints(editorHandle: Long) {
        withEditorContext(editorHandle) {
            EditorNative.clearInlayHints(editorHandle)
        }
    }

    fun nativeClearPhantomTexts(editorHandle: Long) {
        withEditorContext(editorHandle) {
            EditorNative.clearPhantomTexts(editorHandle)
        }
    }

    fun nativeClearGutterIcons(editorHandle: Long) {
        withEditorContext(editorHandle) {
            EditorNative.clearGutterIcons(editorHandle)
        }
    }

    fun nativeClearDiagnostics(editorHandle: Long) {
        withEditorContext(editorHandle) {
            EditorNative.clearDiagnostics(editorHandle)
        }
    }

    fun nativeSetIndentGuides(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                EditorNative.setIndentGuides(editorHandle, data, arena)
            }
        }
    }

    fun nativeSetBracketGuides(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                EditorNative.setBracketGuides(editorHandle, data, arena)
            }
        }
    }

    fun nativeSetFlowGuides(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                EditorNative.setFlowGuides(editorHandle, data, arena)
            }
        }
    }

    fun nativeSetSeparatorGuides(editorHandle: Long, data: ByteArray) {
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                EditorNative.setSeparatorGuides(editorHandle, data, arena)
            }
        }
    }

    fun nativeClearGuides(editorHandle: Long) {
        withEditorContext(editorHandle) {
            EditorNative.clearGuides(editorHandle)
        }
    }

    fun nativeSetFoldRegions(editorHandle: Long, data: ByteArray) {
        Arena.ofConfined().use { arena ->
            EditorNative.setFoldRegions(editorHandle, data, arena)
        }
    }

    fun nativeClearAllDecorations(editorHandle: Long) {
        withEditorContext(editorHandle) {
            EditorNative.clearAllDecorations(editorHandle)
        }
    }

    fun nativeSetMaxGutterIcons(editorHandle: Long, count: Int) {
        withEditorContext(editorHandle) {
            EditorNative.setMaxGutterIcons(editorHandle, count)
        }
    }

    fun nativeTickAnimations(editorHandle: Long): ByteArray? =
        withEditorContext(editorHandle) {
            val result = EditorNative.tickAnimations(editorHandle)
            try {
                if (result.hasData()) {
                    val buffer = result.asByteBuffer()
                    if (buffer != null) {
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        bytes
                    } else {
                        null
                    }
                } else {
                    null
                }
            } finally {
                result.free()
            }
        }

    fun nativeCompositionStart(editorHandle: Long) {
        withEditorContext(editorHandle) {
            EditorNative.compositionStart(editorHandle)
        }
    }

    fun nativeCompositionUpdate(editorHandle: Long, text: String) {
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                EditorNative.compositionUpdate(editorHandle, text, arena)
            }
        }
    }

    fun nativeCompositionEnd(editorHandle: Long, text: String?): ByteArray? =
        withEditorContext(editorHandle) {
            Arena.ofConfined().use { arena ->
                val result = EditorNative.compositionEnd(editorHandle, text, arena)
                try {
                    if (result.hasData()) {
                        val buffer = result.asByteBuffer()
                        if (buffer != null) {
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            bytes
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } finally {
                    result.free()
                }
            }
        }

    fun nativeCompositionCancel(editorHandle: Long) {
        withEditorContext(editorHandle) {
            EditorNative.compositionCancel(editorHandle)
        }
    }

    fun nativeIsComposing(editorHandle: Long): Boolean =
        withEditorContext(editorHandle) {
            EditorNative.isComposing(editorHandle)
        }

    // Helper function to create measurer callbacks
    private fun createMeasurerCallbacks(arena: Arena): MemorySegment {
        // We no longer set currentMeasurer here since callbacks will use editorMeasurers mapping
        // The callback functions will use resolveCurrentMeasurer() which looks up by currentEditorHandle

        // Create upcall stubs using static method handles with findStatic
        val measureTextWidthHandle = MethodHandles.lookup().findStatic(
            DesktopNativeBindings::class.java,
            "measureTextWidthCallback",
            java.lang.invoke.MethodType.methodType(
                java.lang.Float.TYPE,
                MemorySegment::class.java,
                Integer.TYPE
            )
        )
        val measureTextWidthStub = Linker.nativeLinker().upcallStub(
            measureTextWidthHandle,
            EditorNative.MEASURE_TEXT_WIDTH_DESC,
            arena
        )

        val measureInlayHintWidthHandle = MethodHandles.lookup().findStatic(
            DesktopNativeBindings::class.java,
            "measureInlayHintWidthCallback",
            java.lang.invoke.MethodType.methodType(
                java.lang.Float.TYPE,
                MemorySegment::class.java
            )
        )
        val measureInlayHintWidthStub = Linker.nativeLinker().upcallStub(
            measureInlayHintWidthHandle,
            EditorNative.MEASURE_INLAY_HINT_WIDTH_DESC,
            arena
        )

        val measureIconWidthHandle = MethodHandles.lookup().findStatic(
            DesktopNativeBindings::class.java,
            "measureIconWidthCallback",
            java.lang.invoke.MethodType.methodType(
                java.lang.Float.TYPE,
                Integer.TYPE
            )
        )
        val measureIconWidthStub = Linker.nativeLinker().upcallStub(
            measureIconWidthHandle,
            EditorNative.MEASURE_ICON_WIDTH_DESC,
            arena
        )

        val getFontMetricsHandle = MethodHandles.lookup().findStatic(
            DesktopNativeBindings::class.java,
            "getFontMetricsCallback",
            java.lang.invoke.MethodType.methodType(
                Void.TYPE,
                MemorySegment::class.java,
                java.lang.Long.TYPE
            )
        )
        val getFontMetricsStub = Linker.nativeLinker().upcallStub(
            getFontMetricsHandle,
            EditorNative.GET_FONT_METRICS_DESC,
            arena
        )

        // Create struct with function pointers
        val struct = arena.allocate(EditorNative.MEASURER_LAYOUT)
        struct.set(ValueLayout.ADDRESS, 0, measureTextWidthStub)
        struct.set(ValueLayout.ADDRESS, ValueLayout.ADDRESS.byteSize(), measureInlayHintWidthStub)
        struct.set(ValueLayout.ADDRESS, ValueLayout.ADDRESS.byteSize() * 2, measureIconWidthStub)
        struct.set(ValueLayout.ADDRESS, ValueLayout.ADDRESS.byteSize() * 3, getFontMetricsStub)
        return struct
    }
}
