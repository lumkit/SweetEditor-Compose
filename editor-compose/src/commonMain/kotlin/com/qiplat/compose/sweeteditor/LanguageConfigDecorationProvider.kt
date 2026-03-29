package com.qiplat.compose.sweeteditor

import com.qiplat.compose.sweeteditor.model.decoration.StyleSpan
import com.qiplat.compose.sweeteditor.theme.EditorThemeStyleIds
import com.qiplat.compose.sweeteditor.theme.LanguageConfiguration
import com.qiplat.compose.sweeteditor.theme.LanguageRule

class LanguageConfigDecorationProvider(
    private val fallbackConfiguration: LanguageConfiguration? = null,
    override val id: String = "language.config.syntax",
    override val overscanLines: Int = 64,
    override val debounceMillis: Long = 16L,
) : DecorationProvider {
    private var lastConfiguration: LanguageConfiguration? = null
    private var compiledConfiguration: CompiledLanguageConfiguration? = null

    override suspend fun provide(context: DecorationProviderContext): DecorationUpdate? {
        val configuration = context.languageConfiguration ?: fallbackConfiguration ?: return null
        val compiled = compileConfiguration(configuration)
        if (compiled.states.isEmpty()) {
            return null
        }
        val endLine = context.requestedLineRange.last.coerceAtMost(context.document.getLineCount() - 1)
        if (endLine < 0) {
            return null
        }
        val syntaxSpans = linkedMapOf<Int, List<StyleSpan>>()
        var currentState = compiled.defaultStateName
        for (line in 0..endLine) {
            val lineText = context.document.getLineText(line)
            val result = tokenizeLine(
                lineText = lineText,
                initialState = currentState,
                compiled = compiled,
            )
            currentState = result.endState
            if (line in context.requestedLineRange && result.spans.isNotEmpty()) {
                syntaxSpans[line] = result.spans
            }
        }
        return DecorationUpdate(
            decorations = DecorationSet(
                syntaxSpans = syntaxSpans,
            ),
            applyMode = DecorationApplyMode.ReplaceRange,
            lineRange = context.requestedLineRange,
        )
    }

    private fun compileConfiguration(configuration: LanguageConfiguration): CompiledLanguageConfiguration {
        if (configuration == lastConfiguration && compiledConfiguration != null) {
            return compiledConfiguration!!
        }
        val expandedVariables = configuration.variables.mapValues { (name, _) ->
            resolveTemplate(configuration.variables[name].orEmpty(), configuration.variables)
        }
        val compiler = LanguageRuleCompiler(configuration, expandedVariables)
        return compiler.compile().also {
            lastConfiguration = configuration
            compiledConfiguration = it
        }
    }
}

private class LanguageRuleCompiler(
    private val configuration: LanguageConfiguration,
    private val resolvedVariables: Map<String, String>,
) {
    fun compile(): CompiledLanguageConfiguration {
        val states = configuration.states.mapValues { (stateName, rules) ->
            compileState(stateName, rules)
        }
        return CompiledLanguageConfiguration(
            states = states,
            defaultStateName = states.keys.firstOrNull { it == DEFAULT_LANGUAGE_STATE } ?: states.keys.firstOrNull().orEmpty(),
        )
    }

    private fun compileState(
        stateName: String,
        rules: List<LanguageRule>,
    ): CompiledState {
        val lineEndState = rules.lastOrNull { it.onLineEndState != null }?.onLineEndState
        return CompiledState(
            name = stateName,
            tokenRules = expandRules(rules, mutableSetOf()),
            lineEndState = lineEndState,
        )
    }

    private fun expandRules(
        rules: List<LanguageRule>,
        visitedFragments: MutableSet<String>,
    ): List<CompiledRule> = buildList {
        rules.forEach { rule ->
            when {
                rule.include != null -> {
                    val fragmentName = rule.include
                    if (visitedFragments.add(fragmentName)) {
                        addAll(
                            expandRules(
                                configuration.fragments[fragmentName].orEmpty(),
                                visitedFragments,
                            ),
                        )
                        visitedFragments.remove(fragmentName)
                    }
                }

                rule.includes.isNotEmpty() -> {
                    rule.includes.forEach { fragmentName ->
                        if (visitedFragments.add(fragmentName)) {
                            addAll(
                                expandRules(
                                    configuration.fragments[fragmentName].orEmpty(),
                                    visitedFragments,
                                ),
                            )
                            visitedFragments.remove(fragmentName)
                        }
                    }
                }

                rule.pattern != null -> add(compileRule(rule))
            }
        }
    }

    private fun compileRule(rule: LanguageRule): CompiledRule {
        val resolvedPattern = resolveTemplate(rule.pattern.orEmpty(), resolvedVariables)
        return CompiledRule(
            regex = Regex(resolvedPattern),
            styleId = rule.style?.let { resolveStyleId(configuration, it) },
            styleTargets = rule.styles.mapNotNull { target ->
                resolveStyleId(configuration, target.style)?.let { target.group to it }
            },
            nextState = rule.state,
            subStates = rule.subStates.map { it.group to it.state },
        )
    }
}

