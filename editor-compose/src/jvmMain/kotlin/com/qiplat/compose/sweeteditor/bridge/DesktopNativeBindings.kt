package com.qiplat.compose.sweeteditor.bridge

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

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
    external fun nativeGetDocumentLineCount(handle: Long): Int

    @JvmStatic
    external fun nativeGetDocumentLineText(handle: Long, line: Int): String

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
    external fun nativeIsReadOnly(editorHandle: Long): Boolean

    @JvmStatic
    external fun nativeSetCompositionEnabled(editorHandle: Long, enabled: Boolean)

    @JvmStatic
    external fun nativeIsCompositionEnabled(editorHandle: Long): Boolean

    @JvmStatic
    external fun nativeSetAutoIndentMode(editorHandle: Long, mode: Int)

    @JvmStatic
    external fun nativeGetAutoIndentMode(editorHandle: Long): Int

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
    external fun nativeGetCursorPosition(editorHandle: Long): IntArray

    @JvmStatic
    external fun nativeGetSelection(editorHandle: Long): IntArray?

    @JvmStatic
    external fun nativeBuildRenderModel(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeGetScrollMetrics(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeHandleGesture(
        editorHandle: Long,
        type: Int,
        points: FloatArray,
        modifiers: Int,
        wheelDeltaX: Float,
        wheelDeltaY: Float,
        directScale: Float,
    ): ByteArray?

    @JvmStatic
    external fun nativeTickAnimations(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeHandleKeyEvent(
        editorHandle: Long,
        keyCode: Int,
        text: String?,
        modifiers: Int,
    ): ByteArray?

    @JvmStatic
    external fun nativeCompositionStart(editorHandle: Long)

    @JvmStatic
    external fun nativeCompositionUpdate(editorHandle: Long, text: String)

    @JvmStatic
    external fun nativeCompositionEnd(editorHandle: Long, committedText: String?): ByteArray?

    @JvmStatic
    external fun nativeCompositionCancel(editorHandle: Long)

    @JvmStatic
    external fun nativeIsComposing(editorHandle: Long): Boolean

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
    external fun nativeBackspace(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeDeleteForward(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeInsertSnippet(editorHandle: Long, template: String): ByteArray?

    @JvmStatic
    external fun nativeStartLinkedEditing(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeIsInLinkedEditing(editorHandle: Long): Boolean

    @JvmStatic
    external fun nativeLinkedEditingNext(editorHandle: Long): Boolean

    @JvmStatic
    external fun nativeLinkedEditingPrev(editorHandle: Long): Boolean

    @JvmStatic
    external fun nativeCancelLinkedEditing(editorHandle: Long)

    @JvmStatic
    external fun nativeMoveLineUp(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeMoveLineDown(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeCopyLineUp(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeCopyLineDown(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeDeleteLine(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeInsertLineAbove(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeInsertLineBelow(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeUndo(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeRedo(editorHandle: Long): ByteArray?

    @JvmStatic
    external fun nativeCanUndo(editorHandle: Long): Boolean

    @JvmStatic
    external fun nativeCanRedo(editorHandle: Long): Boolean

    @JvmStatic
    external fun nativeSelectAll(editorHandle: Long)

    @JvmStatic
    external fun nativeGetSelectedText(editorHandle: Long): String?

    @JvmStatic
    external fun nativeGetWordRangeAtCursor(editorHandle: Long): IntArray

    @JvmStatic
    external fun nativeGetWordAtCursor(editorHandle: Long): String?

    @JvmStatic
    external fun nativeMoveCursorLeft(editorHandle: Long, extendSelection: Boolean)

    @JvmStatic
    external fun nativeMoveCursorRight(editorHandle: Long, extendSelection: Boolean)

    @JvmStatic
    external fun nativeMoveCursorUp(editorHandle: Long, extendSelection: Boolean)

    @JvmStatic
    external fun nativeMoveCursorDown(editorHandle: Long, extendSelection: Boolean)

    @JvmStatic
    external fun nativeMoveCursorToLineStart(editorHandle: Long, extendSelection: Boolean)

    @JvmStatic
    external fun nativeMoveCursorToLineEnd(editorHandle: Long, extendSelection: Boolean)

    @JvmStatic
    external fun nativeScrollToLine(editorHandle: Long, line: Int, behavior: Int)

    @JvmStatic
    external fun nativeGotoPosition(editorHandle: Long, line: Int, column: Int)

    @JvmStatic
    external fun nativeSetScroll(editorHandle: Long, scrollX: Float, scrollY: Float)

    @JvmStatic
    external fun nativeGetPositionRect(editorHandle: Long, line: Int, column: Int): FloatArray

    @JvmStatic
    external fun nativeGetCursorRect(editorHandle: Long): FloatArray

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
    external fun nativeClearInlayHints(editorHandle: Long)

    @JvmStatic
    external fun nativeClearPhantomTexts(editorHandle: Long)

    @JvmStatic
    external fun nativeClearGutterIcons(editorHandle: Long)

    @JvmStatic
    external fun nativeClearDiagnostics(editorHandle: Long)

    @JvmStatic
    external fun nativeSetIndentGuides(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeSetBracketGuides(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeSetFlowGuides(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeSetSeparatorGuides(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeClearGuides(editorHandle: Long)

    @JvmStatic
    external fun nativeSetFoldRegions(editorHandle: Long, data: ByteArray)

    @JvmStatic
    external fun nativeClearAllDecorations(editorHandle: Long)

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
        val platformDirectory = currentPlatformDirectory()
        val archDirectory = currentArchDirectory(platformDirectory)
        val coreLibrary = extractResourceToTemp(
            "native/$platformDirectory/$archDirectory/${currentCoreLibraryName(platformDirectory)}",
        )
        val bridgeLibrary = extractResourceToTemp(
            "native/$platformDirectory/$archDirectory/${System.mapLibraryName("sweeteditor_desktop_bridge")}",
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

    private fun extractResourceToTemp(resourcePath: String): Path {
        val fileName = resourcePath.substringAfterLast('/')
        val targetPath = extractedLibraryDir.resolve(fileName)
        if (Files.notExists(targetPath)) {
            val inputStream = DesktopNativeBindings::class.java.classLoader.getResourceAsStream(resourcePath)
                ?: throw IllegalArgumentException("Native library resource not found: $resourcePath")
            inputStream.use { stream ->
                Files.copy(stream, targetPath, StandardCopyOption.REPLACE_EXISTING)
            }
            targetPath.toFile().deleteOnExit()
        }
        return targetPath
    }

    private fun currentPlatformDirectory(): String {
        val osName = System.getProperty("os.name")
        return when {
            osName.contains("Mac", ignoreCase = true) -> "osx"
            osName.contains("Linux", ignoreCase = true) -> "linux"
            osName.contains("Windows", ignoreCase = true) -> "windows"
            else -> error("Unsupported desktop platform: $osName")
        }
    }

    private fun currentArchDirectory(platformDirectory: String): String {
        val arch = System.getProperty("os.arch")
        return when (platformDirectory) {
            "windows" -> "x64"
            "osx" -> when {
                arch.contains("aarch64", ignoreCase = true) -> "arm64"
                arch.contains("arm64", ignoreCase = true) -> "arm64"
                else -> "x86_64"
            }

            else -> when {
                arch.contains("x86_64", ignoreCase = true) -> "x86_64"
                arch.contains("amd64", ignoreCase = true) -> "x86_64"
                else -> error("Unsupported desktop arch for $platformDirectory: $arch")
            }
        }
    }

    private fun currentCoreLibraryName(platformDirectory: String): String = when (platformDirectory) {
        "osx" -> "libsweeteditor.dylib"
        "linux" -> "libsweeteditor.so"
        "windows" -> "sweeteditor.dll"
        else -> error("Unsupported desktop platform: $platformDirectory")
    }

    private val extractedLibraryDir: Path by lazy {
        Files.createTempDirectory("sweeteditor-desktop-native").also {
            it.toFile().deleteOnExit()
        }
    }
}
