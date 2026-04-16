# Copilot / Inline Suggestion

SweetEditor 在 Compose 层提供了一套基于 phantom text 的 inline suggestion 能力。

## 主要类型

- `InlineSuggestion`
- `InlineSuggestionController`
- `InlineSuggestionListener`

## 显示一个 Suggestion

```kotlin
controller.inlineSuggestions().show(
    InlineSuggestion(
        line = 12,
        column = 8,
        text = "println(value)",
    )
)
```

## 内置交互

当 suggestion 可见时：

- `Tab` 接受 suggestion
- `Escape` 关闭 suggestion
- action bar 也可以执行接受或关闭

## 监听接受 / 关闭事件

```kotlin
controller.inlineSuggestions().setListener(
    object : InlineSuggestionListener {
        override fun onSuggestionAccepted(suggestion: InlineSuggestion) {
            println("accepted: ${suggestion.text}")
        }

        override fun onSuggestionDismissed(suggestion: InlineSuggestion) {
            println("dismissed: ${suggestion.text}")
        }
    }
)
```

## 说明

- Inline suggestion 当前是在 Compose/Kotlin 层实现的
- 不需要额外修改 C++ core
- 渲染依赖 phantom text decoration 更新
