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
      { text: "Getting Started", link: "/guide/getting-started" },
      { text: "Features", link: "/guide/features" },
      { text: "中文文档", link: "/README_zh" },
      { text: "GitHub", link: "https://github.com/lumkit/SweetEditor-Compose" }
    ],
    sidebar: [
      {
        text: "Guide",
        items: [
          { text: "Introduction", link: "/" },
          { text: "Getting Started", link: "/guide/getting-started" },
          { text: "Features", link: "/guide/features" },
          { text: "Core Library", link: "/guide/core-library" }
        ]
      },
      {
        text: "Docs",
        items: [
          { text: "中文文档", link: "/README_zh" }
        ]
      }
    ],
    socialLinks: [
      { icon: "github", link: "https://github.com/lumkit/SweetEditor-Compose" }
    ],
    footer: {
      message: "SweetEditor documentation site powered by VitePress.",
      copyright: "Copyright © SweetEditor Contributors"
    },
    search: {
      provider: "local"
    }
  }
})
