
<div align="center">

**English** | [简体中文](docs/README_zh.md)

# SweetEditor

### A Multifunctional code editor library for compose multiplatform

**A C++17 core with platform-native rendering, built for long-term evolving editor infrastructure in IDEs, AI programming tools, cloud development workspaces, and similar products.**

**Issues and pull requests are welcome.**

**To check out all docs, please visit:** [Documentation Site](https://lumkit.github.io/SweetEditor-Compose)

[![C++17](https://img.shields.io/badge/C++-17-blue.svg?logo=cplusplus)](https://isocpp.org/)
[![Platforms](https://img.shields.io/badge/Platforms-Android%20%7C%20iOS%20%7C%20Desktop%20%7C%20Web*%20-brightgreen.svg)](#platform-support-status)
[![License](https://img.shields.io/badge/License-LGPL--2.1%2B-yellow.svg)](LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.lumkit/sweet-editor-compose.svg?label=Maven%20Central)](https://search.maven.org/artifact/io.github.lumkit/sweet-editor-compose)

</div>

---

>This repository is still in the development stage, so please pay attention.

## Features
- [x] Native editor kernel (C++17) with Compose Multiplatform wrapper
- [x] Cross-platform targets: Android, iOS, JVM Desktop, JS, Wasm
- [x] Render-model driven architecture (viewport, scroll metrics, cursor/selection rects)
- [x] Syntax/semantic span rendering with batch style registration
- [x] Decoration pipeline: inlay hints, phantom text, gutter icons, diagnostics
- [x] Guide and structure rendering: indent guides, bracket guides, flow guides, separator guides, fold regions
- [x] Rich visual affordances: fold markers, linked-editing highlights, bracket highlights, active scrollbars, pointer cursor hints
- [x] Cursor and selection operations with 0-based line/column semantics
- [x] Selection handle drag state callbacks, hit-target reporting, and context-menu request callbacks
- [x] Editing primitives: insert, replace, delete, backspace, delete-forward
- [x] Line operations: move/copy line up/down, delete line, insert line above/below
- [x] Undo/redo stack integration
- [x] Snippet insertion and linked-editing session support
- [x] Completion pipeline with provider API, popup anchoring, item selection, and custom item renderer
- [x] IME composition lifecycle: start/update/end/cancel
- [x] Gesture and key event bridge to native input model
- [x] Document loading from UTF-16 text and file path
- [x] Read-only mode, wrap mode, auto-indent mode, gutter visibility/sticky options
- [x] Configurable appearance: theme content loading, font configuration, current-line render mode, line spacing, fold arrow mode

## Core Library

The native editor core used by this project is based on the open-source `OpenSweetEditor` repository:

- [FinalScave/OpenSweetEditor](https://github.com/FinalScave/OpenSweetEditor)

## Screenshots

<div align="center">
  <table>
    <tr>
      <td align="center"><b>Android</b><br/><img src="docs/snapshot/Screenshot_Android.jpg" alt="Android screenshot" width="360"/></td>
      <td align="center"><b>IOS</b><br/><img src="docs/snapshot/Screenshot_IOS.png" alt="IOS screenshot" width="360"/></td>
    </tr>
    <tr>
      <td align="center"><b>Desktop</b><br/><img src="docs/snapshot/Screenshot_Desktop.png" alt="Desktop screenshot" width="360"/></td>
      <td align="center"><b>Web</b><br/><img src="" alt="Web screenshot" width="360"/></td>
    </tr>
  </table>
</div>

## Contributors

<a href="https://github.com/lumkit/SweetEditor-Compose/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=lumkit/SweetEditor-Compose" />
</a>

## License

SweetEditor is licensed under the [GNU Lesser General Public License v2.1 or later](LICENSE) (LGPL-2.1+), with an additional [Static Linking Exception](EXCEPTION) provided as a supplementary clarification.

## Star History

<a href="https://www.star-history.com/?repos=lumkit%2FSweetEditor-Compose&type=timeline&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/image?repos=lumkit/SweetEditor-Compose&type=timeline&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/image?repos=lumkit/SweetEditor-Compose&type=timeline&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/image?repos=lumkit/SweetEditor-Compose&type=timeline&legend=top-left" />
 </picture>
</a>
