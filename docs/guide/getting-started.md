# Getting Started

SweetEditor is a Compose Multiplatform code editor library backed by a native C++17 core.

## What You Get

- Compose Multiplatform editor UI
- Native editor kernel integration
- Decoration, completion, and IME pipelines
- Android, iOS, Desktop, JS, and Wasm targets

## Repository Structure

- `editor-compose`: Kotlin Multiplatform wrapper and Compose UI integration
- `editor-core`: native editor kernel and C API
- `example`: Compose demo application
- `platform-demo`: platform-specific demo implementations

## Start Exploring

1. Read [Installation](./installation.md)
2. Follow [Quick Start](./quick-start.md)
3. Explore [Usage](./usage.md)
4. Inspect the [example](https://github.com/lumkit/SweetEditor-Compose/tree/main/example) module
5. Open the [中文文档](../README_zh.md) if you prefer Chinese
6. Review [platform-implementation-standard](../platform-implementation-standard.md)

## Core Library

The native editor core used by this project is based on:

- [FinalScave/OpenSweetEditor](https://github.com/FinalScave/OpenSweetEditor)

## Suggested Reading Path

- [Installation](./installation.md)
- [Quick Start](./quick-start.md)
- [Decorations](./decorations.md)
- [Completion](./completion.md)
- [Copilot / Inline Suggestion](./copilot-inline-suggestion.md)
- [Platform Support](./platform-support.md)
