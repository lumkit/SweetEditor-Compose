package com.qiplat.compose.sweeteditor

import com.qiplat.compose.sweeteditor.model.decoration.StyleSpan
import com.qiplat.compose.sweeteditor.model.foundation.TextEditResult
import com.qiplat.compose.sweeteditor.runtime.EditorDocument
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
    private var session: LanguageAnalysisSession? = null

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
        val lineCount = context.document.getLineCount()
        if (lineCount <= 0) {
            return null
        }
        val startLine = context.requestedLineRange.first.coerceAtLeast(0).coerceAtMost(lineCount - 1)
        val endLine = context.requestedLineRange.last.coerceAtLeast(startLine).coerceAtMost(lineCount - 1)
        val activeSession = getOrCreateSession(
            documentIdentity = context.document.hashCode(),
            configuration = configuration,
            compiled = compiled,
            lineCount = lineCount,
        )
        activeSession.markInvalidated(context.lastEditResult)
        val invalidatedStartLine = activeSession.invalidatedStartLine
        val analysisStartLine = when {
            invalidatedStartLine == null -> (startLine - STATE_LOOKBACK_LINES).coerceAtLeast(0)
            invalidatedStartLine <= startLine -> invalidatedStartLine.coerceAtLeast(0)
            else -> (startLine - STATE_LOOKBACK_LINES).coerceAtLeast(0)
        }
        val seedState = when {
            analysisStartLine == 0 -> compiled.defaultStateName
            else -> activeSession.lineResults.getOrNull(analysisStartLine - 1)?.takeIf {
                it.textHash == context.document.getLineText(analysisStartLine - 1).hashCode()
            }?.outputState ?: compiled.defaultStateName
        }
        val syntaxSpans = linkedMapOf<Int, List<StyleSpan>>()
        var currentState = seedState
        var line = analysisStartLine
        while (line <= endLine) {
            val lineText = context.document.getLineText(line)
            val textHash = lineText.hashCode()
            val cached = activeSession.lineResults.getOrNull(line)
            val inputState = currentState
            val result = if (
                cached != null &&
                cached.textHash == textHash &&
                cached.inputState == inputState
            ) {
                LineTokenizeResult(
                    spans = cached.spans,
                    endState = cached.outputState,
                )
            } else {
                tokenizeLine(
                    lineText = lineText,
                    initialState = currentState,
                    compiled = compiled,
                ).also { analysis ->
                    activeSession.lineResults[line] = CachedLineAnalysis(
                        inputState = inputState,
                        outputState = analysis.endState,
                        textHash = textHash,
                        spans = analysis.spans,
                    )
                }
            }
            currentState = result.endState
            if (line in startLine..endLine) {
                if (result.spans.isNotEmpty()) {
                    syntaxSpans[line] = result.spans
                } else {
                    syntaxSpans.remove(line)
                }
            }
            if (
                invalidatedStartLine != null &&
                line >= invalidatedStartLine &&
                cached != null &&
                cached.textHash == textHash &&
                cached.inputState == inputState &&
                cached.outputState == result.endState &&
                cached.spans == result.spans
            ) {
                activeSession.invalidatedStartLine = null
                val reuseResult = activeSession.tryReuseCachedRange(
                    document = context.document,
                    startLine = line + 1,
                    endLine = endLine,
                    requestedRange = startLine..endLine,
                    initialState = currentState,
                    syntaxSpans = syntaxSpans,
                )
                currentState = reuseResult.finalState
                if (reuseResult.nextLine > endLine) {
                    break
                }
                line = reuseResult.nextLine
                continue
            }
            line++
        }
        return DecorationUpdate(
            decorations = DecorationSet(
                syntaxSpans = syntaxSpans,
            ),
            applyMode = DecorationApplyMode.ReplaceRange,
            lineRange = context.requestedLineRange,
        )
    }

    private fun getOrCreateSession(
        documentIdentity: Int,
        configuration: LanguageConfiguration,
        compiled: CompiledLanguageConfiguration,
        lineCount: Int,
    ): LanguageAnalysisSession {
        val current = session
        if (
            current != null &&
            current.documentIdentity == documentIdentity &&
            current.configuration == configuration &&
            current.lineResults.size == lineCount
        ) {
            return current
        }
        return LanguageAnalysisSession(
            documentIdentity = documentIdentity,
            configuration = configuration,
            lineResults = MutableList(lineCount) { null },
        ).also {
            it.invalidatedStartLine = 0
            session = it
        }
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
            regex = compileLanguageRegex(resolvedPattern),
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

private data class LanguageAnalysisSession(
    val documentIdentity: Int,
    val configuration: LanguageConfiguration,
    val lineResults: MutableList<CachedLineAnalysis?>,
) {
    var invalidatedStartLine: Int? = 0

    fun markInvalidated(editResult: TextEditResult) {
        if (!editResult.changed) {
            return
        }
        val earliestChangedLine = editResult.changes.minOfOrNull { it.range.start.line } ?: return
        invalidatedStartLine = invalidatedStartLine
            ?.let { minOf(it, earliestChangedLine) }
            ?: earliestChangedLine
    }

    fun tryReuseCachedRange(
        document: EditorDocument,
        startLine: Int,
        endLine: Int,
        requestedRange: IntRange,
        initialState: String,
        syntaxSpans: MutableMap<Int, List<StyleSpan>>,
    ): CacheReuseResult {
        var line = startLine
        var currentState = initialState
        while (line <= endLine) {
            val cached = lineResults.getOrNull(line) ?: break
            val lineText = document.getLineText(line)
            val textHash = lineText.hashCode()
            if (cached.textHash != textHash || cached.inputState != currentState) {
                break
            }
            currentState = cached.outputState
            if (line in requestedRange) {
                if (cached.spans.isNotEmpty()) {
                    syntaxSpans[line] = cached.spans
                } else {
                    syntaxSpans.remove(line)
                }
            }
            line++
        }
        return CacheReuseResult(
            nextLine = line,
            finalState = currentState,
        )
    }
}

private data class CachedLineAnalysis(
    val inputState: String,
    val outputState: String,
    val textHash: Int,
    val spans: List<StyleSpan>,
)

private data class CacheReuseResult(
    val nextLine: Int,
    val finalState: String,
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
    val effectiveInitialState = initialState.ifBlank { compiled.defaultStateName }
    val endState = tokenizeRange(
        lineText = lineText,
        start = 0,
        endExclusive = lineText.length,
        initialState = effectiveInitialState,
        compiled = compiled,
        output = spans,
        offset = 0,
    )
    return LineTokenizeResult(
        spans = spans.sortedWith(compareBy(StyleSpan::column, StyleSpan::length)),
        endState = compiled.states[endState]?.lineEndState ?: endState,
    )
}

private fun tokenizeRange(
    lineText: String,
    start: Int,
    endExclusive: Int,
    initialState: String,
    compiled: CompiledLanguageConfiguration,
    output: MutableList<StyleSpan>,
    offset: Int,
): String {
    var currentState = initialState
    var cursor = start
    while (cursor < endExclusive) {
        val state = compiled.states[currentState] ?: compiled.states[compiled.defaultStateName] ?: break
        val matchResult = matchRule(state, lineText, cursor, endExclusive)
        if (matchResult == null) {
            cursor++
            continue
        }
        val matchLength = (matchResult.match.range.last - matchResult.match.range.first + 1).coerceAtLeast(0)
        appendStyles(matchResult.rule, matchResult.match, lineText, offset, output)
        matchResult.rule.subStates.forEach { (group, nextState) ->
            val groupRange = findGroupRange(matchResult.match, lineText, group) ?: return@forEach
            tokenizeRange(
                lineText = lineText,
                start = groupRange.first,
                endExclusive = groupRange.last + 1,
                initialState = nextState,
                compiled = compiled,
                output = output,
                offset = offset,
            )
        }
        if (matchResult.rule.nextState != null) {
            currentState = matchResult.rule.nextState
        }
        cursor += if (matchLength == 0) 1 else matchLength
    }
    return currentState
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
    endExclusive: Int,
): RuleMatch? {
    state.tokenRules.forEach { rule ->
        val match = rule.regex.find(lineText, cursor) ?: return@forEach
        if (match.range.first == cursor && match.range.last < endExclusive) {
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
    lineText: String,
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
        val groupRange = findGroupRange(match, lineText, group) ?: return@forEach
        output += StyleSpan(
            column = groupRange.first + offset,
            length = groupRange.last - groupRange.first + 1,
            styleId = styleId,
        )
    }
}

private fun findGroupRange(
    match: MatchResult,
    lineText: String,
    groupIndex: Int,
): IntRange? {
    val groupValue = match.groups[groupIndex]?.value ?: return null
    if (groupValue.isEmpty()) {
        return null
    }
    val start = lineText.indexOf(
        string = groupValue,
        startIndex = match.range.first,
    )
    if (start < match.range.first) {
        return null
    }
    val end = start + groupValue.length - 1
    if (end > match.range.last) {
        return null
    }
    return start..end
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

private fun compileLanguageRegex(pattern: String): Regex = try {
    Regex(pattern)
} catch (error: IllegalArgumentException) {
    val normalizedPattern = normalizeRegexUnicodeProperties(pattern)
    if (normalizedPattern == pattern) {
        throw error
    }
    Regex(normalizedPattern)
}

private fun normalizeRegexUnicodeProperties(pattern: String): String {
    val propertyPattern = Regex("""\\([pP])\{([A-Za-z][A-Za-z0-9_-]*)\}""")
    return propertyPattern.replace(pattern) { match ->
        val propertyType = match.groupValues[1]
        val propertyName = match.groupValues[2]
        val normalizedName = when {
            propertyName.length <= 2 -> propertyName
            propertyName.startsWith("Is") -> propertyName
            propertyName.startsWith("In") -> propertyName
            propertyName.startsWith("java") -> propertyName
            propertyName in regexUnicodePropertyAliasesToKeep -> propertyName
            else -> "Is$propertyName"
        }
        "\\$propertyType{$normalizedName}"
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
private const val STATE_LOOKBACK_LINES = 128

private val regexUnicodePropertyAliasesToKeep = setOf(
    "ASCII",
    "Alpha",
    "Alnum",
    "Digit",
    "Lower",
    "Upper",
    "Space",
    "Punct",
    "Graph",
    "Print",
    "Blank",
    "Cntrl",
    "XDigit",
)
