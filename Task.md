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

## Phase 2：协议层（进行中）

- 状态：已完成协议层基础设施、首批 encoder/decoder 与协议测试
- 已落地文件：
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/protocol/BinaryWriter.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/protocol/BinaryReader.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/protocol/NativeProtocolMappings.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/protocol/ProtocolEncoder.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/protocol/ProtocolDecoder.kt`
  - `editor-compose/src/commonTest/kotlin/com/qiplat/compose/sweeteditor/protocol/ProtocolCommonTest.kt`
- 本阶段已完成：
  - 建立 little-endian 二进制读写工具
  - 建立 Native enum/int 与 Kotlin 模型间的映射层
  - 完成 `EditorOptions` 编码
  - 完成 `TextEditResult`、`KeyEventResult`、`GestureResult`、`ScrollMetrics`、`EditorRenderModel` 解码基础能力
  - 完成 `LinkedEditingModel`、text styles、line spans、fold regions、inlay hints、phantom texts、gutter icons、diagnostics 的首批编码能力
  - 建立 append-only tail 兼容式 scrollbar 解码策略
  - 补充 commonTest 协议层基础测试
- 本阶段剩余工作：
  - 扩展更多 batch/global payload 编码覆盖面
  - 为 bridge 层接入预留 ByteArray 与平台 native buffer 互转策略
  - 将后续新增模型的 native value 约束持续收敛到映射层

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

## Phase 3：Native 抽象接口（进行中）

- 状态：已完成第一版 commonMain Native bridge 抽象，后续继续补错误模型与跨平台工厂收敛
- 已落地文件：
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/bridge/NativeBridge.kt`
- 本阶段已完成：
  - 定义 `NativeTextMeasurer`
  - 定义 `NativeDocumentBridge`
  - 定义 `NativeEditorBridge`
  - 定义 `NativeBridgeFactory`
  - 明确桥接层与协议层的边界：桥接层接收 `ByteArray` payload，不暴露 native 指针
- 本阶段剩余工作：
  - 补充更明确的错误模型与失败原因封装
  - 视平台落地情况决定是否补 `NativeLibraryLoader` 公共抽象
  - 在 JVM / iOS 接入时统一 bridge 工厂入口

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

## Phase 4：Android 平台桥接（进行中）

- 状态：已完成第一版 Android JNI bridge、`.so` 装载、ABI 配置与 text measurer 回调链路
- 已落地文件：
  - `editor-compose/src/androidMain/kotlin/com/qiplat/compose/sweeteditor/bridge/AndroidNativeBindings.kt`
  - `editor-compose/src/androidMain/kotlin/com/qiplat/compose/sweeteditor/bridge/AndroidNativeBridge.kt`
  - `editor-compose/src/androidMain/cpp/android_bridge.cpp`
  - `editor-compose/src/androidMain/cpp/CMakeLists.txt`
  - `editor-compose/build.gradle.kts`
- 本阶段已完成：
  - 建立 Android Native 实际 bridge
  - 接入 `editor-core/include/android` 下的预编译 `.so`
  - 配置 ABI 过滤与 `jniLibs`
  - 建立 JNI 到 C API 的调用转发
  - 建立 Android `NativeTextMeasurer` 回调链路
  - 让 binary payload 在 JNI 内复制为 `ByteArray` 后立即释放，避免向 Kotlin 暴露裸指针
  - 接入首批编辑、渲染、滚动、装饰更新、选择与文本变更接口
- 本阶段剩余工作：
  - 用更高层 `EditorState` / `Controller` 驱动 bridge
  - 完善 Android 输入系统到 `GestureType` / key modifiers 的映射
  - 补 composition、scrollbar config、handle config 等更多 C API 覆盖面
  - 补 Android 侧桥接测试与真实 demo 验证

- 为 `androidMain` 建立实际 bridge 实现
- 接入 `editor-core/include/android` 下的 `.so`
- 实现 Android 动态库装载与 ABI 配置
- 补齐 Android 文本测量 actual 实现
- 建立 Android 输入事件到 Native 事件的映射
- 封装 Android 平台资源释放与异常保护
- 将 Android JNI 方案收敛到 internal bridge，不向业务层暴露 JNI 细节

## Phase 5：iOS 平台桥接（进行中）

