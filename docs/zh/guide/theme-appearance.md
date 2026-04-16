# 主题 / 外观

SweetEditor 将视觉配置拆分为主题、字体配置、文本测量和运行时设置。

## 主要 API

- `EditorFontConfig`
- `EditorAppearance`
- `rememberEditorAppearance()`
- `rememberEditorTheme()`
- `rememberEditorTextMeasurer()`
- `SweetEditorController.applyTheme()`
- `SweetEditorController.applySettings()`

## 推荐接入方式

```kotlin
@Composable
fun rememberMyEditorController(): SweetEditorController {
    val appearance = rememberEditorAppearance(
        darkMode = true,
        fontConfig = EditorFontConfig(
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            lineNumberFontSize = 13.sp,
            inlayHintFontSize = 12.sp,
        ),
    )

    val controller = rememberSweetEditorController(
        textMeasurer = appearance.textMeasurer,
    )

    LaunchedEffect(controller, appearance) {
        controller.applyTheme(appearance.theme)
        controller.applySettings(
            EditorSettings(
                wrapMode = WrapMode.None,
                gutterVisible = true,
                gutterSticky = true,
            )
        )
    }

    return controller
}
```

## Theme Content

`rememberEditorTheme()` 会解析 `themeContent`，并将其覆盖到内置的亮色或暗色 fallback theme 上。

常见主题键包括：

- `backgroundColor`
- `textColor`
- `cursorColor`
- `selectionColor`
- `lineNumberColor`
- `currentLineColor`
- `guideColor`
- `scrollbarThumbColor`
- `phantomTextColor`
- `diagnosticErrorColor`
- `linkedEditingActiveColor`
- `bracketHighlightBorderColor`

## 字体配置

`EditorFontConfig` 负责控制：

- `fontFamily`
- `fontSize`
- `lineNumberFontSize`
- `inlayHintFontSize`
- `iconSize`

这些值会被 Compose 侧文本测量器和默认主题共同使用。

## 运行时设置

当你需要配置编辑器行为而不是纯视觉配色时，应使用 `EditorSettings`：

- 换行模式
- 只读模式
- 自动缩进模式
- gutter 可见性
- gutter sticky 行为
- 行间距
- 折叠箭头模式
- 当前行渲染模式

## 说明

- Theme 和 Appearance 属于 Compose 层抽象
- 真正参与绘制的仍然是 native core 驱动下的 render model
- 在期望最终颜色和字体生效前，应先调用 `applyTheme()`

## 相关文档

- 英文 [Theme Schema](../../guide/theme-schema.md)
- 英文 [EditorSettings Reference](../../guide/editor-settings-reference.md)
- [API Cookbook](./api-cookbook.md)
