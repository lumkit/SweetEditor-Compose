package com.qiplat.compose.sweeteditor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Matrix
import android.text.*
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputModifierNode
import androidx.compose.ui.platform.PlatformTextInputSessionScope
import androidx.compose.ui.platform.establishTextInputSession
import androidx.compose.ui.text.input.TextFieldValue
import com.qiplat.compose.sweeteditor.model.foundation.*
import com.qiplat.compose.sweeteditor.model.visual.EditorRenderModel
import com.qiplat.compose.sweeteditor.runtime.EditorDocument
import com.qiplat.compose.sweeteditor.runtime.EditorState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.text.TextRange as ComposeTextRange

@Composable
internal actual fun InstallPlatformImeSession(
    controller: SweetEditorController,
    state: EditorState,
    isFocused: Boolean,
    isReadOnly: Boolean,
): Modifier = AndroidPlatformImeElement(
    controller = controller,
    state = state,
    isFocused = isFocused,
    isReadOnly = isReadOnly,
    gestureResult = state.lastGestureResult,
    editToken = state.lastEditResult,
    cursorToken = state.renderModel?.cursor?.textPosition,
)

private data class AndroidPlatformImeElement(
    val controller: SweetEditorController,
    val state: EditorState,
    val isFocused: Boolean,
    val isReadOnly: Boolean,
    val gestureResult: GestureResult,
    val editToken: Any?,
    val cursorToken: Any?,
) : ModifierNodeElement<AndroidPlatformImeNode>() {
    override fun create(): AndroidPlatformImeNode = AndroidPlatformImeNode()

    override fun update(node: AndroidPlatformImeNode) {
        node.update(
            controller = controller,
            state = state,
            isFocused = isFocused,
            isReadOnly = isReadOnly,
            gestureResult = gestureResult,
        )
    }
}

