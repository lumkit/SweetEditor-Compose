<div align="center">

[English](index.md) | **简体中文**

# SweetEditor

### 一个面向 Compose Multiplatform 的多功能代码编辑器库

**基于 C++17 原生内核与 Compose Multiplatform 封装，适用于 IDE、AI 编程工具、云开发工作区等需要长期演进的编辑器基础设施。**

**欢迎提交 Issue 和 Pull Request。**

[![C++17](https://img.shields.io/badge/C++-17-blue.svg?logo=cplusplus)](https://isocpp.org/)
[![Platforms](https://img.shields.io/badge/Platforms-Android%20%7C%20iOS%20%7C%20Desktop%20%7C%20Web*%20-brightgreen.svg)](#平台支持状态)
[![License](https://img.shields.io/badge/License-LGPL--2.1%2B-yellow.svg)](../LICENSE)

</div>

---

> 当前仓库仍处于开发阶段，请在使用时留意版本变化。

## 功能特性
- [x] 原生编辑器内核（C++17）与 Compose Multiplatform 封装
- [x] 跨平台目标：Android、iOS、JVM Desktop、JS、Wasm
- [x] 基于 render model 的架构（viewport、scroll metrics、cursor/selection rects）
- [x] 语法高亮 / 语义高亮渲染，支持批量样式注册
- [x] 装饰系统：inlay hints、phantom text、gutter icons、diagnostics
- [x] 结构化辅助渲染：缩进引导线、括号引导线、流程引导线、分隔线、折叠区域
- [x] 丰富的可视反馈：fold markers、linked-editing 高亮、括号高亮、活动滚动条、指针光标提示
- [x] 基于 0-based 行列语义的光标与选区操作
- [x] 选区拖拽手柄状态回调、命中目标回调、上下文菜单请求回调
- [x] 基础编辑能力：插入、替换、删除、退格、向前删除
- [x] 行操作：上移/下移行、复制行、删除行、在上方/下方插入新行
- [x] Undo / Redo 栈集成
- [x] 代码片段插入与 linked-editing 会话支持
- [x] 补全能力：provider API、补全弹窗定位、条目选择、可自定义条目渲染
- [x] IME 组合输入生命周期：start / update / end / cancel
- [x] 手势事件与键盘事件桥接到原生输入模型
- [x] 支持从 UTF-16 文本和文件路径加载文档
- [x] 只读模式、自动换行模式、自动缩进模式、gutter 显示/吸附等设置
- [x] 可配置外观系统：主题内容加载、字体配置、当前行渲染模式、行距、折叠箭头模式

## Core Library

本项目使用的原生编辑器 core 基于开源仓库 `OpenSweetEditor`：

- [FinalScave/OpenSweetEditor](https://github.com/FinalScave/OpenSweetEditor)

## 截图

<div align="center">
  <table>
    <tr>
      <td align="center"><b>Android</b><br/><img src="./snapshot/Screenshot_Android.jpg" alt="Android 截图" width="360"/></td>
      <td align="center"><b>iOS</b><br/><img src="./snapshot/Screenshot_IOS.png" alt="iOS 截图" width="360"/></td>
    </tr>
    <tr>
      <td align="center"><b>Desktop</b><br/><img src="./snapshot/Screenshot_Desktop.png" alt="Desktop 截图" width="360"/></td>
      <td align="center"><b>Web</b><br/><img src="" alt="Web 截图" width="360"/></td>
    </tr>
  </table>
</div>

## 贡献者

<a href="https://github.com/lumkit/SweetEditor-Compose/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=lumkit/SweetEditor-Compose" />
</a>

## 许可证

SweetEditor 使用 [GNU Lesser General Public License v2.1 or later](https://github.com/lumkit/SweetEditor-Compose/blob/main/LICENSE)（LGPL-2.1+）开源协议，并附带一个额外的 [Static Linking Exception](https://github.com/lumkit/SweetEditor-Compose/blob/main/EXCEPTION) 作为补充说明。

## Star History

<a href="https://www.star-history.com/?repos=lumkit%2FSweetEditor-Compose&type=timeline&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/image?repos=lumkit/SweetEditor-Compose&type=timeline&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/image?repos=lumkit/SweetEditor-Compose&type=timeline&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/image?repos=lumkit/SweetEditor-Compose&type=timeline&legend=top-left" />
 </picture>
</a>
