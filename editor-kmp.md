# SweetEditor KMP Core 重构架构设计

## 1. 文档目标

本文给出一个面向工业级产品的高性能 KMP 编辑器核心架构方案，目标是把当前以 `editor-core` Native Kernel 为中心的能力，逐步收敛为可在 Kotlin Multiplatform 中实现、扩展和维护的统一核心，同时继续满足以下要求：

- 语法分析、语法高亮、语义高亮、诊断、折叠、提示、补全等能力可以持续扩展
- 编辑热路径低延迟，滚动与输入稳定维持高帧率
- `commonMain` 暴露稳定抽象，平台差异下沉到各自 sourceSet
- 保留当前项目中已经成立的设计原则：0-based 索引、`TextRange` 前闭后开、RenderModel 单一事实源、批量装饰更新、Compose 只消费渲染结果
- 在需要时允许平台侧或 Native/Wasm 加速，但不让业务层直接暴露底层实现细节

本文是架构方案，不是一次性重写计划。设计目标是先定义未来稳定内核边界，再按阶段替换当前 C API 内核能力。

## 2. 现状与重构原则

基于仓库当前代码状态，可以确认几个已经很成熟的边界，这些边界在 KMP 重构后应该保留：

- `editor-compose` 已经形成 `EditorController -> EditorState -> RenderModel` 的公开交互面
- 当前核心具备明确的装饰层次：`SyntaxHighlight`、`SemanticHighlight`、`InlayHint`、`Diagnostic`、`FoldRegion`、`GutterIcon`
- 当前渲染协议是批量、结构化、以 viewport 为中心的，而不是 UI 自行推导
- 当前控制器已经把“编辑结果”和“渲染刷新”解耦，存在可复用的 dirty flag 模式
- 当前公共主题与语言配置模型已经具备可迁移性，尤其是 `LanguageConfiguration`、`DecorationSet`、`DecorationBatch`

因此 KMP 重构不能做成“Compose UI 里重新实现一个编辑器”，而应该做成：

- 把“核心编辑、布局、语法分析、装饰聚合、渲染快照”收敛到 KMP Core
- 把“平台输入、字体测量、剪贴板、IME、窗口密度、光标闪烁驱动”保留在平台层
- 把“可选高性能加速器”设计为可插拔实现，而不是新的架构中心

## 3. 设计目标

### 3.1 必须满足

- 单文档 10K~100K 行下仍然可编辑、可滚动、可高亮
- 普通输入路径只处理局部脏区，不进行全量重算
- 语法高亮和语义高亮分层，允许异步更新与过期丢弃
- 渲染快照是不可变快照，Compose 只读消费
- 所有可见结果都绑定 `documentVersion`
- 编辑结果必须返回精确变更，不能只返回最终文本
- UTF-8/UTF-16 映射必须成为核心能力，而不是 UI 层临时换算
- 允许同一套核心运行在 Android、iOS、JVM、JS、Wasm

### 3.2 明确不做

- 不在 Compose 绘制阶段重新推导折叠、高亮、诊断、布局
- 不把语言分析与编辑状态紧耦合到 UI 生命周期
- 不让每个平台维护一套独立编辑算法
- 不要求所有语言都实现完整 AST 解析后才可高亮

## 4. 总体架构

建议未来重构后的结构如下：

```text
editor-compose
  ├─ SweetEditor / RememberEditor / EditorState
  ├─ EditorController
  └─ 平台输入适配、Compose 绘制、IME 对接

editor-kmp-core-api
  ├─ EditorCore
  ├─ EditorDocument
  ├─ TextRange / TextPosition / TextEditResult
  ├─ EditorRenderModel
  ├─ DecorationSnapshot
  └─ LanguageService API

editor-kmp-core-impl
  ├─ DocumentStore
  ├─ EditEngine
  ├─ ViewportEngine
  ├─ LayoutEngine
  ├─ RenderModelBuilder
  ├─ DecorationEngine
  ├─ ParseScheduler
  └─ SnapshotStore

editor-kmp-language
  ├─ LexerRuntime
  ├─ IncrementalParserRuntime
  ├─ HighlightEngine
  ├─ FoldingEngine
  ├─ BracketMatcher
  └─ SemanticTokenAdapter

editor-kmp-platform
  ├─ TextMeasurer
  ├─ Clipboard
  ├─ ImeSessionBridge
  ├─ FrameClock
  └─ PlatformInputAdapter

editor-kmp-accelerator
  ├─ Wasm tokenizer / regex accelerator
  ├─ Native memory optimized buffer
  └─ 可选语言专用加速实现
```

核心思想：

- `editor-compose` 不再直连 Native Kernel，而是直连 `EditorCore`
- `EditorCore` 在 `commonMain` 暴露稳定 API
- 高性能算法主体放在 `commonMain`
- 平台层只提供测量、输入、系统能力
- 如确实需要某些超重型能力，可通过 `accelerator` 作为内部优化替换实现

## 5. 关键分层设计

### 5.1 Public API 层

这一层承担稳定 API 责任，保留当前项目已有概念：

- `EditorState`
- `EditorController`
- `EditorDocument`
- `TextRange`
- `TextEditResult`
- `EditorRenderModel`
- `LanguageConfiguration`

建议演进规则：

- `EditorController` 仍然是唯一协调入口
- `EditorState` 仍然只暴露 Compose 可观察快照
- `EditorCore` 替代当前 `NativeEditorBridge`
- `EditorDocument` 内部不再持有 native handle，而是持有 `DocumentId + DocumentStore reference`
- `ProtocolEncoder/ProtocolDecoder` 从“核心必须路径”降级为“跨线程/跨进程可选协议”

### 5.2 Core Impl 层

这一层是真正的编辑器内核，负责：

- 文本存储
- 编辑事务
- 撤销重做
- 位置映射
- 布局缓存
- 渲染模型构建
- 装饰合并
- 增量语法分析调度

它不依赖 Compose，也不依赖具体平台 UI。

### 5.3 Language Runtime 层

语法系统必须从编辑系统中独立出来，形成统一语言运行时：

- `LanguageDefinition`：语言静态定义
- `LanguageLexer`：词法状态机
- `LanguageParser`：可选增量结构解析器
- `SemanticProvider`：语义 token / 诊断 / 补全 / 符号
- `DecorationProducer`：把语言结果转换为装饰快照

