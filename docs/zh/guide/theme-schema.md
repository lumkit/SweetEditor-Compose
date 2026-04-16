# 主题 Schema

这一页说明 `rememberEditorTheme()` 当前支持解析的主题字段。

## 主题解析方式

- `rememberEditorTheme()` 会先从内置亮色或暗色 fallback theme 开始
- `themeContent` 会在 Compose 层被解析
- 匹配到的键会覆盖 fallback 值
- 字体相关设置会从 `EditorFontConfig` 合并
- `textStyles` 可以通过命名别名进行扩展

## 颜色字段

当前支持的顶层颜色键包括：

- `backgroundColor`
- `textColor`
- `cursorColor`
- `selectionColor`
- `lineNumberColor`
- `currentLineNumberColor`
- `currentLineColor`
- `guideColor`
- `separatorLineColor`
- `splitLineColor`
- `scrollbarTrackColor`
- `scrollbarThumbColor`
- `scrollbarThumbActiveColor`
- `compositionUnderlineColor`
- `inlayHintBackgroundColor`
- `inlayHintTextColor`
- `foldPlaceholderBackgroundColor`
- `foldPlaceholderTextColor`
- `phantomTextColor`
- `inlayHintIconColor`
- `diagnosticErrorColor`
- `diagnosticWarningColor`
- `diagnosticInfoColor`
- `diagnosticHintColor`
- `linkedEditingActiveColor`
- `linkedEditingInactiveColor`
- `bracketHighlightBorderColor`
- `bracketHighlightBackgroundColor`
- `gutterBackgroundColor`

## `textStyles` 对象

`textStyles` 可以定义语法/语义高亮的别名样式。

支持的别名包括：

- `keyword`
- `string`
- `comment`
- `number`
- `builtin`
- `type`
- `class`
- `interface`
- `enum`
- `struct`
- `function`
- `method`
- `variable`
- `property`
- `parameter`
- `constant`
- `field`
- `namespace`
- `module`
- `enum_member`
- `operator`
- `punctuation`
- `annotation`
- `preprocessor`

## 最小示例

```json
{
  "backgroundColor": "#1B1E24",
  "textColor": "#D7DEE9",
  "cursorColor": "#8FB8FF",
  "phantomTextColor": "#8AA3B5D1",
  "textStyles": {
    "keyword": {
      "color": "#7AA2F7",
      "fontStyle": "bold"
    },
    "comment": {
      "color": "#7A8294",
      "fontStyle": "italic"
    }
  }
}
```

## 值格式

颜色值可以写成：

- `#RRGGBB`
- `#AARRGGBB`
- 有符号整数

## 字体样式说明

在 `textStyles` 中，常见的字体样式标记包括：

- `bold`
- `italic`

## 什么不属于 Theme Schema

Theme content **不会**替代下面这些运行时行为设置：

- wrap mode
- read-only mode
- gutter sticky 行为
- current line render mode

这些应通过 `EditorSettings` 来配置。

## 相关文档

- [主题 / 外观](./theme-appearance.md)
- [EditorSettings 参考](./editor-settings-reference.md)
