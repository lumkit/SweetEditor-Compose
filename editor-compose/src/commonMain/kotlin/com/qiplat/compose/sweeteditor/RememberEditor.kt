package com.qiplat.compose.sweeteditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.qiplat.compose.sweeteditor.runtime.EditorController
import com.qiplat.compose.sweeteditor.runtime.EditorState
import com.qiplat.compose.sweeteditor.runtime.EditorTextMeasurer

@Composable
fun rememberEditorState(): EditorState = remember {
    EditorState()
}

@Composable
fun rememberSweetEditorController(
    textMeasurer: EditorTextMeasurer,
    state: EditorState = rememberEditorState(),
): SweetEditorController = remember(state, textMeasurer) {
    SweetEditorController(
        textMeasurer = textMeasurer,
        state = state,
    )
}

@Composable
fun rememberEditorController(
    textMeasurer: EditorTextMeasurer,
    state: EditorState = rememberEditorState(),
): EditorController = remember(state, textMeasurer) {
    EditorController(
        state = state,
        textMeasurer = textMeasurer,
    )
}