这层允许多个实现等级：

- 仅词法高亮
- 词法 + 折叠 + 括号匹配
- 词法 + 增量语法树
- 再叠加语义 token 与诊断

### 5.4 Platform 层

平台层只负责：

- `TextMeasurer`
- 字体 metrics
- IME 生命周期
- 剪贴板
- 鼠标、触摸、键盘事件映射
- 帧时钟与动画驱动

平台层不拥有文档模型，也不拥有语法分析状态。

## 6. 文本存储与索引架构

工业级编辑器的性能根基在于文本模型。建议采用：

- 主存储：`PieceTree` 或 `Rope + PieceBuffer` 混合结构
- 原始内容缓冲区：只追加，不修改
- 编辑日志：记录变更片段和版本
- 行索引：独立维护 line start offsets
- 编码索引：按需缓存 UTF-8 byte offset 与 UTF-16 column 的映射表

推荐结构：

```text
DocumentStore
  ├─ OriginalBuffer
  ├─ AddBuffer
  ├─ PieceTree
  ├─ LineIndex
  ├─ EncodingIndex
  ├─ VersionClock
  └─ UndoRedoLog
```

### 6.1 为什么不直接用 String

- Kotlin `String` 适合 API 暴露，不适合高频中间态编辑
- 大文本频繁拼接会造成复制和 GC 压力
- 行、列、字节偏移三套坐标转换很难稳定优化

### 6.2 坐标体系

必须明确三套坐标同时存在：

- `TextPosition(line, column)`：对外公开语义，0-based
- `Utf16Offset`：IME 与 Compose 文本互操作使用
- `Utf8ByteOffset`：词法器、解析器、增量扫描器内部使用

核心要求：

- 公共 API 仍以 line/column 为主
- 任意一次编辑都要返回精确的 `changedRange`
- 任意一个 line/column 必须可快速映射到 UTF-8 边界
- 行内映射缓存按需构建、按版本失效

### 6.3 Undo/Redo 模型

建议采用事务型变更记录：

- 单次输入法提交是一条事务
- 连续字符输入可按策略合并事务
- 程序性批量替换必须保留原始 `TextChangeSet`
- 撤销重做以版本差量恢复，而不是整文本快照恢复

## 7. 编辑事务模型

建议统一所有编辑入口到 `EditCommand`：

```text
InsertText
ReplaceRange
DeleteRange
Backspace
DeleteForward
MoveLineUp
MoveLineDown
InsertLineAbove
InsertLineBelow
ApplySnippet
CompositionStart
CompositionUpdate
CompositionCommit
CompositionCancel
```

每次执行流程：

1. 输入命令
2. 转换为 `EditTransaction`
3. 写入 `DocumentStore`
4. 生成 `TextChangeSet`
5. 更新 cursor/selection/composition/linked-editing 状态
6. 发布新的 `documentVersion`
7. 触发局部失效与异步语法任务

`TextEditResult` 建议固定包含：

- `documentVersion`
- `changes`
- `selectionAfter`
- `cursorAfter`
- `linkedEditingStateChanged`
- `compositionStateChanged`
- `requiresRenderRefresh`
- `requiresScrollRecompute`

## 8. 语法分析与高亮架构

语法系统必须采用“分层、增量、可取消”的架构。

### 8.1 三层语法结果

建议把语法相关结果拆成三层：

- `LexicalLayer`：token、注释、字符串、关键字、高亮状态机
- `StructuralLayer`：括号匹配、作用域、折叠区、块结构
- `SemanticLayer`：语义 token、诊断、symbol、补全上下文

这三层之间允许版本不同步，但都必须带版本号。

### 8.2 增量词法器

词法高亮是热路径，必须做到低成本增量更新。

建议设计：

- 以“行状态机”作为最小 checkpoint
- 为每一行保存 `LexerLineState`
- 编辑后只从受影响起始行向后重扫，直到状态稳定
- 只为 dirty range 产出新的 `syntaxSpans`
- viewport 外的行只做低优先级补扫

推荐数据：

```text
LexerSnapshot
  ├─ version
  ├─ lineStates[]
  ├─ tokenBlocks[]
  ├─ dirtyRanges
  └─ stableTailLine
```

这套模型非常适合当前仓库已有的按行批量 span 提交模式。

### 8.3 增量结构解析器

不是所有语言都需要完整 AST，但工业级方案必须预留接口。

建议接口：

- `IncrementalParser.parse(changeSet, previousTree): ParseSnapshot`
- `ParseSnapshot` 输出：
  - folding regions
  - bracket scopes
  - outline nodes
  - scope map
  - parser diagnostics

实现上分级：

- 第一层：基于词法与缩进规则生成折叠
- 第二层：基于 lightweight parser 生成结构树
- 第三层：对接语言服务器或专用语义引擎

### 8.4 语义层

语义层不应阻塞输入与滚动，必须异步执行。

建议机制：

- `SemanticRequest(version, visibleRange, priority, reason)`
- 语义任务只读取不可变文档快照
- 返回结果附带 `basedOnVersion`
- 如果文档版本已推进，则结果丢弃或做可验证重用

语义层输出：

- semantic spans
- diagnostics
- inlay hints
- completion context
- code actions metadata

## 9. 装饰系统架构

建议保留当前仓库里已经成立的装饰思想，但把它上升为核心模型。

### 9.1 装饰单一聚合入口

统一由 `DecorationEngine` 聚合：

- syntax spans
- semantic spans
- inlay hints
- phantom texts
- gutter icons
- diagnostics
- indent guides
- bracket guides
- flow guides
- separator guides
- fold regions

输出不可变：

- `DecorationSnapshot(version, visibleRange, payload)`

### 9.2 分层合并原则

建议固定合并顺序：

1. Theme base style
2. Syntax highlight
3. Semantic highlight
4. Composition decoration
5. Diagnostic underline/background
6. Selection/current line
7. Inlay / phantom / gutter / fold marker

这样可以与当前 `SpanLayer.Syntax`、`SpanLayer.Semantic` 的设计完全对齐。

### 9.3 局部更新策略

对于装饰刷新，不做“全量重建后再替换”，而是：

- 行级 span 走 range replace
- guides / fold regions 走结构快照替换
- diagnostics / hints / semantic tokens 走 provider-owned snapshot
- UI 只感知最新合并结果

