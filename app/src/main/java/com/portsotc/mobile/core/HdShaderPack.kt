package com.portsotc.mobile.core

import java.math.BigDecimal
import java.util.Locale

/** One selectable quality preset from the HD shader pack manifest. */
data class HdPreset(
    val id: String,
    val label: String,
    val resolutionScale: Double,
    val rcasSharpness: Double,
    val anisotropyLevel: Int,
    val bloomIntensity: Double,
    val fxaaEnabled: Boolean,
    val widescreen16x9: Boolean
)

/** The parsed contents of the HD shader pack manifest asset. */
data class HdShaderPack(
    val packId: String,
    val packName: String,
    val version: String,
    val presets: Map<String, HdPreset>,
    val shaders: Map<String, String>
) {
    val defaultPresetId: String get() = "balanced"
}

/**
 * Optional per-parameter values layered on top of a named preset.
 *
 * Every field is nullable: `null` means "use the preset value", so an unset
 * override never changes the shipped pack defaults. The quick settings drawer
 * persists these values and the installer re-materializes them on every change.
 */
data class HdShaderOverrides(
    val resolutionScale: Double? = null,
    val rcasSharpness: Double? = null,
    val anisotropyLevel: Int? = null,
    val bloomIntensity: Double? = null,
    val fxaaEnabled: Boolean? = null,
    val widescreen16x9: Boolean? = null
) {
    val isEmpty: Boolean
        get() = resolutionScale == null && rcasSharpness == null && anisotropyLevel == null &&
            bloomIntensity == null && fxaaEnabled == null && widescreen16x9 == null

    /** Returns a copy where out-of-range values are clamped to the pack contract. */
    fun sanitized(): HdShaderOverrides = copy(
        resolutionScale = resolutionScale?.coerceIn(0.25, 2.0),
        rcasSharpness = rcasSharpness?.coerceIn(0.0, 0.65),
        anisotropyLevel = anisotropyLevel?.coerceIn(1, 16),
        bloomIntensity = bloomIntensity?.coerceIn(0.0, 0.45),
        widescreen16x9 = widescreen16x9
    )
}

/** Persisted record of an installed shader pack. */
data class HdInstallState(
    val packId: String,
    val version: String,
    val activePreset: String,
    val installedAtMillis: Long,
    val files: Map<String, String>
) {
    fun digestFor(name: String): String? = files[name]
}

/** Reports a recoverable shader pack installation or selection failure. */
class ShaderPackInstallerException(message: String) : Exception(message)

internal const val SHADER_PACK_MANIFEST_ASSET = "shaders/sotc_hd_pack.json"
internal const val SHADER_PACK_LICENSE = "LICENSE"

internal val KNOWN_SHADER_SLOTS = listOf("es3Vertex", "es3Fragment", "es2Vertex", "es2Fragment")

/**
 * Formats a preset number with a decimal point and Locale.US digits, so the
 * values handed to native code never depend on the device locale.
 */
internal fun formatJsonNumber(value: Double): String {
    require(!value.isNaN() && !value.isInfinite()) { "Preset numbers must be finite" }
    val fixed = String.format(Locale.US, "%.10f", value)
    val trimmed = fixed.trimEnd('0').trimEnd('.')
    return when {
        trimmed.isEmpty() || trimmed == "-0" -> "0.0"
        trimmed.contains('.') -> trimmed
        else -> "$trimmed.0"
    }
}

/**
 * Minimal JSON reader/writer for the small, fixed object shapes used by the
 * shader pack manifest and state file. It has no third-party dependencies and
 * deliberately supports only the JSON value grammar needed by those shapes.
 * Malformed input, duplicate object keys, wrong value types and values outside
 * the documented preset ranges are always rejected.
 */
object HdShaderPackJson {

