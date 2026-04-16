# 排障

这一页汇总集成和使用 SweetEditor 时最常见的问题。

## 滚动时 Decoration 一直重复追加

原因：

- 可视区范围型 decoration provider 使用了 `DecorationApplyMode.Merge`

修复方式：

- 对按行、按可见区重算的 decoration，优先使用 `DecorationApplyMode.ReplaceRange`

## Completion 不弹

检查项：

- 是否通过 `addCompletionProvider()` 注册了 provider
- 是否手动触发了 completion，或者 provider 支持触发字符
- 当前是否处于 linked editing 等可能冲突的状态

## Inline Suggestion 不显示

检查项：

- 是否调用了 `controller.inlineSuggestions().show(...)`
- `line` 和 `column` 是否是合法的 0-based 位置
- 是否在文档加载完成之后再显示 suggestion

## Theme 不生效

检查项：

- 是否调用了 `controller.applyTheme(theme)`
- 是否稳定使用 `rememberEditorAppearance()` 或 `rememberEditorTheme()`
- 行为相关配置是否错误地写进了 themeContent，而不是 `EditorSettings`

## 编辑器外观和预期不一致

检查项：

- 是否在控制器就绪后调用了 `controller.applyTheme(theme)`
- `themeContent` 中使用的键是否属于当前解析器支持的字段
- 行为类配置是否错误地写到了 themeContent，而不是 `EditorSettings`
- 自定义 `textStyles` 名称是否与高亮数据中的样式别名一致

## 相关文档

- [架构](./architecture.md)
- [FAQ](./faq.md)
- 英文 [API Cookbook](../../guide/api-cookbook.md)