private class AndroidPlatformImeNode :
    Modifier.Node(),
    FocusEventModifierNode,
    PlatformTextInputModifierNode {
    internal var controller: SweetEditorController? = null
    internal var state: EditorState? = null
    private var externalFocused: Boolean = false
    private var composeFocused: Boolean = false
    private var readOnly: Boolean = false
    private var gestureResult: GestureResult = GestureResult()
    private var sessionJob: Job? = null
    private var activeView: View? = null
    private var activeInputConnection: SweetEditorInputConnection? = null
    private var imeSessionRequested: Boolean = false
    private var pendingKeyboardGesture: GestureResult? = null
    private var keyboardRequested: Boolean = false
    private var lastProxyContext = AndroidImeProxyContext.empty()
    private var extractedTextToken: Int? = null
    private var cursorUpdateMode: Int = 0

    fun update(
        controller: SweetEditorController,
        state: EditorState,
        isFocused: Boolean,
        isReadOnly: Boolean,
        gestureResult: GestureResult,
    ) {
        this.controller = controller
        this.state = state
        this.externalFocused = isFocused
        this.readOnly = isReadOnly
        this.gestureResult = gestureResult
        when (gestureResult.type) {
            GestureType.Tap -> {
                if (!readOnly && gestureResult.hitTarget.type == HitTargetType.None) {
                    imeSessionRequested = true
                    pendingKeyboardGesture = gestureResult
                }
            }

            GestureType.Scroll,
            GestureType.FastScroll,
            GestureType.LongPress,
            GestureType.DragSelect,
            GestureType.Scale,
            GestureType.ContextMenu,
            GestureType.DoubleTap,
            -> pendingKeyboardGesture = null

            GestureType.Undefined,
            -> Unit
        }
        if (readOnly) {
            imeSessionRequested = false
            pendingKeyboardGesture = null
            keyboardRequested = false
        }
        syncSession()
        syncImeState(
            restartInput = false,
            syncEditable = gestureResult.type !in setOf(
                GestureType.Scroll,
                GestureType.FastScroll,
                GestureType.Scale,
            ),
            syncExtractedText = gestureResult.type !in setOf(
                GestureType.Scroll,
                GestureType.FastScroll,
                GestureType.Scale,
            ),
        )
        if (pendingKeyboardGesture != null) {
            maybeShowKeyboard()
        }
    }

    override fun onFocusEvent(focusState: FocusState) {
        composeFocused = focusState.isFocused
        if (!focusState.isFocused && !externalFocused) {
            imeSessionRequested = false
            pendingKeyboardGesture = null
            keyboardRequested = false
        } else if (!readOnly) {
            imeSessionRequested = true
        }
        syncSession()
        maybeShowKeyboard()
        if (!focusState.isFocused && !externalFocused) {
            hideKeyboard()
        }
    }

    override fun onDetach() {
        sessionJob?.cancel()
        sessionJob = null
        activeView = null
        activeInputConnection = null
    }

    private fun syncSession() {
        val controller = controller ?: return
        val state = state ?: return
        val shouldRun = state.document != null && isEditorFocused() && imeSessionRequested && !readOnly
        if (shouldRun) {
            controller.setCompositionEnabled(true)
            if (sessionJob == null) {
                sessionJob = coroutineScope.launch {
                    establishTextInputSession {
                        activeView = view
                        try {
                            maybeShowKeyboard()
                            startInputMethod(createInputRequest())
                        } finally {
                            activeView = null
                        }
                    }
                }
            }
        } else if (sessionJob != null) {
            if (controller.isComposing()) {
                controller.compositionCancel()
            }
            hideKeyboard()
            sessionJob?.cancel()
            sessionJob = null
            activeInputConnection = null
        }
    }

    private fun maybeShowKeyboard() {
        val view = activeView ?: return
        val pendingGesture = pendingKeyboardGesture
        val shouldShow = isEditorFocused() &&
            !readOnly &&
            imeSessionRequested &&
            (pendingGesture != null || !keyboardRequested)
        if (shouldShow) {
            view.requestFocus()
            val imm = view.context.inputMethodManager()
            view.post {
                imm.restartInput(view)
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }
            keyboardRequested = true
            pendingKeyboardGesture = null
        }
    }

    private fun hideKeyboard() {
        keyboardRequested = false
        val view = activeView ?: return
        view.context.inputMethodManager().hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun syncImeState(
        restartInput: Boolean,
        syncEditable: Boolean = true,
        syncExtractedText: Boolean = true,
    ) {
        val view = activeView ?: return
        val controller = controller ?: return
        val proxyContext = buildProxyContext(controller)
        lastProxyContext = proxyContext
        val proxyValue = proxyContext.value
        val selection = proxyValue.selection.normalized(proxyValue.text.length)
        val composition = proxyValue.composition?.normalized(proxyValue.text.length)
        val imm = view.context.inputMethodManager()
        if (syncEditable) {
            activeInputConnection?.syncEditable(proxyContext)
        }
        imm.updateSelection(
            view,
            selection.start,
            selection.end,
            composition?.start ?: -1,
            composition?.end ?: -1,
        )
        if (syncExtractedText) {
            extractedTextToken?.let { token ->
                imm.updateExtractedText(view, token, proxyContext.toExtractedText())
            }
        }
        if (cursorUpdateMode != 0) {
            buildCursorAnchorInfo(view, proxyContext)?.let { anchorInfo ->
                imm.updateCursorAnchorInfo(view, anchorInfo)
            }
        }
        if (restartInput) {
            imm.restartInput(view)
        }
    }

    private fun PlatformTextInputSessionScope.createInputRequest(): PlatformTextInputMethodRequest =
        PlatformTextInputMethodRequest { outAttrs ->
            val proxyValue = currentProxyValue()
            val selection = proxyValue.selection.normalized(proxyValue.text.length)
            outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
            outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_ENTER_ACTION
            outAttrs.initialSelStart = selection.start
            outAttrs.initialSelEnd = selection.end
            outAttrs.initialCapsMode = TextUtils.getCapsMode(
                proxyValue.text,
                selection.end,
                outAttrs.inputType,
            )
            activeView = view
            SweetEditorInputConnection(
                view = view,
                node = this@AndroidPlatformImeNode,
            ).also {
                activeInputConnection = it
                it.syncEditable(lastProxyContext)
            }
        }

    fun currentProxyValue(): TextFieldValue = lastProxyContext.value

    fun currentProxyContext(): AndroidImeProxyContext = lastProxyContext

    fun onConnectionClosed(connection: SweetEditorInputConnection) {
        if (activeInputConnection === connection) {
            activeInputConnection = null
        }
        extractedTextToken = null
    }

    fun handleSetComposingText(text: CharSequence?) {
        val controller = controller ?: return
        if (text.isNullOrEmpty() && deleteSelectionIfAny(controller)) {
            syncImeState(restartInput = false, syncEditable = false, syncExtractedText = false)
            return
        }
        if (!controller.isComposing()) {
            controller.compositionStart()
        }
        controller.compositionUpdate(text?.toString().orEmpty())
        syncImeState(restartInput = false, syncEditable = false, syncExtractedText = false)
    }

    fun handleCommitText(text: CharSequence?) {
        val controller = controller ?: return
        val committedText = text?.toString().orEmpty()
        if (committedText == "\n" || committedText == "\r\n") {
            if (controller.isComposing()) {
                controller.compositionEnd(null)
            }
            if (controller.hasVisibleCompletion()) {
                controller.applySelectedCompletionItem()
            } else {
                controller.performNewLineAction()
            }
        } else if (controller.isComposing()) {
            controller.compositionEnd(committedText.ifEmpty { null })
        } else if (committedText.isEmpty()) {
            deleteSelectionIfAny(controller)
        } else if (committedText.isNotEmpty()) {
            val selection = controller.getSelection()
            if (selection != null && selection.start != selection.end) {
                controller.replaceText(selection, committedText)
            } else {
                controller.insertText(committedText)
            }
        }
        syncImeState(restartInput = false, syncEditable = false, syncExtractedText = false)
    }

    fun handleFinishComposingText() {
        val controller = controller ?: return
        if (controller.isComposing()) {
            controller.compositionEnd(null)
        }
        syncImeState(restartInput = false, syncEditable = false, syncExtractedText = false)
    }

    fun handleDeleteSurroundingText(beforeLength: Int, afterLength: Int) {
        val controller = controller ?: return
        if (deleteSelectionIfAny(controller)) {
            syncImeState(restartInput = false, syncEditable = false, syncExtractedText = false)
            return
        }
        repeat(beforeLength) {
            controller.backspace()
        }
        repeat(afterLength) {
            controller.deleteForward()
        }
        syncImeState(restartInput = false, syncEditable = false, syncExtractedText = false)
    }

    fun handleDeleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int) {
        val controller = controller ?: return
        if (deleteSelectionIfAny(controller)) {
            syncImeState(restartInput = false, syncEditable = false, syncExtractedText = false)
            return
        }
        val selection = lastProxyContext.value.selection.normalized(lastProxyContext.value.text.length)
        val startOffset = lastProxyContext.value.text.offsetByCodePointsSafe(selection.start, -beforeLength)
        val endOffset = lastProxyContext.value.text.offsetByCodePointsSafe(selection.end, afterLength)
        if (startOffset != selection.start || endOffset != selection.end) {
            val startPosition = lastProxyContext.offsetToPosition(startOffset)
            val endPosition = lastProxyContext.offsetToPosition(endOffset)
            controller.deleteText(
                TextRange(
                    start = startPosition,
                    end = endPosition,
                ),
            )
        }
        syncImeState(restartInput = false, syncEditable = false, syncExtractedText = false)
    }

    fun handleSetSelection(start: Int, end: Int) {
        val controller = controller ?: return
        val proxyValue = lastProxyContext.value
        val boundedSelection = ComposeTextRange(start, end).normalized(proxyValue.text.length)
        controller.setSelection(
            TextRange(
                start = lastProxyContext.offsetToPosition(boundedSelection.start),
                end = lastProxyContext.offsetToPosition(boundedSelection.end),
            ),
        )
        syncImeState(restartInput = false, syncEditable = false, syncExtractedText = false)
    }

    fun handleSetComposingRegion(start: Int, end: Int) {
        val controller = controller ?: return
        val proxyValue = lastProxyContext.value
        val boundedRange = ComposeTextRange(start, end).normalized(proxyValue.text.length)
        val composingText = proxyValue.text.substring(boundedRange.start, boundedRange.end)
        controller.setSelection(
            TextRange(
                start = lastProxyContext.offsetToPosition(boundedRange.start),
                end = lastProxyContext.offsetToPosition(boundedRange.end),
            ),
        )
        if (!controller.isComposing()) {
            controller.compositionStart()
        }
        controller.compositionUpdate(composingText)
        syncImeState(restartInput = false, syncEditable = false, syncExtractedText = false)
    }

    fun handleKeyEvent(event: KeyEvent) {
        val controller = controller ?: return
        if (event.action != KeyEvent.ACTION_DOWN) {
            return
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (controller.hasVisibleCompletion()) {
                    controller.selectNextCompletionItem()
                } else {
                    dispatchAndroidKeyCode(controller, event.keyCode, null, event.toNativeModifiers())
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (controller.hasVisibleCompletion()) {
                    controller.selectPreviousCompletionItem()
                } else {
                    dispatchAndroidKeyCode(controller, event.keyCode, null, event.toNativeModifiers())
                }
            }
            KeyEvent.KEYCODE_TAB -> {
                if (!controller.handleTabAction(reverse = event.isShiftPressed)) {
                    dispatchAndroidKeyCode(controller, event.keyCode, "\t", event.toNativeModifiers())
                }
            }
            KeyEvent.KEYCODE_ESCAPE -> {
                controller.handleEscapeAction()
            }
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> {
                if (controller.isComposing()) {
                    controller.compositionEnd(null)
                }
                controller.handleEnterAction()
            }
            KeyEvent.KEYCODE_DEL -> {
                if (!deleteSelectionIfAny(controller)) {
                    controller.backspace()
                }
            }
            KeyEvent.KEYCODE_FORWARD_DEL -> {
                if (!deleteSelectionIfAny(controller)) {
                    controller.deleteForward()
                }
            }
            else -> dispatchAndroidKeyCode(
                controller = controller,
                androidKeyCode = event.keyCode,
                text = event.unicodeChar.takeIf { it != 0 }?.toChar()?.toString(),
                modifiers = event.toNativeModifiers(),
            )
        }
        syncImeState(restartInput = false, syncEditable = false, syncExtractedText = false)
    }

    fun handleEditorAction(actionCode: Int) {
        val controller = controller ?: return
        if (
            actionCode == EditorInfo.IME_ACTION_NONE ||
            actionCode == EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            if (controller.isComposing()) {
                controller.compositionEnd(null)
            }
            controller.handleEnterAction()
        } else if (controller.isComposing()) {
            controller.compositionEnd(null)
        }
        syncImeState(restartInput = false, syncEditable = true, syncExtractedText = true)
    }

    private fun dispatchAndroidKeyCode(
        controller: SweetEditorController,
        androidKeyCode: Int,
        text: String?,
        modifiers: Int,
    ) {
        val mappedKeyCode = androidKeyCode.toEditorKeyCode()
        if (mappedKeyCode != 0) {
            controller.handleKeyEvent(
                keyCode = mappedKeyCode,
                text = null,
                modifiers = modifiers,
            )
        } else {
            controller.handleKeyEvent(
                keyCode = androidKeyCode,
                text = text,
                modifiers = modifiers,
            )
        }
    }

    private fun deleteSelectionIfAny(controller: SweetEditorController): Boolean {
        val proxySelection = lastProxyContext.value.selection.normalized(lastProxyContext.value.text.length)
        if (proxySelection.collapsed) {
            return false
        }
        controller.deleteText(
            TextRange(
                start = lastProxyContext.offsetToPosition(proxySelection.start),
                end = lastProxyContext.offsetToPosition(proxySelection.end),
            ),
        )
        return true
    }

    private fun isEditorFocused(): Boolean =
        externalFocused || composeFocused

    fun updateExtractedTextToken(token: Int?) {
        extractedTextToken = token
        syncImeState(restartInput = false)
    }

    fun updateCursorUpdateMode(mode: Int) {
        cursorUpdateMode = mode
        if ((mode and InputConnection.CURSOR_UPDATE_IMMEDIATE) != 0) {
            syncImeState(restartInput = false)
        }
    }

    private fun buildProxyContext(controller: SweetEditorController): AndroidImeProxyContext {
        val document = state?.document
        if (document == null) {
            val fallbackValue = controller.synchronizeImeProxyValue(lastProxyContext.value)
            return AndroidImeProxyContext.fromSingleValue(fallbackValue, controller.getCursorPosition().line)
        }

        val cursorPosition = controller.getCursorPosition()
        val selectionRange = controller.getSelection() ?: TextRange(cursorPosition, cursorPosition)
        val lineCount = document.getLineCount()
        val firstLine = (minOf(selectionRange.start.line, selectionRange.end.line) - IME_CONTEXT_LINE_WINDOW)
            .coerceAtLeast(0)
        val lastLine = (maxOf(selectionRange.start.line, selectionRange.end.line) + IME_CONTEXT_LINE_WINDOW)
            .coerceAtMost((lineCount - 1).coerceAtLeast(0))
        val lineEntries = buildList {
            for (line in firstLine..lastLine) {
                add(AndroidImeLineEntry(line = line, text = document.safeLineText(line)))
            }
        }
        if (lineEntries.isEmpty()) {
            val fallbackValue = controller.synchronizeImeProxyValue(lastProxyContext.value)
            return AndroidImeProxyContext.fromSingleValue(fallbackValue, cursorPosition.line)
        }

        var runningOffset = 0
        val mappedEntries = lineEntries.mapIndexed { index, entry ->
            val startOffset = runningOffset
            runningOffset += entry.text.length
            if (index < lineEntries.lastIndex) {
                runningOffset += 1
            }
            entry.copy(startOffset = startOffset)
        }

        val text = buildString {
            mappedEntries.forEachIndexed { index, entry ->
                append(entry.text)
                if (index < mappedEntries.lastIndex) {
                    append('\n')
                }
            }
        }

        val selection = ComposeTextRange(
            start = mappedEntries.positionToOffset(selectionRange.start),
            end = mappedEntries.positionToOffset(selectionRange.end),
        ).normalized(text.length)
        val composition = currentProxyValue().composition
            ?.takeIf { controller.isComposing() }
            ?.let {
                val endOffset = selection.end
                val startOffset = (endOffset - (it.end - it.start)).coerceAtLeast(0)
                ComposeTextRange(startOffset, endOffset).normalized(text.length)
            }

        return AndroidImeProxyContext(
            value = TextFieldValue(
                text = text,
                selection = selection,
                composition = composition,
            ),
            lines = mappedEntries,
        )
    }

    private fun buildCursorAnchorInfo(
        view: View,
        proxyContext: AndroidImeProxyContext,
    ): CursorAnchorInfo? {
        val renderModel = state?.renderModel ?: return null
        val builder = CursorAnchorInfo.Builder()
        val screenLocation = IntArray(2)
        view.getLocationOnScreen(screenLocation)
        val matrix = Matrix().apply {
            postTranslate(screenLocation[0].toFloat(), screenLocation[1].toFloat())
        }
        val selection = proxyContext.value.selection.normalized(proxyContext.value.text.length)
        builder.setMatrix(matrix)
        builder.setSelectionRange(selection.start, selection.end)
        val composition = proxyContext.value.composition?.normalized(proxyContext.value.text.length)
        if (composition != null && composition.start < composition.end) {
            builder.setComposingText(
                composition.start,
                proxyContext.value.text.substring(composition.start, composition.end),
            )
        }
        applyInsertionMarker(renderModel, builder)
        return builder.build()
    }

    private fun applyInsertionMarker(
        renderModel: EditorRenderModel,
        builder: CursorAnchorInfo.Builder,
    ) {
        val cursor = renderModel.cursor
        val baseline = renderModel.lines
            .firstOrNull { it.logicalLine == cursor.textPosition.line && it.runs.isNotEmpty() }
            ?.runs
            ?.firstOrNull()
            ?.y
            ?: (cursor.position.y + cursor.height)
        builder.setInsertionMarkerLocation(
            cursor.position.x,
            cursor.position.y,
            baseline,
            cursor.position.y + cursor.height,
            CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION,
        )
    }
}

