package com.qiplat.compose.sweeteditor.example

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.qiplat.compose.sweeteditor.*
import com.qiplat.compose.sweeteditor.copilot.InlineSuggestion
import com.qiplat.compose.sweeteditor.example.theme.JetBrainsIdeaEditorTheme
import com.qiplat.compose.sweeteditor.example.theme.VisualStudioCodeEditorTheme
import com.qiplat.compose.sweeteditor.example.theme.VisualStudioEditorTheme
import com.qiplat.compose.sweeteditor.model.decoration.*
import com.qiplat.compose.sweeteditor.model.foundation.CurrentLineRenderMode
import com.qiplat.compose.sweeteditor.model.foundation.TextPosition
import com.qiplat.compose.sweeteditor.model.foundation.WrapMode
import com.qiplat.compose.sweeteditor.model.visual.PointF
import com.qiplat.compose.sweeteditor.theme.LanguageConfiguration
import com.qiplat.compose.sweeteditor.theme.LanguageConfigurationParser
import com.qiplat.compose.sweeteditor.theme.SweetEditorSpanStyleKeys
import com.qiplat.compose.sweeteditor.theme.SweetEditorTheme
import com.qiplat.compose.sweeteditor.theme.rememberSweetEditorTheme
import kotlinx.coroutines.delay
import sweeteditor_compose.example.generated.resources.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    MaterialTheme {
        val systemDarkMode = isSystemInDarkTheme()
        var darkThemeMode by rememberSaveable { mutableStateOf(systemDarkMode) }
        val jsonDarkThemeContent by produceState<String?>(initialValue = null) {
            value = Res.readBytes("files/editor/theme_dark.json").decodeToString()
        }
        val jsonLightThemeContent by produceState<String?>(initialValue = null) {
            value = Res.readBytes("files/editor/theme_light.json").decodeToString()
        }
        val jsonTheme = remember(jsonDarkThemeContent, jsonLightThemeContent) {
            SweetEditorDefaults.theme().fromJson(
                darkJson = jsonDarkThemeContent,
                lightJson = jsonLightThemeContent,
            )
        }
        val availableThemes = remember {
            listOf(
                ThemeOption("Default Theme", SweetEditorDefaults.theme()),
                ThemeOption("JetBrains IDEA", JetBrainsIdeaEditorTheme()),
                ThemeOption("Visual Studio Code", VisualStudioCodeEditorTheme()),
                ThemeOption("Visual Studio", VisualStudioEditorTheme()),
            )
        }
        val themes = remember(availableThemes, jsonTheme) {
            availableThemes + ThemeOption("JSON Theme", jsonTheme)
        }
        var selectedThemeIndex by rememberSaveable { mutableIntStateOf(0) }
        var showThemeDialog by rememberSaveable { mutableStateOf(false) }
        val selectedThemeOption = themes[selectedThemeIndex.coerceIn(0, themes.lastIndex)]
        val editorTheme = rememberSweetEditorTheme(
            theme = selectedThemeOption.theme,
            darkMode = darkThemeMode,
        )
        val editorController = rememberSweetEditorController()
        var loadedSamples by remember { mutableStateOf<List<LoadedExampleSample>>(emptyList()) }
        var selectedSampleIndex by remember { mutableIntStateOf(0) }
        var wrapModeOrdinal by rememberSaveable { mutableIntStateOf(WrapMode.None.ordinal) }
        var currentLineRenderModeOrdinal by rememberSaveable {
            mutableIntStateOf(CurrentLineRenderMode.Background.ordinal)
        }
        var readOnly by rememberSaveable { mutableStateOf(false) }
        var compositionEnabled by rememberSaveable { mutableStateOf(true) }
        var gutterVisible by rememberSaveable { mutableStateOf(true) }
        var gutterSticky by rememberSaveable { mutableStateOf(true) }
        var showSplitLine by rememberSaveable { mutableStateOf(true) }

        val sampleSpecs = remember {
            listOf(
                ExampleSampleSpec("example.kt", "files/example_kt", "files/kotlin_json"),
                ExampleSampleSpec("example.java", "files/example_java", "files/java_json"),
                ExampleSampleSpec("View.java", "files/View_java", "files/java_json"),
                ExampleSampleSpec("example.lua", "files/example_lua", "files/lua_json"),
                ExampleSampleSpec("nlohmann-json.hpp", "files/nlohmann-json_hpp", "files/cpp_json"),
            )
        }
        val decorationProviders: List<DecorationProvider> = remember {
            listOf(
                LanguageConfigDecorationProvider(),
//                ExampleDemoDecorationProvider(),
            )
        }
        val demoIconProvider = remember { ExampleDemoIconProvider }
        val completionProvider = remember { ExampleDemoCompletionProvider() }
        val wrapMode = WrapMode.entries[wrapModeOrdinal.coerceIn(0, WrapMode.entries.lastIndex)]
        val currentLineRenderMode = CurrentLineRenderMode.entries[
            currentLineRenderModeOrdinal.coerceIn(0, CurrentLineRenderMode.entries.lastIndex)
        ]
        val editorSettings = remember(
            wrapMode,
            readOnly,
            compositionEnabled,
            gutterVisible,
            gutterSticky,
            currentLineRenderMode,
        ) {
            SweetEditorSettings(
                wrapMode = wrapMode,
                tabSize = 4,
                gutterVisible = gutterVisible,
                gutterSticky = gutterSticky,
                currentLineRenderMode = currentLineRenderMode,
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
            editorController.setShowSplitLine(showSplitLine)
            editorController.onFontMetricsChanged()
        }

        LaunchedEffect(editorController, showSplitLine) {
            editorController.setShowSplitLine(showSplitLine)
        }

        DisposableEffect(editorController, completionProvider) {
            editorController.addCompletionProvider(completionProvider)
            onDispose {
                editorController.removeCompletionProvider(completionProvider)
            }
        }

        DisposableEffect(editorController, demoIconProvider) {
            editorController.setEditorIconProvider(demoIconProvider)
            onDispose {
                editorController.setEditorIconProvider(null)
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = editorTheme.colors.gutterBackground,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = editorTheme.colors.gutterBackground,
                        titleContentColor = editorTheme.colors.text,
                        actionIconContentColor = editorTheme.colors.text,
                    ),
                    title = {
                        Text(activeSample?.spec?.title?.let { "Sweet Editor $it" } ?: "Sweet Editor")
                    },
                    actions = {
                        Actions(
                            editorController = editorController,
                            themeName = themes[selectedThemeIndex].name,
                            darkThemeMode = darkThemeMode,
                            wrapMode = wrapMode,
                            readOnly = readOnly,
                            compositionEnabled = compositionEnabled,
                            gutterVisible = gutterVisible,
                            showSplitLine = showSplitLine,
                            currentLineRenderMode = currentLineRenderMode,
                            onOpenThemeDialog = { showThemeDialog = true },
                            onToggleThemeMode = { darkThemeMode = !darkThemeMode },
                            onCycleWrapMode = {
                                wrapModeOrdinal = (wrapModeOrdinal + 1) % WrapMode.entries.size
                            },
                            onToggleReadOnly = {
                                readOnly = !readOnly
                            },
                            onToggleComposition = {
                                compositionEnabled = !compositionEnabled
                            },
                            onToggleGutterVisible = {
                                gutterVisible = !gutterVisible
                            },
                            onToggleSplitLine = {
                                showSplitLine = !showSplitLine
                            },
                            onCycleCurrentLineRenderMode = {
                                currentLineRenderModeOrdinal =
                                    (currentLineRenderModeOrdinal + 1) % CurrentLineRenderMode.entries.size
                            },
                            onShowInlineSuggestion = {
                                val cursor = editorController.cursorPositionState.value
                                val suggestionText = when {
                                    loadedSamples.getOrNull(selectedSampleIndex)?.spec?.title?.contains("Kotlin", ignoreCase = true) == true ->
                                        "\n    println(\"Inline suggestion accepted\")"
                                    loadedSamples.getOrNull(selectedSampleIndex)?.spec?.title?.contains("C++", ignoreCase = true) == true ->
                                        "\n    std::cout << \"inline suggestion\" << std::endl;"
                                    else -> " // inline suggestion"
                                }
                                editorController.inlineSuggestions().show(
                                    InlineSuggestion(
                                        line = cursor.line,
                                        column = cursor.column,
                                        text = suggestionText,
                                    ),
                                )
                            },
                        )
                    }
                )
            },
            bottomBar = {
                CompositionLocalProvider(
                    LocalContentColor provides editorTheme.colors.text,
                ) {
                    ExampleStatusBar(
                        editorController = editorController,
                    )
                }
            },
        ) { paddingValues ->
            ProvideTextStyle(MaterialTheme.typography.labelSmall.copy(color = editorTheme.colors.text)) {
                Column(
                    modifier = Modifier.padding(paddingValues)
                        .fillMaxSize(),
                ) {
                    if (loadedSamples.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(editorTheme.colors.gutterBackground)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            loadedSamples.forEachIndexed { index, sample ->
                                FilterChip(
                                    selected = index == selectedSampleIndex,
                                    onClick = {
                                        selectedSampleIndex = index
                                    },
                                    label = {
                                        Text(
                                            sample.spec.title,
                                        )
                                    },
                                )
                            }
                        }
                    }

                    SweetEditor(
                        controller = editorController,
                        modifier = Modifier.fillMaxSize(),
                        theme = editorTheme,
                        settings = editorSettings,
                        decorationProviders = decorationProviders,
                        onGestureResult = {},
                        onHitTarget = {},
                        onContextMenuRequest = {},
                        onSelectionHandleDragStateChange = {},
                        completions = { selectedIndex, items, renderer ->
                            val theme = editorTheme
                            val background = theme.colors.gutterBackground
                            val borderColor = theme.colors.scrollbarThumb
                            val selectedColor = theme.colors.selection
                            val text = theme.colors.text

                            Box(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .background(background)
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
                                            color = text,
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
        if (showThemeDialog) {
            ThemePickerDialog(
                currentIndex = selectedThemeIndex,
                options = themes.map { it.name },
                onDismiss = { showThemeDialog = false },
                onApply = { appliedIndex ->
                    selectedThemeIndex = appliedIndex.coerceIn(0, themes.lastIndex)
                    showThemeDialog = false
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExampleStatusBar(
    editorController: SweetEditorController,
) {
    val fps by rememberFps()
    val scale by editorController.scaleState
    val cursorPosition by editorController.cursorPositionState
    val visibleLineRange by editorController.visibleLineRangeState
    val selectedText by editorController.selectedTextState

    ProvideTextStyle(value = MaterialTheme.typography.labelMedium) {
        Row(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(72.dp)
                .padding(horizontal = 16.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ln ${cursorPosition.line + 1}, Col ${cursorPosition.column + 1}",
                modifier = Modifier.width(120.dp),
            )
            Text(
                text = visibleLineRange?.let { "Visible ${it.first + 1}-${it.last + 1}" } ?: "Visible -",
                modifier = Modifier.width(128.dp),
            )
            Text(
                text = if (selectedText.isNullOrEmpty()) "Selection 0" else "Selection ${selectedText!!.length}",
                modifier = Modifier.width(110.dp),
            )
            Text(
                text = "FPS ${fps.toInt()}",
                modifier = Modifier.width(70.dp),
            )
            Text(
                text = "Scale: ${scale.toString().take(4)}",
                modifier = Modifier.width(80.dp),
            )
            Slider(
                value = scale,
                onValueChange = {
                    editorController.setScale(it)
                },
                valueRange = .5f..2f,
                modifier = Modifier.width(120.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.Actions(
    editorController: SweetEditorController,
    themeName: String,
    darkThemeMode: Boolean,
    wrapMode: WrapMode,
    readOnly: Boolean,
    compositionEnabled: Boolean,
    gutterVisible: Boolean,
    showSplitLine: Boolean,
    currentLineRenderMode: CurrentLineRenderMode,
    onOpenThemeDialog: () -> Unit,
    onToggleThemeMode: () -> Unit,
    onCycleWrapMode: () -> Unit,
    onToggleReadOnly: () -> Unit,
    onToggleComposition: () -> Unit,
    onToggleGutterVisible: () -> Unit,
    onToggleSplitLine: () -> Unit,
    onCycleCurrentLineRenderMode: () -> Unit,
    onShowInlineSuggestion: () -> Unit,
) {
    var menuState by rememberSaveable { mutableStateOf(false) }
    val canUndo by editorController.canUndoState
    val canRedo by editorController.canRedoState

    IconButton(
        {
            editorController.undo()
        },
        enabled = canUndo,
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
        enabled = canRedo,
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
                    onCycleWrapMode()
                    menuState = false
                }
            )

            DropdownMenuItem(
                text = {
                    Text("Theme Config: $themeName")
                },
                onClick = {
                    onOpenThemeDialog()
                    menuState = false
                }
            )

            DropdownMenuItem(
                text = {
                    Text(if (darkThemeMode) "Theme: Dark" else "Theme: Light")
                },
                onClick = {
                    onToggleThemeMode()
                    menuState = false
                }
            )

            DropdownMenuItem(
                text = {
                    Text(if (readOnly) "Switch To Editable" else "Switch To ReadOnly")
                },
                onClick = {
                    onToggleReadOnly()
                    menuState = false
                }
            )

            DropdownMenuItem(
                text = {
                    Text(if (compositionEnabled) "Disable IME Composition" else "Enable IME Composition")
                },
                onClick = {
                    onToggleComposition()
                    menuState = false
                }
            )

            DropdownMenuItem(
                text = {
                    Text(if (gutterVisible) "Hide Gutter" else "Show Gutter")
                },
                onClick = {
                    onToggleGutterVisible()
                    menuState = false
                }
            )

            DropdownMenuItem(
                text = {
                    Text(if (showSplitLine) "Hide Split Line" else "Show Split Line")
                },
                onClick = {
                    onToggleSplitLine()
                    menuState = false
                }
            )

            DropdownMenuItem(
                text = {
                    Text("CurrentLine: ${currentLineRenderMode.name}")
                },
                onClick = {
                    onCycleCurrentLineRenderMode()
                    menuState = false
                }
            )

            DropdownMenuItem(
                text = {
                    Text("Show Inline Suggestion")
                },
                onClick = {
                    onShowInlineSuggestion()
                    menuState = false
                }
            )
        }
    }
}

@Composable
private fun ThemePickerDialog(
    currentIndex: Int,
    options: List<String>,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
) {
    var pendingIndex by remember(currentIndex, options) {
        mutableIntStateOf(currentIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0)))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Theme Config")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pendingIndex = index }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = pendingIndex == index,
                            onClick = { pendingIndex = index },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(pendingIndex) },
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private data class ThemeOption(
    val name: String,
    val theme: SweetEditorTheme,
)

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
                        detail = "snippet 锟?class definition",
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
    override val debounceMillis: Long = 80L
    override val capabilities: Set<DecorationType> = setOf(
        DecorationType.SyntaxHighlight,
        DecorationType.InlayHint,
        DecorationType.PhantomText,
        DecorationType.Diagnostic,
        DecorationType.IndentGuide,
        DecorationType.FoldRegion,
        DecorationType.GutterIcon,
        DecorationType.SeparatorGuide,
    )
    private val styleIdColorToken = SweetEditorSpanStyleKeys.USER_BASE + 1
    private val spanStyles = mapOf(
        styleIdColorToken to SpanStyle(
            color = Color(0xFF80CBC4.toInt()),
            fontStyle = SpanFontStyle.Bold,
        ),
    )
    private val colorRegex = Regex("#[0-9a-fA-F]{6}\\b")
    private val classOrStructRegex = Regex("""\b(class|struct)\b""")
    private val returnRegex = Regex("""\breturn\b""")
    private var structuralCache: StructuralDecorationCache? = null

    override suspend fun provideDecorations(
        context: DecorationProviderContext,
        receiver: DecorationReceiver,
    ) {
        val inlayHints = linkedMapOf<Int, MutableList<InlayHint>>()
        val phantomTexts = linkedMapOf<Int, MutableList<PhantomText>>()
        val gutterIcons = linkedMapOf<Int, MutableList<GutterIcon>>()
        val separatorGuides = mutableListOf<SeparatorGuide>()
        val (foldRegions, indentGuides) = buildStructuralDecorations(context)
        val syntaxSpans = linkedMapOf<Int, MutableList<StyleSpan>>()
        val diagnostics = linkedMapOf<Int, MutableList<DiagnosticItem>>()
        var phantomInserted = false

        for (line in context.requestedLineRange) {
            val lineText = context.document.getLineText(line)
            val lineDiagnostics = diagnostics.getOrPut(line) { mutableListOf() }
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
            lineText.indexOf('@').takeIf { it >= 0 }?.let { column ->
                lineDiagnostics += DiagnosticItem(
                    column = column,
                    length = 1,
                    severity = DiagnosticSeverity.Info,
                )
            }

            if (lineText.contains("class") || lineText.contains("struct")) {
                gutterIcons.getOrPut(line) { mutableListOf() }.add(
                    GutterIcon(iconId = 1),
                )
            }
            lineText.indexOf('@').takeIf { it >= 0 }?.let {
                gutterIcons.getOrPut(line) { mutableListOf() }.add(
                    GutterIcon(iconId = 2),
                )
            }

            if ("#region" in lineText || "// region" in lineText.lowercase()) {
                separatorGuides += SeparatorGuide(
                    line = line,
                    style = SeparatorStyle.Double,
                    count = 1,
                    textEndColumn = lineText.length,
                )
            }
            if (lineText.trimStart('/', ' ', '\t').startsWith("-----")) {
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

            appendKeywordTextHints(inlayHints, line, lineText)

            if (!phantomInserted) {
                when {
                    classOrStructRegex.containsMatchIn(lineText) -> {
                        phantomTexts.getOrPut(line) { mutableListOf() }.add(
                            PhantomText(
                                column = lineText.length,
                                text = " /* demo phantom: debugTrace(tag) */",
                            ),
                        )
                        phantomInserted = true
                    }
                    returnRegex.containsMatchIn(lineText) -> {
                        val column = lineText.indexOf("return") + "return".length
                        phantomTexts.getOrPut(line) { mutableListOf() }.add(
                            PhantomText(
                                column = column,
                                text = " /* demo phantom */",
                            ),
                        )
                        phantomInserted = true
                    }
                }
            }
        }
        if (!receiver.accept(
                DecorationResult(
                    spanStyles = spanStyles,
                    spanStylesMode = DecorationApplyMode.Merge,
                    syntaxSpans = syntaxSpans,
                    syntaxSpansMode = DecorationApplyMode.ReplaceRange,
                    inlayHints = inlayHints,
                    inlayHintsMode = DecorationApplyMode.ReplaceRange,
                    phantomTexts = phantomTexts,
                    phantomTextsMode = DecorationApplyMode.ReplaceRange,
                    diagnostics = diagnostics.mapValues { it.value },
                    diagnosticsMode = DecorationApplyMode.ReplaceRange,
                    gutterIcons = gutterIcons,
                    gutterIconsMode = DecorationApplyMode.ReplaceRange,
                    separatorGuides = separatorGuides,
                    separatorGuidesMode = DecorationApplyMode.ReplaceRange,
                    lineRange = context.requestedLineRange,
                ),
            )
        ) {
            return
        }
        receiver.accept(
            DecorationResult(
                indentGuides = indentGuides,
                indentGuidesMode = DecorationApplyMode.ReplaceAll,
                foldRegions = foldRegions,
                foldRegionsMode = DecorationApplyMode.ReplaceAll,
            ),
        )
    }

    private fun appendKeywordTextHints(
        inlayHints: MutableMap<Int, MutableList<InlayHint>>,
        line: Int,
        lineText: String,
    ) {
        fun addHint(keyword: String, hint: String) {
            val index = lineText.indexOf(keyword)
            if (index >= 0) {
                inlayHints.getOrPut(line) { mutableListOf() }.add(
                    InlayHint(
                        type = InlayType.Text,
                        column = index + keyword.length + 1,
                        text = hint,
                    ),
                )
            }
        }
        addHint("const", "immutable")
        addHint("return", "value: ")
        addHint("case", "condition: ")
    }

    private fun buildStructuralDecorations(
        context: DecorationProviderContext,
    ): Pair<List<FoldRegion>, List<IndentGuide>> {
        val cache = structuralCache
        if (cache != null && !shouldRebuildStructuralDecorations(cache, context)) {
            return cache.foldRegions to cache.indentGuides
        }
        val foldSet = linkedSetOf<FoldRegion>()
        val indentSet = linkedSetOf<IndentGuide>()
        val braceStack = ArrayDeque<Int>()
        val regionStack = ArrayDeque<Int>()
        val lineSnapshots = ArrayList<String>(context.totalLineCount)
        var inBlockComment = false

        for (line in 0 until context.totalLineCount) {
            val text = context.document.getLineText(line)
            lineSnapshots += text
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
        val foldRegions = foldSet.toList()
        val indentGuides = indentSet.toList()
        structuralCache = StructuralDecorationCache(
            documentIdentity = context.document.hashCode(),
            totalLineCount = context.totalLineCount,
            foldRegions = foldRegions,
            indentGuides = indentGuides,
            lineSnapshots = lineSnapshots,
        )
        return foldRegions to indentGuides
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

    private fun shouldRebuildStructuralDecorations(
        cache: StructuralDecorationCache,
        context: DecorationProviderContext,
    ): Boolean {
        if (cache.documentIdentity != context.document.hashCode()) {
            return true
        }
        if (cache.totalLineCount != context.totalLineCount) {
            return true
        }
        if (!context.lastEditResult.changed) {
            return false
        }
        return context.textChanges.any { change ->
            if (change.range.start.line != change.range.end.line) {
                return@any true
            }
            val line = change.range.start.line
            val oldLine = cache.lineSnapshots.getOrNull(line).orEmpty()
            val newLine = context.document.getLineText(line)
            structuralPattern.containsMatchIn(oldLine) || structuralPattern.containsMatchIn(newLine)
        }
    }
}

private data class StructuralDecorationCache(
    val documentIdentity: Int,
    val totalLineCount: Int,
    val foldRegions: List<FoldRegion>,
    val indentGuides: List<IndentGuide>,
    val lineSnapshots: List<String>,
)

private object ExampleDemoIconProvider : EditorIconProvider {
    override fun paint(
        drawScope: DrawScope,
        iconId: Int,
        origin: PointF,
        size: Size,
        tint: Color,
    ): Boolean = with(drawScope) {
        when (iconId) {
            1 -> {
                drawCircle(
                    color = tint,
                    radius = minOf(size.width, size.height) * 0.34f,
                    center = androidx.compose.ui.geometry.Offset(
                        origin.x + size.width / 2f,
                        origin.y + size.height / 2f,
                    ),
                )
                true
            }
            2 -> {
                drawRect(
                    color = tint,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        origin.x + size.width * 0.22f,
                        origin.y + size.height * 0.22f,
                    ),
                    size = Size(size.width * 0.56f, size.height * 0.56f),
                )
                true
            }
            else -> false
        }
    }
}

private val structuralPattern = Regex("""\{|\}|#region|#endregion|//\s*region|//\s*endregion|/\*|\*/|'|" """.trim())

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
