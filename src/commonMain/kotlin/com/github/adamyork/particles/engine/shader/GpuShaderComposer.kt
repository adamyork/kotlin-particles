package com.github.adamyork.particles.engine.shader

import com.github.adamyork.particles.engine.EngineException

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public object GpuShaderComposer {

    public const val STYLE_UNIFORM_CAPACITY: Int = 18

    private const val FUNCTIONS_MARKER = "{{STYLE_FUNCTIONS}}"
    private const val HALF_EXTENT_MARKER = "{{STYLE_HALF_EXTENT_HOOKS}}"
    private const val QUAD_MARKER = "{{STYLE_QUAD_HOOKS}}"
    private const val FRAGMENT_MARKER = "{{STYLE_FRAGMENT_DISPATCH}}"
    private val STYLE_UNIFORM_PATTERN = Regex("STYLE_UNIFORM\\((\\d+)\\)")
    private val STYLE_NAME_PATTERN = Regex("\\bSTYLE_")
    private val STYLE_ID_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*")
    private val WGSL_DECLARATION_PATTERN = Regex("^(?:fn|const|struct|override|alias|var(?:<[^>]*>)?)\\s+(\\w+)", RegexOption.MULTILINE)
    private val GLSL_DECLARATION_PATTERN = Regex(
        "^(?:const\\s+)?(?:float|int|uint|bool|vec[234]|ivec[234]|uvec[234]|mat[234]|void|Style\\w+)\\s+(\\w+)\\s*[(=\\[;]",
        RegexOption.MULTILINE
    )
    private val GLSL_STRUCT_PATTERN = Regex("^struct\\s+(\\w+)", RegexOption.MULTILINE)
    private val REPORT_LINE_PATTERNS = listOf(
        Regex("line (\\d+)"),
        Regex("(?:ERROR|WARNING):\\s*\\d+:(\\d+)")
    )

    public fun composeWgsl(baseSource: String, styleShaders: List<GpuStyleShader>): String {
        validate(styleShaders)
        val offsets = uniformOffsets(styleShaders)
        val functions = StringBuilder()
        val halfExtentHooks = StringBuilder()
        val quadHooks = StringBuilder()
        val fragmentDispatch = StringBuilder()
        for ((styleIndex, styleShader) in styleShaders.withIndex()) {
            val prefix = prefixOf(styleShader)
            val code = styleShader.renderStyle.code.toString()
            val source = bind(styleShader.wgslSource, styleShader, offsets[styleIndex], true)
            functions.append(source).append("\n")
            if (source.contains(prefix + "halfExtent(")) {
                halfExtentHooks.append("  if (activeStyleCode == $code) { halfExtent = ${prefix}halfExtent(vertexInput, halfExtent); }\n")
            }
            if (source.contains(prefix + "quad(")) {
                quadHooks.append("  if (activeStyleCode == $code) { quad = ${prefix}quad(vertexInput, halfExtent, quad); }\n")
            }
            fragmentDispatch.append("  if (activeStyleCode == $code) { return ${prefix}fragment(fragmentInput); }\n")
        }
        return replaceMarkers(baseSource, functions, halfExtentHooks, quadHooks, fragmentDispatch)
    }

    public fun composeGlslVertex(baseSource: String, styleShaders: List<GpuStyleShader>): String {
        validate(styleShaders)
        val offsets = uniformOffsets(styleShaders)
        val functions = StringBuilder()
        val halfExtentHooks = StringBuilder()
        val quadHooks = StringBuilder()
        for ((styleIndex, styleShader) in styleShaders.withIndex()) {
            val prefix = prefixOf(styleShader)
            val code = styleShader.renderStyle.code.toInt()
            val source = bind(styleShader.glslVertexSource, styleShader, offsets[styleIndex], false)
            functions.append(source).append("\n")
            if (source.contains(prefix + "halfExtent(")) {
                halfExtentHooks.append("    if (uActiveStyle == $code) { halfExtent = ${prefix}halfExtent(vertexInput, halfExtent); }\n")
            }
            if (source.contains(prefix + "quad(")) {
                quadHooks.append("    if (uActiveStyle == $code) { quad = ${prefix}quad(vertexInput, halfExtent, quad); }\n")
            }
        }
        return replaceMarkers(baseSource, functions, halfExtentHooks, quadHooks, StringBuilder(), requireFragmentMarker = false)
    }

    public fun composeGlslFragment(baseSource: String, styleShaders: List<GpuStyleShader>): String {
        validate(styleShaders)
        val offsets = uniformOffsets(styleShaders)
        val functions = StringBuilder()
        val fragmentDispatch = StringBuilder()
        for ((styleIndex, styleShader) in styleShaders.withIndex()) {
            val prefix = prefixOf(styleShader)
            val code = styleShader.renderStyle.code.toInt()
            val source = bind(styleShader.glslFragmentSource, styleShader, offsets[styleIndex], false)
            functions.append(source).append("\n")
            fragmentDispatch.append("    if (uActiveStyle == $code) { fragmentColor = ${prefix}fragment(fragmentInput); return; }\n")
        }
        return replaceMarkers(
            baseSource,
            functions,
            StringBuilder(),
            StringBuilder(),
            fragmentDispatch,
            requireHookMarkers = false
        )
    }

    public fun uniformOffsets(styleShaders: List<GpuStyleShader>): List<Int> {
        val offsets = ArrayList<Int>()
        var offset = 0
        for (styleShader in styleShaders) {
            offsets.add(offset)
            offset += styleShader.uniformSource?.floatCount ?: 0
        }
        if (offset > STYLE_UNIFORM_CAPACITY) {
            throw EngineException("Render styles need $offset uniform floats but only $STYLE_UNIFORM_CAPACITY are available")
        }
        return offsets
    }

    public fun locateStyle(composedSource: String, lineNumber: Int, styleShaders: List<GpuStyleShader>): String? {
        val lines = composedSource.lines()
        if (lines.isEmpty()) return null
        var index = (lineNumber - 1).coerceIn(0, lines.lastIndex)
        while (index >= 0) {
            val line = lines[index]
            val startsDeclaration = line.isNotEmpty() && !line[0].isWhitespace() && line[0] !in "})]"
            if (startsDeclaration) {
                return styleShaders.firstOrNull { styleShader -> line.contains(prefixOf(styleShader)) }?.renderStyle?.id
            }
            index--
        }
        return null
    }

    public fun annotateCompilationReport(composedSource: String, styleShaders: List<GpuStyleShader>, report: String): String {
        val annotatedLines = report.lines().map { reportLine ->
            val lineNumber = REPORT_LINE_PATTERNS.firstNotNullOfOrNull { pattern ->
                pattern.find(reportLine)?.groupValues?.get(1)?.toIntOrNull()
            }
            val styleId = if (lineNumber != null) locateStyle(composedSource, lineNumber, styleShaders) else null
            if (styleId != null) "$reportLine [render style $styleId]" else reportLine
        }
        return annotatedLines.joinToString("\n")
    }

    private fun prefixOf(styleShader: GpuStyleShader): String = "style_" + styleShader.renderStyle.id.lowercase() + "_"

    private fun validate(styleShaders: List<GpuStyleShader>) {
        val seenIds = HashSet<String>()
        val seenCodes = HashSet<Float>()
        for (styleShader in styleShaders) {
            val renderStyle = styleShader.renderStyle
            if (!STYLE_ID_PATTERN.matches(renderStyle.id)) {
                throw EngineException("Render style id ${renderStyle.id} is not a valid shader identifier")
            }
            if (!seenIds.add(renderStyle.id)) throw EngineException("Render style ${renderStyle.id} is listed twice")
            if (!seenCodes.add(renderStyle.code)) throw EngineException("Render style code ${renderStyle.code} is used twice")
        }
    }

    private fun requirePrefixedNames(source: String, styleShader: GpuStyleShader, isWgsl: Boolean) {
        val declaredNames = ArrayList<String>()
        if (isWgsl) {
            WGSL_DECLARATION_PATTERN.findAll(source).forEach { match -> declaredNames.add(match.groupValues[1]) }
        } else {
            GLSL_DECLARATION_PATTERN.findAll(source).forEach { match -> declaredNames.add(match.groupValues[1]) }
            GLSL_STRUCT_PATTERN.findAll(source).forEach { match -> declaredNames.add(match.groupValues[1]) }
        }
        for (declaredName in declaredNames) {
            if (!declaredName.startsWith("STYLE_")) {
                throw EngineException(
                    "Style ${styleShader.renderStyle.id} declares the top-level name $declaredName; every top-level name must start with STYLE_"
                )
            }
        }
    }

    private fun bind(source: String, styleShader: GpuStyleShader, uniformOffset: Int, isWgsl: Boolean): String {
        requirePrefixedNames(source, styleShader, isWgsl)
        val uniformCount = styleShader.uniformSource?.floatCount ?: 0
        val withUniforms = STYLE_UNIFORM_PATTERN.replace(source) { match ->
            val uniformIndex = match.groupValues[1].toInt()
            if (uniformIndex >= uniformCount) {
                throw EngineException("Style ${styleShader.renderStyle.id} reads uniform $uniformIndex but declares $uniformCount")
            }
            "renderUniforms.styleUniform" + (uniformOffset + uniformIndex)
        }
        return STYLE_NAME_PATTERN.replace(withUniforms, prefixOf(styleShader))
    }

    private fun replaceMarkers(
        baseSource: String,
        functions: StringBuilder,
        halfExtentHooks: StringBuilder,
        quadHooks: StringBuilder,
        fragmentDispatch: StringBuilder,
        requireHookMarkers: Boolean = true,
        requireFragmentMarker: Boolean = true
    ): String {
        var composed = baseSource
        composed = replaceMarker(composed, FUNCTIONS_MARKER, functions, true)
        composed = replaceMarker(composed, HALF_EXTENT_MARKER, halfExtentHooks, requireHookMarkers)
        composed = replaceMarker(composed, QUAD_MARKER, quadHooks, requireHookMarkers)
        composed = replaceMarker(composed, FRAGMENT_MARKER, fragmentDispatch, requireFragmentMarker)
        return composed
    }

    private fun replaceMarker(source: String, marker: String, replacement: StringBuilder, required: Boolean): String {
        val commentMarker = "/*$marker*/"
        if (!source.contains(commentMarker) && !source.contains(marker)) {
            if (required) throw EngineException("Shader template is missing the $marker marker")
            return source
        }
        val trimmedReplacement = replacement.toString().trimEnd('\n')
        return source.replace(commentMarker, trimmedReplacement).replace(marker, trimmedReplacement)
    }

}
