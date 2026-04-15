package com.qiplat.compose.sweeteditor.core

import java.lang.foreign.*
import java.lang.invoke.MethodHandle
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * FFM (Foreign Function & Memory) binding layer for Desktop, calling C++ dynamic library via Java 22 Foreign Linker API.
 * <p>
 * All C API functions are declared as MethodHandles in this class for EditorCore to call.
 */
object EditorNative {

    private val LINKER: Linker = Linker.nativeLinker()
    private val LIB: SymbolLookup
    private const val LIB_PATH_KEY = "sweeteditor.lib.path"
    private const val LOAD_LIBRARY_ERROR =
        "Cannot load native library 'sweeteditor'. " +
                "Set -Dsweeteditor.lib.path=<dir> or add the library to java.library.path. "

    init {
        LIB = loadLibraryLookup()
    }

    private fun loadLibraryLookup(): SymbolLookup {
        val libName = System.mapLibraryName("sweeteditor")
        // -Dsweeteditor.lib.path explicitly specified
        val lookup = tryExplicitLibrary(libName)
        if (lookup != null) {
            return lookup
        }
        // Try auto-extracting from JAR resources to default directory and load
        val jarLookup = tryLoadFromJarResources()
        if (jarLookup != null) {
            return jarLookup
        }
        // Fallback to system path (java.library.path)
        return loadLibraryFromSystem()
    }

    /**
     * Try auto-extracting the native library from JAR resources to the default directory (~/.sweeteditor/native/),
     * automatically set sweeteditor.lib.path and load after successful extraction.
     * This is the automatic fallback loading method for Maven release scenarios.
     */
    private fun tryLoadFromJarResources(): SymbolLookup? {
        try {
            val libPath = NativeLibraryExtractor.extractToDefaultDir()
            if (Files.exists(libPath)) {
                return SymbolLookup.libraryLookup(libPath, Arena.global())
            }
        } catch (_: Exception) {
            // No native library resources in JAR (non-Maven release scenario), silently skip
        }
        return null
    }

    private fun tryExplicitLibrary(libName: String): SymbolLookup? {
        val libPath = System.getProperty(LIB_PATH_KEY) ?: return null
        if (libPath.isBlank()) {
            return null
        }
        return lookupLibrary(Path.of(libPath, libName))
    }

    private fun lookupLibrary(path: Path): SymbolLookup? {
        if (!Files.exists(path)) {
            return null
        }
        return SymbolLookup.libraryLookup(path, Arena.global())
    }

    private fun loadLibraryFromSystem(): SymbolLookup {
        try {
            System.loadLibrary("sweeteditor")
            return SymbolLookup.loaderLookup()
        } catch (_: UnsatisfiedLinkError) {
            throw UnsatisfiedLinkError(LOAD_LIBRARY_ERROR)
        }
    }

    private fun downcall(name: String, desc: FunctionDescriptor): MethodHandle {
        return LINKER.downcallHandle(
            LIB.find(name).orElseThrow { UnsatisfiedLinkError("Symbol not found: $name") },
            desc
        )
    }

    fun interface ThrowableSupplier<T> {
        @Throws(Throwable::class)
        fun get(): T
    }

    fun interface ThrowableRunnable {
        @Throws(Throwable::class)
        fun run()
    }

    fun interface BinaryInvoker {
        @Throws(Throwable::class)
        fun invoke(outSize: MemorySegment): MemorySegment
    }

    /**
     * Encapsulates the binary result returned by native, supporting zero-copy access.
     * <p>
     * The caller must call free() after use to release native memory.
     * Recommend using try-finally pattern to ensure exception safety.
     */
    class NativeBinaryResult internal constructor(
        private val ptr: MemorySegment?,
        private val size: Long
    ) {
        /**
         * Whether there is valid data
         */
        fun hasData(): Boolean {
            return ptr != null && !ptr.equals(MemorySegment.NULL) && size > 0
        }

        /**
         * Zero-copy ByteBuffer view, directly reading native memory.
         * <p>
         * Note: The returned ByteBuffer must not be used after calling free().
         */
        fun asByteBuffer(): ByteBuffer? {
            if (!hasData()) return null
            return ptr!!.asByteBuffer().order(ByteOrder.nativeOrder())
        }

        /**
         * Release memory allocated by the native side
         */
        fun free() {
            if (ptr != null && !ptr.equals(MemorySegment.NULL)) {
                freeBinaryData(ptr.address())
            }
        }
    }

    private fun wrapThrowable(t: Throwable): RuntimeException {
        return RuntimeException(t)
    }

    private fun <T> invokeValue(supplier: ThrowableSupplier<T>): T {
        try {
            return supplier.get()
        } catch (t: Throwable) {
            throw wrapThrowable(t)
        }
    }

    private fun invokeVoid(runnable: ThrowableRunnable) {
        try {
            runnable.run()
        } catch (t: Throwable) {
            throw wrapThrowable(t)
        }
    }

    private fun invokeBoolean(supplier: ThrowableSupplier<Int>): Boolean {
        return invokeValue { supplier.get() != 0 }
    }

    private fun invokeBinaryResult(arena: Arena, invoker: BinaryInvoker): NativeBinaryResult {
        try {
            val outSize = arena.allocate(ValueLayout.JAVA_LONG)
            val ptr = invoker.invoke(outSize)

            // 先获取 size 值，防止 native 函数修改 outSize 内存
            val sizeValue = outSize.get(ValueLayout.JAVA_LONG, 0)

            var resultPtr: MemorySegment
            if (ptr != MemorySegment.NULL && sizeValue > 0 && sizeValue <= Int.MAX_VALUE) {
                resultPtr = ptr.reinterpret(sizeValue)
            } else {
                resultPtr = MemorySegment.NULL
            }
            return NativeBinaryResult(resultPtr, sizeValue)
        } catch (t: Throwable) {
            throw wrapThrowable(t)
        }
    }

