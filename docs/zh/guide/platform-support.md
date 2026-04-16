# 平台支持

SweetEditor 面向多个 Compose Multiplatform 平台。

## 支持的目标平台

- Android
- iOS
- JVM Desktop
- JS
- Wasm

## 项目结构

- `commonMain`: 共享 API、编辑器模型、协议解码、控制器逻辑
- `androidMain`: Android 平台桥接
- `iosMain`: iOS 平台桥接
- `jvmMain`: Desktop/JVM 平台桥接
- `webMain`: Web 平台相关代码

## 平台说明

### Android

- IME 和文本输入通过 Android 平台钩子集成
- Compose UI 承载编辑器，并将手势桥接到原生编辑器输入模型

### iOS

- iOS bridge 代码位于独立的 iOS source set
- 原生生命周期和渲染协调由 Kotlin wrapper 层负责

### Desktop

- 支持鼠标、滚轮、键盘、popup 和 inline suggestion 交互
- Desktop 的滚轮归一化在平台层单独处理

### Web / Wasm

- 作为 wrapper 层的目标平台输出之一
- 某些平台交互能力会随着运行环境继续迭代

## 建议

尽量将共享编辑器逻辑放在 `commonMain`，并把平台特定行为隔离在对应 source set 中。