private class SweetEditorInputConnection(
    view: View,
    private val node: AndroidPlatformImeNode,
) : BaseInputConnection(view, true) {
    private val targetView: View = view
    private val editable = SpannableStringBuilder()

    override fun getEditable(): Editable {
        syncEditable(node.currentProxyContext())
        return editable
    }

    fun syncEditable(proxyContext: AndroidImeProxyContext) {
        val value = proxyContext.value
        editable.replace(0, editable.length, value.text)
        val selection = value.selection.normalized(value.text.length)
        Selection.setSelection(editable, selection.start, selection.end)
        BaseInputConnection.removeComposingSpans(editable)
        value.composition?.normalized(value.text.length)?.let { composition ->
            if (composition.start < composition.end) {
                editable.setSpan(
                    COMPOSING_SPAN,
                    composition.start,
                    composition.end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or Spanned.SPAN_COMPOSING,
                )
            }
        }
    }

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
        val value = node.currentProxyValue()
        val selection = value.selection.normalized(value.text.length)
        return node.readTextBeforeCursor(selection.start, n)
    }

    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence {
        val value = node.currentProxyValue()
        val selection = value.selection.normalized(value.text.length)
        return node.readTextAfterCursor(selection.end, n)
    }

    override fun getSelectedText(flags: Int): CharSequence {
        return node.readSelectedText()
    }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        node.handleSetComposingText(text)
        return true
    }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        node.handleCommitText(text)
        return true
    }

    override fun finishComposingText(): Boolean {
        node.handleFinishComposingText()
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        node.handleDeleteSurroundingText(beforeLength, afterLength)
        return true
    }

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        node.handleDeleteSurroundingTextInCodePoints(beforeLength, afterLength)
        return true
    }

    override fun setSelection(start: Int, end: Int): Boolean {
        node.handleSetSelection(start, end)
        return true
    }

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        node.handleSetComposingRegion(start, end)
        return true
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        node.handleKeyEvent(event)
        return true
    }

    override fun performEditorAction(actionCode: Int): Boolean {
        node.handleEditorAction(actionCode)
        return true
    }

    override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean {
        node.updateCursorUpdateMode(cursorUpdateMode)
        return true
    }

    override fun getCursorCapsMode(reqModes: Int): Int {
        val proxyValue = node.currentProxyValue()
        val selection = proxyValue.selection.normalized(proxyValue.text.length)
        return TextUtils.getCapsMode(proxyValue.text, selection.end, reqModes)
    }

    override fun performContextMenuAction(id: Int): Boolean {
        if (id == android.R.id.selectAll) {
            val context = node.currentProxyContext()
            val textLength = context.value.text.length
            node.handleSetSelection(0, textLength)
            return true
        }
        if (id == android.R.id.copy) {
            val selectedText = node.readSelectedText()
            if (selectedText.isEmpty()) {
                return true
            }
            targetView.context.clipboardManager().setPrimaryClip(
                ClipData.newPlainText("SweetEditor Selection", selectedText),
            )
            return true
        }
        if (id == android.R.id.cut) {
            val selectedText = node.readSelectedText()
            if (selectedText.isEmpty()) {
                return true
            }
            targetView.context.clipboardManager().setPrimaryClip(
                ClipData.newPlainText("SweetEditor Selection", selectedText),
            )
            node.handleDeleteSurroundingText(0, 0)
            return true
        }
        if (id == android.R.id.paste) {
            val text = targetView.context.clipboardManager()
                .primaryClip
                ?.getItemAt(0)
                ?.coerceToText(targetView.context)
                ?.toString()
                .orEmpty()
            if (text.isNotEmpty()) {
                node.handleCommitText(text)
            }
            return true
        }
        return super.performContextMenuAction(id)
    }

    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText {
        node.updateExtractedTextToken(request?.token)
        return node.currentProxyContext().toExtractedText()
    }

    override fun closeConnection() {
        node.updateExtractedTextToken(null)
        node.onConnectionClosed(this)
        super.closeConnection()
    }
}

