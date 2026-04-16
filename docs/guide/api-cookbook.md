# API Cookbook

This page collects small, copyable recipes for common integration tasks.

## Load Text

```kotlin
controller.loadText(
    """
    fun main() {
        println("Hello")
    }
    """.trimIndent()
)
```

## Load File

```kotlin
controller.loadFile("/absolute/path/to/file.kt")
```

## Apply Theme And Settings

```kotlin
val appearance = rememberEditorAppearance(darkMode = true)

LaunchedEffect(controller, appearance) {
    controller.applyTheme(appearance.theme)
    controller.applySettings(EditorSettings())
}
```

## Set Language Configuration

```kotlin
controller.setLanguageConfiguration(configuration)
```

## Attach Editor Metadata

```kotlin
controller.setMetadata(
    EditorMetadata(
        entries = mapOf(
            "fileName" to "Main.kt",
            "language" to "kotlin",
        )
    )
)
```

## Register A Decoration Provider

```kotlin
controller.addDecorationProvider(myDecorationProvider)
```

## Register A Completion Provider

```kotlin
controller.addCompletionProvider(myCompletionProvider)
```

## Provide Gutter Icons

```kotlin
controller.setEditorIconProvider(myIconProvider)
```

## Show Inline Suggestion

```kotlin
controller.inlineSuggestions().show(
    InlineSuggestion(
        line = 4,
        column = 12,
        text = "println(value)",
    )
)
```

## Trigger Completion Manually

```kotlin
controller.triggerCompletion()
```

## Dismiss Completion

```kotlin
controller.dismissCompletion()
```

## Observe Editor Events

```kotlin
val subscription = controller.events().subscribe<TextChangedEvent> { event ->
    println(event.editResult)
}
```

Remember to dispose subscriptions when you no longer need them.
