package com.qiplat.compose.sweeteditor.bridge

import java.nio.file.Path
import java.nio.file.Paths

internal object DesktopNativeBindings {
    init {
        DesktopNativeLibraryLoader.load()
    }

    @JvmStatic
    external fun nativeCreateDocumentFromUtf16(text: String): Long

    @JvmStatic
    external fun nativeCreateDocumentFromFile(path: String): Long

    @JvmStatic
    external fun nativeFreeDocument(handle: Long)

    @JvmStatic
    external fun nativeCreateEditor(textMeasurer: NativeTextMeasurer, optionsData: ByteArray): Long

    @JvmStatic
    external fun nativeFreeEditor(handle: Long)

    @JvmStatic
    external fun nativeSetEditorDocument(editorHandle: Long, documentHandle: Long)

    @JvmStatic
    external fun nativeSetEditorViewport(editorHandle: Long, width: Int, height: Int)

    @JvmStatic
    external fun nativeOnFontMetricsChanged(editorHandle: Long)

    @JvmStatic
    external fun nativeSetFoldArrowMode(editorHandle: Long, mode: Int)

    @JvmStatic
    external fun nativeSetWrapMode(editorHandle: Long, mode: Int)

    @JvmStatic
    external fun nativeSetTabSize(editorHandle: Long, tabSize: Int)

    @JvmStatic
    external fun nativeSetScale(editorHandle: Long, scale: Float)

    @JvmStatic
    external fun nativeSetLineSpacing(editorHandle: Long, add: Float, mult: Float)

    @JvmStatic
    external fun nativeSetShowSplitLine(editorHandle: Long, show: Boolean)

    @JvmStatic
    external fun nativeSetCurrentLineRenderMode(editorHandle: Long, mode: Int)

    @JvmStatic
    external fun nativeSetGutterSticky(editorHandle: Long, sticky: Boolean)

    @JvmStatic
    external fun nativeSetGutterVisible(editorHandle: Long, visible: Boolean)

    @JvmStatic
    external fun nativeSetReadOnly(editorHandle: Long, readOnly: Boolean)

    @JvmStatic
    external fun nativeSetCompositionEnabled(editorHandle: Long, enabled: Boolean)

    @JvmStatic
    external fun nativeSetCursorPosition(editorHandle: Long, line: Int, column: Int)

    @JvmStatic
    external fun nativeSetSelection(
        editorHandle: Long,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
    )

    @JvmStatic
    external fun nativeBuildRenderModel(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeGetScrollMetrics(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeHandleGesture(editorHandle: Long, type: Int, points: FloatArray): ByteArray?

    @JvmStatic
    external fun nativeHandleKeyEvent(
        editorHandle: Long,
        keyCode: Int,
        text: String?,
        modifiers: Int,
    ): ByteArray?

    @JvmStatic
    external fun nativeInsertText(editorHandle: Long, text: String): ByteArray?

    @JvmStatic
    external fun nativeReplaceText(
        editorHandle: Long,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
        text: String,
    ): ByteArray?

    @JvmStatic
    external fun nativeDeleteText(
        editorHandle: Long,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
    ): ByteArray?

    @JvmStatic
    external fun nativeRegisterBatchTextStyles(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeSetBatchLineSpans(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeSetBatchLineInlayHints(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeSetBatchLinePhantomTexts(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeSetBatchLineGutterIcons(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeSetBatchLineDiagnostics(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeSetFoldRegions(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeSetMaxGutterIcons(editorHandle: Long, count: Int)
}

private object DesktopNativeLibraryLoader {
    private var loaded: Boolean = false

    @Synchronized
    fun load() {
        if (loaded) {
            return
        }
        val projectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        val archDirectory = when {
            System.getProperty("os.arch").contains("aarch64", ignoreCase = true) -> "arm64"
            System.getProperty("os.arch").contains("arm64", ignoreCase = true) -> "arm64"
            else -> "x86_64"
        }
        val coreLibrary = projectRoot.resolve("editor-core/include/mac_os/$archDirectory/lib/libsweeteditor.dylib")
        val bridgeLibrary = projectRoot.resolve(
            "editor-compose/build/native/jvm/$archDirectory/${System.mapLibraryName("sweeteditor_desktop_bridge")}",
        )
        loadLibrary(coreLibrary)
        loadLibrary(bridgeLibrary)
        loaded = true
    }

    private fun loadLibrary(path: Path) {
        require(path.toFile().exists()) {
            "Native library not found: $path"
        }
        System.load(path.toString())
    }
}
