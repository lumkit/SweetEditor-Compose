import {defineConfig} from "vitepress"

export default defineConfig({
  title: "SweetEditor",
  description: "Code editor for Compose Multiplatform",
  base: "/SweetEditor-Compose/",
  cleanUrls: true,
  lastUpdated: true,
  ignoreDeadLinks: true,
  locales: {
    root: {
      label: "English",
      lang: "en-US",
      themeConfig: {
        logo: "sweeteditor_icon_only.svg",
        nav: [
          { text: "Guide", link: "/guide/installation" },
        ],
        sidebar: [
          {
            text: "Guide",
            items: [
              { text: "Installation", link: "/guide/installation" },
              { text: "Getting Started", link: "/guide/getting-started" },
              { text: "Architecture", link: "/guide/architecture" },
              { text: "Quick Start", link: "/guide/quick-start" },
              { text: "Features", link: "/guide/features" },
              { text: "Theme / Appearance", link: "/guide/theme-appearance" },
              { text: "Theme Schema", link: "/guide/theme-schema" },
              { text: "EditorSettings Reference", link: "/guide/editor-settings-reference" },
              { text: "Decorations", link: "/guide/decorations" },
              { text: "Completion", link: "/guide/completion" },
              { text: "Copilot / Inline Suggestion", link: "/guide/copilot-inline-suggestion" },
              { text: "Platform Support", link: "/guide/platform-support" },
              { text: "Usage", link: "/guide/usage" },
              { text: "API Cookbook", link: "/guide/api-cookbook" },
              { text: "API Overview", link: "/guide/api-overview" },
              { text: "Troubleshooting", link: "/guide/troubleshooting" },
              { text: "FAQ", link: "/guide/faq" },
              { text: "Core Library", link: "/guide/core-library" }
            ]
          },
          {
            text: "Docs",
            items: [
              { text: "Chinese Site", link: "/zh/" },
            ]
          }
        ],
        socialLinks: [
          { icon: "github", link: "https://github.com/lumkit/SweetEditor-Compose" }
        ],
        search: {
          provider: "local"
        },
        outline: {
          level: [2, 3]
        },
        footer: {
          message: "SweetEditor documentation site powered by VitePress.",
          copyright: "Copyright © SweetEditor Contributors"
        }
      }
    },
    zh: {
      label: "简体中文",
      lang: "zh-CN",
      link: "/zh/",
      themeConfig: {
        logo: "../sweeteditor_icon_only.svg",
        nav: [
            { text: "入门", link: "/guide/installation" },
        ],
        sidebar: [
          {
            text: "中文指南",
            items: [
              { text: "安装", link: "/zh/guide/installation" },
              { text: "入门", link: "/zh/guide/getting-started" },
              { text: "架构", link: "/zh/guide/architecture" },
              { text: "快速开始", link: "/zh/guide/quick-start" },
              { text: "主题 / 外观", link: "/zh/guide/theme-appearance" },
              { text: "主题 Schema", link: "/zh/guide/theme-schema" },
              { text: "EditorSettings 参考", link: "/zh/guide/editor-settings-reference" },
              { text: "Decorations", link: "/zh/guide/decorations" },
              { text: "Completion", link: "/zh/guide/completion" },
              { text: "Copilot / Inline Suggestion", link: "/zh/guide/copilot-inline-suggestion" },
              { text: "平台支持", link: "/zh/guide/platform-support" },
              { text: "使用概览", link: "/zh/guide/usage" },
              { text: "API Cookbook", link: "/zh/guide/api-cookbook" },
              { text: "API 概览", link: "/zh/guide/api-overview" },
              { text: "功能特性", link: "/zh/guide/features" },
              { text: "Core Library", link: "/zh/guide/core-library" },
              { text: "排障", link: "/zh/guide/troubleshooting" },
              { text: "FAQ", link: "/zh/guide/faq" }
            ]
          },
          {
            text: "更多参考",
            items: [
              { text: "English Site", link: "/" }
            ]
          }
        ],
        socialLinks: [
          { icon: "github", link: "https://github.com/lumkit/SweetEditor-Compose" }
        ],
        search: {
          provider: "local"
        },
        outline: {
          level: [2, 3]
        },
        footer: {
          message: "SweetEditor 文档站由 VitePress 驱动。",
          copyright: "Copyright © SweetEditor Contributors"
        }
      }
    }
  }
})