## 10. 渲染模型架构

RenderModel 仍然必须是单一事实源。

### 10.1 RenderModelBuilder 职责

`RenderModelBuilder` 根据以下输入构建渲染快照：

- `DocumentSnapshot`
- `ViewportState`
- `LayoutSnapshot`
- `DecorationSnapshot`
- `CursorSelectionState`
- `ScrollState`
- `EditorSettings`

输出：

- 可见行列表
- 每行 run 列表
- gutter 图标
- fold marker
- selection rects
- cursor rect
- composition rect
- guide segments
- diagnostic decorations
- scrollbar metrics

### 10.2 可见区域优先

必须只计算：

- viewport 内可见行
- 上下少量 overscan 行
- 当前交互关联行

禁止：

- 每次滚动都布局整篇文档
- 每次输入都重建所有 visual line

### 10.3 Layout 缓存

建议引入两级缓存：

- `ParagraphLayoutCache`：按 logical line + wrap width + style hash 缓存
- `VisualLineCache`：按 viewport 配置缓存切分结果

缓存键建议包含：

- `documentVersion`
- `lineNumber`
- `fontMetricsVersion`
- `wrapMode`
- `viewportWidthBucket`
- `decorationHash`

### 10.4 失效粒度

不同变更触发不同粒度失效：

- 文本编辑：失效受影响行及其后续 wrap/lexer 传播区
- 字体变化：失效全部 layout，保留语义结果
- 主题变化：失效 style/layout，保留 parse tree
- viewport 滚动：复用大部分布局，只重组可见窗口

## 11. 并发与调度模型

KMP 重构成功的关键是“主线程串行写，后台并行读”。

### 11.1 基本规则

- 所有文档变更在单一 `EditorMutationDispatcher` 上串行执行
- 所有渲染快照都基于不可变版本快照
- 词法、结构、语义任务可以后台并发
- 后台结果提交前必须检查版本一致性

### 11.2 推荐调度器

```text
UI Dispatcher
  └─ EditorMutationDispatcher

Background Dispatchers
  ├─ ParseDispatcher
  ├─ SemanticDispatcher
  ├─ CompletionDispatcher
  └─ IndexingDispatcher
```

### 11.3 Snapshot 规则

每次变更后发布：

- `DocumentSnapshot`
- `LayoutInvalidation`
- `ParseInvalidation`
- `DecorationInvalidation`

后台任务永远读取 snapshot，不直接读取 live mutable state。

## 12. 平台桥接设计

KMP Core 不是“所有东西都 commonMain 实现”，而是“核心逻辑 commonMain，平台能力 expect/actual”。

### 12.1 commonMain 提供

- 编辑状态机
- 文本模型
- 行列映射
- 词法器框架
- 增量解析框架
- 装饰聚合
- RenderModelBuilder
- 命令系统
- undo/redo

### 12.2 platform sourceSet 提供

- text measuring
- font metrics
- input event mapping
- IME session binding
- clipboard
- pointer icon / haptic / selection handle visuals
- high precision clock

### 12.3 Web / Wasm 特殊策略

Web 与 Wasm 是 KMP 重构的重要约束，建议：

- 内部文本块尽量使用 `ByteArray`/`IntArray` 等稳定结构，避免过多平台专有对象
- 大量小对象避免频繁分配，优先数组化快照
- 对高频 token/run/rect 数据采用池化或压缩结构
- 可选在 Wasm 下启用专门的 tokenizer accelerator

## 13. 工业级语言能力插件体系

建议定义统一插件接口：

```text
LanguageService
  ├─ createLexer()
  ├─ createParser()
  ├─ createSemanticProvider()
  ├─ createCompletionProvider()
  ├─ createFormattingProvider()
  └─ createBracketMatcher()
```

### 13.1 插件分级

- `BasicLanguageService`：只提供词法高亮
- `StructuredLanguageService`：提供 folding / scopes / outline
- `SemanticLanguageService`：提供 semantic token / diagnostics / inlay hints

### 13.2 配置与规则

当前 `LanguageConfiguration` 很适合作为：

- 基础括号规则
- comment token
- auto closing pairs
- surrounding pairs
- lexer 规则输入

但不应该承载所有工业级语言能力。建议把它定位为：

- 轻量 declarative config
- 与语言服务接口并存
- 用于 fallback 与 demo 语言支持

## 14. Completion / Diagnostic / Inlay Hint 统一模型

工业级编辑器中，这三类能力必须被视为统一的异步服务，而不是零散回调。

建议增加：

```text
AnalysisService
  ├─ requestDiagnostics(snapshot, range)
  ├─ requestSemanticTokens(snapshot, range)
  ├─ requestInlayHints(snapshot, range)
  ├─ requestCompletion(snapshot, position)
  └─ requestCodeActions(snapshot, range)
```

所有返回值都带：

- `requestId`
- `documentVersion`
- `targetRange`
- `producerId`

这样可以天然支持：

- 取消过期结果
- 多来源结果合并
- provider 优先级排序
- 调试与性能统计

## 15. 性能优化策略

### 15.1 热路径预算

建议以发布版目标制定预算：

- 普通字符输入主线程预算：`<= 2ms`
- 单次可见区 RenderModel 构建：`<= 4ms`
- 局部词法增量更新：`<= 3ms`
- 滚动帧内布局与快照更新：`<= 8ms`
- 语义刷新：异步，不阻塞输入

### 15.2 必做优化

- 文本增量结构，不做整串复制
- 行状态 checkpoint，避免全量重扫
- viewport scoped layout
- 批量 span / hint / diagnostic 合并
- 渲染快照不可变，减少锁竞争
- 版本化缓存与精确失效
- 高频数据结构数组化，降低对象数量

### 15.3 内存策略

建议核心内存模型：

- 文本块复用
- token block 池化
- rect/run/span 小对象压缩
- 大快照分层缓存，不重复存储文本
- 语义结果按版本与可见区分段缓存

## 16. 观测性与调试能力

工业级核心必须自带可观测性。

建议内置：

- `PerfCounters`
- `TraceEvent`
- `SlowPathWarning`
- `SnapshotDebugDump`
- `InvalidationReason`

最少要能观测：

- 文本编辑耗时
- 词法增量传播行数
- RenderModel 构建耗时
- 可见区布局命中率
- 语义任务丢弃率
- provider 响应耗时

