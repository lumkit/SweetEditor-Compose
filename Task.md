# SweetEditor-Compose CMP 高亮编辑器 TASK 清单

## 说明

- 目标：基于现有 `editor-core` Native 内核，为 `editor-compose` 补齐 Compose Multiplatform 高亮编辑器能力
- 参考对象：Android 平台样板的架构分层、JNI/Native 桥接、协议编解码、渲染与输入系统
- 当前状态：本仓库的 CMP 工程仍是项目骨架，`editor-compose` 还缺少公共模型、平台桥接、状态管理、Compose 渲染与输入链路
- 核心原则：
  - 不在 Kotlin 层重写 `editor-core` 已具备的编辑、布局、折叠、高亮、linked editing 等核心算法
  - Kotlin/CMP 层负责公共 API、状态同步、协议编解码、Compose UI 集成、平台桥接
  - 全部 line / column 均保持 0-based
  - `TextRange` 语义保持 start inclusive、end exclusive
  - 注意 UTF-8 / UTF-16 边界、二进制 payload 释放与批量接口优先

## Phase 0：架构定稿（已完成）

- 状态：已完成架构定稿，后续开发统一按本节决议执行
- 明确 CMP 总体分层：
  - `commonMain API`
  - `commonMain model/protocol`
  - `platform bridge`
  - `Compose UI`
  - `example/demo`
- 明确 Native 单一事实源：
  - 渲染模型
  - 编辑算法
  - 折叠
  - 高亮
  - diagnostics
  - linked editing
  - IME 状态
- 确定平台策略：
  - Android / JVM / iOS 走 Native 桥接
  - JS / Wasm 先提供占位实现或受限 fallback
- 把 Android 样板从 `View` 结构拆解为 CMP 可复用职责：
  - 控制器
  - 状态
  - 协议层
  - 桥接层
  - 渲染层
- 架构定稿决议：
  - 不新增第三个业务模块，继续以 `:editor-compose` 作为唯一 Library 模块承载公共 API、协议层、平台桥接与 Compose UI
  - `:example` 仅作为演示与手工验收工程，不承载任何可复用核心逻辑
  - `editor-core` 继续作为 Native 内核产物来源，Kotlin 层只消费其 C API，不复刻编辑器算法
- `editor-compose` 内部分层定稿：
  - `commonMain/api`
    - 对外暴露 `SweetEditor` Composable、`SweetEditorState`、`SweetEditorController`
    - 对外暴露 `EditorTheme`、`EditorSettings`、`LanguageConfiguration`
  - `commonMain/model`
    - 承载 `foundation`、`visual`、`decoration`、`snippet` 等纯数据模型
  - `commonMain/protocol`
    - 承载 `ProtocolEncoder`、`ProtocolDecoder`、binary reader/writer
  - `commonMain/runtime`
    - 承载状态同步、flush/rebuild、事件流、provider manager
  - `commonMain/bridge`
    - 仅定义 Native 抽象接口，不包含具体平台调用
  - `commonMain/ui`
    - 承载 Compose 渲染与输入分发，不直接触碰底层句柄
  - `androidMain` / `iosMain` / `jvmMain`
    - 仅承载 actual bridge、动态库装载、平台测量、IME/输入适配
- Android 样板到 CMP 的职责映射定稿：
  - `SweetEditor.java` → `SweetEditor` Composable + `SweetEditorState` + `SweetEditorController`
  - `EditorCore.java` → internal `NativeEditorBridge` actual 实现
  - `Document.java` → `EditorDocument` 包装层 + `NativeDocumentBridge`
  - `ProtocolEncoder.java` / `ProtocolDecoder.java` → `commonMain/protocol`
  - `EditorRenderer.java` → `commonMain/ui` 的 Compose draw/measure 实现
  - `SweetEditorInputConnection.java` → `androidMain` IME 适配层
  - `DecorationProviderManager.java` / `CompletionProviderManager.java` → `commonMain/runtime` 可复用管理器
- 平台策略定稿：
  - Android：第一优先级，负责最早打通触摸、IME、`.so` 装载与 Native 闭环
  - JVM Desktop：与 Android 同优先级，作为高效调试与主验收平台
  - iOS：在 Android/Desktop 跑通后接入 cinterop 与输入系统
  - JS / Wasm：第一阶段不承诺完整编辑能力，只提供占位实现或只读/fallback 策略
