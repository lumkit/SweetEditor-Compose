# FAQ

## SweetEditor 是 `BasicTextField` 的增强版吗？

不是。

SweetEditor 是一套建立在原生编辑器内核之上的专用编辑器架构，并通过 Compose Multiplatform 暴露给业务层。

## SweetEditor 是否依赖 native code？

是的。

整个项目基于原生编辑器 core 和 Kotlin 包装层共同工作。

## 原生 core 基于什么项目？

当前仓库使用的原生 core 基于：

- [FinalScave/OpenSweetEditor](https://github.com/FinalScave/OpenSweetEditor)

## 支持哪些平台？

当前目标平台包括：

- Android
- iOS
- JVM Desktop
- JS
- Wasm

## 可以在 Compose Multiplatform 应用里直接使用吗？

可以。

这正是 `editor-compose` 模块的主要用途。

## 可以自定义高亮和装饰吗？

可以。

你可以使用：

- `DecorationProvider`
- `DecorationUpdate`
- `DecorationSet`
- 自定义 `textStyles`

## 可以提供补全能力吗？

可以。

你可以使用：

- `CompletionProvider`
- `CompletionContext`
- `CompletionReceiver`

## 是否支持 Copilot / Inline Suggestion？

支持。

Compose 层已经提供：

- `InlineSuggestion`
- `InlineSuggestionController`
- inline action bar 行为

## 可以自定义主题和字体吗？

可以。

常用入口包括：

- `rememberEditorAppearance()`
- `rememberEditorTheme()`
- `EditorFontConfig`
- `applyTheme()`
- `applySettings()`

## 是否应该在 Kotlin 里重写 core 编辑逻辑？

不建议。

更推荐复用 native core 的能力，让 Kotlin 侧专注于包装、状态同步和 UI 集成。

## 平台特定代码应该放哪里？

应放在对应的 source set 中：

- `androidMain`
- `iosMain`
- `jvmMain`
- `webMain`

## 去哪里看实际接入示例？

建议先看：

- [快速开始](./quick-start.md)
- 英文 [API Cookbook](../../guide/api-cookbook.md)
- [Example App](https://github.com/lumkit/SweetEditor-Compose/tree/main/example)
