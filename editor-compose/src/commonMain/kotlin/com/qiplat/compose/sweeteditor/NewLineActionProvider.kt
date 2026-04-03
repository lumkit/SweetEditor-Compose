package com.qiplat.compose.sweeteditor

import com.qiplat.compose.sweeteditor.theme.LanguageConfiguration

data class NewLineAction(
    val text: String,
)

data class NewLineContext(
    val lineNumber: Int,
    val column: Int,
    val lineText: String,
    val languageConfiguration: LanguageConfiguration?,
    val editorMetadata: EditorMetadata?,
)

fun interface NewLineActionProvider {
    fun provideNewLineAction(context: NewLineContext): NewLineAction?
}
