# Installation

SweetEditor Compose is published as a Kotlin Multiplatform library.

## Maven Coordinate

```kotlin
dependencies {
    implementation("io.github.lumkit:sweet-editor-compose:<version>")
}
```

## Requirements

- Kotlin Multiplatform project
- Compose Multiplatform enabled
- JVM target 11 for desktop/JVM usage
- Native binaries available for the target platforms you plan to run

## Modules In This Repository

- `editor-compose`: public Compose Multiplatform wrapper
- `editor-core`: native editor kernel and C API bridge
- `example`: reference demo application

## Local Development

If you are integrating from source instead of Maven Central:

```kotlin
include(":editor-compose")
```

Then depend on the module directly in your app.

## Next

- [Getting Started](./getting-started.md)
- [Quick Start](./quick-start.md)
