package com.qiplat.compose.sweeteditor.runtime

import com.qiplat.compose.sweeteditor.NewLineAction
import com.qiplat.compose.sweeteditor.NewLineActionProvider
import com.qiplat.compose.sweeteditor.NewLineContext

class NewLineActionProviderManager {
    private val providers = linkedSetOf<NewLineActionProvider>()

    fun addProvider(provider: NewLineActionProvider) {
        providers += provider
    }

    fun removeProvider(provider: NewLineActionProvider) {
        providers -= provider
    }

    fun request(context: NewLineContext): NewLineAction? {
        providers.forEach { provider ->
            val action = runCatching {
                provider.provideNewLineAction(context)
            }.getOrNull()
            if (action != null) {
                return action
            }
        }
        return null
    }
}
