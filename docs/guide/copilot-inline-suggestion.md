# Copilot / Inline Suggestion

SweetEditor provides a Compose-side inline suggestion layer built around phantom text.

## Main Types

- `InlineSuggestion`
- `InlineSuggestionController`
- `InlineSuggestionListener`

## Show A Suggestion

```kotlin
controller.inlineSuggestions().show(
    InlineSuggestion(
        line = 12,
        column = 8,
        text = "println(value)",
    )
)
```

## Built-in Interaction

When a suggestion is visible:

- `Tab` accepts it
- `Escape` dismisses it
- the action bar can also accept or dismiss it

## Listen For Accept / Dismiss

```kotlin
controller.inlineSuggestions().setListener(
    object : InlineSuggestionListener {
        override fun onSuggestionAccepted(suggestion: InlineSuggestion) {
            println("accepted: ${suggestion.text}")
        }

        override fun onSuggestionDismissed(suggestion: InlineSuggestion) {
            println("dismissed: ${suggestion.text}")
        }
    }
)
```

## Notes

- Inline suggestion is currently implemented in the Compose/Kotlin layer
- It does not require C++ core changes
- Rendering is driven through phantom text decoration updates