    fun parseManifest(text: String): HdShaderPack {
        val root = parseObjectDocument(text, "manifest")
        val packId = root.requireString("packId", "manifest")
        val packName = root.requireString("packName", "manifest")
        val version = root.requireString("version", "manifest")

        val presetObjects = root.requireObject("presets", "manifest")
        if (presetObjects.isEmpty()) invalid("manifest: presets must not be empty")
        val presets = LinkedHashMap<String, HdPreset>()
        for ((id, value) in presetObjects) {
            if (id.isEmpty()) invalid("manifest: preset id must not be empty")
            val path = "manifest.presets.$id"
            val preset = value.asObject(path)
            val label = preset.requireString("label", path)
            val resolutionScale = preset.requireNumber("resolutionScale", path)
                .rangedDouble(0.25, 2.0, "$path.resolutionScale")
            val rcasSharpness = preset.requireNumber("rcasSharpness", path)
                .rangedDouble(0.0, 1.0, "$path.rcasSharpness")
            val anisotropyLevel = preset.requireNumber("anisotropyLevel", path)
                .integral(1, 16, "$path.anisotropyLevel")
            val bloomIntensity = preset.requireNumber("bloomIntensity", path)
                .rangedDouble(0.0, 1.0, "$path.bloomIntensity")
            val fxaaEnabled = preset.requireBoolean("fxaaEnabled", path)
            val widescreen = preset.requireBoolean("widescreen16x9", path)
            presets[id] = HdPreset(
                id = id,
                label = label,
                resolutionScale = resolutionScale,
                rcasSharpness = rcasSharpness,
                anisotropyLevel = anisotropyLevel,
                bloomIntensity = bloomIntensity,
                fxaaEnabled = fxaaEnabled,
                widescreen16x9 = widescreen
            )
        }
        if (!presets.containsKey("balanced")) {
            invalid("manifest: missing required preset 'balanced'")
        }

        val shaderObjects = root.requireObject("shaders", "manifest")
        val shaders = LinkedHashMap<String, String>()
        for ((slot, value) in shaderObjects) {
            if (slot !in KNOWN_SHADER_SLOTS) invalid("manifest: unknown shader slot '$slot'")
            val name = value.asString("manifest.shaders.$slot")
            validateAssetFileName(name, "manifest.shaders.$slot")
            if (shaders.containsValue(name)) {
                invalid("manifest: shader file '$name' is used by more than one slot")
            }
            shaders[slot] = name
        }
        for (slot in KNOWN_SHADER_SLOTS) {
            if (!shaders.containsKey(slot)) invalid("manifest: missing shader slot '$slot'")
        }

        return HdShaderPack(packId, packName, version, presets, shaders)
    }

    fun parseState(text: String): HdInstallState {
        val root = parseObjectDocument(text, "state")
        val packId = root.requireString("packId", "state")
        val version = root.requireString("version", "state")
        val activePreset = root.requireString("activePreset", "state")
        val installedAtMillis = root.requireNumber("installedAtMillis", "state")
            .integralLong("state.installedAtMillis")
        val fileObjects = root.requireObject("files", "state")
        val files = LinkedHashMap<String, String>()
        for ((name, value) in fileObjects) {
            files[name] = value.asString("state.files.$name")
        }
        return HdInstallState(packId, version, activePreset, installedAtMillis, files)
    }

    /** Serializes with a fixed field order and sorted file names. */
    fun serializeState(state: HdInstallState): String {
        val out = StringBuilder(256)
        out.append('{')
        appendStringField(out, "packId", state.packId, first = true)
        appendStringField(out, "version", state.version, first = false)
        appendStringField(out, "activePreset", state.activePreset, first = false)
        out.append(',')
        appendQuoted(out, "installedAtMillis")
        out.append(':').append(state.installedAtMillis)
        out.append(",\"files\":{")
        var firstFile = true
        for ((name, digest) in state.files.toSortedMap()) {
            if (!firstFile) out.append(',')
            firstFile = false
            appendQuoted(out, name)
            out.append(':')
            appendQuoted(out, digest)
        }
        out.append("}}")
        return out.toString()
    }

    private fun parseObjectDocument(text: String, context: String): Map<String, Any?> {
        val value = JsonParser(text, context).parseDocument()
        val map = value as? Map<*, *> ?: invalid("$context: expected a JSON object")
        if (map.keys.any { key -> key !is String }) {
            invalid("$context: expected string object keys")
        }
        @Suppress("UNCHECKED_CAST")
        return map as Map<String, Any?>
    }