## 17. 兼容当前仓库的演进方式

为了降低重构风险，建议采用兼容式演进，而不是一次性替换。

### 阶段 1：API 对齐

- 保留 `EditorController` / `EditorState` / `EditorDocument`
- 在内部引入 `EditorCore` 接口
- 当前 NativeBridge 与未来 KMP Core 都实现该接口

### 阶段 2：KMP 文本内核落地

- 用 KMP `DocumentStore + EditEngine` 替换 native 文档编辑能力
- 保留当前平台输入与 Compose UI
- 保留现有公共模型与测试

### 阶段 3：KMP 渲染快照落地

- 用 KMP `RenderModelBuilder` 替换二进制 render model 解码路径
- `ProtocolDecoder.decodeRenderModel()` 逐步退出主路径

### 阶段 4：KMP 词法与装饰落地

- `LanguageConfigDecorationProvider` 从 demo provider 进化为 fallback lexer runtime
- 引入真正的 `ParseScheduler` 与 `DecorationEngine`

### 阶段 5：语义与插件体系落地

- 把 diagnostic / inlay / completion 统一到 `AnalysisService`
- 引入语言服务插件注册与版本治理

### 阶段 6：NativeBridge 降级为可选加速器

- 只保留特殊平台优化能力
- 不再承担唯一核心职责

## 18. 最终推荐的核心对象模型

```text
EditorCore
  ├─ DocumentStore
  ├─ SelectionState
  ├─ CompositionState
  ├─ ViewportState
  ├─ ScrollState
  ├─ LayoutEngine
  ├─ ParseScheduler
  ├─ DecorationEngine
  ├─ RenderModelBuilder
  └─ AnalysisServiceRegistry
```

重要不可变快照：

```text
DocumentSnapshot
ParseSnapshot
DecorationSnapshot
LayoutSnapshot
RenderSnapshot
```

重要事件：

```text
DocumentChanged
SelectionChanged
ViewportChanged
ThemeChanged
LanguageChanged
AnalysisResultArrived
RenderSnapshotUpdated
```

## 19. 核心设计结论

如果目标是“使用 KMP 重构 editor 核心”，最合理的工业级路线不是把当前 C++ 核心逐行翻译成 Kotlin，而是重建一个分层明确的 KMP Core：

- 文本模型采用增量结构
- 语法系统采用分层增量分析
- 装饰系统采用统一聚合与分层快照
- 渲染系统继续坚持 RenderModel 单一事实源
- 平台层只负责输入与测量
- 语义能力全部异步、可取消、带版本
- 通过兼容式接口逐步替换当前 NativeBridge

这个方案既能保留 SweetEditor 现有架构里最有价值的部分，也能让未来的 KMP Core 真正承担“可维护、可扩展、可跨平台演进”的核心职责。

## 20. 推荐下一步

如果按这个方案继续推进，下一份设计文档建议直接细化以下三个子系统：

1. `DocumentStore + TextChangeSet + UndoRedoLog` 详细数据结构
2. `LexerRuntime + ParseScheduler + DecorationEngine` 增量分析流水线
3. `EditorCore` 对 `EditorController` 的替换接口清单

## 21. 直接开发实施方案

本节把前面的总体设计收敛成“可以直接开始编码”的方案，目标是：

- 明确第一批要新建和改造的模块
- 明确每个阶段的产出物与完成标准
- 明确哪些内容先做，哪些内容后补
- 明确哪些接口现在就可以落代码

推荐遵循一条硬规则：

- 第一阶段不追求“完全替换全部 native 能力”
- 第一阶段先让 `EditorController` 通过 `EditorCore` 抽象工作起来
- 只要 `EditorCore` 抽象稳定，后续实现可以分阶段替换

## 22. 仓库内模块落位方案

为了避免一次性大拆仓库，建议先在现有仓库中新增一个实现模块，而不是立刻拆成多个 Gradle 工程。

### 22.1 第一阶段模块策略

建议先新增一个模块：

```text
editor-core-kmp/
  └─ src/
     ├─ commonMain/kotlin/com/qiplat/compose/sweeteditor/core/
     ├─ commonTest/kotlin/com/qiplat/compose/sweeteditor/core/
     ├─ jvmTest/kotlin/com/qiplat/compose/sweeteditor/core/
     └─ ...
```

原因：

- 便于和现有 `editor-compose` 并行开发
- 不必立刻调整现有平台桥接目录
- 可以先把 KMP Core 作为内部实现模块接入
- Gradle 依赖图简单，适合快速验证

### 22.2 第二阶段模块细分

当第一阶段稳定后，再按职责拆分：

```text
editor-core-kmp-api
editor-core-kmp-impl
editor-language-kmp
editor-analysis-kmp
```

拆分时机：

- `EditorCore` API 稳定
- 文本内核与语法内核已具备独立测试
- 语言插件与诊断/补全服务开始增多

## 23. 第一批目录与文件规划

下面是建议第一批直接创建的文件清单。

### 23.1 Core API

```text
editor-core-kmp/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/core/
  EditorCore.kt
  EditorCoreFactory.kt
  EditorDocumentModel.kt
  EditorSnapshot.kt
  EditorInvalidation.kt
```

### 23.2 Document 子系统

```text
editor-core-kmp/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/core/document/
  DocumentStore.kt
  DocumentPiece.kt
  DocumentVersion.kt
  LineIndex.kt
  EncodingIndex.kt
  DocumentSnapshot.kt
  TextChange.kt
  TextChangeSet.kt
  UndoRedoLog.kt
```

### 23.3 Edit 子系统

```text
editor-core-kmp/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/core/edit/
  EditCommand.kt
  EditEngine.kt
  EditTransaction.kt
  EditSession.kt
  EditResultBuilder.kt
```

### 23.4 State 子系统

```text
editor-core-kmp/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/core/state/
  SelectionState.kt
  CompositionState.kt
  LinkedEditingState.kt
  ViewportState.kt
  ScrollState.kt
  EditorRuntimeState.kt
```

### 23.5 Render 子系统

```text
editor-core-kmp/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/core/render/
  LayoutEngine.kt
  LayoutSnapshot.kt
  VisualLineLayout.kt
  RenderModelBuilder.kt
  RenderSnapshot.kt
  RenderInvalidation.kt
```

