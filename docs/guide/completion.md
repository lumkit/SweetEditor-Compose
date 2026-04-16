# Completion

SweetEditor exposes completion through `CompletionProvider` and controller APIs.

## Register A Completion Provider

```kotlin
val provider = object : CompletionProvider {
    override fun isTriggerCharacter(ch: String): Boolean = ch == "."

    override suspend fun provideCompletions(
        context: CompletionContext,
        receiver: CompletionReceiver,
    ) {
        receiver.accept(
            CompletionResult(
                items = listOf(
                    CompletionItem(
                        label = "println",
                        detail = "Kotlin standard output",
                        insertText = "println()",
                        kind = CompletionItem.KIND_FUNCTION,
                    ),
                )
            )
        )
    }
}

controller.addCompletionProvider(provider)
```

## Trigger Completion

```kotlin
controller.triggerCompletion()
```

## Manual Results

If you already have a completion source, you can push items directly:

```kotlin
controller.showCompletionItems(
    listOf(
        CompletionItem(label = "hello"),
        CompletionItem(label = "world"),
    )
)
```

## Controller APIs

- `addCompletionProvider()`
- `removeCompletionProvider()`
- `triggerCompletion()`
- `dismissCompletion()`
- `showCompletionItems()`
- `selectNextCompletionItem()`
- `selectPreviousCompletionItem()`
