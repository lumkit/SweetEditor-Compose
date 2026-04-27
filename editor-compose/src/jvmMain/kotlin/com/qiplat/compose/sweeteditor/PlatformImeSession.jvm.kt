package com.qiplat.compose.sweeteditor

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.text.input.*
import com.qiplat.compose.sweeteditor.runtime.SweetEditorState

@Composable
internal actual fun InstallPlatformImeSession(
    controller: SweetEditorController,
    state: SweetEditorState,
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
        if (shouldTearDownJvmImeSession(
                textInputServiceAvailable = textInputService != null,
                documentAvailable = state.document != null,
                isFocused = isFocused,
                isReadOnly = isReadOnly,
            )
        ) {
            val teardownPlan = computeJvmImeTeardownPlan(
                textInputServiceAvailable = textInputService != null,
                sessionActive = imeSession != null,
                isComposing = controller.isComposing(),
                currentValue = imeValue,
            )
            if (teardownPlan.stopInput) {
                imeSession?.let(textInputService!!::stopInput)
            }
            imeSession = null
            if (teardownPlan.cancelComposition) {
                controller.compositionCancel()
            }
            if (teardownPlan.clearValue) {
                val clearedValue = TextFieldValue()
                imeValue = clearedValue
                imeEditProcessor.reset(clearedValue, null)
            }
            if (teardownPlan.hideKeyboard) {
                textInputService?.hideSoftwareKeyboard()
            }
            return@LaunchedEffect
        }
        val activeTextInputService = requireNotNull(textInputService)
        if (isFocused && !isReadOnly) {
            controller.setCompositionEnabled(true)
            if (imeSession == null) {
                val session = activeTextInputService.startInput(
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
                        if (shouldSyncJvmImeState(oldValue, normalizedValue)) {
                            imeSession?.updateState(oldValue, normalizedValue)
                            imeEditProcessor.reset(normalizedValue, imeSession)
                        }
                    },
                    onImeActionPerformed = { action: ImeAction ->
                        val oldValue = imeValue
                        val normalizedValue = controller.handleImeAction(action, oldValue)
                        imeValue = normalizedValue
                        if (shouldSyncJvmImeState(oldValue, normalizedValue)) {
                            imeSession?.updateState(oldValue, normalizedValue)
                            imeEditProcessor.reset(normalizedValue, imeSession)
                        }
                    },
                )
                imeSession = session
                imeEditProcessor.reset(imeValue, session)
                activeTextInputService.showSoftwareKeyboard()
            }
        }
    }
    return Modifier
}

internal data class JvmImeTeardownPlan(
    val stopInput: Boolean,
    val cancelComposition: Boolean,
    val clearValue: Boolean,
    val hideKeyboard: Boolean,
)

internal fun shouldTearDownJvmImeSession(
    textInputServiceAvailable: Boolean,
    documentAvailable: Boolean,
    isFocused: Boolean,
    isReadOnly: Boolean,
): Boolean = !textInputServiceAvailable || !documentAvailable || !isFocused || isReadOnly

internal fun computeJvmImeTeardownPlan(
    textInputServiceAvailable: Boolean,
    sessionActive: Boolean,
    isComposing: Boolean,
    currentValue: TextFieldValue,
): JvmImeTeardownPlan {
    val hasImeState = currentValue != TextFieldValue()
    return JvmImeTeardownPlan(
        stopInput = textInputServiceAvailable && sessionActive,
        cancelComposition = isComposing,
        clearValue = hasImeState,
        hideKeyboard = textInputServiceAvailable && (sessionActive || isComposing || hasImeState),
    )
}

internal fun shouldSyncJvmImeState(
    previousValue: TextFieldValue,
    nextValue: TextFieldValue,
): Boolean = previousValue != nextValue
