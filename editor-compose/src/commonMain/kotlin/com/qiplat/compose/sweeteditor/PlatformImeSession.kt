package com.qiplat.compose.sweeteditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.qiplat.compose.sweeteditor.runtime.SweetEditorState

@Composable
internal expect fun InstallPlatformImeSession(
    controller: SweetEditorController,
    state: SweetEditorState,
    isFocused: Boolean,
    isReadOnly: Boolean,
): Modifier