private data class CompiledLanguageConfiguration(
    val states: Map<String, CompiledState>,
    val defaultStateName: String,
)

private data class CompiledState(
    val name: String,
    val tokenRules: List<CompiledRule>,
    val lineEndState: String?,
)

private data class CompiledRule(
    val regex: Regex,
    val styleId: Int?,
    val styleTargets: List<Pair<Int, Int>>,
    val nextState: String?,
    val subStates: List<Pair<Int, String>>,
)

private data class LineTokenizeResult(
    val spans: List<StyleSpan>,
    val endState: String,
)

private fun tokenizeLine(
    lineText: String,
    initialState: String,
    compiled: CompiledLanguageConfiguration,
): LineTokenizeResult {
    val spans = mutableListOf<StyleSpan>()
    var currentState = initialState.ifBlank { compiled.defaultStateName }
    var cursor = 0
    while (cursor < lineText.length) {
        val state = compiled.states[currentState] ?: compiled.states[compiled.defaultStateName] ?: break
        val matchResult = matchRule(state, lineText, cursor)
        if (matchResult == null) {
            cursor++
            continue
        }
        val matchLength = (matchResult.match.range.last - matchResult.match.range.first + 1).coerceAtLeast(0)
        appendStyles(matchResult.rule, matchResult.match, 0, spans)
        matchResult.rule.subStates.forEach { (group, nextState) ->
            val groupRange = matchResult.match.groups[group]?.range ?: return@forEach
            val nestedText = lineText.substring(groupRange.first, groupRange.last + 1)
            val nestedResult = tokenizeLine(nestedText, nextState, compiled)
            nestedResult.spans.forEach { nestedSpan ->
                spans += nestedSpan.copy(column = nestedSpan.column + groupRange.first)
            }
        }
        if (matchResult.rule.nextState != null) {
            currentState = matchResult.rule.nextState
        }
        cursor += if (matchLength == 0) 1 else matchLength
    }
    val effectiveState = compiled.states[currentState]?.lineEndState ?: currentState
    return LineTokenizeResult(
        spans = spans
            .sortedWith(compareBy(StyleSpan::column, StyleSpan::length)),
        endState = effectiveState,
    )
}

private data class RuleMatch(
    val rule: CompiledRule,
    val match: MatchResult,
)

private fun matchRule(
    state: CompiledState,
    lineText: String,
    cursor: Int,
): RuleMatch? {
    state.tokenRules.forEach { rule ->
        val match = rule.regex.find(lineText, cursor) ?: return@forEach
        if (match.range.first == cursor) {
            return RuleMatch(rule = rule, match = match)
        }
    }
    return null
}

private fun appendStyles(
    rule: CompiledRule,
    match: MatchResult,
    offset: Int,
    output: MutableList<StyleSpan>,
) {
    rule.styleId?.let { styleId ->
        val range = match.range
        output += StyleSpan(
            column = range.first + offset,
            length = range.last - range.first + 1,
            styleId = styleId,
        )
    }
    rule.styleTargets.forEach { (group, styleId) ->
        val groupRange = match.groups[group]?.range ?: return@forEach
        output += StyleSpan(
            column = groupRange.first + offset,
            length = groupRange.last - groupRange.first + 1,
            styleId = styleId,
        )
    }
}

private fun resolveTemplate(
    pattern: String,
    variables: Map<String, String>,
    visited: MutableSet<String> = mutableSetOf(),
): String {
    val templatePattern = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)\}""")
    return templatePattern.replace(pattern) { match ->
        val variableName = match.groupValues[1]
        if (!visited.add(variableName)) {
            return@replace match.value
        }
        val resolved = variables[variableName]
            ?.let { resolveTemplate(it, variables, visited) }
            ?: match.value
        visited.remove(variableName)
        "(?:$resolved)"
    }
}

private fun resolveStyleId(
    configuration: LanguageConfiguration,
    styleName: String,
): Int? = configuration.highlightStyleIds[styleName] ?: EditorThemeStyleIds.resolve(styleName)

private const val DEFAULT_LANGUAGE_STATE = "default"
