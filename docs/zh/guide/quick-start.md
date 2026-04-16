# 快速开始

这一页给出一个最小可运行的 Compose 集成示例。

## 最小示例

```kotlin
@Composable
fun EditorScreen() {
    val appearance = rememberEditorAppearance()
    val controller = rememberSweetEditorController(
        textMeasurer = appearance.textMeasurer,
    )

    LaunchedEffect(controller) {
        controller.loadText(
            """
            fun hello(name: String) {
                println("Hello, $name")
            }
            """.trimIndent()
        )
        controller.applyTheme(appearance.theme)
    }

    SweetEditor(
        controller = controller,
        modifier = Modifier.fillMaxSize(),
    )
}
```

## 这里发生了什么

- `rememberEditorAppearance()` 创建主题和文本测量资源
- `rememberSweetEditorController()` 创建编辑器主控制器
- `loadText()` 将 UTF-16 文本加载进原生驱动的文档模型
- `applyTheme()` 将主题同步到编辑器运行时
- `SweetEditor()` 负责渲染编辑器表面

## 接下来可以继续

- 英文 [Decorations](../../guide/decorations.md)
- 英文 [Completion](../../guide/completion.md)
- 英文 [Copilot / Inline Suggestion](../../guide/copilot-inline-suggestion.md)
