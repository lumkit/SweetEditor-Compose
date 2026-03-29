package com.qiplat.compose.sweeteditor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.qiplat.compose.sweeteditor.theme.LanguageConfigurationParser
import org.jetbrains.compose.resources.Font
import sweeteditor_compose.example.generated.resources.JetBrainsMono_Regular
import sweeteditor_compose.example.generated.resources.Res

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
        var sampleLoaded by remember { mutableStateOf(false) }
        var languageTitle by remember { mutableStateOf("Sweet Editor Demo") }

        LaunchedEffect(editorController) {
            val languageConfig = LanguageConfigurationParser.parse(
                Res.readBytes("files/kotlin.json").decodeToString(),
            )
            val sampleText = Res.readBytes("files/example.kt").decodeToString()
            editorController.applyTheme(editorAppearance.theme)
            editorController.loadText(sampleText)
            editorController.setShowSplitLine(true)
            editorController.setGutterVisible(true)
            editorController.setGutterSticky(true)
            editorController.setTabSize(4)
            editorController.onFontMetricsChanged()
            languageTitle = if (languageConfig.name.isNotBlank()) {
                "Sweet Editor Demo · ${languageConfig.name}"
            } else {
                "Sweet Editor Demo"
            }
            sampleLoaded = true
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = editorAppearance.theme.backgroundColor.toComposeColor(),
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = editorAppearance.theme.backgroundColor.toComposeColor(),
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = editorAppearance.theme.gutterBackgroundColor.toComposeColor(),
                            titleContentColor = editorAppearance.theme.textColor.toComposeColor(),
                        ),
                        title = {
                            Text(languageTitle)
                        }
                    )
                }
            ) { innerPadding ->
                if (sampleLoaded) {
                    SweetEditor(
                        state = editorState,
                        controller = editorController,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        theme = editorAppearance.theme,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

private fun Int.toComposeColor() = androidx.compose.ui.graphics.Color(this)
