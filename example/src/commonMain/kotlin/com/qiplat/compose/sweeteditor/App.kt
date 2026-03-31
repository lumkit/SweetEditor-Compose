package com.qiplat.compose.sweeteditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiplat.compose.sweeteditor.model.decoration.DiagnosticItem
import com.qiplat.compose.sweeteditor.model.decoration.DiagnosticSeverity
import com.qiplat.compose.sweeteditor.model.foundation.WrapMode
import com.qiplat.compose.sweeteditor.theme.LanguageConfiguration
import com.qiplat.compose.sweeteditor.theme.LanguageConfigurationParser
import org.jetbrains.compose.resources.Font
import sweeteditor_compose.example.generated.resources.JetBrainsMono_Regular
import sweeteditor_compose.example.generated.resources.Res
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    MaterialTheme {
        val editorFontFamily = FontFamily(Font(Res.font.JetBrainsMono_Regular))
        val editorFontConfig = remember(editorFontFamily) {
            EditorFontConfig(
                fontFamily = editorFontFamily,
                fontSize = 14.sp,
                lineNumberFontSize = 13.sp,
                inlayHintFontSize = 12.sp,
                iconSize = 16.sp,
            )
        }
        val editorAppearance = rememberEditorAppearance(
            themeContent = null,
            fontConfig = editorFontConfig,
            darkMode = true,
        )
        val editorState = rememberEditorState()
        val editorController = rememberEditorController(
            textMeasurer = editorAppearance.textMeasurer,
            state = editorState,
        )
        var loadedSamples by remember { mutableStateOf<List<LoadedExampleSample>>(emptyList()) }
        var selectedSampleIndex by remember { mutableIntStateOf(0) }
        var gestureSummary by remember { mutableStateOf("No gesture") }
        var hitTargetSummary by remember { mutableStateOf("No hit target") }
        var contextMenuSummary by remember { mutableStateOf("No context menu") }
        var handleDragSummary by remember { mutableStateOf("HandleDrag=false") }
        var wrapEnabled by remember { mutableStateOf(false) }
        var readOnly by remember { mutableStateOf(false) }
        var compositionEnabled by remember { mutableStateOf(true) }
        val sampleSpecs = remember {
            listOf(
                ExampleSampleSpec("example.kt", "files/example_kt", "files/kotlin_json"),
                ExampleSampleSpec("example.java", "files/example_java", "files/java_json"),
                ExampleSampleSpec("View.java", "files/View.java", "files/java_json"),
                ExampleSampleSpec("example.lua", "files/example_lua", "files/lua_json"),
                ExampleSampleSpec("nlohmann-json.hpp", "files/nlohmann-json_hpp", "files/cpp_json"),
            )
        }
        val decorationProviders = remember {
            listOf(
                LanguageConfigDecorationProvider(),
//                ExampleDiagnosticsDecorationProvider(),
            )
        }
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
        val activeSample = loadedSamples.getOrNull(selectedSampleIndex.coerceIn(0, (loadedSamples.size - 1).coerceAtLeast(0)))
        val languageSummary = activeSample?.let { sample ->
            buildString {
                val configuration = sample.configuration
                append(configuration.scopeName.ifBlank { configuration.name.ifBlank { "unknown" } })
                append(" · ext=")
                append(configuration.fileExtensions.joinToString().ifBlank { "-" })
                append(" · styles=")
                append(configuration.highlightStyleIds.size)
                configuration.comments.lineComment?.let {
                    append(" · lineComment=")
                    append(it)
                }
            }
        } ?: "Language metadata unavailable"

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

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = IntelliJBackground,
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize()
            ) {
                if (loadedSamples.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(IntelliJBackground)
                            .padding(it),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(IntelliJToolbar)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = "SweetEditor Compose",
                                    color = IntelliJTextPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    IdeToggleChip(
                                        label = if (wrapEnabled) "Soft Wraps" else "No Wrap",
                                        selected = wrapEnabled,
                                        onClick = { wrapEnabled = !wrapEnabled },
                                    )
                                    IdeToggleChip(
                                        label = if (readOnly) "ReadOnly" else "Editable",
                                        selected = readOnly,
                                        onClick = { readOnly = !readOnly },
                                    )
                                    IdeToggleChip(
                                        label = if (compositionEnabled) "IME On" else "IME Off",
                                        selected = compositionEnabled,
                                        onClick = { compositionEnabled = !compositionEnabled },
                                    )
                                }
                            }
                        }
                        SecondaryScrollableTabRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(IntelliJTabStrip),
                            selectedTabIndex = selectedSampleIndex.coerceIn(0, loadedSamples.lastIndex),
                            containerColor = IntelliJTabStrip,
                            contentColor = IntelliJTextPrimary,
                            edgePadding = 12.dp,
                            divider = {
                                HorizontalDivider(color = IntelliJBorder)
                            },
                        ) {
                            loadedSamples.forEachIndexed { index, sample ->
                                Tab(
                                    selected = index == selectedSampleIndex,
                                    onClick = { selectedSampleIndex = index },
                                    selectedContentColor = IntelliJTextPrimary,
                                    unselectedContentColor = IntelliJTextSecondary,
                                    text = {
                                        Text(sample.spec.title)
                                    },
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(IntelliJSurface)
                                    .border(width = 1.dp, color = IntelliJBorder)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = activeSample?.spec?.title.orEmpty(),
                                    color = IntelliJTextPrimary,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = languageSummary,
                                    color = IntelliJTextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(IntelliJEditorSurface)
                                    .border(width = 1.dp, color = IntelliJBorder),
                            ) {
                                SweetEditor(
                                    state = editorState,
                                    controller = editorController,
                                    modifier = Modifier.fillMaxSize(),
                                    theme = editorAppearance.theme,
                                    settings = editorSettings,
                                    decorationProviders = decorationProviders,
                                    onGestureResult = { result ->
                                        gestureSummary = "${result.type.name} · scale=${result.viewScale.toReadableScale()}"
                                    },
                                    onHitTarget = { hitTarget ->
                                        hitTargetSummary = buildString {
                                            append(hitTarget.type.name)
                                            if (hitTarget.line != 0 || hitTarget.column != 0) {
                                                append(" @ ")
                                                append(hitTarget.line)
                                                append(':')
                                                append(hitTarget.column)
                                            }
                                        }
                                    },
                                    onContextMenuRequest = { request ->
                                        contextMenuSummary = buildString {
                                            append("ContextMenu @ ")
                                            append(request.gestureResult.tapPoint.x.toInt())
                                            append(',')
                                            append(request.gestureResult.tapPoint.y.toInt())
                                            append(" · ")
                                            append(request.hitTarget.type.name)
                                        }
                                    },
                                    onSelectionHandleDragStateChange = { dragState ->
                                        handleDragSummary = buildString {
                                            append("HandleDrag=")
                                            append(dragState.active)
                                            if (dragState.active) {
                                                append(" @ ")
                                                append(dragState.startHandle.position.x.toInt())
                                                append(',')
                                                append(dragState.startHandle.position.y.toInt())
                                                append(" -> ")
                                                append(dragState.endHandle.position.x.toInt())
                                                append(',')
                                                append(dragState.endHandle.position.y.toInt())
                                            }
                                        }
                                    },
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(IntelliJStatusBar)
                                    .border(width = 1.dp, color = IntelliJBorder)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                StatusLabel("Gesture", gestureSummary)
                                StatusLabel("Hit", hitTargetSummary)
                                StatusLabel("Menu", contextMenuSummary)
                                StatusLabel("Selection", handleDragSummary)
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = IntelliJAccent)
                    }
                }
            }
        }
    }
}