### 23.6 Language 子系统

```text
editor-core-kmp/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/core/language/
  LanguageService.kt
  LexerRuntime.kt
  LexerSnapshot.kt
  ParseScheduler.kt
  ParseSnapshot.kt
  DecorationEngine.kt
  DecorationSnapshot.kt
```

### 23.7 Platform 抽象

```text
editor-core-kmp/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/core/platform/
  PlatformTextMeasurer.kt
  PlatformImeAdapter.kt
  PlatformClipboard.kt
  PlatformFrameClock.kt
```

## 24. 第一版必须落地的最小闭环

可直接编码的最小闭环建议如下：

### 24.1 必须有

- KMP `DocumentStore`
- KMP `EditEngine`
- KMP `SelectionState`
- KMP `ViewportState`
- KMP `LayoutEngine`
- KMP `RenderModelBuilder`
- KMP `EditorCore`
- `EditorController` 通过 `EditorCore` 而不是 `NativeEditorBridge` 驱动

### 24.2 暂时复用现有能力

- 语义高亮
- diagnostics
- inlay hints
- gutter icons
- completion

这些可以先继续挂在现有 provider 管线或 native 通道上，只要公共接口对齐即可。

### 24.3 第一版不要求

- 真正完整的增量 parser
- 全语言插件体系
- 所有平台统一 IME 深度实现
- 所有装饰都由 KMP Core 生成

这样可以确保第一版的目标清晰：先替换编辑与渲染主链路，再替换分析链路。

## 25. EditorCore 接口草案

下面这个接口级别已经可以直接进入编码。

```kotlin
interface EditorCore {
    val documentVersion: Long

    fun attachDocument(document: EditorDocumentModel?)

    fun getDocumentSnapshot(): DocumentSnapshot?

    fun setViewport(width: Int, height: Int)

    fun setScale(scale: Float)

    fun setSettings(settings: EditorSettings)

    fun setLanguageConfiguration(configuration: LanguageConfiguration?)

    fun setCursorPosition(position: TextPosition)

    fun setSelection(range: TextRange)

    fun getCursorPosition(): TextPosition

    fun getSelection(): TextRange?

    fun edit(command: EditCommand): TextEditResult

    fun applyDecorationSnapshot(snapshot: DecorationSnapshot)

    fun requestDecorationRefresh()

    fun buildRenderModel(): EditorRenderModel?

    fun getScrollMetrics(): ScrollMetrics

    fun tickAnimations(): GestureResult

    fun close()
}
```

### 25.1 接口设计说明

- `edit(command)` 统一所有编辑入口
- `applyDecorationSnapshot(snapshot)` 替换当前零散批量提交接口的上层概念
- `buildRenderModel()` 直接返回 Kotlin 模型，不再走二进制解码
- `documentVersion` 是所有异步分析与渲染缓存的版本基准

### 25.2 Factory 草案

```kotlin
interface EditorCoreFactory {
    fun create(
        textMeasurer: PlatformTextMeasurer,
        frameClock: PlatformFrameClock,
    ): EditorCore
}
```

## 26. EditCommand 直接编码草案

建议不要继续把编辑 API 分散在多个 bridge 方法里，而是尽快统一成命令模型。

```kotlin
sealed interface EditCommand {
    data class InsertText(val text: String) : EditCommand
    data class ReplaceRange(val range: TextRange, val text: String) : EditCommand
    data class DeleteRange(val range: TextRange) : EditCommand
    data object Backspace : EditCommand
    data object DeleteForward : EditCommand
    data object MoveLineUp : EditCommand
    data object MoveLineDown : EditCommand
    data object CopyLineUp : EditCommand
    data object CopyLineDown : EditCommand
    data object DeleteLine : EditCommand
    data object InsertLineAbove : EditCommand
    data object InsertLineBelow : EditCommand
    data class InsertSnippet(val template: String) : EditCommand
    data object Undo : EditCommand
    data object Redo : EditCommand
}
```

IME 与 linked editing 建议单独保留状态接口，不与普通文本编辑混在一起：

```kotlin
interface CompositionCapableEditorCore {
    fun compositionStart()
    fun compositionUpdate(text: String)
    fun compositionCommit(committedText: String? = null): TextEditResult
    fun compositionCancel()
    fun isComposing(): Boolean
}
```

## 27. DocumentStore 详细实现方案

第一版不建议一开始就写完整 PieceTree 平衡实现。为了尽快落地，可以采用“两阶段文档内核策略”。

### 27.1 第一阶段实现

- 使用 `PieceTable` 思想
- 先实现简化版本：`originalBuffer + addBuffer + mutable piece list`
- 行索引独立维护
- 单次编辑后按受影响区更新 line starts

这版目标是：

- 正确性优先
- 足够支撑 10K~50K 行文档
- 接口稳定，未来可替换成平衡树版本

### 27.2 第二阶段实现

- 将 piece list 升级为平衡树
- 节点缓存累计长度、累计换行数
- 支持大文档下近似 `O(log n)` 定位和编辑

### 27.3 建议数据结构

```kotlin
data class DocumentPiece(
    val bufferKind: BufferKind,
    val start: Int,
    val length: Int,
)

enum class BufferKind {
    Original,
    Add,
}
```

```kotlin
class DocumentStore(
    initialText: String,
) {
    val version: Long

    fun getText(): String

    fun getLineCount(): Int

    fun getLineText(line: Int): String

    fun getCharAtUtf16(offset: Int): Char

    fun getOffsetForPosition(position: TextPosition): Int

    fun getPositionForOffset(offset: Int): TextPosition

    fun apply(changeSet: TextChangeSet): DocumentSnapshot

    fun snapshot(): DocumentSnapshot
}
```

### 27.4 LineIndex 建议

第一版使用：

- `IntArray` 或 `MutableIntList` 存 line start offsets
- 每次修改仅重算受影响区域后的偏移
- 保留最后一行无换行也有 line entry

### 27.5 EncodingIndex 建议

第一版不做全量缓存，只做：

- 当前可见区行映射缓存
- IME 所在线映射缓存
- 最近编辑行映射缓存

缓存键：

- `documentVersion`
- `line`

## 28. TextChangeSet 结构

`TextEditResult` 的正确性依赖 `TextChangeSet` 精度，因此这部分必须优先定型。

