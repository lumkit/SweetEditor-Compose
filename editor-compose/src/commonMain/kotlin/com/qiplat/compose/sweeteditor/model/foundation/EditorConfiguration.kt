package com.qiplat.compose.sweeteditor.model.foundation

data class EditorOptions(
    val touchSlop: Float = 10f,
    val doubleTapTimeout: Long = 300L,
    val longPressMs: Long = 500L,
    val flingFriction: Float = 3.5f,
    val flingMinVelocity: Float = 50f,
    val flingMaxVelocity: Float = 8000f,
    val maxUndoStackSize: Long = 512L,
)