- 状态：已完成第一版 iOS bridge 骨架，当前受限于仓库尚未提供 iOS native editor-core 产物
- 已落地文件：
  - `editor-compose/src/iosMain/kotlin/com/qiplat/compose/sweeteditor/bridge/IosNativeBridge.kt`
- 本阶段已完成：
  - 建立 iOS `NativeBridgeFactory` 骨架入口
  - 明确当前 iOS 桥接的阻塞条件为缺少 iOS native 库与 cinterop 配置
  - 将运行时失败显式收敛为受控错误，而不是隐式链接失败
- 本阶段剩余工作：
  - 增加 iOS native editor-core 产物
  - 接入 cinterop 配置与符号绑定
  - 实现 iOS 文本测量、输入、IME 与资源释放
- 阻塞说明：
  - 当前仓库仅提供 Android `.so`、macOS `.dylib` 和 Windows `.dll`
  - 在未引入 iOS 原生产物前，iOS bridge 只能先落骨架，不能接成完整闭环

- 基于 `c_api.h` 建立 iOS cinterop
- 实现 iOS 实际 bridge
- 处理动态库/静态库加载与符号绑定
- 实现 iOS 文本测量 actual
- 接入 iOS 输入与 IME 语义
- 建立 iOS 资源释放策略

## Phase 6：JVM Desktop 平台桥接（进行中）

- 状态：已完成第一版 Desktop JNI bridge、macOS `.dylib` 定位逻辑与本地构建任务
- 已落地文件：
  - `editor-compose/src/jvmMain/kotlin/com/qiplat/compose/sweeteditor/bridge/DesktopNativeBindings.kt`
  - `editor-compose/src/jvmMain/kotlin/com/qiplat/compose/sweeteditor/bridge/DesktopNativeBridge.kt`
  - `editor-compose/src/jvmMain/cpp/desktop_bridge.cpp`
  - `editor-compose/src/jvmMain/cpp/CMakeLists.txt`
  - `editor-compose/build.gradle.kts`
- 本阶段已完成：
  - 建立 Desktop Native 实际 bridge
  - 接入 macOS `libsweeteditor.dylib` 路径解析
  - 建立 Desktop JNI 到 C API 的调用转发
  - 建立 Desktop `NativeTextMeasurer` 回调链路
  - 新增 `buildDesktopBridge` 构建任务，输出 Desktop JNI bridge 动态库
- 本阶段剩余工作：
  - 用更高层 `EditorState` / `Controller` 驱动 Desktop bridge
  - 补充 Desktop 输入事件映射与 Compose Desktop 接入
  - 补充 Desktop 运行时验证与 demo 演示
  - 视后续需要决定是否将 Desktop bridge 自动挂接到 JVM 资源打包流程

- 为 JVM Desktop 绑定 macOS `.dylib`
- 封装加载路径与失败兜底
- 实现 JVM 文本测量 actual
- 接入鼠标、滚轮、缩放、右键、键盘修饰键映射
- 优先完成 Desktop 可运行闭环，作为 CMP 主验证平台

## Phase 7：Document、Controller、State（进行中）