```kotlin
data class TextChange(
    val rangeBefore: TextRange,
    val rangeAfter: TextRange,
    val insertedText: String,
    val deletedText: String,
)

data class TextChangeSet(
    val beforeVersion: Long,
    val afterVersion: Long,
    val changes: List<TextChange>,
)
```

第一版原则：

- 所有编辑命令都必须返回 `TextChangeSet`
- 即便是 `Backspace` 和 `DeleteForward` 也不允许只返回最终文本
- undo/redo 内部也必须以 `TextChangeSet` 驱动

## 29. EditEngine 落地顺序

推荐按下面顺序编码：

### 29.1 第一批

- `InsertText`
- `ReplaceRange`
- `DeleteRange`
- `Backspace`
- `DeleteForward`

### 29.2 第二批

- `Undo`
- `Redo`
- `SelectAll`
- 基础 cursor move

### 29.3 第三批

- `MoveLineUp`
- `MoveLineDown`
- `CopyLineUp`
- `CopyLineDown`
- `DeleteLine`
- `InsertLineAbove`
- `InsertLineBelow`
- `InsertSnippet`

原因：

- 第一批决定文本模型是否成立
- 第二批决定基础编辑体验是否可用
- 第三批属于增强编辑操作，可以在主链路稳定后补齐

## 30. LayoutEngine 直接开发方案

布局引擎建议按“先不追求最复杂样式布局，先把可见区文本画对”的策略实现。

### 30.1 第一版输入

- `DocumentSnapshot`
- `ViewportState`
- `EditorSettings`
- `PlatformTextMeasurer`
- `DecorationSnapshot`

### 30.2 第一版输出

- 可见 logical lines
- wrap 后 visual lines
- 每段 text run 的坐标
- 当前行区域
- cursor rect
- selection rects

### 30.3 第一版不做

- 极复杂混排优化
- 所有 guide 类型的细粒度布局
- 特殊字体 fallback 优化

### 30.4 VisualLineLayout 草案

```kotlin
data class VisualLineLayout(
    val logicalLine: Int,
    val wrapIndex: Int,
    val top: Float,
    val height: Float,
    val runs: List<VisualRunLayout>,
)

data class VisualRunLayout(
    val text: String,
    val x: Float,
    val width: Float,
    val styleId: Int,
)
```

### 30.5 第一版缓存策略

- 以逻辑行为缓存粒度
- 若 line text、wrap width、style span 不变，则复用布局
- viewport 变化时只重组可见区，不全量重测

## 31. RenderModelBuilder 落地方案

KMP 重构中，`RenderModelBuilder` 是替换 binary protocol 的核心节点，建议尽早完成。

### 31.1 第一版产物

- 直接生成现有 `EditorRenderModel` 对应的 Kotlin 模型
- 字段命名尽量对齐当前 render model 语义
- 允许早期字段不全，但结构先稳定

### 31.2 第一版字段优先级

优先实现：

- lines
- runs
- cursor
- selection rects
- scroll metrics
- current line

第二批再实现：

- gutter icon render items
- fold marker render items
- guide segments
- diagnostic decorations
- composition decoration

## 32. DecorationEngine 落地方案

在第一阶段，`DecorationEngine` 不需要生成所有装饰，只需先承担聚合职责。

### 32.1 第一版职责

- 接收 syntax spans
- 接收 semantic spans
- 接收 diagnostics
- 接收 inlay hints
- 聚合为统一 `DecorationSnapshot`

### 32.2 第一版来源

- `LanguageConfigDecorationProvider`
- 现有 completion / diagnostic provider manager
- 未来的 `ParseScheduler`

### 32.3 第一版实现策略

- 不改变现有 provider 公开行为
- 只在 controller 到 core 之间增加统一聚合层
- 先把“谁生产装饰”和“谁消费装饰”解耦

## 33. ParseScheduler 落地方案

语法系统第一版只做词法增量，不做完整 parser。

### 33.1 第一版职责

- 接收 `TextChangeSet`
- 计算 dirty lines
- 驱动 `LexerRuntime`
- 输出 syntax spans
- 触发装饰刷新

### 33.2 第一版实现

- 基于当前 `LanguageConfiguration.states` 规则
- 为每行维护 `LexerLineState`
- 从受影响行开始向后扫描直到状态稳定

### 33.3 第一版线程模型

- 输入字符后同步更新受影响可见区
- viewport 外的扫尾任务异步补齐
- 若新编辑发生，旧任务直接丢弃

## 34. EditorController 改造步骤

这里是最关键的接入清单，按这个顺序改几乎可以直接开发。

### 34.1 第一步

新增 `EditorCore` 字段，替代 `NativeEditorBridge` 作为 controller 主依赖。

目标状态：

- `EditorController` 所有公共方法依然不改签名
- 方法内部从调用 `nativeEditorBridge.xxx()` 改为 `editorCore.xxx()`

### 34.2 第二步

保留一个 `NativeBackedEditorCore` 适配器：

- 内部仍然调用现有 `NativeEditorBridge`
- 对外实现同一个 `EditorCore`

这样可以做到：

- controller 先切到新抽象
- 行为保持不变
- 默认核心实现可以逐步替换具体方法实现

### 34.3 第三步

增加 `EditorCoreImpl`：

- 先实现 document/edit/render 主链路
- 未完成的能力继续委托给 native 或现有 provider

### 34.4 第四步

通过 factory 决定注入哪种 core：

- `NativeBackedEditorCoreFactory`
- `EditorCoreFactoryImpl`

## 35. 推荐的编码顺序

下面这个顺序最稳，也最容易持续提交。

### 35.1 第 1 批提交

- 新增 `editor-core-kmp` 模块
- 新增 `EditorCore`、`EditorCoreFactory`
- 新增 `NativeBackedEditorCore`
- `EditorController` 切到 `EditorCore`

验收标准：

- 所有现有行为保持一致
- 现有测试可继续通过
- 对外 API 无破坏

### 35.2 第 2 批提交

- 新增 `DocumentStore`
- 新增 `TextChangeSet`
- 新增 `EditEngine`
- 实现 `InsertText/ReplaceRange/DeleteRange`

验收标准：

- 可以纯 KMP 完成基础文本修改
- 返回精确 `TextEditResult`
- 单元测试覆盖多行、emoji、CRLF/LF