- 公共 API 边界定稿：
  - 业务层只接触 `State`、`Controller`、Composable、Theme、Settings、事件流
  - 不向业务层暴露 native handle、指针、DirectBuffer、二进制 payload
  - 所有 line/column、selection range、edit result 语义与 `editor-core` 完全一致
- 渲染策略定稿：
  - Native `EditorRenderModel` 是唯一渲染事实源
  - Compose 只负责消费 render model 并绘制，不二次推导折叠、高亮、选择、括号匹配
  - viewport、scrollbar、gutter、selection handle、diagnostics 等均以 Native 输出为准
- 输入策略定稿：
  - Compose 负责收集 pointer / keyboard / IME 事件
  - platform bridge 负责把事件转成 Native 能识别的结构
  - `State` 只接收解码后的语义结果并驱动重绘
- 生命周期与资源策略定稿：
  - `Document`、`Editor`、binary payload 都必须由桥接层统一管理释放
  - `ProtocolDecoder` 不持有 native 内存，只消费桥接层提供的安全视图
  - 所有异常优先在 bridge 内收敛，向上转成 Kotlin 可处理错误
- 第一阶段可执行范围定稿：
  - 先完成 Android + JVM Desktop
  - 先完成 `State` / `Controller` / `Protocol` / `Bridge` / `Composable` 五层闭环
  - 先实现文本加载、渲染、基础编辑、滚动、选择、高亮、diagnostics
  - completion、inline suggestion、linked editing、iOS 放到后续 Phase
- Phase 0 验收标准：
  - 目录职责、平台策略、公共 API 边界已明确
  - 后续每个 Phase 都能映射到具体 sourceSet 与内部层次
  - 不再把 Android View 架构直接移植到 CMP，而是按本节的职责拆分推进

## Phase 1：commonMain 基础模型（进行中）

