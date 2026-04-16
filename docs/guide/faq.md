# FAQ

## Is SweetEditor a `BasicTextField` enhancement?

No.

SweetEditor is a dedicated editor architecture built on a native editor kernel with a Compose Multiplatform wrapper.

## Does SweetEditor depend on native code?

Yes.

The project is built around a native editor core and a Kotlin wrapper layer.

## What is the native core based on?

The native core used by this project is based on:

- [FinalScave/OpenSweetEditor](https://github.com/FinalScave/OpenSweetEditor)

## Which platforms are supported?

Current targets include:

- Android
- iOS
- JVM Desktop
- JS
- Wasm

## Can I use SweetEditor in Compose Multiplatform apps?

Yes.

That is the primary purpose of `editor-compose`.

## Can I customize syntax highlighting and decorations?

Yes.

Use:

- `DecorationProvider`
- `DecorationUpdate`
- `DecorationSet`
- custom `textStyles`

## Can I provide completion items?

Yes.

Use:

- `CompletionProvider`
- `CompletionContext`
- `CompletionReceiver`

## Does the project support inline suggestions or copilot-style UX?

Yes.

The Compose layer provides:

- `InlineSuggestion`
- `InlineSuggestionController`
- inline action bar behavior

## Can I change theme and typography?

Yes.

Use:

- `rememberEditorAppearance()`
- `rememberEditorTheme()`
- `EditorFontConfig`
- `applyTheme()`
- `applySettings()`

## Should I re-implement core editor logic in Kotlin?

No.

The recommended approach is to reuse native core capabilities and keep Kotlin focused on wrapping, state synchronization, and UI integration.

## Where should platform-specific code live?

Platform-specific behavior should stay in the corresponding source set:

- `androidMain`
- `iosMain`
- `jvmMain`
- `webMain`

## Where can I see practical integration examples?

Start with:

- [Quick Start](./quick-start.md)
- [API Cookbook](./api-cookbook.md)
- [Example App](https://github.com/lumkit/SweetEditor-Compose/tree/main/example)