    private fun invokeBinaryResult(invoker: BinaryInvoker): NativeBinaryResult {
        Arena.ofConfined().use { arena ->
            return invokeBinaryResult(arena, invoker)
        }
    }

    private fun nullableString(arena: Arena, text: String?): MemorySegment {
        return if (text != null) {
            arena.allocateFrom(text)
        } else MemorySegment.NULL
    }

    private fun byteArraySegment(arena: Arena, data: ByteArray): MemorySegment {
        return arena.allocateFrom(ValueLayout.JAVA_BYTE, *data)
    }

    // ===================== text_measurer_t callback struct layout =====================
    // struct { float(*)(const U16Char*, int32_t); float(*)(const U16Char*); float(*)(int32_t); void(*)(float*, size_t); }
    // 4 function pointers, each occupying ADDRESS size
    val MEASURER_LAYOUT: MemoryLayout = MemoryLayout.structLayout(
        ValueLayout.ADDRESS.withName("measure_text_width"),
        ValueLayout.ADDRESS.withName("measure_inlay_hint_width"),
        ValueLayout.ADDRESS.withName("measure_icon_width"),
        ValueLayout.ADDRESS.withName("get_font_metrics")
    )

    // Callback function descriptors
    val MEASURE_TEXT_WIDTH_DESC: FunctionDescriptor = FunctionDescriptor.of(
        ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
    )
    val MEASURE_INLAY_HINT_WIDTH_DESC: FunctionDescriptor = FunctionDescriptor.of(
        ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS
    )
    val MEASURE_ICON_WIDTH_DESC: FunctionDescriptor = FunctionDescriptor.of(
        ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT
    )
    val GET_FONT_METRICS_DESC: FunctionDescriptor = FunctionDescriptor.ofVoid(
        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
    )

    // ===================== Native handles =====================

    private val CREATE_DOCUMENT = downcall(
        "create_document_from_utf16",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    )
    private val CREATE_DOCUMENT_FROM_FILE = downcall(
        "create_document_from_file",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    )

    private val GET_DOCUMENT_LINE_TEXT = downcall(
        "get_document_line_utf16",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
    )

    private val GET_DOCUMENT_LINE_COUNT = downcall(
        "get_document_line_count",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
    )

    private val CREATE_EDITOR = downcall(
        "create_editor",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, MEASURER_LAYOUT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
    )

    private val SET_EDITOR_DOCUMENT = downcall(
        "set_editor_document",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
    )

