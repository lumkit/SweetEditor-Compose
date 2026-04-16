# EditorSettings 参考

`EditorSettings` 负责控制运行时编辑器行为，而不是纯粹的配色或字体。

## 字段说明

## `wrapMode`

控制换行行为。

类型：

- `WrapMode`

## `tabSize`

编辑器使用的逻辑 tab 宽度。

类型：

- `Int`

默认值：

- `4`

## `lineSpacingExtra`

附加到每一行的额外行距。

类型：

- `Float`

## `lineSpacingMultiplier`

作用于布局的行高倍率。

类型：

- `Float`

## `foldArrowMode`

控制折叠箭头的行为和显示模式。

类型：

- `FoldArrowMode`

## `gutterSticky`

控制 gutter 是否以 sticky 方式跟随滚动呈现。

类型：

- `Boolean`

## `gutterVisible`

控制 gutter 是否显示。

类型：

- `Boolean`

## `currentLineRenderMode`

控制当前行如何被强调显示。

类型：

- `CurrentLineRenderMode`

## `readOnly`

启用后禁止编辑操作，但仍允许查看和选区交互。

类型：

- `Boolean`

## `compositionEnabled`

控制 IME 组合输入能力是否启用。

类型：

- `Boolean`

## `autoIndentMode`

控制自动缩进模式。

类型：

- `AutoIndentMode`

## 示例

```kotlin
controller.applySettings(
    EditorSettings(
        wrapMode = WrapMode.None,
        tabSize = 4,
        lineSpacingExtra = 1f,
        lineSpacingMultiplier = 1.1f,
        gutterSticky = true,
        gutterVisible = true,
        currentLineRenderMode = CurrentLineRenderMode.Background,
        readOnly = false,
        compositionEnabled = true,
        autoIndentMode = AutoIndentMode.None,
    )
)
```

## 什么时候用 Settings，什么时候用 Theme

`EditorSettings` 负责：

- 行为
- 布局策略
- 交互开关

`EditorTheme` 负责：

- 颜色
- 文本样式映射
- 视觉外观
- 字体相关默认值

## 相关文档

- [主题 / 外观](./theme-appearance.md)
- [主题 Schema](./theme-schema.md)
- [API Cookbook](./api-cookbook.md)
