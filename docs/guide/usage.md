# Usage

This page gives a practical overview of how SweetEditor is typically used in a Compose Multiplatform application.

## Core Concepts

- `SweetEditor`: the composable editor surface
- `SweetEditorController`: controller for editing, commands, providers, and runtime integration
- `EditorState`: editor state and platform bridge lifecycle
- `EditorDocument`: the active document bridge

## Typical Integration Flow

1. Create or remember a `SweetEditorController`
2. Load a document into the controller
3. Attach completion providers or decoration providers as needed
4. Render `SweetEditor`
5. React to editor events through the controller event bus

## Common Extension Points

### Decorations

Use decoration providers to add:

- inlay hints
- phantom text
- diagnostics
- gutter icons
- fold regions
- guides and highlights

### Completion

Completion providers can return:

- popup items
- anchors
- selection updates
- custom item renderers

### Copilot-style Inline Suggestions

SweetEditor also supports copilot-style inline suggestions in the Compose layer:

- show an `InlineSuggestion`
- render it as phantom text
- accept with `Tab`
- dismiss with `Escape`

## Related Guides

- [Theme / Appearance](./theme-appearance.md)
- [API Cookbook](./api-cookbook.md)
- [Decorations](./decorations.md)
- [Completion](./completion.md)
- [Copilot / Inline Suggestion](./copilot-inline-suggestion.md)

## Example Modules

- [Example App](https://github.com/lumkit/SweetEditor-Compose/tree/main/example)
- [Platform Demos](https://github.com/lumkit/SweetEditor-Compose/tree/main/platform-demo)
