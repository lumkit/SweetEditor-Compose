import {defineConfig} from "vitepress"

export default defineConfig({
  title: "SweetEditor",
  description: "A multifunctional code editor library for Compose Multiplatform",
  base: "/SweetEditor-Compose/",
  cleanUrls: true,
  lastUpdated: true,
  ignoreDeadLinks: true,
  themeConfig: {
    logo: "/snapshot/Screenshot_Desktop.png",
    nav: [
      { text: "Home", link: "/" },
      { text: "Installation", link: "/guide/installation" },
      { text: "Getting Started", link: "/guide/getting-started" },
      { text: "Quick Start", link: "/guide/quick-start" },
      { text: "Theme", link: "/guide/theme-appearance" },
      { text: "Usage", link: "/guide/usage" },
      { text: "Cookbook", link: "/guide/api-cookbook" },
      { text: "API Overview", link: "/guide/api-overview" },
      { text: "中文文档", link: "/README_zh" },
      { text: "GitHub", link: "https://github.com/lumkit/SweetEditor-Compose" }
    ],
    sidebar: [
      {
        text: "Guide",
        items: [
          { text: "Introduction", link: "/" },
          { text: "Installation", link: "/guide/installation" },
          { text: "Getting Started", link: "/guide/getting-started" },
          { text: "Quick Start", link: "/guide/quick-start" },
          { text: "Features", link: "/guide/features" },
          { text: "Theme / Appearance", link: "/guide/theme-appearance" },
          { text: "Decorations", link: "/guide/decorations" },
          { text: "Completion", link: "/guide/completion" },
          { text: "Copilot / Inline Suggestion", link: "/guide/copilot-inline-suggestion" },
          { text: "Platform Support", link: "/guide/platform-support" },
          { text: "Usage", link: "/guide/usage" },
          { text: "API Cookbook", link: "/guide/api-cookbook" },
          { text: "API Overview", link: "/guide/api-overview" },
          { text: "Core Library", link: "/guide/core-library" }
        ]
      },
      {
        text: "Docs",
        items: [
          { text: "中文文档", link: "/README_zh" },
          { text: "Platform Implementation Standard", link: "/platform-implementation-standard" }
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
})
