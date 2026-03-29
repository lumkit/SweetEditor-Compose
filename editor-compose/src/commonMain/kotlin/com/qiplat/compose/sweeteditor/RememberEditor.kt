package com.qiplat.compose.sweeteditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.qiplat.compose.sweeteditor.runtime.EditorController
import com.qiplat.compose.sweeteditor.runtime.EditorState
import com.qiplat.compose.sweeteditor.runtime.EditorTextMeasurer

@Composable
fun rememberEditorState(): EditorState = remember {
    EditorState()
}

@Composable
fun rememberEditorController(
    textMeasurer: EditorTextMeasurer,
    state: EditorState = rememberEditorState(),
): EditorController {
    val controller = remember(state, textMeasurer) {
        EditorController(
            state = state,
            textMeasurer = textMeasurer,
        )
    }
    DisposableEffect(controller) {
        onDispose {
            controller.close()
        }
    }
    return controller
}
