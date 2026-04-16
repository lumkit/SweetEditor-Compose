# 排障

这一页汇总集成和发布 SweetEditor 时最常见的问题。

## GitHub Pages 显示成普通 Markdown 页面

原因：

- GitHub Pages 仍在直接发布 branch 或 `docs/` 目录，而不是 GitHub Actions 构建产物

修复方式：

1. 打开仓库 `Settings`
2. 进入 `Pages`
3. 将 `Build and deployment > Source` 切到 `GitHub Actions`
4. 重新运行 Pages workflow

## 首页 Hero 图或 Logo 不显示

原因：

- VitePress 首页和站点 logo 使用的是 public 静态资源
- 对应图片必须放在 `docs/public` 下

修复方式：

- 将 hero/logo 图片放到 `docs/public/...`
- 使用类似 `/snapshot/Screenshot_Desktop.png` 的 public 路径

## 滚动时 Decoration 一直重复追加

原因：

- 可视区范围型 decoration provider 使用了 `DecorationApplyMode.Merge`

修复方式：

- 对按行、按可见区重算的 decoration，优先使用 `DecorationApplyMode.ReplaceRange`

## Completion 不弹

检查项：

- 是否通过 `addCompletionProvider()` 注册了 provider
- 是否手动触发了 completion，或者 provider 支持触发字符
- 当前是否处于 linked editing 等可能冲突的状态

## Inline Suggestion 不显示

检查项：

- 是否调用了 `controller.inlineSuggestions().show(...)`
- `line` 和 `column` 是否是合法的 0-based 位置
- 是否在文档加载完成之后再显示 suggestion

## Theme 不生效

检查项：

- 是否调用了 `controller.applyTheme(theme)`
- 是否稳定使用 `rememberEditorAppearance()` 或 `rememberEditorTheme()`
- 行为相关配置是否错误地写进了 themeContent，而不是 `EditorSettings`

## 本地构建成功，但线上看起来还是旧站

原因：

- Pages 可能还在使用旧部署
- 或者当前仍指向错误的部署来源

修复方式：

- 检查最新 workflow 是否成功
- 确认 Pages source 是 `GitHub Actions`
- 部署完成后强制刷新页面

## 相关文档

- [架构](./architecture.md)
- [FAQ](./faq.md)
- 英文 [API Cookbook](../../guide/api-cookbook.md)
