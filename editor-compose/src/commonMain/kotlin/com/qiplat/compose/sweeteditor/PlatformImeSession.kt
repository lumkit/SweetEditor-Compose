package com.qiplat.compose.sweeteditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.qiplat.compose.sweeteditor.runtime.EditorState

@Composable
internal expect fun InstallPlatformImeSession(
    controller: SweetEditorController,
    state: EditorState,
    isFocused: Boolean,
    isReadOnly: Boolean,
): Modifier
