# Theme Schema

This page documents the theme keys currently parsed by `rememberEditorTheme()`.

## How Theme Parsing Works

- `rememberEditorTheme()` starts from a built-in dark or light fallback theme
- `themeContent` is parsed in the Compose layer
- matching keys override fallback values
- font settings are merged from `EditorFontConfig`
- `textStyles` can be extended through named style aliases

## Color Keys

The following top-level color keys are supported:

- `backgroundColor`
- `textColor`
- `cursorColor`
- `selectionColor`
- `lineNumberColor`
- `currentLineNumberColor`
- `currentLineColor`
- `guideColor`
- `separatorLineColor`
- `splitLineColor`
- `scrollbarTrackColor`
- `scrollbarThumbColor`
- `scrollbarThumbActiveColor`
- `compositionUnderlineColor`
- `inlayHintBackgroundColor`
- `inlayHintTextColor`
- `foldPlaceholderBackgroundColor`
- `foldPlaceholderTextColor`
- `phantomTextColor`
- `inlayHintIconColor`
- `diagnosticErrorColor`
- `diagnosticWarningColor`
- `diagnosticInfoColor`
- `diagnosticHintColor`
- `linkedEditingActiveColor`
- `linkedEditingInactiveColor`
- `bracketHighlightBorderColor`
- `bracketHighlightBackgroundColor`
- `gutterBackgroundColor`

## Text Style Block

The `textStyles` object lets you define syntax/semantic style aliases.

Supported aliases include:

- `keyword`
- `string`
- `comment`
- `number`
- `builtin`
- `type`
- `class`
- `interface`
- `enum`
- `struct`
- `function`
- `method`
- `variable`
- `property`
- `parameter`
- `constant`
- `field`
- `namespace`
- `module`
- `enum_member`
- `operator`
- `punctuation`
- `annotation`
- `preprocessor`

## Minimal Example

```json
{
  "backgroundColor": "#1B1E24",
  "textColor": "#D7DEE9",
  "cursorColor": "#8FB8FF",
  "phantomTextColor": "#8AA3B5D1",
  "textStyles": {
    "keyword": {
      "color": "#7AA2F7",
      "fontStyle": "bold"
    },
    "comment": {
      "color": "#7A8294",
      "fontStyle": "italic"
    }
  }
}
```

## Value Format

Color values can be written as:

- `#RRGGBB`
- `#AARRGGBB`
- signed integer values

## Font Style Notes

Inside `textStyles`, supported font style flags are mapped by parser logic. Typical values are:

- `bold`
- `italic`

## What Is Not Theme Schema

Theme content does **not** replace runtime behavior settings such as:

- wrap mode
- read-only mode
- gutter sticky behavior
- current line render mode

Use `EditorSettings` for those.

## Related Docs

- [Theme / Appearance](./theme-appearance.md)
- [EditorSettings Reference](./editor-settings-reference.md)
