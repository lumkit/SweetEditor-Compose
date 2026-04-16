# Decorations

SweetEditor uses decoration providers to supply non-text editing overlays and semantic information.

## What You Can Decorate

- syntax highlight
- semantic highlight
- inlay hints
- phantom text
- diagnostics
- gutter icons
- indent / bracket / flow / separator guides
- fold regions

## Registering A Decoration Provider

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

## Important Merge Rule

For viewport-scoped, line-based decorations, prefer:

- `DecorationApplyMode.ReplaceRange`

This avoids duplicate decorations during scroll and refresh.

## Related APIs

- `DecorationProvider`
- `DecorationProviderContext`
- `DecorationSet`
- `DecorationUpdate`
- `DecorationApplyMode`
