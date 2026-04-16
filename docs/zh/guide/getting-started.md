# 入门

SweetEditor 是一个以原生 C++17 编辑器内核为基础、通过 Kotlin Multiplatform 和 Compose 封装出来的代码编辑器库。

## 你可以得到什么

- Compose Multiplatform 编辑器 UI
- 原生编辑器内核桥接
- decoration、completion、IME 等扩展能力
- Android、iOS、Desktop、JS、Wasm 多平台目标

## 仓库结构

- `editor-compose`: Kotlin Multiplatform 包装层与 Compose UI 集成
- `editor-core`: 原生编辑器内核与 C API
- `example`: 示例应用
- `platform-demo`: 平台特定 demo 与实验代码

## 建议阅读顺序

1. 阅读 [安装](./installation.md)
2. 跟着 [快速开始](./quick-start.md) 跑通最小示例
3. 阅读 [架构](./architecture.md) 理解 wrapper 与 native core 的边界
4. 按需查看英文参考页，例如 Theme、Cookbook、API Overview

## Core 来源

本项目使用的原生编辑器 core 基于：

- [FinalScave/OpenSweetEditor](https://github.com/FinalScave/OpenSweetEditor)