- 状态：已完成第一版 commonMain 运行时骨架，平台 bridge 已开始通过公共控制层收敛
- 已落地文件：
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/bridge/PlatformNativeBridgeFactory.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/runtime/EditorTextMeasurer.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/runtime/EditorDocument.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/runtime/EditorState.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/runtime/EditorController.kt`
  - `editor-compose/src/androidMain/kotlin/com/qiplat/compose/sweeteditor/bridge/PlatformNativeBridgeFactory.android.kt`
  - `editor-compose/src/jvmMain/kotlin/com/qiplat/compose/sweeteditor/bridge/PlatformNativeBridgeFactory.jvm.kt`
  - `editor-compose/src/iosMain/kotlin/com/qiplat/compose/sweeteditor/bridge/PlatformNativeBridgeFactory.ios.kt`
  - `editor-compose/src/jsMain/kotlin/com/qiplat/compose/sweeteditor/bridge/PlatformNativeBridgeFactory.js.kt`
  - `editor-compose/src/wasmJsMain/kotlin/com/qiplat/compose/sweeteditor/bridge/PlatformNativeBridgeFactory.wasmJs.kt`
- 本阶段已完成：
  - 将 `EditorDocument`、`EditorState`、`EditorController` 优先沉淀到 commonMain
  - 建立平台 bridge 工厂的公共入口，并把平台差异收敛到 actual/sourceSet
  - 让控制层直接消费协议层与 bridge 层，完成渲染刷新、文本编辑、键盘事件、手势事件、装饰批量更新的公共编排
  - 为 Web/Wasm 提供显式未实现的桥接占位，避免把平台细节泄露到 commonMain
- 本阶段剩余工作：
  - 增加更细粒度的配置对象与事件流
  - 补 snippet / linked editing 入口
  - 增加 `EditorState` 与 Compose 渲染层之间的绑定辅助 API
  - 补控制层测试与后续 `remember` 风格接入

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

## Phase 8：Compose 渲染层（进行中）

- 状态：已完成 commonMain 的第一版 Editor Surface 入口与基础渲染骨架
- 已落地文件：
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/SweetEditor.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/RememberEditor.kt`
- 本阶段已完成：
  - 定义 `@Composable SweetEditor(...)` 入口
  - 定义 `rememberEditorState()` 与 `rememberEditorController()`
  - 将 Compose UI 层建立在 commonMain `EditorState` / `EditorController` 之上
  - 不使用 `Text` / `BasicTextField` 组件承载编辑内容，而是将 Compose 端实现为真正的 Editor Surface
    - 可滚动 viewport
    - 可接收键盘事件的宿主
    - 可接收 pointer/drag/scroll 手势的宿主
    - 基于 Canvas 的渲染面板
  - 基于 Native `EditorRenderModel` 实现第一版绘制链路
    - 背景
    - 当前行
    - 选择区
    - text runs
    - guides
    - diagnostics
    - cursor
    - linked editing rect
    - scrollbar
    - gutter 背景
    - gutter line number
    - gutter icon 占位图形
    - fold marker 占位图形
    - selection handle 占位图形
- 本阶段剩余工作：
  - 补 gutter icon、fold marker、selection handle 的正式图标/命中逻辑
  - 补更完整的输入系统与命中测试接入
  - 补 IME 组合输入桥接
  - 把文本测量与 Compose 文本绘制之间的视觉一致性继续收敛
  - 为 `SweetEditor` 补主题、配置与事件回调 API

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

## Phase 9：输入系统（进行中）

- 状态：已完成第一版 commonMain 输入桥接骨架，并已把点击 / 双击 / 长按恢复到由 Native Core 识别
- 本阶段已完成：
  - 建立 `SweetEditor` 作为可聚焦键盘宿主
  - 建立逐帧原始 pointer event 到 Native gesture event 的转换
  - 建立 touch down / pointer down / move / pointer up / up / cancel 的基础映射
  - 建立鼠标 down / move / up / right down / wheel 的基础映射
  - 建立键盘事件到 native keyCode / modifiers / text 的转换
  - 建立 `needsAnimation -> tickAnimations()` 的统一动画驱动
  - 扩展 bridge 层支持 gesture modifiers、wheel delta、direct scale、tickAnimations
  - 移除 Compose / bridge 层手动 `setScroll` 旁路，scrollbar 语义回归 Core
  - 移除 Compose 层对 selection handle / fold marker / gutter icon 的本地命中决策
  - 恢复点击文本定位光标
  - 恢复双击、长按由 Core 基于原始事件序列识别
  - 修正多指 `TouchMove` 传递完整活动触点集，向 Core 对齐 `MotionEvent` 风格输入
  - 建立双指缩放到 `DIRECT_SCALE` 的映射，并将活动触点质心作为缩放焦点传给 Core
  - 建立 `GestureResult` / `HitTarget` 到 `SweetEditor` 外部回调的分发
  - 建立 `ContextMenu` 到 `SweetEditor` 外部回调的分发
  - 建立 `SelectionHandleDragState` 到 `SweetEditor` 外部回调的分发，与 Core `isHandleDrag` 状态对齐
  - 在 Android / JVM / iOS / JS / Wasm sourceSet 分别接入平台 IME 会话入口，并将 composition 编辑命令映射到 Core composition API
  - 补基础 IME action 提交语义与 composition 代理值同步，减少平台输入会话中的陈旧 composition 状态
  - 新增 cursor / selection 原生查询桥接，并基于当前逻辑行文本生成 IME proxy extracted text / surrounding text 视图
  - Android 改为基于 `PlatformTextInputModifierNode` 的低层 IME 会话，并接入 Compose focus 事件驱动的 IME session 生命周期
  - Android 补齐 `setComposingRegion`、`deleteSurroundingTextInCodePoints`、`ExtractedText`、多行 surrounding text 查询与协议级 keyCode 映射
  - Android 补齐软键盘光标/选区/换行/复制/剪切/粘贴/选区删除语义，并修正符号键盘页不应被提交动作错误重置的问题
  - Android 补基础 `requestCursorUpdates` / `CursorAnchorInfo` 更新链，向 IME 暴露插入点与选区范围
- 本阶段剩余工作：
  - 继续校准 Android IME 的 UTF-16 / UTF-8 列偏移边界与 emoji/surrogate pair 输入
  - 继续细化 Android `CursorAnchorInfo` 的字符边界、可见行边界与更准确的 baseline/几何信息
  - 按平台进一步细化更大范围 surrounding text 与 extracted text 映射

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
- 建立 Core hit target 到平台回调的分发
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

## Phase 11：Theme 与 Settings（进行中）

- 状态：已完成 commonMain 主题模型、`EditorSettings`、可扩展 style id 语义、语言元数据解析增强与 example 设置入口联动
- 已落地文件：
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/theme/EditorTheme.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/theme/LanguageConfiguration.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/EditorSettings.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/SweetEditor.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/runtime/EditorController.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/runtime/EditorState.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/RememberEditor.kt`
  - `example/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/App.kt`
  - `editor-compose/src/commonTest/kotlin/com/qiplat/compose/sweeteditor/theme/ThemeAndLanguageCommonTest.kt`
- 本阶段已完成：
  - 定义 `EditorTheme`
  - 定义 `EditorSettings`
  - 定义 `LanguageConfiguration`
  - 建立主题 text style 注册入口
  - 扩展 `EditorThemeStyleIds` 与语义别名映射，覆盖 method/property/constant/operator/namespace 等更多 style id
  - 补充主题解析器对 `textStyles` 自定义覆盖的支持
  - 建立 `EditorSettings -> EditorController -> NativeEditorBridge` 的批量设置下发链路
  - 将主题颜色与字体应用到 `SweetEditor` 的渲染面板
  - 将 theme / settings 自动接入 `SweetEditor` 生命周期
  - 扩展 `LanguageConfiguration` 元数据，补 scopeName、comments、bracket pairs、auto closing pairs、surrounding pairs、highlight style id 映射
  - 将 `LanguageConfiguration` 接入 `EditorState` / `EditorController`，为后续高亮与补全 provider 建立语言上下文入口
  - 在 example 中从 `Res/files/kotlin.json` 解析语言配置，并从 `Res.font` 读取字体
  - 在 example 中接入 wrap / readOnly / composition 设置切换入口，并展示语言元数据摘要
- 本阶段剩余工作：
  - 在 decoration/completion provider 正式落地后，把 `LanguageConfiguration` 直接接入高亮状态机构建与补全上下文生成
  - 视主题格式演进继续补充更细粒度 token 与文本样式能力

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

## Phase 12：Decoration Provider 系统（进行中）

- 状态：已完成首版 KMP `DecorationProvider` / `DecorationProviderManager`、可见区调度与批量 flush 闭环，并补上 `LanguageConfigDecorationProvider` 的 json 驱动语法装饰
- 已落地文件：
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/DecorationProvider.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/LanguageConfigDecorationProvider.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/model/decoration/DecorationModels.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/protocol/ProtocolEncoder.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/bridge/NativeBridge.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/runtime/EditorController.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/runtime/DecorationProviderManager.kt`
  - `editor-compose/src/androidMain/kotlin/com/qiplat/compose/sweeteditor/bridge/AndroidNativeBridge.kt`
  - `editor-compose/src/jvmMain/kotlin/com/qiplat/compose/sweeteditor/bridge/DesktopNativeBridge.kt`