private fun ComposeTextRange.normalized(textLength: Int): ComposeTextRange {
    val normalizedStart = start.coerceIn(0, textLength)
    val normalizedEnd = end.coerceIn(0, textLength)
    return if (normalizedStart <= normalizedEnd) {
        ComposeTextRange(normalizedStart, normalizedEnd)
    } else {
        ComposeTextRange(normalizedEnd, normalizedStart)
    }
}

private fun Context.inputMethodManager(): InputMethodManager =
    getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

private fun Context.clipboardManager(): ClipboardManager =
    getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

private fun AndroidPlatformImeNode.readTextBeforeCursor(selectionStart: Int, maxChars: Int): String {
    val limitedChars = maxChars.coerceAtMost(MAX_IME_TEXT_LENGTH)
    val proxyValue = currentProxyValue()
    return proxyValue.text
        .substring(0, selectionStart.coerceIn(0, proxyValue.text.length))
        .takeLast(limitedChars)
}

private fun AndroidPlatformImeNode.readTextAfterCursor(selectionEnd: Int, maxChars: Int): String {
    val limitedChars = maxChars.coerceAtMost(MAX_IME_TEXT_LENGTH)
    val proxyValue = currentProxyValue()
    return proxyValue.text
        .substring(selectionEnd.coerceIn(0, proxyValue.text.length))
        .take(limitedChars)
}

