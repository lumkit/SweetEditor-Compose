package com.qiplat.compose.sweeteditor.runtime

import com.qiplat.compose.sweeteditor.*

class CompletionProviderManager {
    private val providers = linkedSetOf<CompletionProvider>()
    private var activeRequestId: Long = 0

    fun addProvider(provider: CompletionProvider) {
        providers += provider
    }

    fun removeProvider(provider: CompletionProvider) {
        providers -= provider
    }

    fun dismiss() {
        activeRequestId += 1
    }

    fun isTriggerCharacter(ch: String): Boolean =
        providers.any { provider ->
            runCatching { provider.isTriggerCharacter(ch) }.getOrDefault(false)
        }

    suspend fun request(context: CompletionContext): CompletionResult? {
        val requestId = activeRequestId + 1
        activeRequestId = requestId
        val providerResults = linkedMapOf<CompletionProvider, CompletionResult>()
        providers.forEach { provider ->
            val receiver = object : CompletionReceiver {
                override fun accept(result: CompletionResult): Boolean {
                    if (activeRequestId != requestId) {
                        return false
                    }
                    providerResults[provider] = result
                    return true
                }

                override fun isCancelled(): Boolean = activeRequestId != requestId
            }
            runCatching {
                provider.provideCompletions(context, receiver)
            }.onFailure {
                providerResults.remove(provider)
            }
            if (receiver.isCancelled()) {
                return null
            }
        }
        return merge(providerResults.values).takeIf { it.items.isNotEmpty() || it.isIncomplete }
    }

    private fun merge(results: Collection<CompletionResult>): CompletionResult {
        val mergedItems = results
            .flatMap { it.items }
            .sortedWith(
                compareBy<CompletionItem> { it.sortKey ?: it.label }
                    .thenBy { it.label },
            )
        return CompletionResult(
            items = mergedItems,
            isIncomplete = results.any { it.isIncomplete },
        )
    }
}
