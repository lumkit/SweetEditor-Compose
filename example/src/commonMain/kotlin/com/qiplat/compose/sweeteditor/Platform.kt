package com.qiplat.compose.sweeteditor

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform