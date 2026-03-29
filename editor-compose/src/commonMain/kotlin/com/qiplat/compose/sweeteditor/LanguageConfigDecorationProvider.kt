package com.qiplat.compose.sweeteditor

import com.qiplat.compose.sweeteditor.model.decoration.StyleSpan
import com.qiplat.compose.sweeteditor.theme.EditorThemeStyleIds
import com.qiplat.compose.sweeteditor.theme.LanguageConfiguration
import com.qiplat.compose.sweeteditor.theme.LanguageRule

/**
 * Builds syntax highlight spans from a declarative [LanguageConfiguration].
 *
 * This provider is a bridge layer used by the Compose demo and common decoration pipeline. It compiles
 * language rules once per configuration and evaluates them against document lines when decorations are
 * requested.
 *
 * @property fallbackConfiguration configuration used when the context does not supply one.
 * @property id stable provider identifier.
 * @property overscanLines additional lines requested around the visible range.
 * @property debounceMillis debounce interval before a provider refresh starts.
 */
class LanguageConfigDecorationProvider(
    private val fallbackConfiguration: LanguageConfiguration? = null,
    override val id: String = "language.config.syntax",
    override val overscanLines: Int = 64,
    override val debounceMillis: Long = 16L,
) : DecorationProvider {
    private var lastConfiguration: LanguageConfiguration? = null
    private var compiledConfiguration: CompiledLanguageConfiguration? = null

    /**
     * Produces syntax spans for the requested line range.
     *
     * @param context decoration request context describing document, viewport, and language state.
     * @return replace-range update containing syntax spans, or null when no language configuration is active.
     */
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

    /**
     * Compiles the active language configuration and reuses the previous compilation when possible.
     *
     * @param configuration language configuration to compile.
     * @return compiled rule graph used by the tokenizer.
     */
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

/**
 * Converts declarative language rules into executable regex rules.
 *
 * @property configuration source language configuration.
 * @property resolvedVariables expanded variable values used during pattern compilation.
 */
private class LanguageRuleCompiler(
    private val configuration: LanguageConfiguration,
    private val resolvedVariables: Map<String, String>,
) {
    /**
     * Compiles all states defined by the language configuration.
     *
     * @return compiled language configuration ready for tokenization.
     */
    fun compile(): CompiledLanguageConfiguration {
        val states = configuration.states.mapValues { (stateName, rules) ->
            compileState(stateName, rules)
        }
        return CompiledLanguageConfiguration(
            states = states,
            defaultStateName = states.keys.firstOrNull { it == DEFAULT_LANGUAGE_STATE } ?: states.keys.firstOrNull().orEmpty(),
        )
    }

    /**
     * Compiles one named state.
     *
     * @param stateName state name being compiled.
     * @param rules declarative rules belonging to the state.
     * @return compiled state definition.
     */
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

    /**
     * Expands fragment references and compiles inline rules.
     *
     * @param rules rules to expand.
     * @param visitedFragments fragment set used to break include cycles.
     * @return compiled rule list.
     */
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

    /**
     * Compiles one declarative language rule into an executable regex rule.
     *
     * @param rule declarative rule definition.
     * @return compiled regex rule.
     */
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

/**
 * Executable language configuration produced by [LanguageRuleCompiler].
 */
private data class CompiledLanguageConfiguration(
    val states: Map<String, CompiledState>,
    val defaultStateName: String,
)

/**
 * Executable state definition used during line tokenization.
 */
private data class CompiledState(
    val name: String,
    val tokenRules: List<CompiledRule>,
    val lineEndState: String?,
)

/**
 * Executable regex rule used during line tokenization.
 */
private data class CompiledRule(
    val regex: Regex,
    val styleId: Int?,
    val styleTargets: List<Pair<Int, Int>>,
    val nextState: String?,
    val subStates: List<Pair<Int, String>>,
)

/**
 * Result of tokenizing one line.
 */
private data class LineTokenizeResult(
    val spans: List<StyleSpan>,
    val endState: String,
)

/**
 * Tokenizes one logical line using the compiled language state machine.
 *
 * @param lineText raw line text.
 * @param initialState state entering the line.
 * @param compiled compiled language configuration.
 * @return line tokenization result containing spans and the outgoing state.
 */
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

/**
 * Bundles one matched rule with the produced regex match object.
 */
private data class RuleMatch(
    val rule: CompiledRule,
    val match: MatchResult,
)

/**
 * Finds the first rule that matches exactly at the supplied cursor.
 *
 * @param state compiled state definition.
 * @param lineText source line text.
 * @param cursor current scan cursor.
 * @return matched rule and match result, or null when no rule matches at the cursor.
 */
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

/**
 * Converts one regex match into editor style spans.
 *
 * @param rule compiled rule that owns the match.
 * @param match regex match object.
 * @param offset column offset applied to produced spans.
 * @param output mutable span list receiving generated spans.
 */
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

/**
 * Expands `${variable}` placeholders inside one regex fragment.
 *
 * @param pattern source pattern that may contain placeholders.
 * @param variables available variable values keyed by variable name.
 * @param visited recursion guard used to avoid cyclic substitutions.
 * @return expanded regex fragment.
 */
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

/**
 * Resolves a named style to a concrete style id.
 *
 * @param configuration active language configuration.
 * @param styleName style name defined by a language rule.
 * @return resolved style id, or null when the style cannot be resolved.
 */
private fun resolveStyleId(
    configuration: LanguageConfiguration,
    styleName: String,
): Int? = configuration.highlightStyleIds[styleName] ?: EditorThemeStyleIds.resolve(styleName)

private const val DEFAULT_LANGUAGE_STATE = "default"
