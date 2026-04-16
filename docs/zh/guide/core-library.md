# Core Library

SweetEditor 的原生编辑器 core 基于开源 `OpenSweetEditor` 项目。

## 参考

- [FinalScave/OpenSweetEditor](https://github.com/FinalScave/OpenSweetEditor)

## 在当前仓库中的位置

- `editor-core` 包含原生内核和 C API 集成面
- `editor-compose` 负责把 native core 封装成 Kotlin Multiplatform 与 Compose 友好的 API

## 说明

- Compose 层应优先消费 native core 能力，而不是在 Kotlin 中重写核心编辑算法
- native kernel 变更和 Kotlin wrapper 变更应分开评估
