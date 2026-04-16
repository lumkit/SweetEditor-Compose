# EditorSettings Reference

`EditorSettings` controls runtime editor behavior rather than color palette or typography.

## Fields

## `wrapMode`

Controls line wrapping behavior.

Type:

- `WrapMode`

## `tabSize`

Logical tab width used by the editor.

Type:

- `Int`

Default:

- `4`

## `lineSpacingExtra`

Additional spacing added to each line.

Type:

- `Float`

## `lineSpacingMultiplier`

Line height multiplier applied to layout.

Type:

- `Float`

## `foldArrowMode`

Controls fold arrow behavior and visibility mode.

Type:

- `FoldArrowMode`

## `gutterSticky`

Controls whether gutter presentation remains sticky relative to scroll behavior.

Type:

- `Boolean`

## `gutterVisible`

Shows or hides the gutter.

Type:

- `Boolean`

## `currentLineRenderMode`

Controls how the current line is emphasized.

Type:

- `CurrentLineRenderMode`

## `readOnly`

Disables editing operations while allowing viewing and selection.

Type:

- `Boolean`

## `compositionEnabled`

Enables IME composition behavior.

Type:

- `Boolean`

## `autoIndentMode`

Controls auto-indent behavior.

Type:

- `AutoIndentMode`

## Example

```kotlin
controller.applySettings(
    EditorSettings(
        wrapMode = WrapMode.None,
        tabSize = 4,
        lineSpacingExtra = 1f,
        lineSpacingMultiplier = 1.1f,
        gutterSticky = true,
        gutterVisible = true,
        currentLineRenderMode = CurrentLineRenderMode.Background,
        readOnly = false,
        compositionEnabled = true,
        autoIndentMode = AutoIndentMode.None,
    )
)
```

## When To Use Settings vs Theme

Use `EditorSettings` for:

- behavior
- layout policy
- interaction toggles

Use `EditorTheme` for:

- colors
- text style mapping
- visual palette
- font-related defaults

## Related Docs

- [Theme / Appearance](./theme-appearance.md)
- [Theme Schema](./theme-schema.md)
- [API Cookbook](./api-cookbook.md)
