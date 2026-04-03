package com.qiplat.compose.sweeteditor

import com.qiplat.compose.sweeteditor.model.foundation.TextPosition
import com.qiplat.compose.sweeteditor.model.foundation.TextRange
import com.qiplat.compose.sweeteditor.theme.LanguageConfiguration

enum class CompletionTriggerKind {
    Invoked,
    Character,
    Retrigger,
}

data class CompletionTextEdit(
    val range: TextRange,
    val newText: String,
)

data class CompletionItem(
    val label: String,
    val detail: String? = null,
    val insertText: String? = null,
    val insertTextFormat: Int = PLAIN_TEXT,
    val textEdit: CompletionTextEdit? = null,
    val filterText: String? = null,
    val sortKey: String? = null,
    val kind: Int = KIND_TEXT,
) {
    companion object {
        const val PLAIN_TEXT: Int = 1
        const val SNIPPET: Int = 2

        const val KIND_KEYWORD: Int = 0
        const val KIND_FUNCTION: Int = 1
        const val KIND_VARIABLE: Int = 2
        const val KIND_CLASS: Int = 3
        const val KIND_INTERFACE: Int = 4
        const val KIND_MODULE: Int = 5
        const val KIND_PROPERTY: Int = 6
        const val KIND_SNIPPET: Int = 7
        const val KIND_TEXT: Int = 8
    }
}

data class CompletionResult(
    val items: List<CompletionItem> = emptyList(),
    val isIncomplete: Boolean = false,
)

data class CompletionContext(
    val triggerKind: CompletionTriggerKind,
    val triggerCharacter: String?,
    val cursorPosition: TextPosition,
    val lineText: String,
    val wordRange: TextRange,
    val languageConfiguration: LanguageConfiguration?,
    val editorMetadata: EditorMetadata?,
)

fun interface CompletionItemRenderer {
    fun render(item: CompletionItem): String
}

interface CompletionReceiver {
    fun accept(result: CompletionResult): Boolean

    fun isCancelled(): Boolean
}

interface CompletionProvider {
    fun isTriggerCharacter(ch: String): Boolean = false

    suspend fun provideCompletions(
        context: CompletionContext,
        receiver: CompletionReceiver,
    )
}