private fun AndroidPlatformImeNode.readSelectedText(): String {
    val proxyValue = currentProxyValue()
    val selection = proxyValue.selection.normalized(proxyValue.text.length)
    return if (selection.collapsed) "" else proxyValue.text.substring(selection.start, selection.end)
}

private fun EditorDocument.safeLineText(line: Int): String =
    if (line in 0 until getLineCount()) {
        getLineText(line)
    } else {
        ""
    }

private data class AndroidImeLineEntry(
    val line: Int,
    val text: String,
    val startOffset: Int = 0,
)

private data class AndroidImeProxyContext(
    val value: TextFieldValue,
    val lines: List<AndroidImeLineEntry>,
) {
    fun offsetToPosition(offset: Int): TextPosition {
        if (lines.isEmpty()) {
            return TextPosition.Zero
        }
        val boundedOffset = offset.coerceIn(0, value.text.length)
        val lineEntry = lines.lastOrNull { it.startOffset <= boundedOffset } ?: lines.first()
        val localOffset = (boundedOffset - lineEntry.startOffset).coerceIn(0, lineEntry.text.length)
        return TextPosition(
            line = lineEntry.line,
            column = localOffset,
        )
    }

    companion object {
        fun empty(): AndroidImeProxyContext = AndroidImeProxyContext(
            value = TextFieldValue(),
            lines = emptyList(),
        )

        fun fromSingleValue(value: TextFieldValue, line: Int): AndroidImeProxyContext =
            AndroidImeProxyContext(
                value = value,
                lines = listOf(AndroidImeLineEntry(line = line, text = value.text, startOffset = 0)),
            )
    }
}

