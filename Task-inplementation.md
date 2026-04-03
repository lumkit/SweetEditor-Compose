# editor-compose 平台实现标准合规性检查

## 范围

- 标准文档：`docs/platform-implementation-standard.md`
- 扫描范围：`editor-compose/src/commonMain`、`editor-compose/src/androidMain`、`editor-compose/src/jvmMain`、`editor-compose/src/iosMain`
- 判定原则：
  - `MUST`：判定为必须整改
  - `SHOULD`：判定为建议整改
  - `MAY`：仅记录可选缺口，不作为必须整改项

## 最新进度（2026-04-02）

- 已完成的主线整改：
  - 已提供标准命名的 [SweetEditorController](file:///Users/lumkit/Projects/Idea/SweetEditor-Compose/editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/SweetEditorController.kt)，并补齐 `whenReady`、`bind`、`unbind`、`dispose/close`
  - Widget 层 MUST 模块已补齐并对外可识别：
    - `CompletionProvider` / `CompletionContext` / `CompletionItem` / `CompletionResult`
    - `NewLineActionProvider` / `NewLineAction` / `NewLineContext`
    - `EditorEventBus` 与标准事件类型
    - `EditorIconProvider`
    - `EditorMetadata`
  - 3.2 公共 API 已大幅补齐，包括：
    - `loadDocument/getDocument/getTheme/getSettings`
    - `setEditorIconProvider/setMetadata/getMetadata`
    - `addCompletionProvider/removeCompletionProvider`
    - `addNewLineActionProvider/removeNewLineActionProvider`
    - `triggerCompletion/showCompletionItems/dismissCompletion/setCompletionItemRenderer`
    - `requestDecorationRefresh/clearInlayHints/clearPhantomTexts/clearGutterIcons/clearGuides/clearDiagnostics/clearAllDecorations/flush`
    - `getVisibleLineRange/getTotalLineCount`
  - 3.1 Core Public API 已补齐一大批，包括：
    - `moveLineUp/moveLineDown/copyLineUp/copyLineDown`
    - `deleteLine/insertLineAbove/insertLineBelow`
    - `undo/redo/canUndo/canRedo`
    - `selectAll/getSelectedText/getWordRangeAtCursor/getWordAtCursor`
    - 光标移动一组、滚动定位一组、几何查询一组
    - `isReadOnly/isCompositionEnabled/setAutoIndentMode/getAutoIndentMode`
    - 多项状态查询 API
  - Completion 已从“模块缺失”推进到“最小闭环可用”：
    - 支持 `triggerCompletion`
    - 支持 trigger character / retrigger
    - 支持选择、应用、关闭、最小弹层展示
    - Enter/Tab/Escape/Up/Down 已接入补全交互路径
  - `NewLineActionProvider` 已接入 Enter 路径：
    - Desktop Compose 按键路径
    - Android IME `commitText("\n")` / editor action / enter key 路径
  - DecorationProvider 契约已完成第一轮升级：
    - `DecorationProviderContext` 已补齐 `visibleStartLine` / `visibleEndLine` / `totalLineCount` / `textChanges` / `editorMetadata`
    - 已提供 `DecorationReceiver`
    - 已支持 `provideDecorations(context, receiver)` 语义
    - 已支持按字段 `DecorationResult` + 独立 `DecorationApplyMode`
  - 动态库导入与 JVM Desktop 原生库加载问题已修复
  - `./gradlew assemble` 当前可通过

- 进行中的下一步：
  - 继续完善 provider 交互链
  - 已将 `addDecorationProvider/removeDecorationProvider` 接入 `SweetEditorController` 挂载链路，并在 [SweetEditor.kt](file:///Users/lumkit/Projects/Idea/SweetEditor-Compose/editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/SweetEditor.kt) 中与参数传入的 provider 列表合并
  - DecorationProvider runtime 已补强进行中请求跟踪，开始将 `isDecorationDirty` 从“首个结果即清空”收口为“当前一轮 provider 请求全部结束后再清空”
  - 下一步继续收口 DecorationProvider Manager 的多次 snapshot 与异常语义

- 仍然待处理的重点 MUST 项：
  - `EditorCore` / `Document` / `TextMeasurer` 等公共桥接命名与别名体系仍未完全对齐标准
  - DecorationProvider 的多次 snapshot、异常隔离、取消/过期语义还需继续系统化梳理
  - 仍有部分 3.1 / 3.2 Public API 缺口未完全收口，例如：
    - `setContentStartPadding`
    - `setHandleConfig`
    - `setScrollbarConfig`
    - `getLayoutMetrics`
    - `tickEdgeScroll`
    - `tickFling`
    - snippet / linked editing 一组

- 说明：
  - 下方“已识别的不合规项”保留了初始扫描记录，部分条目已经被上述整改覆盖，不再代表当前实时状态

## 已识别的 MUST 不合规项

### 1. 声明式框架未提供标准名称的 `SweetEditorController`

- 标准依据：
  - `docs/platform-implementation-standard.md` 3.0.2、3.0.3、2.1
- 当前实现：
  - 控件入口为 [SweetEditor](file:///Users/lumkit/Projects/Idea/SweetEditor-Compose/editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/SweetEditor.kt#L65-L87)
  - 对外控制器为 [EditorController](file:///Users/lumkit/Projects/Idea/SweetEditor-Compose/editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/runtime/EditorController.kt#L24-L32)
  - `rememberEditorController(...)` 直接暴露 `EditorController`：[RememberEditor.kt](file:///Users/lumkit/Projects/Idea/SweetEditor-Compose/editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/RememberEditor.kt#L16-L31)
- 不合规点：
  - Jetpack Compose 属于声明式框架，标准要求 MUST 提供 `SweetEditorController`
  - 当前名称为 `EditorController`，不满足标准命名
  - 未发现 `whenReady(callback)`、内部 `bind(editorApi)` / `unbind()` 机制
  - 当前控件生命周期中直接 `controller.close()`，不符合标准要求的“控件挂载绑定、卸载解绑、Controller 可重绑定”语义

### 2. Widget 层必需公共类型缺失

- 标准依据：
  - `docs/platform-implementation-standard.md` 1.2、2.1
- 当前实现中缺失或不可识别的必需类型：
  - `EditorIconProvider`
  - `EditorMetadata`
  - `CompletionProvider`
  - `CompletionProviderManager`
  - `CompletionContext`
  - `CompletionItem`
  - `CompletionResult`
  - `NewLineActionProvider`
  - `NewLineActionProviderManager`
  - `NewLineAction`
  - `NewLineContext`
  - `EditorEvent`
  - `TextChangedEvent`
  - `CursorChangedEvent`
  - `SelectionChangedEvent`
  - `ScrollChangedEvent`
  - `ScaleChangedEvent`
  - `DocumentLoadedEvent`
  - `FoldToggleEvent`
  - `GutterIconClickEvent`
  - `InlayHintClickEvent`
  - `DoubleTapEvent`
  - `ContextMenuEvent`
- 现有文件列表可见公共根包仅包含如下主要类型：
  - [LS 列表](file:///Users/lumkit/Projects/Idea/SweetEditor-Compose/editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor)
- 不合规点：
  - 标准要求这些类型在 Widget 层 MUST 可识别、且类型完整覆盖
  - 当前模块中这些类型不存在，或对应实现已被删除

### 3. Core Bridge 命名与公共桥接类型不符合标准

- 标准依据：
  - `docs/platform-implementation-standard.md` 1.1、2.1
- 当前实现：
  - 内部桥接接口为 [NativeEditorBridge / NativeDocumentBridge / NativeTextMeasurer](file:///Users/lumkit/Projects/Idea/SweetEditor-Compose/editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/bridge/NativeBridge.kt#L5-L129)
  - 文档包装类型为 [EditorDocument](file:///Users/lumkit/Projects/Idea/SweetEditor-Compose/editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/runtime/EditorDocument.kt#L7-L25)
  - 文本测量接口为 [EditorTextMeasurer](file:///Users/lumkit/Projects/Idea/SweetEditor-Compose/editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/runtime/EditorTextMeasurer.kt#L7-L21)
- 不合规点：
  - 标准要求 Core Bridge 可识别类型必须包含 `EditorCore`、`Document`、`ProtocolEncoder`、`ProtocolDecoder`、`TextMeasurer`、`EditorOptions`
  - 当前只有 `ProtocolEncoder` / `ProtocolDecoder` / `EditorOptions` 可识别
  - `Document` 使用 `EditorDocument`
  - `TextMeasurer` 使用 `EditorTextMeasurer`
  - `EditorCore` 不存在，当前只有内部 `NativeEditorBridge`

### 4. `SweetEditorController` / 控件公共 API 不完整

- 标准依据：
  - `docs/platform-implementation-standard.md` 3.0.3、3.2
- 当前实现：
  - 控制器方法主要集中在 [EditorController.kt](file:///Users/lumkit/Projects/Idea/SweetEditor-Compose/editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/runtime/EditorController.kt)
- 已确认缺失的 3.2 公共 API 示例：
  - `loadDocument(doc)`
  - `getDocument()`
  - `getTheme()`
  - `getSettings()`
  - `setEditorIconProvider(provider)`
  - `setMetadata(metadata)`
  - `getMetadata()`
  - `addDecorationProvider(provider)`
  - `removeDecorationProvider(provider)`
  - `requestDecorationRefresh()`
  - `addCompletionProvider(provider)`
  - `removeCompletionProvider(provider)`
  - `addNewLineActionProvider(provider)`
  - `removeNewLineActionProvider(provider)`
  - `triggerCompletion()`
  - `showCompletionItems(items)`
  - `dismissCompletion()`
  - `setCompletionItemRenderer(renderer)`
  - `clearHighlights()`
  - `clearHighlights(layer)`
  - `clearInlayHints()`
  - `clearPhantomTexts()`
  - `clearGutterIcons()`
  - `clearGuides()`
  - `clearDiagnostics()`
  - `clearAllDecorations()`
  - `flush()`
  - `getVisibleLineRange()`
  - `getTotalLineCount()`
- 证据：
  - 在 [EditorController.kt](file:///Users/lumkit/Projects/Idea/SweetEditor-Compose/editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/runtime/EditorController.kt) 中检索上述方法无结果

### 5. Core Public API 不完整

- 标准依据：
  - `docs/platform-implementation-standard.md` 3.1
- 当前实现：
  - `EditorController` 与内部 `NativeEditorBridge` 仅覆盖了一部分 core API
- 已确认缺失的 3.1 API 示例：
  - `setContentStartPadding(padding)`
  - `setHandleConfig(...)`
  - `setScrollbarConfig(...)`
  - `getLayoutMetrics()`
  - `tickEdgeScroll()`
  - `tickFling()`
  - `moveLineUp()`
  - `moveLineDown()`
  - `copyLineUp()`
  - `copyLineDown()`
  - `deleteLine()`
  - `insertLineAbove()`
  - `insertLineBelow()`
  - `undo()`
  - `redo()`
  - `canUndo()`
  - `canRedo()`
  - `selectAll()`
  - `getSelectedText()`
  - `getWordRangeAtCursor()`
  - `getWordAtCursor()`
  - `moveCursorLeft(extend)` / `Right` / `Up` / `Down`
  - `moveCursorToLineStart(extend)` / `moveCursorToLineEnd(extend)`
  - `isCompositionEnabled()`
  - `isReadOnly()`
  - `setAutoIndentMode(mode)`
  - `getAutoIndentMode()`
  - `scrollToLine(line, behavior)`
  - `gotoPosition(line, col)`
  - `ensureCursorVisible()`
  - `setScroll(x, y)`
  - `getPositionRect(line, col)`
  - `getCursorRect()`
  - `registerTextStyle(id, ...)`
  - `setLineSpans(line, layer, spans)`
  - `clearLineSpans(line, layer)`
  - `setLineInlayHints(line, hints)`
  - `clearInlayHints()`
  - `setLinePhantomTexts(line, phantoms)`
  - `clearPhantomTexts()`
  - `setLineGutterIcons(line, icons)`
  - `clearGutterIcons()`
  - `setLineDiagnostics(line, items)`
  - `clearDiagnostics()`
  - `setIndentGuides(guides)`
  - `setBracketGuides(guides)`
  - `setFlowGuides(guides)`
  - `setSeparatorGuides(guides)`
  - `clearGuides()`
  - `setBracketPairs(open, close)`
  - `setMatchedBrackets(...)`
  - `clearMatchedBrackets()`
  - `toggleFold(line)`
  - `foldAt(line)`
  - `unfoldAt(line)`
  - `foldAll()`
  - `unfoldAll()`
  - `isLineVisible(line)`
  - `clearAllDecorations()`
  - `insertSnippet(template)`
  - `startLinkedEditing(model)`
  - `isInLinkedEditing()`
  - `linkedEditingNext()`
  - `linkedEditingPrev()`
  - `cancelLinkedEditing()`
- 证据：
  - [NativeBridge.kt](file:///Users/lumkit/Projects/Idea/SweetEditor-Compose/editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/bridge/NativeBridge.kt#L25-L118)
  - [EditorController.kt](file:///Users/lumkit/Projects/Idea/SweetEditor-Compose/editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/runtime/EditorController.kt)

### 6. DecorationProvider 契约不符合标准

- 标准依据：
  - `docs/platform-implementation-standard.md` 4.1
- 当前实现：
  - Provider 接口：[DecorationProvider.kt](file:///Users/lumkit/Projects/Idea/SweetEditor-Compose/editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/DecorationProvider.kt#L89-L123)
  - Manager：[DecorationProviderManager.kt](file:///Users/lumkit/Projects/Idea/SweetEditor-Compose/editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/runtime/DecorationProviderManager.kt#L13-L122)
- 不合规点：
  - 标准要求 MUST 同时支持同步和异步结果交付，且 `DecorationProvider` MUST 支持同一请求多次 snapshot 更新
  - 当前接口仅为 `suspend fun provide(context): DecorationUpdate?`，单次返回，无法表达多次结果更新
  - 标准要求进行中的请求 MUST 有明确取消 / 过期契约
  - 当前 Manager 没有对 Provider 暴露 `isCancelled()` / receiver 语义
  - `DecorationProviderContext` 缺少标准 MUST 字段：
    - `visibleStartLine`
    - `visibleEndLine`
    - `totalLineCount`
    - `textChanges`
    - `editorMetadata`
  - 当前 `DecorationUpdate` 只有整体 `applyMode`
  - 标准要求 `DecorationResult` 的 11 类装饰数据分别带有独立的 `ApplyMode`
  - 当前模型也缺少 guide 结果字段：
    - `indentGuides`
    - `bracketGuides`
    - `flowGuides`
    - `separatorGuides`

### 7. Completion 模块整体缺失

- 标准依据：
  - `docs/platform-implementation-standard.md` 1.2、3.2、4.2、5
- 当前实现：
  - 模块中不存在 `CompletionProvider`、`CompletionProviderManager`、`CompletionContext`、`CompletionItem`、`CompletionResult`
  - 相关公共 API 也不存在
- 不合规点：
  - Completion 属于 Widget 层 MUST 模块
  - 当前整个模块缺失

### 8. NewLineAction 模块整体缺失

- 标准依据：
  - `docs/platform-implementation-standard.md` 1.2、3.2、4.3
- 当前实现：
  - 模块中不存在 `NewLineActionProvider`、`NewLineActionProviderManager`、`NewLineAction`、`NewLineContext`
- 不合规点：
  - NewLine 属于 Widget 层 MUST 模块
  - 当前整个模块缺失

### 9. 事件系统整体缺失

- 标准依据：
  - `docs/platform-implementation-standard.md` 1.2、10 节前置索引说明、3.2 注释说明
- 当前实现：
  - 仅存在回调数据类型 [EditorContextMenuRequest / EditorSelectionHandleDragState](file:///Users/lumkit/Projects/Idea/SweetEditor-Compose/editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/EditorInteraction.kt#L7-L17)
  - 不存在类型安全事件机制及规范事件类型
- 不合规点：
  - 缺失 `EditorEvent` 以及一组标准事件类型
  - 也未发现等价的 typed stream / event bus / listener 机制

### 10. `EditorIconProvider` 与 `EditorMetadata` 必需概念缺失

- 标准依据：
  - `docs/platform-implementation-standard.md` 1.2、2.1、3.2
- 当前实现：
  - `SweetEditor` 内部自绘了 gutter icon painter，但没有公开 `EditorIconProvider`
  - 未发现 `EditorMetadata` 类型，也无 `setMetadata/getMetadata`
- 不合规点：
  - 两者都属于 Widget 层 MUST 类型
  - 当前只存在内部实现，未形成标准公共概念

## SHOULD 级建议整改项

### 1. 建议将渲染逻辑从 `SweetEditor.kt` 中进一步抽离为 `EditorRenderer`

- 标准依据：
  - `docs/platform-implementation-standard.md` 1.3
- 当前实现：
  - 大量绘制逻辑集中在 [SweetEditor.kt](file:///Users/lumkit/Projects/Idea/SweetEditor-Compose/editor-compose/src/commonMain/kotlin/com/qiplat/compose/sweeteditor/SweetEditor.kt#L366-L1583)
- 说明：
  - 标准将 `EditorRenderer` 视为 SHOULD 级推荐模式
  - 当前虽然功能存在，但职责边界较重，不利于与 Swing/Apple renderer 风格统一推进

### 2. 建议补齐 Copilot / InlineSuggestion 模块

- 标准依据：
  - `docs/platform-implementation-standard.md` 1.2、6
- 当前实现：
  - `InlineSuggestion` 相关类型已不存在
- 不合规点：
  - 该模块为 SHOULD
  - 若计划支持内联建议，应按标准补齐 listener / accept / dismiss 契约

### 3. 建议补齐 Perf 模块

- 标准依据：
  - `docs/platform-implementation-standard.md` 1.2
- 当前实现：
  - 不存在 `PerfOverlay`、`MeasurePerfStats`、`PerfStepRecorder`
- 不合规点：
  - 该模块为 SHOULD
  - 若要按标准对齐跨平台诊断能力，应补齐

## MAY 级可选缺口

### 1. Selection Menu 模块未实现

- 标准依据：
  - `docs/platform-implementation-standard.md` 7
- 当前实现：
  - 未发现 `SelectionMenuController`、`SelectionMenuItem`、`SelectionMenuItemProvider`
- 结论：
  - Selection Menu 为移动端 MAY
  - 当前缺失不构成违规，但如果后续要补齐移动端体验，应按第 7 节契约实现

## 汇总建议

### 必须优先整改（按影响排序）

1. 统一声明式控制器命名与生命周期契约：`EditorController` → `SweetEditorController`，补齐 `whenReady`、`bind`、`unbind`、`dispose`
2. 补齐 Widget 层 MUST 模块：Completion、NewLine、Event、EditorIconProvider、EditorMetadata
3. 补齐 3.1 / 3.2 Public API 缺口，尤其是编辑器命令、查询、清理、Provider 管理接口
4. 重构 DecorationProvider 契约，补齐多次结果更新、取消/过期语义、标准 `DecorationContext` 与按字段 ApplyMode
5. 补齐 Core Bridge 命名与公共桥接概念，确保 `EditorCore` / `Document` / `TextMeasurer` 可识别

### 建议后续整理

1. 将渲染逻辑抽为 `EditorRenderer`
2. 恢复或重建 InlineSuggestion SHOULD 模块
3. 增加 Perf 诊断能力
