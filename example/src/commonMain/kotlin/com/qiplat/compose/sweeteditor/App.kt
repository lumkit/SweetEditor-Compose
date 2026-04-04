package com.qiplat.compose.sweeteditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiplat.compose.sweeteditor.model.decoration.*
import com.qiplat.compose.sweeteditor.model.foundation.TextPosition
import com.qiplat.compose.sweeteditor.model.foundation.WrapMode
import com.qiplat.compose.sweeteditor.theme.EditorThemeStyleIds
import com.qiplat.compose.sweeteditor.theme.LanguageConfiguration
import com.qiplat.compose.sweeteditor.theme.LanguageConfigurationParser
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.Font
import sweeteditor_compose.example.generated.resources.JetBrainsMono_Regular
import sweeteditor_compose.example.generated.resources.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    MaterialTheme {
        var darkThemeState by rememberSaveable { mutableStateOf(true) }
        val editorFontFamily = FontFamily(Font(Res.font.JetBrainsMono_Regular))
        val editorFontConfig = remember(editorFontFamily) {
            EditorFontConfig(
                fontFamily = editorFontFamily,
                fontSize = 12.sp,
                lineNumberFontSize = 13.sp,
                inlayHintFontSize = 12.sp,
                iconSize = 16.sp,
            )
        }
        val editorAppearance = rememberEditorAppearance(
            themeContent = null,
            fontConfig = editorFontConfig,
            darkMode = darkThemeState,
        )
        val editorState = rememberEditorState()
        val editorController = rememberSweetEditorController(
            textMeasurer = editorAppearance.textMeasurer,
            state = editorState,
        )
        var loadedSamples by remember { mutableStateOf<List<LoadedExampleSample>>(emptyList()) }
        var selectedSampleIndex by remember { mutableIntStateOf(0) }
        var wrapEnabled by remember { mutableStateOf(false) }
        var readOnly by remember { mutableStateOf(false) }
        var compositionEnabled by remember { mutableStateOf(true) }

        val sampleSpecs = remember {
            listOf(
                ExampleSampleSpec("example.kt", "files/example_kt", "files/kotlin_json"),
                ExampleSampleSpec("example.java", "files/example_java", "files/java_json"),
                ExampleSampleSpec("View.java", "files/View_java", "files/java_json"),
                ExampleSampleSpec("example.lua", "files/example_lua", "files/lua_json"),
                ExampleSampleSpec("nlohmann-json.hpp", "files/nlohmann-json_hpp", "files/cpp_json"),
            )
        }
        val decorationProviders = remember {
            listOf(
                LanguageConfigDecorationProvider(),
                ExampleDemoDecorationProvider(),
            )
        }
        val completionProvider = remember { ExampleDemoCompletionProvider() }
        val editorSettings = remember(wrapEnabled, readOnly, compositionEnabled) {
            EditorSettings(
                wrapMode = if (wrapEnabled) WrapMode.WordBreak else WrapMode.None,
                tabSize = 4,
                gutterVisible = true,
                gutterSticky = true,
                readOnly = readOnly,
                compositionEnabled = compositionEnabled,
            )
        }
        val activeSample =
            loadedSamples.getOrNull(selectedSampleIndex.coerceIn(0, (loadedSamples.size - 1).coerceAtLeast(0)))

        LaunchedEffect(sampleSpecs) {
            val configurationCache = mutableMapOf<String, LanguageConfiguration>()
            loadedSamples = sampleSpecs.map { spec ->
                val configuration = configurationCache.getOrPut(spec.languageConfigPath) {
                    LanguageConfigurationParser.parse(
                        Res.readBytes(spec.languageConfigPath).decodeToString(),
                    )
                }
                LoadedExampleSample(
                    spec = spec,
                    configuration = configuration,
                )
            }
        }

        LaunchedEffect(editorController, activeSample) {
            val sample = activeSample ?: return@LaunchedEffect
            val sampleText = Res.readBytes(sample.spec.samplePath).decodeToString()
            editorController.setLanguageConfiguration(sample.configuration)
            editorController.loadText(sampleText)
            editorController.setShowSplitLine(true)
            editorController.onFontMetricsChanged()
        }

        DisposableEffect(editorController, completionProvider) {
            editorController.addCompletionProvider(completionProvider)
            onDispose {
                editorController.removeCompletionProvider(completionProvider)
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color(editorAppearance.theme.gutterBackgroundColor),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(editorAppearance.theme.gutterBackgroundColor),
                        titleContentColor = Color(editorAppearance.theme.textColor),
                        actionIconContentColor = Color(editorAppearance.theme.textColor),
                    ),
                    title = {
                        Text("Sweet Editor")
                    },
                    actions = {
                        Actions(
                            editorController,
                            darkThemeState,
                            onDarkThemeChanged = {
                                darkThemeState = it
                            }
                        )
                    }
                )
            },
            bottomBar = {
                CompositionLocalProvider(
                    LocalContentColor provides Color(editorAppearance.theme.textColor),
                ) {
                    ProvideTextStyle(value = MaterialTheme.typography.labelMedium) {
                        val scale by editorController.scaleState
                        Row(
                            Modifier.fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .height(56.dp)
                                .padding(horizontal = 16.dp)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Scale: ${scale.toString().take(4)}",
                                modifier = Modifier.width(80.dp)
                            )

                            Slider(
                                value = scale,
                                onValueChange = {
                                    editorController.setScale(it)
                                },
                                valueRange = .5f..2f,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        ) {
            SweetEditor(
                controller = editorController,
                modifier = Modifier.padding(it).fillMaxSize(),
                theme = editorAppearance.theme,
                settings = editorSettings,
                decorationProviders = decorationProviders,
                onGestureResult = { result ->

                },
                onHitTarget = { hitTarget ->

                },
                onContextMenuRequest = { request ->

                },
                onSelectionHandleDragStateChange = { dragState ->

                },
                completions = { selectedIndex, items, renderer ->
                    val theme = editorAppearance.theme
                    val backgroundColor = theme.gutterBackgroundColor.toComposeColor()
                    val borderColor = theme.scrollbarThumbColor.toComposeColor()
                    val selectedColor = theme.selectionColor.toComposeColor()
                    val textColor = theme.textColor.toComposeColor()

                    Box(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Column(
                            modifier = Modifier
                                .background(backgroundColor)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                .padding(vertical = 4.dp),
                        ) {
                            items.forEachIndexed { index, item ->
                                val isSelected = index == selectedIndex
                                Text(
                                    text = renderer?.render(item) ?: item.detail?.let { "${item.label}  $it" } ?: item.label,
                                    modifier = Modifier.fillMaxWidth()
                                        .background(if (isSelected) selectedColor else Color.Transparent)
                                        .clickable {
                                            editorController.selectCompletionItem(index)
                                            editorController.applySelectedCompletionItem()
                                            editorController.dismissCompletion()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.Actions(
    editorController: SweetEditorController,
    darkThemeState: Boolean,
    onDarkThemeChanged: (Boolean) -> Unit,
) {
    var menuState by rememberSaveable { mutableStateOf(false) }
    val wrapMode by editorController.wrapModeState

    IconButton(
        {
            editorController.undo()
        },
        enabled = editorController.canUndoState.value,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Undo,
            contentDescription = "Undo",
        )
    }

    IconButton(
        {
            editorController.redo()
        },
        enabled = editorController.canRedoState.value,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Redo,
            contentDescription = "Redo",
        )
    }

    Column {
        IconButton(
            onClick = {
                menuState = true
            }
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = null,
            )
        }

        DropdownMenu(
            expanded = menuState,
            onDismissRequest = { menuState = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text("TextWrap: ${wrapMode.name}")
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.WrapText,
                        contentDescription = null,
                    )
                },
                onClick = {
                    val nextMode = (wrapMode.ordinal + 1).let {
                        if (it >= WrapMode.entries.size) 0 else it
                    }
                    editorController.setWrapMode(WrapMode.entries[nextMode])
                    menuState = false
                }
            )

            DropdownMenuItem(
                text = {
                    Text(if (darkThemeState) "Dark" else "Light")
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Colorize,
                        contentDescription = null,
                    )
                },
                onClick = {
                    onDarkThemeChanged(!darkThemeState)
                    menuState = false
                }
            )
        }
    }
}

private class ExampleDemoCompletionProvider : CompletionProvider {
    private val triggerChars = setOf(".", ":")

    override fun isTriggerCharacter(ch: String): Boolean = ch in triggerChars

    override suspend fun provideCompletions(
        context: CompletionContext,
        receiver: CompletionReceiver,
    ) {
        if (
            context.triggerKind == CompletionTriggerKind.Character &&
            context.triggerCharacter == "."
        ) {
            receiver.accept(
                CompletionResult(
                    items = listOf(
                        CompletionItem(
                            label = "length",
                            detail = "size_t",
                            kind = CompletionItem.KIND_PROPERTY,
                            insertText = "length()",
                            sortKey = "a_length",
                        ),
                        CompletionItem(
                            label = "push_back",
                            detail = "void push_back(T)",
                            kind = CompletionItem.KIND_FUNCTION,
                            insertText = "push_back()",
                            sortKey = "b_push_back",
                        ),
                        CompletionItem(
                            label = "begin",
                            detail = "iterator",
                            kind = CompletionItem.KIND_FUNCTION,
                            insertText = "begin()",
                            sortKey = "c_begin",
                        ),
                        CompletionItem(
                            label = "end",
                            detail = "iterator",
                            kind = CompletionItem.KIND_FUNCTION,
                            insertText = "end()",
                            sortKey = "d_end",
                        ),
                        CompletionItem(
                            label = "size",
                            detail = "size_t",
                            kind = CompletionItem.KIND_FUNCTION,
                            insertText = "size()",
                            sortKey = "e_size",
                        ),
                    ),
                ),
            )
            return
        }

        delay(200)
        if (receiver.isCancelled()) {
            return
        }
        receiver.accept(
            CompletionResult(
                items = listOf(
                    CompletionItem(
                        label = "std::string",
                        detail = "class",
                        kind = CompletionItem.KIND_CLASS,
                        insertText = "std::string",
                        sortKey = "a_string",
                    ),
                    CompletionItem(
                        label = "std::vector",
                        detail = "template class",
                        kind = CompletionItem.KIND_CLASS,
                        insertText = "std::vector<>",
                        sortKey = "b_vector",
                    ),
                    CompletionItem(
                        label = "std::cout",
                        detail = "ostream",
                        kind = CompletionItem.KIND_VARIABLE,
                        insertText = "std::cout",
                        sortKey = "c_cout",
                    ),
                    CompletionItem(
                        label = "if",
                        detail = "snippet",
                        kind = CompletionItem.KIND_SNIPPET,
                        insertText = "if (${ '$' }{1:condition}) {\n\t${ '$' }0\n}",
                        insertTextFormat = CompletionItem.SNIPPET,
                        sortKey = "d_if",
                    ),
                    CompletionItem(
                        label = "for",
                        detail = "snippet",
                        kind = CompletionItem.KIND_SNIPPET,
                        insertText = "for (int ${ '$' }{1:i} = 0; ${ '$' }{1:i} < ${ '$' }{2:n}; ++${ '$' }{1:i}) {\n\t${ '$' }0\n}",
                        insertTextFormat = CompletionItem.SNIPPET,
                        sortKey = "e_for",
                    ),
                    CompletionItem(
                        label = "class",
                        detail = "snippet — class definition",
                        kind = CompletionItem.KIND_SNIPPET,
                        insertText = "class ${ '$' }{1:ClassName} {\npublic:\n\t${ '$' }{1:ClassName}() {\n\t\t${ '$' }2\n\t}\n\t~${ '$' }{1:ClassName}() {\n\t\t${ '$' }3\n\t}\n\t${ '$' }0\n};",
                        insertTextFormat = CompletionItem.SNIPPET,
                        sortKey = "f_class",
                    ),
                    CompletionItem(
                        label = "return",
                        detail = "keyword",
                        kind = CompletionItem.KIND_KEYWORD,
                        insertText = "return ",
                        sortKey = "g_return",
                    ),
                ),
            ),
        )
    }
}

private class ExampleDemoDecorationProvider : DecorationProvider {
    override val id: String = "example.demo.decoration"
    override val overscanLines: Int = 8
    override val capabilities: Set<DecorationType> = setOf(
        DecorationType.InlayHint,
        DecorationType.Diagnostic,
        DecorationType.IndentGuide,
        DecorationType.FoldRegion,
        DecorationType.GutterIcon,
        DecorationType.SeparatorGuide,
    )

    override suspend fun provide(context: DecorationProviderContext): DecorationUpdate {
        val inlayHints = linkedMapOf<Int, MutableList<InlayHint>>()
        val gutterIcons = linkedMapOf<Int, MutableList<GutterIcon>>()
        val separatorGuides = mutableListOf<SeparatorGuide>()
        val (foldRegions, indentGuides) = buildStructuralDecorations(context)
        val styleIdColorToken = EditorThemeStyleIds.UserBase + 1
        val textStyles = mapOf(
            styleIdColorToken to TextStyle(color = 0xFF80CBC4.toInt(), fontStyle = TextStyle.Bold),
        )
        val syntaxSpans = linkedMapOf<Int, MutableList<StyleSpan>>()
        val diagnostics = linkedMapOf<Int, List<DiagnosticItem>>()
        val colorRegex = Regex("#[0-9a-fA-F]{6}\\b")

        for (line in context.requestedLineRange) {
            val lineText = context.document.getLineText(line)
            val lineDiagnostics = mutableListOf<DiagnosticItem>()
            lineText.indexOf("TODO").takeIf { it >= 0 }?.let { column ->
                lineDiagnostics += DiagnosticItem(
                    column = column,
                    length = 4,
                    severity = DiagnosticSeverity.Hint,
                )
            }
            lineText.indexOf("FIXME").takeIf { it >= 0 }?.let { column ->
                lineDiagnostics += DiagnosticItem(
                    column = column,
                    length = 5,
                    severity = DiagnosticSeverity.Warning,
                )
            }
            if (lineDiagnostics.isNotEmpty()) {
                diagnostics[line] = lineDiagnostics
            }

            lineText.indexOf('@').takeIf { it >= 0 }?.let {
                gutterIcons.getOrPut(line) { mutableListOf() }.add(
                    GutterIcon(iconId = if (line % 2 == 0) 1 else 2),
                )
            }

            if ("#region" in lineText || "// region" in lineText.lowercase()) {
                separatorGuides += SeparatorGuide(
                    line = line,
                    style = SeparatorStyle.Single,
                    count = 1,
                    textEndColumn = lineText.length,
                )
            }

            colorRegex.findAll(lineText).forEach { match ->
                inlayHints.getOrPut(line) { mutableListOf() }.add(
                    InlayHint(
                        type = InlayType.Color,
                        column = match.range.last + 1,
                        color = match.value.removePrefix("#").toLong(16).toInt() or 0xFF000000.toInt(),
                    ),
                )
                syntaxSpans.getOrPut(line) { mutableListOf() }.add(
                    StyleSpan(
                        column = match.range.first,
                        length = match.value.length,
                        styleId = styleIdColorToken,
                    ),
                )
            }
        }
        return DecorationUpdate(
            decorations = DecorationSet(
                textStyles = textStyles,
                syntaxSpans = syntaxSpans,
                inlayHints = inlayHints,
                gutterIcons = gutterIcons,
                diagnostics = diagnostics,
                separatorGuides = separatorGuides,
                indentGuides = indentGuides,
                foldRegions = foldRegions,
            ),
            applyMode = DecorationApplyMode.Merge,
            lineRange = context.requestedLineRange,
        )
    }

    private fun buildStructuralDecorations(
        context: DecorationProviderContext,
    ): Pair<List<FoldRegion>, List<IndentGuide>> {
        val foldSet = linkedSetOf<FoldRegion>()
        val indentSet = linkedSetOf<IndentGuide>()
        val braceStack = ArrayDeque<Int>()
        val regionStack = ArrayDeque<Int>()
        var inBlockComment = false

        for (line in 0 until context.totalLineCount) {
            val text = context.document.getLineText(line)
            val trimmed = text.trim().lowercase()
            if (trimmed.startsWith("#region") || trimmed.startsWith("// region")) {
                regionStack.addLast(line)
            } else if (trimmed.startsWith("#endregion") || trimmed.startsWith("// endregion")) {
                val start = regionStack.removeLastOrNull()
                if (start != null && line > start) {
                    addStructuralRegion(
                        foldSet = foldSet,
                        indentSet = indentSet,
                        startLine = start,
                        endLine = line,
                        startText = context.document.getLineText(start),
                    )
                }
            }

            var i = 0
            var inString = false
            var stringQuote = '\u0000'
            while (i < text.length) {
                val c = text[i]
                val next = text.getOrNull(i + 1)
                if (inString) {
                    if (c == '\\') {
                        i += 2
                        continue
                    }
                    if (c == stringQuote) {
                        inString = false
                    }
                    i++
                    continue
                }
                if (inBlockComment) {
                    if (c == '*' && next == '/') {
                        inBlockComment = false
                        i += 2
                    } else {
                        i++
                    }
                    continue
                }
                if (c == '/' && next == '/') {
                    break
                }
                if (c == '/' && next == '*') {
                    inBlockComment = true
                    i += 2
                    continue
                }
                if (c == '"' || c == '\'') {
                    inString = true
                    stringQuote = c
                    i++
                    continue
                }
                if (c == '{') {
                    braceStack.addLast(line)
                } else if (c == '}') {
                    val start = braceStack.removeLastOrNull()
                    if (start != null && line > start) {
                        addStructuralRegion(
                            foldSet = foldSet,
                            indentSet = indentSet,
                            startLine = start,
                            endLine = line,
                            startText = context.document.getLineText(start),
                        )
                    }
                }
                i++
            }
        }
        return foldSet.toList() to indentSet.toList()
    }

    private fun addStructuralRegion(
        foldSet: MutableSet<FoldRegion>,
        indentSet: MutableSet<IndentGuide>,
        startLine: Int,
        endLine: Int,
        startText: String,
    ) {
        if (endLine <= startLine) {
            return
        }
        foldSet += FoldRegion(startLine = startLine, endLine = endLine)
        val indentColumn = startText.indexOfFirst { !it.isWhitespace() }
            .takeIf { it >= 0 }
            ?.coerceAtLeast(0)
            ?: 0
        indentSet += IndentGuide(
            start = TextPosition(startLine, indentColumn),
            end = TextPosition(endLine, indentColumn),
        )
    }
}

@Composable
fun rememberFps(): State<Float> {
    val fpsState = remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastFrameTime = 0L

        while (true) {
            withFrameNanos { frameTime ->
                if (lastFrameTime != 0L) {
                    val delta = frameTime - lastFrameTime
                    fpsState.value = 1_000_000_000f / delta
                }
                lastFrameTime = frameTime
            }
        }
    }

    return fpsState
}

private data class ExampleSampleSpec(
    val title: String,
    val samplePath: String,
    val languageConfigPath: String,
)

private data class LoadedExampleSample(
    val spec: ExampleSampleSpec,
    val configuration: LanguageConfiguration,
)