    private val SET_VIEWPORT = downcall(
        "set_editor_viewport",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_SHORT, ValueLayout.JAVA_SHORT)
    )

    private val SET_FOLD_ARROW_MODE = downcall(
        "editor_set_fold_arrow_mode",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
    )

    private val SET_WRAP_MODE = downcall(
        "editor_set_wrap_mode",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
    )

    private val SET_TAB_SIZE = downcall(
        "editor_set_tab_size",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
    )

    private val SET_SCALE = downcall(
        "editor_set_scale",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_FLOAT)
    )

    private val SET_LINE_SPACING = downcall(
        "editor_set_line_spacing",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT)
    )

    private val SET_SHOW_SPLIT_LINE = downcall(
        "editor_set_show_split_line",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
    )

    private val SET_CURRENT_LINE_RENDER_MODE = downcall(
        "editor_set_current_line_render_mode",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
    )

    private val SET_GUTTER_STICKY = downcall(
        "editor_set_gutter_sticky",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
    )

    private val SET_GUTTER_VISIBLE = downcall(
        "editor_set_gutter_visible",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
    )
    private val SET_HANDLE_CONFIG = downcall(
        "editor_set_handle_config",
        FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_FLOAT,
        )
    )
    private val SET_SCROLLBAR_CONFIG = downcall(
        "editor_set_scrollbar_config",
        FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        )
    )

    private val BUILD_RENDER_MODEL = downcall(
        "build_editor_render_model",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    )

    private val GET_SCROLL_METRICS = downcall(
        "editor_get_scroll_metrics",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    )

    private val HANDLE_GESTURE_EX = downcall(
        "handle_editor_gesture_event_ex",
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_BYTE, ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS,
            ValueLayout.JAVA_BYTE, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS)
    )

    private val GET_CURSOR = downcall(
        "editor_get_cursor_position",
        FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, // out_line
            ValueLayout.ADDRESS  // out_column
        )
    )

    private val SET_SELECTION = downcall(
        "editor_set_selection",
        FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, // start_line
            ValueLayout.JAVA_LONG, // start_column
            ValueLayout.JAVA_LONG, // end_line
            ValueLayout.JAVA_LONG  // end_column
        )
    )

    private val SET_MAX_GUTTER_ICONS = downcall(
        "editor_set_max_gutter_icons",
        FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_INT
        )
    )

    private val HANDLE_KEY_EVENT = downcall(
        "handle_editor_key_event",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_SHORT, ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS)
    )

    private val INSERT_TEXT = downcall(
        "editor_insert_text",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    )

    private val REPLACE_TEXT = downcall(
        "editor_replace_text",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    )

    private val DELETE_TEXT = downcall(
        "editor_delete_text",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    )

    private val BACKSPACE = downcall(
        "editor_backspace",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    )

    private val DELETE_FORWARD = downcall(
        "editor_delete_forward",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    )

    private val INSERT_SNIPPET = downcall(
        "editor_insert_snippet",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    )

    private val MOVE_LINE_UP = downcall(
        "editor_move_line_up",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    )

    private val MOVE_LINE_DOWN = downcall(
        "editor_move_line_down",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    )

    private val COPY_LINE_UP = downcall(
        "editor_copy_line_up",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    )

    private val COPY_LINE_DOWN = downcall(
        "editor_copy_line_down",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    )

    private val DELETE_LINE = downcall(
        "editor_delete_line",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    )

    private val INSERT_LINE_ABOVE = downcall(
        "editor_insert_line_above",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    )

    private val INSERT_LINE_BELOW = downcall(
        "editor_insert_line_below",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    )

    private val UNDO = downcall(
        "editor_undo",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    )

    private val REDO = downcall(
        "editor_redo",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    )

    private val CAN_UNDO = downcall(
        "editor_can_undo",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)
    )

    private val CAN_REDO = downcall(
        "editor_can_redo",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)
    )

    private val SET_CURSOR = downcall(
        "editor_set_cursor_position",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
    )

    private val MOVE_CURSOR_LEFT = downcall(
        "editor_move_cursor_left",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
    )

    private val MOVE_CURSOR_RIGHT = downcall(
        "editor_move_cursor_right",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
    )

    private val MOVE_CURSOR_UP = downcall(
        "editor_move_cursor_up",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
    )

    private val MOVE_CURSOR_DOWN = downcall(
        "editor_move_cursor_down",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
    )

    private val MOVE_CURSOR_TO_LINE_START = downcall(
        "editor_move_cursor_to_line_start",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
    )

    private val MOVE_CURSOR_TO_LINE_END = downcall(
        "editor_move_cursor_to_line_end",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
    )

    private val SELECT_ALL = downcall(
        "editor_select_all",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG)
    )

    private val GET_SELECTION = downcall(
        "editor_get_selection",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    )

    private val GET_SELECTED_TEXT = downcall(
        "editor_get_selected_text",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
    )

    private val GET_WORD_AT_CURSOR = downcall(
        "editor_get_word_at_cursor",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
    )

    private val GET_WORD_RANGE_AT_CURSOR = downcall(
        "editor_get_word_range_at_cursor",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    )

    private val COMP_START = downcall(
        "editor_composition_start",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG)
    )

    private val COMP_UPDATE = downcall(
        "editor_composition_update",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    )

    private val COMP_END = downcall(
        "editor_composition_end",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    )

    private val COMP_CANCEL = downcall(
        "editor_composition_cancel",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG)
    )

    private val IS_COMPOSING = downcall(
        "editor_is_composing",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)
    )

    private val SET_COMPOSITION_ENABLED = downcall(
        "editor_set_composition_enabled",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
    )

    private val IS_COMPOSITION_ENABLED = downcall(
        "editor_is_composition_enabled",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)
    )

    private val SET_READ_ONLY = downcall(
        "editor_set_read_only",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
    )

    private val IS_READ_ONLY = downcall(
        "editor_is_read_only",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)
    )

    private val SET_AUTO_INDENT_MODE = downcall(
        "editor_set_auto_indent_mode",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
    )

    private val GET_AUTO_INDENT_MODE = downcall(
        "editor_get_auto_indent_mode",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)
    )

    private val GET_POSITION_RECT = downcall(
        "editor_get_position_rect",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    )

    private val GET_CURSOR_RECT = downcall(
        "editor_get_cursor_rect",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    )

    private val START_LINKED_EDITING = downcall(
        "editor_start_linked_editing",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    )

    private val IS_IN_LINKED_EDITING = downcall(
        "editor_is_in_linked_editing",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)
    )

    private val LINKED_EDITING_NEXT = downcall(
        "editor_linked_editing_next",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)
    )

    private val LINKED_EDITING_PREV = downcall(
        "editor_linked_editing_prev",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)
    )

    private val CANCEL_LINKED_EDITING = downcall(
        "editor_cancel_linked_editing",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG)
    )

    private val SCROLL_TO_LINE = downcall(
        "editor_scroll_to_line",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_BYTE)
    )

    private val GOTO_POSITION = downcall(
        "editor_goto_position",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
    )

    private val SET_SCROLL = downcall(
        "editor_set_scroll",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT)
    )

    private val REGISTER_BATCH_TEXT_STYLES = downcall(
        "editor_register_batch_text_styles",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
    )

    private val SET_BATCH_LINE_SPANS = downcall(
        "editor_set_batch_line_spans",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
    )

    private val SET_BATCH_LINE_INLAY_HINTS = downcall(
        "editor_set_batch_line_inlay_hints",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
    )

    private val SET_BATCH_LINE_PHANTOM_TEXTS = downcall(
        "editor_set_batch_line_phantom_texts",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
    )

    private val SET_BATCH_LINE_GUTTER_ICONS = downcall(
        "editor_set_batch_line_gutter_icons",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
    )

    private val SET_BATCH_LINE_DIAGNOSTICS = downcall(
        "editor_set_batch_line_diagnostics",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
    )

    private val CLEAR_INLAY_HINTS = downcall(
        "editor_clear_inlay_hints",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG)
    )

    private val CLEAR_PHANTOM_TEXTS = downcall(
        "editor_clear_phantom_texts",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG)
    )

    private val CLEAR_GUTTER_ICONS = downcall(
        "editor_clear_gutter_icons",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG)
    )

    private val CLEAR_DIAGNOSTICS = downcall(
        "editor_clear_diagnostics",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG)
    )

    private val SET_INDENT_GUIDES = downcall(
        "editor_set_indent_guides",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
    )

    private val SET_BRACKET_GUIDES = downcall(
        "editor_set_bracket_guides",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
    )

    private val SET_FLOW_GUIDES = downcall(
        "editor_set_flow_guides",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
    )

    private val SET_SEPARATOR_GUIDES = downcall(
        "editor_set_separator_guides",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
    )

    private val CLEAR_GUIDES = downcall(
        "editor_clear_guides",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG)
    )

    private val SET_FOLD_REGIONS = downcall(
        "editor_set_fold_regions",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
    )

    private val FOLD_AT = downcall(
        "editor_fold_at",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
    )

    private val FOLD_ALL = downcall(
        "editor_fold_all",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG)
    )

    private val IS_LINE_VISIBLE = downcall(
        "editor_is_line_visible",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
    )

    private val CLEAR_HIGHLIGHTS = downcall(
        "editor_clear_highlights",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG)
    )

    private val CLEAR_LINE_SPANS = downcall(
        "editor_clear_line_spans",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_BYTE)
    )

    private val CLEAR_ALL_DECORATIONS = downcall(
        "editor_clear_all_decorations",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG)
    )

    private val FREE_BINARY_DATA = downcall(
        "free_binary_data",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG)
    )

    private val FREE_DOCUMENT = downcall(
        "free_document",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG)
    )

    private val TICK_ANIMATIONS = downcall(
        "editor_tick_animations",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    )

    private val FREE_EDITOR = downcall(
        "free_editor",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG)
    )

    private val ON_FONT_METRICS_CHANGED = downcall(
        "editor_on_font_metrics_changed",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG)
    )

    private val FREE_U16_STRING = downcall(
        "free_u16_string",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG)
    )

    // ===================== Public API =====================

    fun createDocument(arena: Arena, text: String?): Long {
        try {
            val utf16 = if (text != null) {
                arena.allocateFrom(text, StandardCharsets.UTF_16LE)
            } else MemorySegment.NULL
            return CREATE_DOCUMENT.invokeExact(utf16) as Long
        } catch (t: Throwable) {
            throw RuntimeException(t)
        }
    }

    fun createDocumentFromFile(arena: Arena, path: String?): Long {
        try {
            val utf8 = if (path != null) {
                arena.allocateFrom(path)
            } else MemorySegment.NULL
            return CREATE_DOCUMENT_FROM_FILE.invokeExact(utf8) as Long
        } catch (t: Throwable) {
            throw RuntimeException(t)
        }
    }

    fun getDocumentLineText(documentHandle: Long, line: Int): String {
        try {
            val ptr = GET_DOCUMENT_LINE_TEXT.invokeExact(documentHandle, line.toLong()) as MemorySegment
            val text = readUtf16String(ptr)
            if (ptr != MemorySegment.NULL) {
                freeU16String(ptr.address())
            }
            return text ?: ""
        } catch (t: Throwable) {
            throw RuntimeException(t)
        }
    }

    fun getDocumentLineCount(documentHandle: Long): Long {
        try {
            return GET_DOCUMENT_LINE_COUNT.invokeExact(documentHandle) as Long
        } catch (t: Throwable) {
            throw RuntimeException(t)
        }
    }

    fun createEditor(measurerSegment: MemorySegment, optionsData: ByteArray, arena: Arena): Long {
        try {
            val optionsSegment = byteArraySegment(arena, optionsData)
            return CREATE_EDITOR.invokeExact(measurerSegment, optionsSegment, optionsData.size.toLong()) as Long
        } catch (t: Throwable) {
            throw RuntimeException(t)
        }
    }

    fun setEditorDocument(editorHandle: Long, documentHandle: Long) {
        invokeVoid { SET_EDITOR_DOCUMENT.invokeExact(editorHandle, documentHandle) }
    }

    fun setViewport(editorHandle: Long, width: Int, height: Int) {
        invokeVoid { SET_VIEWPORT.invokeExact(editorHandle, width.toShort(), height.toShort()) }
    }

    fun setFoldArrowMode(editorHandle: Long, mode: Int) {
        invokeVoid { SET_FOLD_ARROW_MODE.invokeExact(editorHandle, mode) }
    }

    fun setWrapMode(editorHandle: Long, mode: Int) {
        invokeVoid { SET_WRAP_MODE.invokeExact(editorHandle, mode) }
    }

    fun setTabSize(editorHandle: Long, tabSize: Int) {
        invokeVoid { SET_TAB_SIZE.invokeExact(editorHandle, tabSize) }
    }

    fun setScale(editorHandle: Long, scale: Float) {
        invokeVoid { SET_SCALE.invokeExact(editorHandle, scale) }
    }

    fun setLineSpacing(editorHandle: Long, add: Float, mult: Float) {
        invokeVoid { SET_LINE_SPACING.invokeExact(editorHandle, add, mult) }
    }

    fun setShowSplitLine(editorHandle: Long, show: Boolean) {
        invokeVoid { SET_SHOW_SPLIT_LINE.invokeExact(editorHandle, if (show) 1 else 0) }
    }

    fun setCurrentLineRenderMode(editorHandle: Long, mode: Int) {
        invokeVoid { SET_CURRENT_LINE_RENDER_MODE.invokeExact(editorHandle, mode) }
    }

    fun setGutterSticky(editorHandle: Long, sticky: Boolean) {
        invokeVoid { SET_GUTTER_STICKY.invokeExact(editorHandle, if (sticky) 1 else 0) }
    }

    fun setGutterVisible(editorHandle: Long, visible: Boolean) {
        invokeVoid { SET_GUTTER_VISIBLE.invokeExact(editorHandle, if (visible) 1 else 0) }
    }

    fun setHandleConfig(
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
        invokeVoid {
            SET_HANDLE_CONFIG.invokeExact(
                editorHandle,
                startLeft,
                startTop,
                startRight,
                startBottom,
                endLeft,
                endTop,
                endRight,
                endBottom,
            )
        }
    }

    fun setScrollbarConfig(
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
        invokeVoid {
            SET_SCROLLBAR_CONFIG.invokeExact(
                editorHandle,
                thickness,
                minThumb,
                thumbHitPadding,
                mode,
                if (thumbDraggable) 1 else 0,
                trackTapMode,
                fadeDelayMillis,
                fadeDurationMillis,
            )
        }
    }

    fun setMaxGutterIcons(handle: Long, count: Int) {
        try {
            SET_MAX_GUTTER_ICONS.invokeExact(handle, count)
        } catch (t: Throwable) {
            throw RuntimeException(t)
        }
    }

    fun buildRenderModel(handle: Long): NativeBinaryResult {
        return invokeBinaryResult { outSize -> BUILD_RENDER_MODEL.invokeExact(handle, outSize) as MemorySegment }
    }

    fun getScrollMetrics(handle: Long, arena: Arena): NativeBinaryResult {
        val outSize = arena.allocate(ValueLayout.JAVA_LONG)
        val ptr = try {
            GET_SCROLL_METRICS.invokeExact(handle, outSize) as MemorySegment
        } catch (t: Throwable) {
            throw RuntimeException(t)
        }
        val size = outSize.get(ValueLayout.JAVA_LONG, 0)
        val resultPtr = if (ptr != MemorySegment.NULL && size > 0 && size <= Int.MAX_VALUE) {
            ptr.reinterpret(size)
        } else {
            MemorySegment.NULL
        }
        return NativeBinaryResult(resultPtr, size)
    }

    fun handleGestureEventEx(
        handle: Long, type: Int, pointerCount: Int, arena: Arena, points: FloatArray,
        modifiers: Int, wheelDeltaX: Float, wheelDeltaY: Float, directScale: Float
    ): NativeBinaryResult {
        return invokeBinaryResult(arena) { outSize ->
            val pointsSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, *points)
            HANDLE_GESTURE_EX.invokeExact(handle,
                type.toByte(), pointerCount.toByte(), pointsSeg,
                modifiers.toByte(), wheelDeltaX, wheelDeltaY, directScale, outSize) as MemorySegment
        }
    }

    fun handleKeyEvent(handle: Long, keyCode: Int, text: String?, modifiers: Int, arena: Arena): NativeBinaryResult {
        return invokeBinaryResult(arena) { outSize ->
            HANDLE_KEY_EVENT.invokeExact(handle,
                keyCode.toShort(), nullableString(arena, text), modifiers.toByte(), outSize) as MemorySegment
        }
    }

    fun insertText(handle: Long, text: String, arena: Arena): NativeBinaryResult {
        return invokeBinaryResult(arena) { outSize ->
            val textSeg = arena.allocateFrom(text)
            INSERT_TEXT.invokeExact(handle, textSeg, outSize) as MemorySegment
        }
    }

    fun replaceText(
        handle: Long,
        startLine: Int, startColumn: Int, endLine: Int, endColumn: Int,
        text: String, arena: Arena
    ): NativeBinaryResult {
        return invokeBinaryResult(arena) { outSize ->
            val textSeg = arena.allocateFrom(text)
            REPLACE_TEXT.invokeExact(handle,
                startLine.toLong(), startColumn.toLong(), endLine.toLong(), endColumn.toLong(),
                textSeg, outSize) as MemorySegment
        }
    }

    fun deleteText(
        handle: Long,
        startLine: Int, startColumn: Int, endLine: Int, endColumn: Int,
        arena: Arena
    ): NativeBinaryResult {
        return invokeBinaryResult(arena) { outSize ->
            DELETE_TEXT.invokeExact(handle,
                startLine.toLong(), startColumn.toLong(), endLine.toLong(), endColumn.toLong(),
                outSize) as MemorySegment
        }
    }

    fun backspace(handle: Long): NativeBinaryResult {
        return invokeBinaryResult { outSize -> BACKSPACE.invokeExact(handle, outSize) as MemorySegment }
    }

    fun deleteForward(handle: Long): NativeBinaryResult {
        return invokeBinaryResult { outSize -> DELETE_FORWARD.invokeExact(handle, outSize) as MemorySegment }
    }

    fun insertSnippet(handle: Long, template: String, arena: Arena): NativeBinaryResult {
        return invokeBinaryResult(arena) { outSize ->
            val templateSeg = arena.allocateFrom(template)
            INSERT_SNIPPET.invokeExact(handle, templateSeg, outSize) as MemorySegment
        }
    }

    fun moveLineUp(handle: Long): NativeBinaryResult {
        return invokeBinaryResult { outSize -> MOVE_LINE_UP.invokeExact(handle, outSize) as MemorySegment }
    }

    fun moveLineDown(handle: Long): NativeBinaryResult {
        return invokeBinaryResult { outSize -> MOVE_LINE_DOWN.invokeExact(handle, outSize) as MemorySegment }
    }

    fun copyLineUp(handle: Long): NativeBinaryResult {
        return invokeBinaryResult { outSize -> COPY_LINE_UP.invokeExact(handle, outSize) as MemorySegment }
    }

    fun copyLineDown(handle: Long): NativeBinaryResult {
        return invokeBinaryResult { outSize -> COPY_LINE_DOWN.invokeExact(handle, outSize) as MemorySegment }
    }

    fun deleteLine(handle: Long): NativeBinaryResult {
        return invokeBinaryResult { outSize -> DELETE_LINE.invokeExact(handle, outSize) as MemorySegment }
    }

    fun insertLineAbove(handle: Long): NativeBinaryResult {
        return invokeBinaryResult { outSize -> INSERT_LINE_ABOVE.invokeExact(handle, outSize) as MemorySegment }
    }

    fun insertLineBelow(handle: Long): NativeBinaryResult {
        return invokeBinaryResult { outSize -> INSERT_LINE_BELOW.invokeExact(handle, outSize) as MemorySegment }
    }

    fun undo(handle: Long): NativeBinaryResult {
        return invokeBinaryResult { outSize -> UNDO.invokeExact(handle, outSize) as MemorySegment }
    }

    fun redo(handle: Long): NativeBinaryResult {
        return invokeBinaryResult { outSize -> REDO.invokeExact(handle, outSize) as MemorySegment }
    }

    fun canUndo(handle: Long): Boolean {
        return invokeBoolean { CAN_UNDO.invokeExact(handle) as Int }
    }

    fun canRedo(handle: Long): Boolean {
        return invokeBoolean { CAN_REDO.invokeExact(handle) as Int }
    }

    fun setCursorPosition(handle: Long, line: Int, column: Int) {
        invokeVoid { SET_CURSOR.invokeExact(handle, line.toLong(), column.toLong()) }
    }

    fun getCursorPosition(handle: Long, arena: Arena): IntArray {
        try {
            val linePtr = arena.allocate(ValueLayout.JAVA_LONG)
            val columnPtr = arena.allocate(ValueLayout.JAVA_LONG)
            GET_CURSOR.invokeExact(handle, linePtr, columnPtr)
            return intArrayOf(linePtr.get(ValueLayout.JAVA_LONG, 0).toInt(),
                              columnPtr.get(ValueLayout.JAVA_LONG, 0).toInt())
        } catch (t: Throwable) {
            throw RuntimeException(t)
        }
    }

    fun moveCursorLeft(handle: Long, extendSelection: Boolean) {
        invokeVoid { MOVE_CURSOR_LEFT.invokeExact(handle, if (extendSelection) 1 else 0) }
    }

    fun moveCursorRight(handle: Long, extendSelection: Boolean) {
        invokeVoid { MOVE_CURSOR_RIGHT.invokeExact(handle, if (extendSelection) 1 else 0) }
    }

    fun moveCursorUp(handle: Long, extendSelection: Boolean) {
        invokeVoid { MOVE_CURSOR_UP.invokeExact(handle, if (extendSelection) 1 else 0) }
    }

    fun moveCursorDown(handle: Long, extendSelection: Boolean) {
        invokeVoid { MOVE_CURSOR_DOWN.invokeExact(handle, if (extendSelection) 1 else 0) }
    }

    fun moveCursorToLineStart(handle: Long, extendSelection: Boolean) {
        invokeVoid { MOVE_CURSOR_TO_LINE_START.invokeExact(handle, if (extendSelection) 1 else 0) }
    }

    fun moveCursorToLineEnd(handle: Long, extendSelection: Boolean) {
        invokeVoid { MOVE_CURSOR_TO_LINE_END.invokeExact(handle, if (extendSelection) 1 else 0) }
    }

    fun selectAll(handle: Long) {
        invokeVoid { SELECT_ALL.invokeExact(handle) }
    }

    fun getSelection(handle: Long, arena: Arena): IntArray? {
        val outSL = arena.allocate(ValueLayout.JAVA_LONG)
        val outSC = arena.allocate(ValueLayout.JAVA_LONG)
        val outEL = arena.allocate(ValueLayout.JAVA_LONG)
        val outEC = arena.allocate(ValueLayout.JAVA_LONG)
        val hasSelection = invokeValue { GET_SELECTION.invokeExact(handle, outSL, outSC, outEL, outEC) as Int }
        if (hasSelection == 0) return null
        return intArrayOf(
            outSL.get(ValueLayout.JAVA_LONG, 0).toInt(),
            outSC.get(ValueLayout.JAVA_LONG, 0).toInt(),
            outEL.get(ValueLayout.JAVA_LONG, 0).toInt(),
            outEC.get(ValueLayout.JAVA_LONG, 0).toInt()
        )
    }

    fun setSelection(handle: Long, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int) {
        invokeVoid {
            SET_SELECTION.invokeExact(handle, startLine.toLong(), startColumn.toLong(), endLine.toLong(), endColumn.toLong())
        }
    }

    fun getSelectedText(handle: Long): String {
        try {
            val ptr = GET_SELECTED_TEXT.invokeExact(handle) as MemorySegment
            if (ptr.equals(MemorySegment.NULL)) return ""
            return ptr.reinterpret(Long.MAX_VALUE).getString(0)
        } catch (t: Throwable) {
            throw RuntimeException(t)
        }
    }

    fun getWordAtCursor(handle: Long): String {
        try {
            val ptr = GET_WORD_AT_CURSOR.invokeExact(handle) as MemorySegment
            if (ptr.equals(MemorySegment.NULL)) return ""
            return ptr.reinterpret(Long.MAX_VALUE).getString(0)
        } catch (t: Throwable) {
            throw RuntimeException(t)
        }
    }

    fun getWordRangeAtCursor(handle: Long, arena: Arena): IntArray {
        val outSL = arena.allocate(ValueLayout.JAVA_LONG)
        val outSC = arena.allocate(ValueLayout.JAVA_LONG)
        val outEL = arena.allocate(ValueLayout.JAVA_LONG)
        val outEC = arena.allocate(ValueLayout.JAVA_LONG)
        invokeVoid { GET_WORD_RANGE_AT_CURSOR.invokeExact(handle, outSL, outSC, outEL, outEC) }
        return intArrayOf(
            outSL.get(ValueLayout.JAVA_LONG, 0).toInt(),
            outSC.get(ValueLayout.JAVA_LONG, 0).toInt(),
            outEL.get(ValueLayout.JAVA_LONG, 0).toInt(),
            outEC.get(ValueLayout.JAVA_LONG, 0).toInt()
        )
    }

    fun compositionStart(handle: Long) {
        invokeVoid { COMP_START.invokeExact(handle) }
    }

    fun compositionUpdate(handle: Long, text: String, arena: Arena) {
        val textSeg = arena.allocateFrom(text)
        invokeVoid { COMP_UPDATE.invokeExact(handle, textSeg) }
    }

    fun compositionEnd(handle: Long, committedText: String?, arena: Arena): NativeBinaryResult {
        return invokeBinaryResult(arena) { outSize ->
            COMP_END.invokeExact(handle, nullableString(arena, committedText), outSize) as MemorySegment
        }
    }

    fun compositionCancel(handle: Long) {
        invokeVoid { COMP_CANCEL.invokeExact(handle) }
    }

    fun isComposing(handle: Long): Boolean {
        return invokeBoolean { IS_COMPOSING.invokeExact(handle) as Int }
    }

    fun setCompositionEnabled(handle: Long, enabled: Boolean) {
        invokeVoid { SET_COMPOSITION_ENABLED.invokeExact(handle, if (enabled) 1 else 0) }
    }

    fun isCompositionEnabled(handle: Long): Boolean {
        return invokeBoolean { IS_COMPOSITION_ENABLED.invokeExact(handle) as Int }
    }

    fun setReadOnly(handle: Long, readOnly: Boolean) {
        invokeVoid { SET_READ_ONLY.invokeExact(handle, if (readOnly) 1 else 0) }
    }

    fun isReadOnly(handle: Long): Boolean {
        return invokeBoolean { IS_READ_ONLY.invokeExact(handle) as Int }
    }

    fun setAutoIndentMode(handle: Long, mode: Int) {
        invokeVoid { SET_AUTO_INDENT_MODE.invokeExact(handle, mode) }
    }

    fun getAutoIndentMode(handle: Long): Int {
        return invokeValue { GET_AUTO_INDENT_MODE.invokeExact(handle) as Int }
    }

    fun getPositionRect(handle: Long, line: Int, column: Int, arena: Arena): FloatArray {
        val px = arena.allocate(ValueLayout.JAVA_FLOAT)
        val py = arena.allocate(ValueLayout.JAVA_FLOAT)
        val ph = arena.allocate(ValueLayout.JAVA_FLOAT)
        invokeVoid { GET_POSITION_RECT.invokeExact(handle, line.toLong(), column.toLong(), px, py, ph) }
        return floatArrayOf(
            px.get(ValueLayout.JAVA_FLOAT, 0),
            py.get(ValueLayout.JAVA_FLOAT, 0),
            ph.get(ValueLayout.JAVA_FLOAT, 0)
        )
    }

    fun getCursorRect(handle: Long, arena: Arena): FloatArray {
        val px = arena.allocate(ValueLayout.JAVA_FLOAT)
        val py = arena.allocate(ValueLayout.JAVA_FLOAT)
        val ph = arena.allocate(ValueLayout.JAVA_FLOAT)
        invokeVoid { GET_CURSOR_RECT.invokeExact(handle, px, py, ph) }
        return floatArrayOf(
            px.get(ValueLayout.JAVA_FLOAT, 0),
            py.get(ValueLayout.JAVA_FLOAT, 0),
            ph.get(ValueLayout.JAVA_FLOAT, 0)
        )
    }

    fun startLinkedEditing(handle: Long, data: ByteArray, arena: Arena) {
        invokeVoid { START_LINKED_EDITING.invokeExact(handle, byteArraySegment(arena, data)) }
    }

    fun isInLinkedEditing(handle: Long): Boolean {
        return invokeBoolean { IS_IN_LINKED_EDITING.invokeExact(handle) as Int }
    }

    fun linkedEditingNext(handle: Long): Boolean {
        return invokeBoolean { LINKED_EDITING_NEXT.invokeExact(handle) as Int }
    }

    fun linkedEditingPrev(handle: Long): Boolean {
        return invokeBoolean { LINKED_EDITING_PREV.invokeExact(handle) as Int }
    }

    fun cancelLinkedEditing(handle: Long) {
        invokeVoid { CANCEL_LINKED_EDITING.invokeExact(handle) }
    }

    fun tickAnimations(handle: Long): NativeBinaryResult {
        return invokeBinaryResult { outSize ->
            TICK_ANIMATIONS.invokeExact(handle, outSize) as MemorySegment
        }
    }

    fun scrollToLine(handle: Long, line: Int, behavior: Int) {
        invokeVoid { SCROLL_TO_LINE.invokeExact(handle, line.toLong(), behavior.toByte()) }
    }

    fun gotoPosition(handle: Long, line: Int, column: Int) {
        invokeVoid { GOTO_POSITION.invokeExact(handle, line.toLong(), column.toLong()) }
    }

    fun setScroll(handle: Long, scrollX: Float, scrollY: Float) {
        invokeVoid { SET_SCROLL.invokeExact(handle, scrollX, scrollY) }
    }

    fun registerBatchTextStyles(handle: Long, data: ByteArray, arena: Arena) {
        invokeVoid { REGISTER_BATCH_TEXT_STYLES.invokeExact(handle, byteArraySegment(arena, data), data.size.toLong()) }
    }

    fun setBatchLineSpans(handle: Long, data: ByteArray, arena: Arena) {
        invokeVoid { SET_BATCH_LINE_SPANS.invokeExact(handle, byteArraySegment(arena, data), data.size.toLong()) }
    }

    fun setBatchLineInlayHints(handle: Long, data: ByteArray, arena: Arena) {
        invokeVoid { SET_BATCH_LINE_INLAY_HINTS.invokeExact(handle, byteArraySegment(arena, data), data.size.toLong()) }
    }

    fun setBatchLinePhantomTexts(handle: Long, data: ByteArray, arena: Arena) {
        invokeVoid { SET_BATCH_LINE_PHANTOM_TEXTS.invokeExact(handle, byteArraySegment(arena, data), data.size.toLong()) }
    }

    fun setBatchLineGutterIcons(handle: Long, data: ByteArray, arena: Arena) {
        invokeVoid { SET_BATCH_LINE_GUTTER_ICONS.invokeExact(handle, byteArraySegment(arena, data), data.size.toLong()) }
    }

    fun setBatchLineDiagnostics(handle: Long, data: ByteArray, arena: Arena) {
        invokeVoid { SET_BATCH_LINE_DIAGNOSTICS.invokeExact(handle, byteArraySegment(arena, data), data.size.toLong()) }
    }

    fun clearInlayHints(handle: Long) {
        invokeVoid { CLEAR_INLAY_HINTS.invokeExact(handle) }
    }

    fun clearPhantomTexts(handle: Long) {
        invokeVoid { CLEAR_PHANTOM_TEXTS.invokeExact(handle) }
    }

    fun clearGutterIcons(handle: Long) {
        invokeVoid { CLEAR_GUTTER_ICONS.invokeExact(handle) }
    }

    fun clearDiagnostics(handle: Long) {
        invokeVoid { CLEAR_DIAGNOSTICS.invokeExact(handle) }
    }

    fun setIndentGuides(handle: Long, data: ByteArray, arena: Arena) {
        invokeVoid { SET_INDENT_GUIDES.invokeExact(handle, byteArraySegment(arena, data), data.size.toLong()) }
    }

    fun setBracketGuides(handle: Long, data: ByteArray, arena: Arena) {
        invokeVoid { SET_BRACKET_GUIDES.invokeExact(handle, byteArraySegment(arena, data), data.size.toLong()) }
    }

    fun setFlowGuides(handle: Long, data: ByteArray, arena: Arena) {
        invokeVoid { SET_FLOW_GUIDES.invokeExact(handle, byteArraySegment(arena, data), data.size.toLong()) }
    }

    fun setSeparatorGuides(handle: Long, data: ByteArray, arena: Arena) {
        invokeVoid { SET_SEPARATOR_GUIDES.invokeExact(handle, byteArraySegment(arena, data), data.size.toLong()) }
    }

    fun clearGuides(handle: Long) {
        invokeVoid { CLEAR_GUIDES.invokeExact(handle) }
    }

    fun setFoldRegions(handle: Long, data: ByteArray, arena: Arena) {
        invokeVoid { SET_FOLD_REGIONS.invokeExact(handle, byteArraySegment(arena, data), data.size.toLong()) }
    }

    fun foldAt(handle: Long, line: Long) {
        invokeVoid { FOLD_AT.invokeExact(handle, line) }
    }

    fun foldAll(handle: Long) {
        invokeVoid { FOLD_ALL.invokeExact(handle) }
    }

    fun isLineVisible(handle: Long, line: Long): Boolean {
        return invokeBoolean { IS_LINE_VISIBLE.invokeExact(handle, line) as Int }
    }

    fun clearHighlights(handle: Long) {
        invokeVoid { CLEAR_HIGHLIGHTS.invokeExact(handle) }
    }

    fun clearLineSpans(handle: Long, line: Long, flags: Int) {
        invokeVoid { CLEAR_LINE_SPANS.invokeExact(handle, line, flags.toByte()) }
    }

    fun clearAllDecorations(handle: Long) {
        invokeVoid { CLEAR_ALL_DECORATIONS.invokeExact(handle) }
    }

    fun freeBinaryData(address: Long) {
        invokeVoid { FREE_BINARY_DATA.invokeExact(address) }
    }

    fun freeDocument(handle: Long) {
        invokeVoid { FREE_DOCUMENT.invokeExact(handle) }
    }

    fun freeEditor(handle: Long) {
        invokeVoid { FREE_EDITOR.invokeExact(handle) }
    }

    fun onFontMetricsChanged(handle: Long) {
        invokeVoid { ON_FONT_METRICS_CHANGED.invokeExact(handle) }
    }

    /**
     * Read the null-terminated UTF-16LE string returned by C++
     */
    fun readUtf16String(ptr: MemorySegment): String? {
        if (ptr == MemorySegment.NULL) return null

        // Additional safety check: verify segment validity
        try {
            // Check if the segment has valid size
            val actualSize = ptr.byteSize()

            // When byteSize is 0 (e.g., from native callbacks with raw pointers),
            // we need to reinterpret with a maximum safe size and find null terminator
            val reinterpreted = if (actualSize == 0L) {
                // Reinterpret with a safe maximum size for callback strings
                // (typically lines won't exceed 64KB)
                ptr.reinterpret(65536)
            } else {
                ptr.reinterpret(actualSize)
            }

            // Check if first 2 bytes are null terminator (empty string case)
            if (reinterpreted.get(ValueLayout.JAVA_SHORT, 0) == 0.toShort()) {
                return ""
            }

            // Find null terminator (2-byte aligned)
            val maxSize = if (actualSize == 0L) 65536 else actualSize.toInt()
            var offset = 0L
            while (offset + 1 < maxSize &&
                   reinterpreted.get(ValueLayout.JAVA_SHORT, offset) != 0.toShort()) {
                offset += 2
                // Safety limit to prevent infinite loop
                if (offset > 65536) break
            }

            if (offset == 0L) return ""

            // Ensure we don't read beyond the actual size
            val charCount = (offset / 2).toInt()
            val byteCount = minOf((charCount * 2), maxSize)
            val bytes = ByteArray(byteCount)

            if (byteCount > 0) {
                MemorySegment.copy(reinterpreted, ValueLayout.JAVA_BYTE, 0, bytes, 0, byteCount)
            }

            return String(bytes, StandardCharsets.UTF_16LE)
        } catch (e: Exception) {
            // Catch any other exceptions that might occur during memory access
            return null
        }
    }

    /**
     * Free the UTF-16 string allocated by C++
     */
    fun freeU16String(address: Long) {
        invokeVoid { FREE_U16_STRING.invokeExact(address) }
    }
}