### 35.3 第 3 批提交

- 新增 `LayoutEngine`
- 新增 `RenderModelBuilder`
- `EditorCoreImpl` 能生成基础 render model

验收标准：

- 文本可见区正确显示
- cursor/selection 正确
- scroll metrics 正确

### 35.4 第 4 批提交

- 新增 `ParseScheduler`
- 新增 `LexerRuntime`
- 产出 syntax spans

验收标准：

- 可见区语法高亮正确
- 局部编辑只触发局部扫描
- 过期扫描结果可丢弃

### 35.5 第 5 批提交

- 把 diagnostics / semantic spans / inlay hints 并入 `DecorationEngine`
- 替换 controller 内零散装饰刷新逻辑

验收标准：

- 各装饰层顺序稳定
- 不阻塞输入
- decoration snapshot 带版本并可追踪来源

## 36. 每个阶段的测试方案

### 36.1 DocumentStore 测试

- 单行插入、删除、替换
- 多行插入、删除、替换
- CRLF/LF 混合输入
- emoji、代理对、中文
- 头部、中部、尾部编辑
- 大文本随机编辑回归

### 36.2 EditEngine 测试

- `Backspace/DeleteForward`
- selection replace
- undo/redo
- line operation
- snippet 基础插入

### 36.3 LayoutEngine 测试

- wrap on/off
- tab size 变化
- line spacing 变化
- viewport 改变
- cursor rect 与 line/column 对齐

### 36.4 ParseScheduler 测试

- 行状态传播停止条件
- 注释/字符串跨行传播
- 增量编辑仅影响必要行
- 版本不一致结果丢弃

### 36.5 Controller 集成测试

- `EditorController` 切换 `NativeBackedEditorCore` 与 `EditorCoreImpl` 后对外行为一致
- `refreshNow()` 后 `EditorState.renderModel` 更新正确
- `TextEditResult` 与 state dirty flag 同步正确

## 37. 直接可执行的验收清单

当以下清单全部满足，说明可以宣布 KMP Core 第一阶段完成：

- `EditorController` 不再直接依赖 `NativeEditorBridge`
- 存在稳定的 `EditorCore` 抽象
- KMP `DocumentStore` 能支撑基础编辑
- KMP `EditEngine` 返回精确 `TextChangeSet`
- KMP `RenderModelBuilder` 能生成基础 render model
- KMP `LayoutEngine` 支撑可见区绘制
- 基础语法高亮由 `ParseScheduler + LexerRuntime` 提供
- 现有 Compose UI 无需重写
- 至少 JVM/Android common test 能跑通主路径

## 38. 第一阶段不建议现在做的事情

为了避免项目失控，下面这些内容不建议在第一阶段并行推进：

- 完整 LSP 客户端
- 全量 AST parser 框架
- 多文档共享索引
- 协作编辑
- 持久化增量索引
- 完整性能面板 UI

第一阶段最怕的问题不是功能不够，而是抽象不稳定、主链路太长、一次性替换太多。

## 39. 最终建议

如果现在就进入编码，我建议以以下一句话作为实际开发准则：

- 先让 `EditorController -> EditorCore -> Default Document/Edit/Layout/Render` 跑通，再逐步把语法、装饰、语义迁入

按这个顺序做，你可以在不破坏当前项目可用性的前提下，逐步把 SweetEditor 的核心控制权迁移到 KMP。

## 40. 编码进度记录

### 40.1 第一批编码 - 已完成

本轮已完成第一批改造中的“抽象切换”部分，目标是先把 `EditorController` 从直接依赖 `NativeEditorBridge` 改为依赖 `EditorCore` 抽象。

已完成内容：

- 在 `editor-core-shared` 中新增 `EditorCore`、`EditorCoreFactory`、`EditorCoreDocument`、`EditorCoreTextMeasurer`
- 在 `editor-core-shared` 中新增第一批核心桥接模型：
  - `EditorCoreTextPosition`
  - `EditorCoreTextRange`
  - `EditorCoreCursorRect`
  - `EditorCoreLinkedEditingModel`
  - `EditorCoreWrapMode`
  - `EditorCoreCurrentLineRenderMode`
  - `EditorCoreFoldArrowMode`
  - `EditorCoreAutoIndentMode`
  - `EditorCoreScrollBehavior`
- 在 `editor-compose` 中新增 `NativeBackedEditorCoreFactory`
- 在 `editor-compose` 中新增 `NativeBackedEditorCore`
- `EditorController` 已改为通过 `EditorCore` 驱动，而不是直接持有 `NativeEditorBridge`
- `NativeDocumentBridge` 与 `NativeTextMeasurer` 已接入新的 shared 抽象接口

本轮改造结果：

- `EditorController` 对外 API 保持不变
- 现有 native 路径行为保持不变
- 新增了后续接入 `EditorCoreImpl` 的稳定插槽

验证结果：

- `:editor-core-shared:compileKotlinJvm` 通过
- `:editor-compose:compileKotlinJvm` 通过
- `:editor-compose:jvmTest` 未完全通过，但失败点位于现有测试文件 `DecorationProviderManagerCommonTest.kt` 的 `runBlocking` 相关编译问题，不是本轮抽象切换引入的新错误

### 40.2 第二批编码 - 已完成

本轮已完成第二批改造中的“基础文本内核”部分，目标是先在 `editor-core-shared` 中落可测试的文档模型和基础编辑引擎。

已完成内容：

- 在 `editor-core-shared` 中新增 `DocumentStore`
- 在 `editor-core-shared` 中新增：
  - `DocumentPiece`
  - `BufferKind`
  - `DocumentSnapshot`
  - `TextChange`
  - `TextChangeSet`
- `DocumentStore` 已支持：
  - 文本快照读取
  - 行数读取
  - 行文本读取
  - `line/column <-> offset` 双向换算
  - 单次 replace 变更
  - 基于 `TextChangeSet` 的 apply
- 在 `editor-core-shared` 中新增 `EditEngine`
- `EditEngine` 第一版已支持：
  - `InsertText`
  - `ReplaceRange`
  - `DeleteRange`
  - `Backspace`
  - `DeleteForward`
- 已补充 `DocumentStoreCommonTest`
- 已补充 `EditEngineCommonTest`

本轮验证结果：

