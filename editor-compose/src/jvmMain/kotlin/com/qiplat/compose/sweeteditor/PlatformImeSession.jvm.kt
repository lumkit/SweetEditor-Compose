package com.qiplat.compose.sweeteditor

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.text.input.*
import com.qiplat.compose.sweeteditor.runtime.EditorState

@Composable
internal actual fun InstallPlatformImeSession(
    controller: SweetEditorController,
    state: EditorState,
    isFocused: Boolean,
    isReadOnly: Boolean,
): Modifier {
    val textInputService = LocalTextInputService.current
    val imeEditProcessor = remember { EditProcessor() }
    var imeValue by remember { mutableStateOf(TextFieldValue()) }
    var imeSession by remember { mutableStateOf<TextInputSession?>(null) }

    LaunchedEffect(
        isFocused,
        state.lastEditResult,
        state.lastGestureResult,
        state.renderModel?.cursor?.textPosition,
    ) {
        if (!isFocused || isReadOnly) {
            return@LaunchedEffect
        }
        if (controller.isComposing()) {
            return@LaunchedEffect
        }
        val oldValue = imeValue
        val synchronizedValue = controller.synchronizeImeProxyValue(oldValue)
        if (synchronizedValue != oldValue) {
            imeValue = synchronizedValue
            imeSession?.updateState(oldValue, synchronizedValue)
            imeEditProcessor.reset(synchronizedValue, imeSession)
        }
    }

    LaunchedEffect(
        isFocused,
        isReadOnly,
        imeSession,
        state.renderModel?.cursor?.position?.x,
        state.renderModel?.cursor?.position?.y,
        state.renderModel?.cursor?.height,
    ) {
        if (!isFocused || isReadOnly) {
            return@LaunchedEffect
        }
        val session = imeSession ?: return@LaunchedEffect
        val cursor = state.renderModel?.cursor ?: return@LaunchedEffect
        session.notifyFocusedRect(
            Rect(
                left = cursor.position.x,
                top = cursor.position.y,
                right = cursor.position.x + 1f,
                bottom = cursor.position.y + cursor.height.coerceAtLeast(1f),
            ),
        )
    }

    LaunchedEffect(isFocused, isReadOnly, textInputService, controller, state.document) {
        if (textInputService == null || state.document == null) {
            imeSession = null
            return@LaunchedEffect
        }
        if (isFocused && !isReadOnly) {
            controller.setCompositionEnabled(true)
            if (imeSession == null) {
                val session = textInputService.startInput(
                    value = imeValue,
                    imeOptions = ImeOptions.Default,
                    onEditCommand = { commands ->
                        val oldValue = imeValue
                        val newValue = imeEditProcessor.apply(commands)
                        val normalizedValue = controller.editorController.applyImeProxyValueChange(
                            previousValue = oldValue,
                            newValue = newValue,
                        )
                        imeValue = normalizedValue
                        imeSession?.updateState(oldValue, normalizedValue)
                        imeEditProcessor.reset(normalizedValue, imeSession)
                    },
                    onImeActionPerformed = { action: ImeAction ->
                        val oldValue = imeValue
                        val normalizedValue = controller.handleImeAction(action, oldValue)
                        imeValue = normalizedValue
                        imeSession?.updateState(oldValue, normalizedValue)
                        imeEditProcessor.reset(normalizedValue, imeSession)
                    },
                )
                imeSession = session
                imeEditProcessor.reset(imeValue, session)
                textInputService.showSoftwareKeyboard()
            }
        } else {
            imeSession?.let(textInputService::stopInput)
            imeSession = null
            if (controller.isComposing()) {
                controller.compositionCancel()
            }
            val clearedValue = TextFieldValue()
            imeValue = clearedValue
            imeEditProcessor.reset(clearedValue, null)
            textInputService.hideSoftwareKeyboard()
        }
    }
    return Modifier
}
