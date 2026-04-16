# Quick Start

This page shows the smallest practical setup for rendering SweetEditor in a Compose screen.

## Minimal Compose Setup

```kotlin
@Composable
fun EditorScreen() {
    val appearance = rememberEditorAppearance()
    val controller = rememberSweetEditorController(
        textMeasurer = appearance.textMeasurer,
    )

    LaunchedEffect(controller) {
        controller.loadText(
            """
            fun hello(name: String) {
                println("Hello, $name")
            }
            """.trimIndent()
        )
        controller.applyTheme(appearance.theme)
    }

    SweetEditor(
        controller = controller,
        modifier = Modifier.fillMaxSize(),
    )
}
```

## What Happens Here

- `rememberEditorAppearance()` creates theme and text measurement resources
- `rememberSweetEditorController()` creates the main editor controller
- `loadText()` loads a UTF-16 document into the native-backed editor
- `applyTheme()` pushes the theme into the editor runtime
- `SweetEditor()` renders the editor surface

## Next Steps

- Add [Decorations](./decorations.md)
- Add [Completion](./completion.md)
- Add [Copilot / Inline Suggestion](./copilot-inline-suggestion.md)
