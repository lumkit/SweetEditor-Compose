# API Overview

This page summarizes the major public-facing concepts exposed by the Compose layer.

## Main Types

### Editor Surface

- `SweetEditor`
- `SweetEditorController`
- `EditorState`
- `EditorDocument`

### Decoration System

- `DecorationProvider`
- `DecorationProviderContext`
- `DecorationSet`
- `DecorationUpdate`
- `DecorationResult`

### Completion System

- `CompletionProvider`
- completion result models
- completion popup item rendering hooks

### Inline Suggestion / Copilot Layer

- `InlineSuggestion`
- `InlineSuggestionController`
- `InlineSuggestionListener`

### Event System

- `EditorEventBus`
- `TextChangedEvent`
- `CursorChangedEvent`
- `DocumentLoadedEvent`

## Platform / Bridge Layer

- Compose consumes render models from the native core
- Decorations and commands are bridged through the Kotlin wrapper
- Platform differences are handled in their respective source sets

## Related Docs

- [Getting Started](./getting-started.md)
- [Theme / Appearance](./theme-appearance.md)
- [Usage](./usage.md)
- [API Cookbook](./api-cookbook.md)
- [Features](./features.md)
- [Core Library](./core-library.md)
