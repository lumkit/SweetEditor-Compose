---
layout: home

hero:
  name: "SweetEditor"
  text: "面向 Compose Multiplatform 的代码编辑器"
  tagline: "基于原生 C++17 编辑器内核与 Kotlin Multiplatform / Compose 封装，适用于 IDE、AI 编程工具、云开发工作区以及长期演进的编辑器基础设施。"
  image:
    src: /Screenshot_Xiaomi_14_Ultra.png
    alt: SweetEditor Desktop Screenshot
  actions:
    - theme: brand
      text: 快速开始
      link: /zh/guide/quick-start
    - theme: alt
      text: GitHub
      link: https://github.com/lumkit/SweetEditor-Compose

features:
  - title: 快速接入
    details: 提供 Compose Multiplatform 包装层、控制器、状态、Provider 与平台桥接，可覆盖 Android、iOS、Desktop、JS、Wasm。
  - title: 完整编辑体验
    details: 已集成光标、选区、折叠、补全、IME 组合输入、上下文菜单回调和手势事件桥接。
  - title: 丰富装饰能力
    details: 支持 inlay hints、phantom text、gutter icons、diagnostics、guides、fold markers、括号高亮和 linked-editing 高亮。
  - title: 高性能渲染
    details: 基于 render model 的渲染管线、批量装饰更新、原生内核集成与 Kotlin 侧缓存优化共同支撑复杂编辑场景。
  - title: 可扩展架构
    details: 支持 completion providers、decoration providers、icon painters、主题加载、metadata、语言配置和 copilot 风格 inline suggestion。
  - title: 开源 Core
    details: 原生编辑器 core 基于开源 OpenSweetEditor 项目，并由 SweetEditor Compose 层完成多平台封装。
---

## 快速链接

- [仓库 README](https://github.com/lumkit/SweetEditor-Compose/blob/main/README.md)
- [OpenSweetEditor Core](https://github.com/FinalScave/OpenSweetEditor)
- [许可证](https://github.com/lumkit/SweetEditor-Compose/blob/main/LICENSE)
- [静态链接例外](https://github.com/lumkit/SweetEditor-Compose/blob/main/EXCEPTION)

## 截图

<div align="center">
  <table>
    <tr>
      <td align="center"><b>Android</b><br/><img src="/snapshot/Screenshot_Android.jpg" alt="Android screenshot" width="360"/></td>
      <td align="center"><b>iOS</b><br/><img src="/snapshot/Screenshot_IOS.png" alt="iOS screenshot" width="360"/></td>
    </tr>
    <tr>
      <td align="center"><b>Desktop</b><br/><img src="/snapshot/Screenshot_Desktop.png" alt="Desktop screenshot" width="360"/></td>
      <td align="center"><b>Web</b><br/><img src="" alt="Web screenshot" width="360"/></td>
    </tr>
  </table>
</div>
