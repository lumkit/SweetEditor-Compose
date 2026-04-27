package com.qiplat.compose.sweeteditor.copilot

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import com.qiplat.compose.sweeteditor.*
import com.qiplat.compose.sweeteditor.model.decoration.PhantomText

class InlineSuggestionController internal constructor(
    private val editorController: SweetEditorController,
) {
    private val suggestionProvider = InlineSuggestionDecorationProvider {
        inlineSuggestionState.value
    }
    private val inlineSuggestionState = mutableStateOf<InlineSuggestion?>(null)
    private var listener: InlineSuggestionListener? = null
    private var isProviderAttached: Boolean = false
    private var subscriptions: List<SweetEditorEventSubscription> = emptyList()

    val suggestionState: State<InlineSuggestion?> = inlineSuggestionState

    fun currentSuggestion(): InlineSuggestion? = inlineSuggestionState.value

    fun isShowing(): Boolean = inlineSuggestionState.value != null

    fun setListener(listener: InlineSuggestionListener?) {
        this.listener = listener
    }

    fun show(suggestion: InlineSuggestion?) {
        if (suggestion == null || suggestion.text.isEmpty()) {
            return
        }
        if (inlineSuggestionState.value == suggestion) {
            return
        }
        clearCurrentSuggestion(notifyDismiss = false)
        editorController.dismissCompletion()
        ensureProviderAttached()
        ensureSubscriptions()
        inlineSuggestionState.value = suggestion
        editorController.requestDecorationRefresh()
    }

    fun accept() {
        val suggestion = inlineSuggestionState.value ?: return
        clearCurrentSuggestion(notifyDismiss = false)
        editorController.setCursorPosition(
            com.qiplat.compose.sweeteditor.model.foundation.TextPosition(
                line = suggestion.line,
                column = suggestion.column,
            ),
        )
        editorController.insertText(suggestion.text)
        listener?.onSuggestionAccepted(suggestion)
    }

    fun dismiss() {
        clearCurrentSuggestion(notifyDismiss = true)
    }

    fun handleKeyEvent(key: Key): Boolean {
        if (!isShowing()) {
            return false
        }
        return when (key) {
            Key.Tab -> {
                accept()
                true
            }

            Key.Escape -> {
                dismiss()
                true
            }

            else -> false
        }
    }

    internal fun dispose() {
        clearCurrentSuggestion(notifyDismiss = false)
        subscriptions.forEach(SweetEditorEventSubscription::dispose)
        subscriptions = emptyList()
        if (isProviderAttached) {
            editorController.removeDecorationProvider(suggestionProvider)
            isProviderAttached = false
        }
        listener = null
    }

    private fun ensureProviderAttached() {
        if (isProviderAttached) {
            return
        }
        editorController.addDecorationProvider(suggestionProvider)
        isProviderAttached = true
    }

    private fun ensureSubscriptions() {
        if (subscriptions.isNotEmpty()) {
            return
        }
        subscriptions = listOf(
            editorController.events().subscribe<TextChangedEvent> {
                clearCurrentSuggestion(notifyDismiss = true)
            },
            editorController.events().subscribe<CursorChangedEvent> {
                clearCurrentSuggestion(notifyDismiss = true)
            },
            editorController.events().subscribe<DocumentLoadedEvent> {
                clearCurrentSuggestion(notifyDismiss = true)
            },
        )
    }

    private fun clearCurrentSuggestion(notifyDismiss: Boolean) {
        val previous = inlineSuggestionState.value ?: return
        inlineSuggestionState.value = null
        editorController.requestDecorationRefresh()
        if (notifyDismiss) {
            listener?.onSuggestionDismissed(previous)
        }
    }
}

internal fun buildInlineSuggestionPhantomTexts(
    suggestion: InlineSuggestion?,
    requestedLineRange: IntRange,
): Map<Int, List<PhantomText>> {
    if (suggestion == null || suggestion.text.isEmpty() || suggestion.line !in requestedLineRange) {
        return emptyMap()
    }
    return mapOf(
        suggestion.line to listOf(
            PhantomText(
                column = suggestion.column,
                text = suggestion.text,
            ),
        ),
    )
}

private class InlineSuggestionDecorationProvider(
    private val suggestionProvider: () -> InlineSuggestion?,
) : DecorationProvider {
    override val id: String = "editor.inlineSuggestion"
    override val capabilities: Set<DecorationType> = setOf(DecorationType.PhantomText)

    override suspend fun provide(context: DecorationProviderContext): DecorationUpdate {
        val suggestion = suggestionProvider()
        return DecorationUpdate(
            decorations = DecorationSet(
                phantomTexts = buildInlineSuggestionPhantomTexts(suggestion, context.requestedLineRange),
            ),
            applyMode = DecorationApplyMode.ReplaceAll,
            lineRange = context.requestedLineRange,
        )
    }
}
