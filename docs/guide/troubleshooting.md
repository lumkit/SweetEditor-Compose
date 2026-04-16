# Troubleshooting

This page covers common issues encountered while integrating or publishing SweetEditor.

## GitHub Pages Shows Plain Markdown Instead Of The VitePress Site

Cause:

- GitHub Pages is still deploying the branch or `docs/` folder directly instead of the GitHub Actions build artifact

Fix:

1. Open repository `Settings`
2. Go to `Pages`
3. Set `Build and deployment > Source` to `GitHub Actions`
4. Re-run the Pages workflow

## Homepage Hero Image Or Logo Does Not Show

Cause:

- VitePress homepage and logo use static public assets
- the image must exist under `docs/public`

Fix:

- keep hero/logo images under `docs/public/...`
- use public paths such as `/snapshot/Screenshot_Desktop.png`

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
- verify the latest editor-compose version and site build if behavior differs between local and published versions

## Build Succeeds Locally But Site Looks Old Online

Cause:

- Pages may still be serving an old deployment or the wrong deployment source

Fix:

- verify latest workflow run succeeded
- verify Pages source is `GitHub Actions`
- refresh after deployment completes

## Related Docs

- [Architecture](./architecture.md)
- [Theme / Appearance](./theme-appearance.md)
- [API Cookbook](./api-cookbook.md)
- [FAQ](./faq.md)
