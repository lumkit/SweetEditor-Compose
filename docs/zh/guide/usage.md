# 使用概览

这一页从实际集成角度概述 SweetEditor 在 Compose Multiplatform 应用中的典型使用方式。

## 核心概念

- `SweetEditor`: 编辑器 Composable 表面
- `SweetEditorController`: 负责编辑命令、providers、运行时集成的控制器
- `EditorState`: 编辑器状态与平台桥接生命周期
- `EditorDocument`: 当前活动文档桥接对象

## 典型集成流程

1. 创建或 remember 一个 `SweetEditorController`
2. 将文档加载到控制器中
3. 按需附加 completion providers 或 decoration providers
4. 渲染 `SweetEditor`
5. 通过 controller 的 event bus 响应编辑器事件

## 常见扩展点

### Decorations

你可以通过 decoration providers 添加：

- inlay hints
- phantom text
- diagnostics
- gutter icons
- fold regions
- 各类 guide 与 highlight

### Completion

Completion providers 可以返回：

- popup items
- anchors
- selection updates
- 自定义条目渲染相关数据

### Copilot 风格 Inline Suggestions

SweetEditor 也支持在 Compose 层提供 copilot 风格的 inline suggestions：

- 显示 `InlineSuggestion`
- 以 phantom text 方式渲染
- 用 `Tab` 接受
- 用 `Escape` 关闭

## 相关指南

- [主题 / 外观](./theme-appearance.md)
- [API Cookbook](./api-cookbook.md)
- [Decorations](./decorations.md)
- [Completion](./completion.md)
- [Copilot / Inline Suggestion](./copilot-inline-suggestion.md)

## 示例模块

- [Example App](https://github.com/lumkit/SweetEditor-Compose/tree/main/example)
- [Platform Demos](https://github.com/lumkit/SweetEditor-Compose/tree/main/platform-demo)
