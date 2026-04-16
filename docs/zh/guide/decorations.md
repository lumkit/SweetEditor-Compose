# Decorations

SweetEditor 使用 decoration provider 为编辑器补充非纯文本编辑相关的覆盖层和语义信息。

## 可以装饰哪些内容

- syntax highlight
- semantic highlight
- inlay hints
- phantom text
- diagnostics
- gutter icons
- indent / bracket / flow / separator guides
- fold regions

## 注册 Decoration Provider

```kotlin
val provider = object : DecorationProvider {
    override val id: String = "demo.decorations"
    override val capabilities: Set<DecorationType> = setOf(
        DecorationType.InlayHint,
        DecorationType.Diagnostic,
        DecorationType.PhantomText,
    )

    override suspend fun provide(context: DecorationProviderContext): DecorationUpdate {
        return DecorationUpdate(
            decorations = DecorationSet(
                inlayHints = mapOf(
                    0 to listOf(
                        InlayHint(
                            type = InlayType.Text,
                            column = 3,
                            text = "value: "
                        )
                    )
                )
            ),
            applyMode = DecorationApplyMode.ReplaceRange,
            lineRange = context.requestedLineRange,
        )
    }
}

controller.addDecorationProvider(provider)
```

## 一个重要的合并规则

对于按可视区、按行重新计算的 decoration，优先使用：

- `DecorationApplyMode.ReplaceRange`

这样可以避免滚动和刷新过程中不断重复追加 decoration。

## 相关 API

- `DecorationProvider`
- `DecorationProviderContext`
- `DecorationSet`
- `DecorationUpdate`
- `DecorationApplyMode`
