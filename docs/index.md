---
layout: home

hero:
  name: "SweetEditor"
  text: "Code editor for Compose Multiplatform"
  tagline: "A native C++17 editor core wrapped by Kotlin Multiplatform and Compose, built for IDEs, AI coding tools, cloud workspaces, and long-term editor infrastructure."
  image:
    src: /Screenshot_Xiaomi_14_Ultra.png
    alt: SweetEditor Desktop Screenshot
  actions:
    - theme: brand
      text: Getting Started
      link: /guide/getting-started
    - theme: alt
      text: GitHub
      link: https://github.com/lumkit/SweetEditor-Compose

features:
  - title: Fast Integration
    details: Compose Multiplatform wrapper with editor state, controller, providers, and platform bridges ready for Android, iOS, Desktop, JS, and Wasm.
  - title: Smooth Editing Experience
    details: Cursor, selection, folding, completion, IME composition, context menu callbacks, and gesture routing are already wired into the editor pipeline.
  - title: Rich Decorations
    details: Inlay hints, phantom text, gutter icons, diagnostics, guides, fold markers, bracket highlights, linked-editing highlights, and active scrollbars.
  - title: High Performance
    details: Viewport-scoped render model consumption, batched decoration updates, native kernel integration, and optimized Kotlin-side caching.
  - title: Pluggable Architecture
    details: Completion providers, decoration providers, icon painters, theme loading, metadata, language configuration, and copilot-style inline suggestions.
  - title: Open Core
    details: The native editor core is based on the open-source OpenSweetEditor project and wrapped by SweetEditor Compose APIs.
---

## Quick Links

- [Repository README](https://github.com/lumkit/SweetEditor-Compose/blob/main/README.md)
- [Usage Guide](./guide/usage.md)
- [API Overview](./guide/api-overview.md)
- [Core Library: OpenSweetEditor](https://github.com/FinalScave/OpenSweetEditor)
- [License](https://github.com/lumkit/SweetEditor-Compose/blob/main/LICENSE)
- [Static Linking Exception](https://github.com/lumkit/SweetEditor-Compose/blob/main/EXCEPTION)

## Screenshots

<div align="center">
  <table>
    <tr>
      <td align="center"><b>Android</b><br/><img src="./snapshot/Screenshot_Android.jpg" alt="Android screenshot" width="360"/></td>
      <td align="center"><b>iOS</b><br/><img src="./snapshot/Screenshot_IOS.png" alt="iOS screenshot" width="360"/></td>
    </tr>
    <tr>
      <td align="center"><b>Desktop</b><br/><img src="./snapshot/Screenshot_Desktop.png" alt="Desktop screenshot" width="360"/></td>
      <td align="center"><b>Web</b><br/><img src="" alt="Web screenshot" width="360"/></td>
    </tr>
  </table>
</div>