private fun Float.toReadableScale(): String =
    ((this * 100).roundToInt() / 100f).toString()

@Composable
private fun IdeToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(label)
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = IntelliJAccent.copy(alpha = 0.18f),
            selectedLabelColor = IntelliJTextPrimary,
            containerColor = IntelliJToolbar,
            labelColor = IntelliJTextSecondary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = IntelliJBorder,
            selectedBorderColor = IntelliJAccent,
        ),
    )
}

@Composable
private fun StatusLabel(
    title: String,
    value: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.widthIn(max = 420.dp),
    ) {
        Text(
            text = title,
            color = IntelliJTextSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = value,
            color = IntelliJTextPrimary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
    }
}

private class ExampleDiagnosticsDecorationProvider : DecorationProvider {
    override val id: String = "example.demo.diagnostics"
    override val overscanLines: Int = 8

    override suspend fun provide(context: DecorationProviderContext): DecorationUpdate {
        val diagnostics = linkedMapOf<Int, List<DiagnosticItem>>()
        for (line in context.requestedLineRange) {
            val lineText = context.document.getLineText(line)
            if ("TODO" !in lineText) {
                continue
            }
            diagnostics[line] = listOf(
                DiagnosticItem(
                    column = lineText.indexOf("TODO").coerceAtLeast(0),
                    length = 4,
                    severity = DiagnosticSeverity.Hint,
                ),
            )
        }
        return DecorationUpdate(
            decorations = DecorationSet(diagnostics = diagnostics),
            applyMode = DecorationApplyMode.ReplaceRange,
            lineRange = context.requestedLineRange,
        )
    }
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

private val IntelliJBackground = Color(0xFF1E1F22)
private val IntelliJToolbar = Color(0xFF2B2D30)
private val IntelliJTabStrip = Color(0xFF313338)
private val IntelliJSurface = Color(0xFF2B2D30)
private val IntelliJEditorSurface = Color(0xFF1F2023)
private val IntelliJStatusBar = Color(0xFF2B2D30)
private val IntelliJBorder = Color(0xFF3C3F41)
private val IntelliJTextPrimary = Color(0xFFD8D8D8)
private val IntelliJTextSecondary = Color(0xFF9DA0A6)
private val IntelliJAccent = Color(0xFF4C89FF)
