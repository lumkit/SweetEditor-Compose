package com.qiplat.compose.sweeteditor.model.snippet

import com.qiplat.compose.sweeteditor.model.foundation.TextRange

data class TabStopGroup(
    val index: Int,
    val ranges: List<TextRange>,
    val defaultText: String? = null,
)

data class LinkedEditingModel(
    val groups: List<TabStopGroup>,
)
