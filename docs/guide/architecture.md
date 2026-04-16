# Architecture

SweetEditor is built around a strict split between a native editor kernel and a Kotlin/Compose wrapper.

## High-level Structure

- `editor-core`: native editor kernel
- `editor-compose`: Kotlin Multiplatform wrapper and Compose UI integration
- `example`: reference application
- `platform-demo`: platform-specific demos and experiments

## Core Principle

The project is **not** a `BasicTextField` enhancement. It is a dedicated editor architecture:

- native kernel owns editing semantics
- Kotlin layer owns state wrapping, protocol decoding, and Compose integration
- Compose layer consumes render models instead of re-deriving editor state

## Layer Responsibilities

## Native Core

The native core is responsible for:

- document model
- editing algorithms
- cursor and selection semantics
- render model generation
- fold state, bracket matching, highlighting, diagnostics payloads
- C API surface exported to Kotlin

The core used by this repository is based on:

- [FinalScave/OpenSweetEditor](https://github.com/FinalScave/OpenSweetEditor)

## Kotlin Wrapper

The Kotlin Multiplatform layer is responsible for:

- safe native bridge calls
- binary protocol parsing
- lifecycle management of native-backed documents
- public APIs such as controllers, providers, and events
- platform source set isolation

Typical types in this layer include:

- `SweetEditorController`
- `EditorDocument`
- `DecorationProvider`
- `CompletionProvider`
- `InlineSuggestionController`

## Compose UI Layer

The Compose layer is responsible for:

- rendering the editor surface
- pointer, keyboard, and IME integration
- popup UI such as completion and inline suggestion action bars
- text measurement and theme application

## Render Flow

The typical rendering pipeline is:

1. editor input is sent through the Kotlin wrapper
2. native core updates document and render state
3. core emits render payload / model data
4. Kotlin decodes payload into Compose-consumable models
5. Compose draws the editor surface from that render model

## Decoration Flow

The decoration pipeline is intentionally layered:

1. Compose/Kotlin provider reads `DecorationProviderContext`
2. provider returns `DecorationUpdate`
3. manager merges or replaces provider-owned data
4. wrapper encodes the aggregated result
5. native core consumes the decoration payload
6. render model reflects the final result

## Important Boundaries

- Do not re-implement native editing algorithms in Kotlin
- Keep common abstractions in `commonMain`
- Keep platform-specific hooks in their own source sets
- Treat the render model as the single source of truth for drawing

## Related Docs

- [Getting Started](./getting-started.md)
- [Theme / Appearance](./theme-appearance.md)
- [API Overview](./api-overview.md)
- [Platform Support](./platform-support.md)
