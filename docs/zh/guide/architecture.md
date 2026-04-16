# 架构

SweetEditor 的核心设计是“原生编辑器内核 + Kotlin/Compose 包装层”的严格分层。

## 顶层结构

- `editor-core`: 原生编辑器内核
- `editor-compose`: Kotlin Multiplatform 包装层与 Compose UI 集成
- `example`: 参考应用
- `platform-demo`: 平台专用 demo 与实验代码

## 核心原则

它**不是** `BasicTextField` 的增强版，而是一套专用编辑器架构：

- native kernel 负责编辑语义和核心算法
- Kotlin 层负责状态包装、协议解码和平台桥接
- Compose 层消费 render model，而不是重新推导编辑器状态

## 各层职责

## Native Core

原生 core 负责：

- 文档模型
- 编辑算法
- 光标与选区语义
- render model 生成
- 折叠、括号匹配、高亮和诊断等核心能力
- 对 Kotlin 暴露的 C API

## Kotlin Wrapper

Kotlin Multiplatform 层负责：

- 安全调用 native bridge
- 解析二进制协议
- 管理 native-backed document 生命周期
- 暴露控制器、Provider、事件总线等公共 API
- 按 source set 隔离平台差异

## Compose UI

Compose 层负责：

- 渲染编辑器表面
- 指针、键盘、IME 事件集成
- completion popup、inline suggestion action bar 等 UI
- 文本测量和主题应用

## 为什么不建议在 Kotlin 里重写 Core 算法

- 容易与原生语义分叉
- 会破坏 render model 作为单一事实来源的设计
- 难以保证跨平台一致性

## 相关文档

- [入门](./getting-started.md)
- 英文 [Theme / Appearance](../../guide/theme-appearance.md)
- 英文 [API Overview](../../guide/api-overview.md)
