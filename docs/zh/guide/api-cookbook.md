# API Cookbook

这一页汇总了一组可以直接复制的常见接入配方。

## 加载文本

```kotlin
controller.loadText(
    """
    fun main() {
        println("Hello")
    }
    """.trimIndent()
)
```

## 加载文件

```kotlin
controller.loadFile("/absolute/path/to/file.kt")
```

## 应用主题和设置

```kotlin
val appearance = rememberEditorAppearance(darkMode = true)

LaunchedEffect(controller, appearance) {
    controller.applyTheme(appearance.theme)
    controller.applySettings(EditorSettings())
}
```

## 设置语言配置

```kotlin
controller.setLanguageConfiguration(configuration)
```

## 附加编辑器 Metadata

```kotlin
controller.setMetadata(
    EditorMetadata(
        entries = mapOf(
            "fileName" to "Main.kt",
            "language" to "kotlin",
        )
    )
)
```

## 注册 Decoration Provider

```kotlin
controller.addDecorationProvider(myDecorationProvider)
```

## 注册 Completion Provider

```kotlin
controller.addCompletionProvider(myCompletionProvider)
```

## 提供 Gutter Icon

```kotlin
controller.setEditorIconProvider(myIconProvider)
```

## 显示 Inline Suggestion

```kotlin
controller.inlineSuggestions().show(
    InlineSuggestion(
        line = 4,
        column = 12,
        text = "println(value)",
    )
)
```

## 手动触发 Completion

```kotlin
controller.triggerCompletion()
```

## 关闭 Completion

```kotlin
controller.dismissCompletion()
```

## 监听编辑器事件

```kotlin
val subscription = controller.events().subscribe<TextChangedEvent> { event ->
    println(event.editResult)
}
```

不再需要监听时，记得释放对应的订阅。
