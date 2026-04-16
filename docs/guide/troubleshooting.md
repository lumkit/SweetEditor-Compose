# Troubleshooting

This page covers common issues encountered while integrating and using SweetEditor.

## Decorations Keep Duplicating While Scrolling

Cause:

- a viewport-scoped decoration provider uses `DecorationApplyMode.Merge`

Fix:

- use `DecorationApplyMode.ReplaceRange` for line-based viewport recomputation

## Completion Does Not Appear

Checklist:

- a `CompletionProvider` is registered with `addCompletionProvider()`
- completion is manually triggered or a trigger character is supported
- the controller is not currently in a conflicting state such as linked editing

## Inline Suggestion Does Not Show

Checklist:

- call `controller.inlineSuggestions().show(...)`
- use a valid 0-based `line` and `column`
- ensure the document is loaded before showing the suggestion

## Theme Changes Do Not Apply

Checklist:

- call `controller.applyTheme(theme)`
- use `rememberEditorAppearance()` or `rememberEditorTheme()` consistently
- keep behavior changes in `EditorSettings`, not in theme content

## Desktop Mouse Wheel Feels Wrong

Note:

- Desktop wheel normalization is handled at the platform layer

## Editor Looks Different From Expected Theme

Checklist:

- verify `controller.applyTheme(theme)` is called after the controller is ready
- verify the expected `themeContent` keys are supported by the parser
- verify behavior flags are configured in `EditorSettings` rather than theme content
- verify custom `textStyles` names match the style aliases used by your highlighting data

## Related Docs

- [Architecture](./architecture.md)
- [Theme / Appearance](./theme-appearance.md)
- [API Cookbook](./api-cookbook.md)
- [FAQ](./faq.md)
