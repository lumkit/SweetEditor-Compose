# Completion

SweetEditor 通过 `CompletionProvider` 与控制器 API 暴露补全能力。

## 注册 Completion Provider

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

## 触发 Completion

```kotlin
controller.triggerCompletion()
```

## 手动推送结果

如果你已经有自己的补全数据源，也可以直接把条目推给控制器：

```kotlin
controller.showCompletionItems(
    listOf(
        CompletionItem(label = "hello"),
        CompletionItem(label = "world"),
    )
)
```

## 常用控制器 API

- `addCompletionProvider()`
- `removeCompletionProvider()`
- `triggerCompletion()`
- `dismissCompletion()`
- `showCompletionItems()`
- `selectNextCompletionItem()`
- `selectPreviousCompletionItem()`
