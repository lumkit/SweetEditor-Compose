package com.qiplat.compose.sweeteditor

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.text.input.*
import com.qiplat.compose.sweeteditor.runtime.EditorController
import com.qiplat.compose.sweeteditor.runtime.EditorState

@Composable
internal actual fun InstallPlatformImeSession(
    controller: EditorController,
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
        val oldValue = imeValue
        val synchronizedValue = controller.synchronizeImeProxyValue(oldValue)
        if (synchronizedValue != oldValue) {
            imeValue = synchronizedValue
            imeSession?.updateState(oldValue, synchronizedValue)
            imeEditProcessor.reset(synchronizedValue, imeSession)
        }
    }

    LaunchedEffect(isFocused, isReadOnly, textInputService, controller, state.document) {
        val service = textInputService
        if (service == null || state.document == null) {
            imeSession = null
            return@LaunchedEffect
        }
        if (isFocused && !isReadOnly) {
            controller.setCompositionEnabled(true)
            if (imeSession == null) {
                val session = service.startInput(
                    value = imeValue,
                    imeOptions = ImeOptions.Default,
                    onEditCommand = { commands ->
                        val oldValue = imeValue
                        val newValue = imeEditProcessor.apply(commands)
                        val normalizedValue = controller.handleImeEditCommands(
                            commands = commands,
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
                service.showSoftwareKeyboard()
            }
        } else {
            imeSession?.let(service::stopInput)
            imeSession = null
            if (controller.isComposing()) {
                controller.compositionCancel()
            }
            val clearedValue = TextFieldValue()
            imeValue = clearedValue
            imeEditProcessor.reset(clearedValue, null)
            service.hideSoftwareKeyboard()
        }
    }
    return Modifier
}