private fun AndroidImeProxyContext.toExtractedText(): ExtractedText {
    val selection = value.selection.normalized(value.text.length)
    return ExtractedText().apply {
        text = value.text
        startOffset = 0
        partialStartOffset = -1
        partialEndOffset = -1
        selectionStart = selection.start
        selectionEnd = selection.end
        flags = if (value.text.indexOf('\n') >= 0) 0 else ExtractedText.FLAG_SINGLE_LINE
    }
}

private fun List<AndroidImeLineEntry>.positionToOffset(position: TextPosition): Int {
    val entry = firstOrNull { it.line == position.line } ?: return 0
    return entry.startOffset + position.column.coerceIn(0, entry.text.length)
}

private fun String.offsetByCodePointsSafe(index: Int, codePointDelta: Int): Int =
    when {
        isEmpty() -> 0
        codePointDelta == 0 -> index.coerceIn(0, length)
        else -> runCatching {
            offsetByCodePoints(index.coerceIn(0, length), codePointDelta)
        }.getOrElse {
            if (codePointDelta > 0) {
                length
            } else {
                0
            }
        }
    }

private const val MAX_IME_TEXT_LENGTH = 2048
private const val IME_CONTEXT_LINE_WINDOW = 8
private object COMPOSING_SPAN

private fun Int.toEditorKeyCode(): Int = when (this) {
    KeyEvent.KEYCODE_DEL -> 8
    KeyEvent.KEYCODE_TAB -> 9
    KeyEvent.KEYCODE_ENTER -> 13
    KeyEvent.KEYCODE_ESCAPE -> 27
    KeyEvent.KEYCODE_FORWARD_DEL -> 46
    KeyEvent.KEYCODE_DPAD_LEFT -> 37
    KeyEvent.KEYCODE_DPAD_UP -> 38
    KeyEvent.KEYCODE_DPAD_RIGHT -> 39
    KeyEvent.KEYCODE_DPAD_DOWN -> 40
    KeyEvent.KEYCODE_MOVE_HOME -> 36
    KeyEvent.KEYCODE_MOVE_END -> 35
    KeyEvent.KEYCODE_PAGE_UP -> 33
    KeyEvent.KEYCODE_PAGE_DOWN -> 34
    else -> 0
}

private fun KeyEvent.toNativeModifiers(): Int {
    var value = 0
    if (isShiftPressed) {
        value = value or 1
    }
    if (isCtrlPressed) {
        value = value or 2
    }
    if (isAltPressed) {
        value = value or 4
    }
    if (isMetaPressed) {
        value = value or 8
    }
    return value
}