- 本阶段已完成：
  - 定义 spans / inlay hints / phantom texts / gutter icons / diagnostics / fold regions 数据模型
  - 建立 decoration 相关协议编码器
  - 设计 KMP 版 `DecorationProvider`
  - 设计 `DecorationProviderManager`
  - 支持以下机制：
    - visible range
    - overscan
    - debounce
    - generation cancel
    - merge / replace-range / replace-all
  - 实现 `LanguageConfigDecorationProvider`，支持 `variables / fragments / states / include / includes / subStates / onLineEndState`
  - 将 decoration 更新统一收敛为批量 bridge 调用
  - 在 `EditorController` 暴露统一批量 flush 入口
  - 将 decoration 刷新与文本变化、滚动变化解耦，并接入 `SweetEditor` 生命周期
- 本阶段剩余工作：
  - 继续细化 provider 调度策略，例如更稳定的可见区 diff 与更精确的 scroll 触发时机
  - 继续补 decoration provider 的公共结果模型，例如更细粒度的清理/优先级语义
  - 继续补全更复杂的 language state 机语义与增量重算缓存
  - 在 example 中补更多 decoration provider 演示

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

## Phase 15：Linked Editing 与 Snippet（进行中）

- 状态：已完成 linked editing 的公共模型、协议编码与渲染消费基础，snippet/controller 交互流程尚未实现
- 已落地文件：
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/model/snippet/LinkedEditingModels.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/protocol/ProtocolEncoder.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/protocol/ProtocolDecoder.kt`
  - `editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/SweetEditor.kt`
- 本阶段已完成：
  - 实现 `LinkedEditingModel` 编码器
  - 在 render model / Compose 层消费 linked editing rect 并进行高亮绘制
- 本阶段剩余工作：
  - 实现 `insertSnippet`
  - 实现 `startLinkedEditing`
  - 实现 `next / prev / cancel`
  - 处理 linked editing 期间与 completion 的互斥关系

## Phase 16：Example 与集成验证（进行中）

- 状态：`example` 已脱离空壳，具备文本加载、语言元数据展示、设置开关与 json 驱动 syntax/diagnostics provider 演示，更多交互演示仍待补齐
- 已落地文件：
  - `example/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/App.kt`
- 本阶段已完成：
  - 将 `example` 从空壳升级为可运行的真实编辑器演示
  - 完成文本加载
  - 完成 wrap 开关
  - 完成 readOnly / IME composition 设置开关
  - 展示语言配置摘要信息
  - 完成 `LanguageConfigDecorationProvider` 驱动的 syntax highlight 演示
  - 完成 diagnostics provider 演示
  - 优先保证 Android 与 Desktop 构建闭环可运行
- 本阶段剩余工作：
  - 主题切换
  - undo / redo
  - fold
  - completion
  - linked editing

## Phase 17：测试（进行中）

- 状态：已建立协议层、运行时、theme/language、decoration manager 与 language-config provider 的 commonTest 基础覆盖，bridge 集成测试与 example 验收仍缺失
- 已落地文件：
  - `editor-compose/src/commonTest/kotlin/com/qiplat/compose/sweeteditor/protocol/ProtocolCommonTest.kt`
  - `editor-compose/src/commonTest/kotlin/com/qiplat/compose/sweeteditor/runtime/EditorControllerCommonTest.kt`
  - `editor-compose/src/commonTest/kotlin/com/qiplat/compose/sweeteditor/runtime/DecorationProviderManagerCommonTest.kt`
  - `editor-compose/src/commonTest/kotlin/com/qiplat/compose/sweeteditor/LanguageConfigDecorationProviderCommonTest.kt`
  - `editor-compose/src/commonTest/kotlin/com/qiplat/compose/sweeteditor/theme/ThemeAndLanguageCommonTest.kt`
- 本阶段已完成：
  - 为协议层编写单元测试
    - encoder / decoder
    - append-only tail
    - 空 payload / null payload 的部分解码覆盖
  - 为运行时层补充 controller 设置与手势分发测试
  - 为 decoration manager 的 merge / replace-range 聚合策略补充 commonTest
  - 为 theme / language configuration 解析补充 commonTest
  - 为 `LanguageConfigDecorationProvider` 的 json 驱动 span 产出补充 commonTest
- 本阶段剩余工作：
  - 继续补协议层的可变长字符串与更复杂 payload 边界测试
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