- 状态：已完成第一批公共数据模型落地，当前进入协议层前置准备阶段
- 已落地文件：
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/model/foundation/TextTypes.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/model/foundation/EditResults.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/model/decoration/DecorationModels.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/model/visual/VisualModels.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/model/snippet/LinkedEditingModels.kt`
- 本阶段已完成：
  - 建立 `foundation` 的位置、范围、基础枚举
  - 建立 `TextChange`、`TextEditResult`、`KeyEventResult`
  - 建立 `visual` 首批渲染模型
  - 建立 `decoration` 首批装饰模型
  - 建立 `snippet/linked-editing` 首批数据模型
- 本阶段剩余工作：
  - 为模型补充与 C API 的 ordinal/数值映射约束
  - 为后续 `ProtocolEncoder` / `ProtocolDecoder` 增加更明确的协议友好结构
  - 在 bridge 层接入前，确认模型默认值与空状态语义

- 建立 `foundation` 公共模型
  - `TextPosition`
  - `TextRange`
  - `ScrollBehavior`
  - `AutoIndentMode`
  - `WrapMode`
  - `CurrentLineRenderMode`
- 建立 `visual` 公共模型
  - `PointF`
  - `VisualRun`
  - `VisualLine`
  - `Cursor`
  - `SelectionRect`
  - `SelectionHandle`
  - `ScrollbarModel`
  - `EditorRenderModel`
  - `ScrollMetrics`
- 建立 `decoration` 公共模型
  - `TextStyle`
  - `StyleSpan`
  - `InlayHint`
  - `PhantomText`
  - `GutterIcon`
  - `DiagnosticItem`
  - `FoldRegion`
  - `IndentGuide`
  - `BracketGuide`
  - `FlowGuide`
  - `SeparatorGuide`
- 建立 `snippet/linked-editing` 公共模型
  - `LinkedEditingModel`
  - `TabStopGroup`
  - `LinkedEditingRect`
- 建立 `TextEditResult` 与 `TextChange`
  - 保留精确变更信息
  - 不只返回最终全文

## Phase 2：协议层

- 实现 Kotlin Multiplatform 版 `ProtocolEncoder`
- 实现 Kotlin Multiplatform 版 `ProtocolDecoder`
- 建立统一二进制读写工具
  - little-endian 读写
  - UTF-8 字符串读写
  - 可选尾字段解析
  - Direct buffer / native memory 读取封装
- 覆盖以下 payload：
  - `EditorOptions`
  - `TextEditResult`
  - `KeyEventResult`
  - `GestureResult`
  - `EditorRenderModel`
  - `ScrollMetrics`
  - decorations 批量更新 payload
  - `LinkedEditingModel`
- 建立 append-only tail 兼容策略，保证 render model 协议可演进
- 明确字符串与 binary payload 的释放策略，避免内存泄漏

## Phase 3：Native 抽象接口

- 在 `commonMain` 定义 Native 抽象层
  - `NativeEditorBridge`
  - `NativeDocumentBridge`
  - `NativeLibraryLoader`
- 封装资源生命周期
  - editor handle
  - document handle
  - binary payload handle
- 设计错误模型
  - 动态库缺失
  - 符号绑定失败
  - payload 解码失败
  - 平台不支持
- 定义 `PlatformTextMeasurer` 接口，对齐 Native 所需回调
  - 文本宽度
  - inlay hint 宽度
  - icon 宽度
  - font metrics

## Phase 4：Android 平台桥接

- 为 `androidMain` 建立实际 bridge 实现
- 接入 `editor-core/include/android` 下的 `.so`
- 实现 Android 动态库装载与 ABI 配置
- 补齐 Android 文本测量 actual 实现
- 建立 Android 输入事件到 Native 事件的映射
- 封装 Android 平台资源释放与异常保护
- 将 Android JNI 方案收敛到 internal bridge，不向业务层暴露 JNI 细节

## Phase 5：iOS 平台桥接

- 基于 `c_api.h` 建立 iOS cinterop
- 实现 iOS 实际 bridge
- 处理动态库/静态库加载与符号绑定
- 实现 iOS 文本测量 actual
- 接入 iOS 输入与 IME 语义
- 建立 iOS 资源释放策略

## Phase 6：JVM Desktop 平台桥接

- 为 JVM Desktop 绑定 macOS `.dylib`
- 封装加载路径与失败兜底
- 实现 JVM 文本测量 actual
- 接入鼠标、滚轮、缩放、右键、键盘修饰键映射
- 优先完成 Desktop 可运行闭环，作为 CMP 主验证平台

## Phase 7：Document、Controller、State

- 设计 `EditorDocument` Kotlin 包装层
- 设计 `EditorController`
  - 编辑操作
  - 光标与选择操作
  - 滚动操作
  - decoration 更新
  - snippet / linked editing 控制
- 设计 `EditorState`
  - 当前 render model
  - scroll metrics
  - cursor / selection
  - composition 状态
  - 是否需要动画刷新
- 建立 `flush/rebuild` 机制
- 建立 `TextEditResult` 到上层事件流的分发机制

## Phase 8：Compose 渲染层

- 定义 `@Composable SweetEditor(...)` 入口
- 设计 Compose 侧主题、配置、回调 API
- 基于 Native render model 实现绘制链路
  - 背景
  - 当前行
  - 选择区
  - text runs
  - inlay hints
  - phantom texts
  - guides
  - diagnostics
  - cursor
  - selection handles
  - gutter
  - fold marker
  - scrollbar
- 建立图标解析能力，适配 gutter icon / inlay icon
- 建立最小缓存策略，避免每帧重复分配和重复解码

## Phase 9：输入系统

- 建立 Compose PointerInput 到 Native gesture event 的转换层
- 支持以下输入类型：
  - 点击
  - 双击
  - 长按
  - 拖选
  - handle drag
  - mouse wheel
  - direct scale
  - direct scroll
- 建立键盘事件映射
  - 普通字符输入
  - Backspace / Delete
  - 方向键
  - Home / End
  - Enter / Tab
  - Shift / Ctrl / Alt / Meta 修饰键
- 实现统一 animation tick
  - edge-scroll
  - fling
  - transient scrollbar
- 建立 hit target 分发
  - inlay hint click
  - gutter icon click
  - fold toggle click

## Phase 10：IME 与文本输入

- 定义 commonMain IME 协议接口
  - `compositionStart`
  - `compositionUpdate`
  - `compositionEnd`
  - `compositionCancel`
- Android 对接 Compose/平台输入系统
- iOS 对接系统 IME
- JVM Desktop 对接文本输入与组合输入
- 提供 IME 上下文读取能力
  - `getTextBeforeCursor`
  - `getTextAfterCursor`
  - `getSelectedText`
- 验证 UTF-8 / UTF-16 列偏移正确性
  - emoji
  - 中文
  - surrogate pair
  - 多行组合输入

## Phase 11：Theme 与 Settings

- 定义 `EditorTheme`
- 定义 `EditorSettings`
- 定义 `LanguageConfiguration`
- 接入以下配置能力：
  - wrap mode
  - tab size
  - line spacing
  - fold arrow mode
  - gutter sticky
  - gutter visible
  - current line render mode
  - read only
  - composition enabled
- 建立 style 注册机制
  - 先注册 text style
  - 再批量设置 spans

## Phase 12：Decoration Provider 系统

- 设计 KMP 版 `DecorationProvider`
- 设计 `DecorationProviderManager`
- 支持以下机制：
  - visible range
  - overscan
  - debounce
  - generation cancel
  - merge / replace-range / replace-all
- 把 decoration 更新统一收敛为批量 bridge 调用
- 将 decoration 刷新与文本变化、滚动变化解耦
- 保证 decoration 改变后统一触发一次 flush

## Phase 13：Completion 与 Inline Suggestion

- 设计 `CompletionProvider`
- 设计 `CompletionProviderManager`
- 建立 completion context
  - cursor
  - line text
  - word range
  - language configuration
  - metadata
- 支持触发方式：
  - invoked
  - trigger character
  - retrigger
- 先实现 controller 侧 completion 流程
- 再实现 Compose popup UI
- 实现 completion commit 逻辑
  - `textEdit`
  - `insertText`
  - `label`
  - snippet
- 基于 phantom text 实现 inline suggestion 第一版

## Phase 14：事件系统

- 定义公共事件模型
  - `TextChanged`
  - `CursorChanged`
  - `SelectionChanged`
  - `ScrollChanged`
  - `ScaleChanged`
  - `LongPress`
  - `DoubleTap`
  - `FoldToggle`
- 使用 `Flow` 风格暴露订阅接口
- 只暴露语义事件，不暴露底层平台事件或原始 payload

## Phase 15：Linked Editing 与 Snippet

- 实现 `LinkedEditingModel` 编码器
- 实现 `insertSnippet`
- 实现 `startLinkedEditing`
- 实现 `next / prev / cancel`
- 在 Compose 层消费 linked editing rect 并正确高亮
- 处理 linked editing 期间与 completion 的互斥关系

## Phase 16：Example 与集成验证

- 将 `example` 从空壳升级为真实演示工程
- 至少完成以下 demo：
  - 文本加载
  - 主题切换
  - wrap 开关
  - undo / redo
  - syntax highlight
  - diagnostics
  - fold
  - completion
  - linked editing
- 优先保证 Android 与 Desktop 可运行

## Phase 17：测试

- 为协议层编写单元测试
  - encoder / decoder
  - append-only tail
  - 可变长字符串
  - 空 payload / null payload
- 为语义层编写测试
  - 0-based 索引
  - exclusive end
  - UTF-16 列偏移
  - 多行编辑
  - linked editing
  - fold visibility
- 为 bridge 层编写最小集成测试
  - native 库加载
  - buildRenderModel
  - insert / replace / delete
- 为 example 建立人工验收清单

## 优先级建议

### P0

- Phase 0：架构定稿
- Phase 1：commonMain 基础模型
- Phase 2：协议层
- Phase 3：Native 抽象接口

### P1

- Phase 4：Android 平台桥接
- Phase 6：JVM Desktop 平台桥接
- Phase 7：Document、Controller、State
- Phase 8：Compose 渲染层

### P2

- Phase 9：输入系统
- Phase 10：IME 与文本输入
- Phase 11：Theme 与 Settings

### P3

- Phase 12：Decoration Provider 系统
- Phase 13：Completion 与 Inline Suggestion
- Phase 15：Linked Editing 与 Snippet

### P4

- Phase 5：iOS 平台桥接
- Phase 16：Example 与集成验证
- Phase 17：测试

## 实施原则

- 不要把 Android `View` 架构直接搬进 CMP，必须拆成 `Controller + State + Composable + Platform Bridge`
- Compose 层只消费 Native render model，不重新推导核心编辑语义
- 批量接口优先，减少 Kotlin 与 Native 间高频往返
- 所有 Native 返回的 binary payload 必须释放
- 公共 API 优先放在 `commonMain`
- 平台差异收敛到各自 `actual` 或 internal bridge
- 在 Android / Desktop 跑通闭环之前，不要急于扩散到更多能力面
