# 安装

SweetEditor Compose 以 Kotlin Multiplatform 库的形式提供。

## Maven 坐标 [![Maven Central](https://img.shields.io/maven-central/v/io.github.lumkit/sweet-editor-compose.svg?label=Maven%20Central)](https://search.maven.org/artifact/io.github.lumkit/sweet-editor-compose)

```kotlin
dependencies {
    implementation("io.github.lumkit:sweet-editor-compose:<version>")
}
```

## 使用前提

- Kotlin Multiplatform 工程
- 已启用 Compose Multiplatform
- Desktop / JVM 场景建议使用 JVM 11
- 目标平台所需的 native binary 已准备好

## 仓库模块

- `editor-compose`: 对外 Kotlin Multiplatform 包装层
- `editor-core`: 原生编辑器内核与 C API
- `example`: 参考示例应用

## 本地源码接入

如果你不是从 Maven Central 依赖，而是直接从源码集成：

```kotlin
include(":editor-compose")
```

然后在你的应用模块中直接依赖这个子模块。

## 下一步

- [入门](./getting-started.md)
- [快速开始](./quick-start.md)
