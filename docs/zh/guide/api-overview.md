# API 概览

这一页汇总 Compose 层对外暴露的主要公共概念。

## 主要类型

## Editor Surface

- `SweetEditor`
- `SweetEditorController`
- `EditorState`
- `EditorDocument`

## Decoration System

- `DecorationProvider`
- `DecorationProviderContext`
- `DecorationSet`
- `DecorationUpdate`
- `DecorationResult`

## Completion System

- `CompletionProvider`
- completion result models
- completion popup item rendering hooks

## Inline Suggestion / Copilot Layer

- `InlineSuggestion`
- `InlineSuggestionController`
- `InlineSuggestionListener`

## Event System

- `EditorEventBus`
- `TextChangedEvent`
- `CursorChangedEvent`
- `DocumentLoadedEvent`

## Platform / Bridge Layer

- Compose 层消费来自 native core 的 render model
- Decorations 和命令通过 Kotlin wrapper 进行桥接
- 平台差异应放在对应的 source set 中处理

## 相关文档

- [入门](./getting-started.md)
- [主题 / 外观](./theme-appearance.md)
- [API Cookbook](./api-cookbook.md)
- [FAQ](./faq.md)