    private fun appendStringField(out: StringBuilder, key: String, value: String, first: Boolean) {
        if (!first) out.append(',')
        appendQuoted(out, key)
        out.append(':')
        appendQuoted(out, value)
    }

    private fun appendQuoted(out: StringBuilder, value: String) {
        out.append('"')
        for (character in value) {
            when (character) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> {
                    val code = character.code
                    if (code < 0x20) {
                        val hex = code.toString(16)
                        out.append("\\u")
                        repeat(4 - hex.length) { out.append('0') }
                        out.append(hex)
                    } else {
                        out.append(character)
                    }
                }
            }
        }
        out.append('"')
    }

    private fun validateAssetFileName(name: String, path: String) {
        if (name.isEmpty() || name == "." || name == ".." ||
            name.contains('/') || name.contains('\\') || name.contains(':') ||
            name.endsWith(".part") || name == SHADER_PACK_LICENSE ||
            name.any { it.code < 0x20 }
        ) {
            invalid("$path is not a safe asset file name")
        }
    }

    private fun Map<String, Any?>.requireValue(key: String, context: String): Any? {
        if (!containsKey(key)) invalid("$context: missing required key '$key'")
        return get(key)
    }

    private fun Map<String, Any?>.requireString(key: String, context: String): String =
        requireValue(key, context).asString("$context.$key")

    private fun Map<String, Any?>.requireObject(key: String, context: String): Map<String, Any?> =
        requireValue(key, context).asObject("$context.$key")

    private fun Map<String, Any?>.requireNumber(key: String, context: String): JsonNumber =
        requireValue(key, context).asNumber("$context.$key")

    private fun Map<String, Any?>.requireBoolean(key: String, context: String): Boolean =
        requireValue(key, context).asBoolean("$context.$key")

    private fun Any?.asString(path: String): String = this as? String ?: invalid("$path must be a string")

    private fun Any?.asObject(path: String): Map<String, Any?> {
        val map = this as? Map<*, *> ?: invalid("$path must be an object")
        if (map.keys.any { it !is String }) invalid("$path has a non-string key")
        @Suppress("UNCHECKED_CAST")
        return map as Map<String, Any?>
    }

    private fun Any?.asNumber(path: String): JsonNumber = this as? JsonNumber ?: invalid("$path must be a number")

    private fun Any?.asBoolean(path: String): Boolean = this as? Boolean ?: invalid("$path must be a boolean")

    private fun JsonNumber.rangedDouble(min: Double, max: Double, path: String): Double {
        val value = decimal.toDouble()
        if (value.isNaN() || value.isInfinite() || value < min || value > max) {
            invalid("$path must be between $min and $max")
        }
        return value
    }

    private fun JsonNumber.integral(min: Int, max: Int, path: String): Int {
        if (decimal.stripTrailingZeros().scale() > 0) {
            invalid("$path must be a whole number between $min and $max")
        }
        if (decimal.compareTo(BigDecimal.valueOf(min.toLong())) < 0 ||
            decimal.compareTo(BigDecimal.valueOf(max.toLong())) > 0
        ) {
            invalid("$path must be between $min and $max")
        }
        return decimal.toInt()
    }

    private fun JsonNumber.integralLong(path: String): Long {
        if (decimal.stripTrailingZeros().scale() > 0) invalid("$path must be a whole number")
        if (decimal.compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) < 0 ||
            decimal.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0
        ) {
            invalid("$path must be a 64-bit whole number")
        }
        return decimal.toLong()
    }

    private fun invalid(message: String): Nothing = throw IllegalArgumentException(message)

    /** A JSON number kept in decimal form so integral checks are exact. */
    private class JsonNumber(raw: String) {
        val decimal: BigDecimal = try {
            BigDecimal(raw)
        } catch (numberFormat: NumberFormatException) {
            throw IllegalArgumentException("Invalid JSON number '$raw'")
        }
    }

    /** Recursive-descent reader for objects, arrays, strings, numbers and literals. */
    private class JsonParser(private val text: String, private val context: String) {
        private var position = 0

        fun parseDocument(): Any? {
            skipWhitespace()
            val value = parseValue(0)
            skipWhitespace()
            if (position != text.length) syntax("unexpected trailing content")
            return value
        }

        private fun parseValue(depth: Int): Any? {
            if (depth > MAX_DEPTH) syntax("JSON nesting is too deep")
            skipWhitespace()
            val current = peek() ?: syntax("unexpected end of JSON input")
            return when (current) {
                '{' -> parseObject(depth)
                '[' -> parseArray(depth)
                '"' -> parseString()
                't' -> expectLiteral("true", true)
                'f' -> expectLiteral("false", false)
                'n' -> expectLiteral("null", null)
                '-' -> parseNumber()
                else -> if (isDigit(current)) parseNumber() else syntax("unsupported JSON value '$current'")
            }
        }

        private fun parseObject(depth: Int): Map<String, Any?> {
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                position++
                return result
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') syntax("expected a quoted object key")
                val key = parseString()
                if (result.containsKey(key)) syntax("duplicate object key '$key'")
                skipWhitespace()
                expect(':')
                result[key] = parseValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    '}' -> {
                        position++
                        return result
                    }
                    else -> syntax("expected ',' or '}' in object")
                }
            }
        }

        private fun parseArray(depth: Int): List<Any?> {
            expect('[')
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                position++
                return result
            }
            while (true) {
                result.add(parseValue(depth + 1))
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    ']' -> {
                        position++
                        return result
                    }
                    else -> syntax("expected ',' or ']' in array")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val result = StringBuilder()
            while (true) {
                val current = peek() ?: syntax("unterminated JSON string")
                position++
                when {
                    current == '"' -> return result.toString()
                    current == '\\' -> {
                        val escape = peek() ?: syntax("unterminated JSON escape sequence")
                        position++
                        when (escape) {
                            '"' -> result.append('"')
                            '\\' -> result.append('\\')
                            '/' -> result.append('/')
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000C')
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            'u' -> {
                                if (position + 4 > text.length) syntax("incomplete unicode escape")
                                val hex = text.substring(position, position + 4)
                                val code = hex.toIntOrNull(16) ?: syntax("invalid unicode escape '$hex'")
                                position += 4
                                result.append(code.toChar())
                            }
                            else -> syntax("unsupported JSON escape '\\$escape'")
                        }
                    }
                    current.code < 0x20 -> syntax("unescaped control character in JSON string")
                    else -> result.append(current)
                }
            }
        }

        private fun parseNumber(): JsonNumber {
            val start = position
            if (peek() == '-') position++
            when (val first = peek()) {
                '0' -> position++
                else -> if (isDigit(first) && first != '0') {
                    while (isDigit(peek())) position++
                } else {
                    syntax("invalid JSON number")
                }
            }
            if (peek() == '.') {
                position++
                if (!isDigit(peek())) syntax("invalid JSON number: expected a digit after '.'")
                while (isDigit(peek())) position++
            }
            if (peek() == 'e' || peek() == 'E') {
                position++
                if (peek() == '+' || peek() == '-') position++
                if (!isDigit(peek())) syntax("invalid JSON number: expected an exponent digit")
                while (isDigit(peek())) position++
            }
            return JsonNumber(text.substring(start, position))
        }

        private fun <T> expectLiteral(literal: String, value: T): T {
            if (!text.startsWith(literal, position)) syntax("invalid JSON literal")
            position += literal.length
            return value
        }

        private fun skipWhitespace() {
            while (position < text.length) {
                val current = text[position]
                if (current != ' ' && current != '\t' && current != '\n' && current != '\r') break
                position++
            }
        }

        private fun expect(expected: Char) {
            if (peek() != expected) syntax("expected '$expected'")
            position++
        }

        private fun isDigit(character: Char?): Boolean = character != null && character in '0'..'9'

        private fun peek(): Char? = if (position < text.length) text[position] else null

        private fun syntax(message: String): Nothing =
            throw IllegalArgumentException("$context: $message at character $position")

        private companion object {
            const val MAX_DEPTH = 32
        }
    }
}
