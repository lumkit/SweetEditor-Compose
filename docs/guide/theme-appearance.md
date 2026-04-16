# Theme / Appearance

SweetEditor separates visual configuration into theme, font configuration, text measurement, and runtime settings.

## Main APIs

- `EditorFontConfig`
- `EditorAppearance`
- `rememberEditorAppearance()`
- `rememberEditorTheme()`
- `rememberEditorTextMeasurer()`
- `SweetEditorController.applyTheme()`
- `SweetEditorController.applySettings()`

## Recommended Setup

```kotlin
@Composable
fun rememberMyEditorController(): SweetEditorController {
    val appearance = rememberEditorAppearance(
        darkMode = true,
        fontConfig = EditorFontConfig(
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            lineNumberFontSize = 13.sp,
            inlayHintFontSize = 12.sp,
        ),
    )

    val controller = rememberSweetEditorController(
        textMeasurer = appearance.textMeasurer,
    )

    LaunchedEffect(controller, appearance) {
        controller.applyTheme(appearance.theme)
        controller.applySettings(
            EditorSettings(
                wrapMode = WrapMode.None,
                gutterVisible = true,
                gutterSticky = true,
            )
        )
    }

    return controller
}
```

## Theme Content

`rememberEditorTheme()` can parse `themeContent` and merge it into the fallback light or dark theme.

Typical theme keys include:

- `backgroundColor`
- `textColor`
- `cursorColor`
- `selectionColor`
- `lineNumberColor`
- `currentLineColor`
- `guideColor`
- `scrollbarThumbColor`
- `phantomTextColor`
- `diagnosticErrorColor`
- `linkedEditingActiveColor`
- `bracketHighlightBorderColor`

## Font Configuration

`EditorFontConfig` controls:

- `fontFamily`
- `fontSize`
- `lineNumberFontSize`
- `inlayHintFontSize`
- `iconSize`

These values are used by the Compose-side text measurer and theme defaults.

## Runtime Settings

Use `EditorSettings` when you want to configure editor behavior rather than visual palette:

- wrap mode
- read-only mode
- auto-indent mode
- gutter visibility
- gutter sticky mode
- line spacing
- fold arrow mode
- current line render mode

## Notes

- Theme and appearance are Compose-side abstractions
- The native render model remains the single source of truth for what is drawn
- Apply the theme before expecting the editor to reflect final colors and typography

## Related Docs

- [Theme Schema](./theme-schema.md)
- [EditorSettings Reference](./editor-settings-reference.md)
- [API Cookbook](./api-cookbook.md)
