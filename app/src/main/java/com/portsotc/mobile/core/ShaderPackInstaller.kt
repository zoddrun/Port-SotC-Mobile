package com.portsotc.mobile.core

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Installs the bundled HD shader pack into an application-private directory.
 *
 * The manifest is always read from assets. Files are staged and hashed before
 * anything visible is replaced; the published directory and state file are
 * swapped in last, so a failed install leaves the previous copy usable.
 */
class ShaderPackInstaller(
    private val openAsset: (String) -> InputStream,
    private val targetRoot: File,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {

    val manifestAsset: String = SHADER_PACK_MANIFEST_ASSET
    val stateFile: File = File(targetRoot, "state.json")
    val installedDirectory: File = File(targetRoot, "pack")

    /** Reads and validates the manifest asset. */
    fun readManifest(): HdShaderPack {
        val text = try {
            openAsset(manifestAsset).use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        } catch (exception: Exception) {
            throw ShaderPackInstallerException(
                "Unable to read shader pack manifest '$manifestAsset': ${describe(exception)}"
            )
        }
        return try {
            HdShaderPackJson.parseManifest(text)
        } catch (exception: Exception) {
            throw ShaderPackInstallerException(
                "Invalid shader pack manifest '$manifestAsset': ${describe(exception)}"
            )
        }
    }

    /** Returns null when the state is missing, corrupt or bound to another pack version. */
    fun loadState(): HdInstallState? {
        if (!stateFile.isFile) return null
        return try {
            val manifest = readManifest()
            val state = HdShaderPackJson.parseState(stateFile.readText(Charsets.UTF_8))
            if (state.packId != manifest.packId || state.version != manifest.version) null else state
        } catch (exception: Exception) {
            null
        }
    }

    /** True only when every required file is present with the recorded digest. */
    fun isInstalled(): Boolean {
        val state = loadState() ?: return false
        val manifest = try {
            readManifest()
        } catch (exception: Exception) {
            return false
        }
        for (name in requiredFileNames(manifest)) {
            val digest = state.digestFor(name) ?: return false
            val file = File(installedDirectory, name)
            if (!file.isFile || sha256(file) != digest) return false
        }
        return true
    }

    /** Installs the pack if needed, replacing any incomplete installation. */
    fun ensureInstalled(): HdInstallState {
        if (isInstalled()) return requireNotNull(loadState())

        val manifest = readManifest()
        val required = requiredFileNames(manifest)
        if (!targetRoot.isDirectory && !targetRoot.mkdirs()) {
            throw ShaderPackInstallerException(
                "Unable to create shader pack directory ${targetRoot.absolutePath}"
            )
        }

        val stage = File(targetRoot, "pack.stage.${UUID.randomUUID()}")
        val backup = File(targetRoot, "pack.backup.${UUID.randomUUID()}")
        val statePart = File(targetRoot, "state.json.${UUID.randomUUID()}.part")
        var backupCreated = false
        var published = false

        try {
            if (!stage.mkdirs()) throw IOException("Unable to create staging directory ${stage.name}")

            val digests = LinkedHashMap<String, String>()
            for (name in required) {
                val finalFile = File(stage, name)
                val part = File(stage, "$name.part")
                try {
                    val streamedDigest = copyAssetWithDigest("shaders/$name", part)
                    if (!part.renameTo(finalFile)) throw IOException("Unable to finalize shader pack file '$name'")
                    val writtenDigest = sha256(finalFile)
                    if (writtenDigest != streamedDigest) {
                        throw IOException("Digest mismatch for shader pack file '$name'")
                    }
                    digests[name] = writtenDigest
                } finally {
                    part.delete()
                }
            }
            for (name in required) {
                if (!File(stage, name).isFile) {
                    throw IOException("Shader pack file '$name' is missing after extraction")
                }
            }

            if (installedDirectory.exists()) {
                if (!installedDirectory.renameTo(backup)) {
                    throw IOException("Unable to move the previous shader pack aside")
                }
                backupCreated = true
            }
            if (!stage.renameTo(installedDirectory)) {
                throw IOException("Unable to publish the shader pack directory")
            }
            published = true

            val state = HdInstallState(
                packId = manifest.packId,
                version = manifest.version,
                activePreset = manifest.defaultPresetId,
                installedAtMillis = nowMillis(),
                files = digests
            )
            writeState(state, statePart)

            if (backupCreated) {
                backup.deleteRecursively()
                backupCreated = false
            }
            return state
        } catch (exception: ShaderPackInstallerException) {
            val restored = rollback(published, backupCreated, backup)
            if (restored) throw exception
            throw ShaderPackInstallerException("${exception.message}. The previous shader pack could not be restored")
        } catch (exception: Exception) {
            val restored = rollback(published, backupCreated, backup)
            val suffix = if (restored) "" else ". The previous shader pack could not be restored"
            throw ShaderPackInstallerException("Unable to install shader pack: ${describe(exception)}$suffix")
        } finally {
            statePart.delete()
            if (stage.exists()) stage.deleteRecursively()
        }
    }

    /** Changes only the active preset, keeping all recorded file digests. */
    fun selectPreset(presetId: String): HdInstallState {
        val manifest = readManifest()
        if (!manifest.presets.containsKey(presetId)) {
            throw ShaderPackInstallerException("Unknown shader preset '$presetId'")
        }
        val state = loadState() ?: throw ShaderPackInstallerException("The shader pack is not installed")
        val updated = state.copy(activePreset = presetId)
        val part = File(targetRoot, "state.json.${UUID.randomUUID()}.part")
        try {
            writeState(updated, part)
        } catch (exception: Exception) {
            throw ShaderPackInstallerException("Unable to select shader preset '$presetId': ${describe(exception)}")
        } finally {
            part.delete()
        }
        return updated
    }

    /**
     * Produces locale-independent string values ready for the native side.
     * [overrides] are layered over the named preset, allowing the quick settings
     * drawer to tune one parameter without losing the validated pack defaults.
     */
    fun materializePreset(
        presetId: String,
        overrides: HdShaderOverrides = HdShaderOverrides()
    ): Map<String, String> {
        val manifest = readManifest()
        val preset = manifest.presets[presetId]
            ?: throw ShaderPackInstallerException("Unknown shader preset '$presetId'")
        val safe = overrides.sanitized()

        val values = LinkedHashMap<String, String>()
        values["backend"] = "es2"
        values["preset"] = preset.id + if (safe.isEmpty) "" else ":custom"
        values["resolutionScale"] = formatJsonNumber(safe.resolutionScale ?: preset.resolutionScale)
        values["rcasSharpness"] = formatJsonNumber(safe.rcasSharpness ?: preset.rcasSharpness)
        values["anisotropyLevel"] = (safe.anisotropyLevel ?: preset.anisotropyLevel).toString()
        values["bloomIntensity"] = formatJsonNumber(safe.bloomIntensity ?: preset.bloomIntensity)
        values["fxaaEnabled"] = if (safe.fxaaEnabled ?: preset.fxaaEnabled) "1" else "0"
        values["widescreen16x9"] = if (safe.widescreen16x9 ?: preset.widescreen16x9) "1" else "0"

        if (isInstalled()) {
            for ((slot, name) in manifest.shaders.toSortedMap()) {
                val file = File(installedDirectory, name)
                if (file.isFile) values[slot] = file.absolutePath
            }
        }
        return values
    }

    private fun requiredFileNames(manifest: HdShaderPack): List<String> =
        (manifest.shaders.values + SHADER_PACK_LICENSE).distinct().sorted()

    private fun copyAssetWithDigest(assetName: String, part: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        openAsset(assetName).use { input ->
            FileOutputStream(part).use { output ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                }
                output.fd.sync()
            }
        }
        return toLowerHex(digest.digest())
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                digest.update(buffer, 0, read)
            }
        }
        return toLowerHex(digest.digest())
    }

    private fun toLowerHex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            out.append(HEX_DIGITS[value ushr 4])
            out.append(HEX_DIGITS[value and 0x0F])
        }
        return out.toString()
    }

    private fun writeState(state: HdInstallState, part: File) {
        try {
            FileOutputStream(part).use { output ->
                output.write(HdShaderPackJson.serializeState(state).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            if (!part.renameTo(stateFile)) {
                if (stateFile.exists() && stateFile.delete() && part.renameTo(stateFile)) return
                throw IOException("Unable to publish ${stateFile.name}")
            }
        } finally {
            part.delete()
        }
    }

    private fun rollback(published: Boolean, backupCreated: Boolean, backup: File): Boolean {
        if (published) installedDirectory.deleteRecursively()
        if (backupCreated) return backup.renameTo(installedDirectory)
        return true
    }

    private fun describe(exception: Exception): String =
        exception.message?.takeIf { it.isNotBlank() } ?: exception.javaClass.simpleName

    private companion object {
        const val BUFFER_SIZE = 16 * 1024
        val HEX_DIGITS = "0123456789abcdef".toCharArray()
    }
}