- `:editor-core-shared:jvmTest` 通过
- `:editor-core-shared:compileKotlinJvm` 通过
- `:editor-compose:compileKotlinJvm` 回归通过

### 40.3 第三批编码 - 已完成

本轮已完成第三批改造中的“撤销重做基础设施 + 默认核心骨架”部分，同时按最新要求统一了命名策略。

命名策略调整：

- 后续默认实现统一使用正常命名，不使用带 `Kmp` 字样的类名
- 默认核心实现命名为 `EditorCoreImpl`
- 默认核心工厂命名为 `EditorCoreFactoryImpl`
- 文档中的后续规划也同步改成上述命名

已完成内容：

- 在 `editor-core-shared` 中新增 `UndoRedoLog`
- `EditEngine` 已支持：
  - `Undo`
  - `Redo`
  - `canUndo()`
  - `canRedo()`
- 已补充 `EditEngineCommonTest` 的 undo/redo 用例
- 已新增 `EditorDocument`
- 已新增 `EditorDocuments`
- 已新增 `EditorCoreFactoryImpl`
- 已新增 `EditorCoreImpl`

当前 `EditorCoreImpl` 的定位：

- 它是默认核心实现的第一版骨架
- 已能接入 `DocumentStore + EditEngine`
- 已能处理基础文本编辑与 undo/redo 状态推进
- `shared` 内部已改为强类型结果模型，不再为了兼容 `editor-compose` 输出二进制编辑结果
- `editor-compose` 当前已与 `editor-core-shared` 解耦，暂不作为 shared 设计约束
- 已接入第一版 `LayoutEngine`
- 已能输出基础 `EditorCoreRenderModel`
- 已能输出基础 `EditorCoreScrollMetrics`
- 尚未接入完整 render pipeline、复杂布局与 decoration 强类型模型

本轮验证结果：

- `:editor-core-shared:jvmTest` 通过
- `:editor-core-shared:compileKotlinJvm` 通过
- `:editor-compose:compileKotlinJvm` 回归通过

### 40.4 当前未完成项

当前主链路尚未完成的内容：

- `SelectAll` 之外的更多 cursor/selection 行为
- `RenderModelBuilder`
- `ParseScheduler`
- 更完整的 wrap、selection、hit-test 与 viewport 布局策略

### 40.5 下一步直接编码顺序

下一步按以下顺序继续：

1. 在 shared 层把 `LayoutEngine` 扩展到更完整的 wrap / hit-test / visual line 语义
2. 提炼独立 `RenderModelBuilder`
3. 补充更多 cursor/selection 行为
4. 建立基础 viewport 命中与滚动定位策略
5. 再开始接语法分析调度

### 40.6 解耦决策记录

本轮新增一个明确决策：

- `editor-compose` 先不接入 `editor-core-shared`
- `editor-core-shared` 不再为了兼容 `editor-compose` 设计返回协议
- shared 内部优先建立 Kotlin 强类型模型
- protocol / binary 只允许未来出现在真正的 native 边界，而不是 shared 内部

本轮已完成的落地动作：

- 移除 `editor-compose` 对 `editor-core-shared` 的 Gradle 依赖
- `editor-compose` 保留本地过渡抽象，继续独立编译
- 删除 shared 内部的 `EditResultEncoding`
- `EditorCoreImpl` 的编辑接口已改成返回 `EditorCoreTextEditResult`

### 40.7 Layout 主线进度

本轮已完成 shared 主线里的基础布局接入：

- 新增 `LayoutEngine`
- 新增 `LayoutSettings`
- 新增 `LayoutSnapshot`
- 为 `EditorCoreRenderModel` 增加：
  - `lines`
  - `cursorRect`
  - `selectionRects`
  - `contentWidth`
  - `contentHeight`
  - `lineHeight`
- `EditorCoreImpl` 已接入：
  - 基础 viewport 布局
  - 基础 scroll metrics
  - 基础 cursor rect
  - 基础 selection rect
  - 基础 char break / word break wrapping

本轮验证结果：

- `:editor-core-shared:jvmTest` 通过
- `:editor-core-shared:compileKotlinJvm` 通过

### 40.8 RenderModelBuilder 主线进度

本轮已完成 shared 主线里的渲染模型构建拆分：

- 新增 `RenderModelBuilder`
- 新增 `RenderModelBuildInput`
- `EditorCoreImpl` 不再直接拼装 `EditorCoreRenderModel`
- `EditorCoreImpl` 现在通过：
  - `LayoutEngine` 负责布局快照
  - `RenderModelBuilder` 负责渲染模型组装

这次拆分后的职责边界：

- `LayoutEngine`：负责文本布局、换行、位置矩形、选择矩形
- `RenderModelBuilder`：负责把布局快照与交互态合成为 `EditorCoreRenderModel`
- `EditorCoreImpl`：负责协调文档、编辑、布局、滚动与渲染构建

本轮验证结果：

- `:editor-core-shared:jvmTest` 通过
- `:editor-core-shared:compileKotlinJvm` 通过

下一步建议顺序：

1. 给 `LayoutEngine` 增加 hit-test 与 point -> position 反查
2. 扩展 `EditorCoreImpl` 的 cursor / selection 移动语义
3. 再把 viewport 与 scroll 定位策略做细化

### 40.9 Hit-Test 与 Cursor Movement 主线进度

本轮已完成 shared 主线里的命中测试与基础光标移动：

- `LayoutEngine` 新增 `LayoutHitTestResult`
- `LayoutEngine` 新增 `hitTest(x, y)`，可从点坐标反查 `TextPosition`
- `EditorCoreImpl` 已接入：
  - `moveCursorLeft`
  - `moveCursorRight`
  - `moveCursorUp`
  - `moveCursorDown`
  - `moveCursorToLineStart`
  - `moveCursorToLineEnd`
- 垂直移动已引入基础 `x` anchor 语义
- 选择扩展移动已具备基础行为

本轮补充修正：

- 修正了 selection range 的 clamp 行为
- 修正了 cursor movement 中的位置比较逻辑

本轮验证结果：

- `:editor-core-shared:jvmTest` 通过
- `:editor-core-shared:compileKotlinJvm` 通过

下一步建议顺序：

1. 细化 selection anchor / extent 语义
2. 增加 point -> visual line / wrap segment 命中能力
3. 补充 viewport scroll 跟随与 ensure cursor visible
