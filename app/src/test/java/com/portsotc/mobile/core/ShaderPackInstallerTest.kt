package com.portsotc.mobile.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.Locale

class ShaderPackInstallerTest {

    @JvmField
    @Rule
    val temporary = TemporaryFolder()

    private lateinit var root: File
    private val assets = linkedMapOf<String, String>()
    private var nowMillis = 1_700_000_000_000L

    @Before
    fun setUp() {
        root = temporary.newFolder()
        assets.clear()
        assets[SHADER_PACK_MANIFEST_ASSET] = MANIFEST_JSON
        assets["shaders/sotc_hd_es3.vert"] = "// ES 3 vertex shader\n"
        assets["shaders/sotc_hd_es3.frag"] = "// ES 3 fragment shader\n"
        assets["shaders/sotc_hd_es2.vert"] = "// ES 2 vertex shader\n"
        assets["shaders/sotc_hd_es2.frag"] = "// ES 2 fragment shader\n"
        assets["shaders/LICENSE"] = "MIT License\nCopyright (c) 2026\n"
    }

    private fun installer(directory: File = root): ShaderPackInstaller = ShaderPackInstaller(
        openAsset = { name ->
            val content = assets[name] ?: throw FileNotFoundException("Missing asset: $name")
            ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
        },
        targetRoot = directory,
        nowMillis = { nowMillis }
    )

    @Test
    fun installsEveryFileWithMatchingDigestAndRoundTripsState() {
        val installer = installer()

        val state = installer.ensureInstalled()

        assertEquals("sotc-forbidden-lands-hd-v1", state.packId)
        assertEquals("1.0.0", state.version)
        assertEquals("balanced", state.activePreset)
        assertEquals(nowMillis, state.installedAtMillis)
        assertEquals(5, state.files.size)
        for ((name, digest) in state.files) {
            val file = File(installer.installedDirectory, name)
            assertTrue("missing $name", file.isFile)
            assertEquals(64, digest.length)
            assertEquals("wrong digest for $name", digest, sha256(file))
            assertEquals(digest, state.digestFor(name))
        }
        assertTrue(installer.isInstalled())
        assertEquals(state, HdShaderPackJson.parseState(HdShaderPackJson.serializeState(state)))
        assertNull(state.digestFor("not-installed.file"))
        assertEquals(0, root.listFiles()!!.count { it.name.contains(".stage.") || it.name.endsWith(".part") })
    }

    @Test
    fun isInstalledBecomesFalseWhenAShaderIsCorruptedOrRemoved() {
        val installer = installer()
        installer.ensureInstalled()
        assertTrue(installer.isInstalled())

        val shader = File(installer.installedDirectory, "sotc_hd_es2.vert")
        val original = shader.readBytes()
        shader.writeText("corrupted shader")
        assertFalse(installer.isInstalled())
        assertNotNull("a corrupt file must not invalidate a readable state", installer.loadState())

        shader.writeBytes(original)
        assertTrue(installer.isInstalled())

        assertTrue(shader.delete())
        assertFalse(installer.isInstalled())
    }

    @Test
    fun ensureInstalledReplacesCorruptStateAndReextractsFiles() {
        val installer = installer()
        val first = installer.ensureInstalled()
        val shader = File(installer.installedDirectory, "sotc_hd_es3.frag")
        shader.writeText("damaged")
        installer.stateFile.writeText("{ this is not JSON")

        assertNull(installer.loadState())
        assertFalse(installer.isInstalled())

        val repaired = installer.ensureInstalled()

        assertTrue(installer.isInstalled())
        assertEquals(first.copy(activePreset = "balanced"), repaired)
        assertEquals(assets["shaders/sotc_hd_es3.frag"], shader.readText())
        assertEquals(first.files, repaired.files)
        assertEquals(0, root.listFiles()!!.count { it.name.contains(".backup.") || it.name.contains(".stage.") })
    }

    @Test
    fun selectPresetChangesOnlyTheActivePreset() {
        val installer = installer()
        val before = installer.ensureInstalled()

        val after = installer.selectPreset("ultra")

        assertEquals("ultra", after.activePreset)
        assertEquals(before.copy(activePreset = "ultra"), after)
        assertEquals(before.files, after.files)
        assertEquals(before.installedAtMillis, after.installedAtMillis)
        assertEquals(before.packId, after.packId)
        assertEquals(before.version, after.version)
        assertTrue(installer.isInstalled())
        assertEquals(after, installer.loadState())
        assertTrue(installer.stateFile.readText().contains("\"activePreset\":\"ultra\""))
    }

    @Test
    fun unknownPresetsAreRejected() {
        val installer = installer()
        assertThrowsMessage("not installed") { installer.selectPreset("balanced") }
        installer.ensureInstalled()

        val selectError = expectThrows(ShaderPackInstallerException::class.java) {
            installer.selectPreset("does-not-exist")
        }
        assertTrue(selectError.message!!.contains("does-not-exist"))

        val materializeError = expectThrows(ShaderPackInstallerException::class.java) {
            installer.materializePreset("does-not-exist")
        }
        assertTrue(materializeError.message!!.contains("does-not-exist"))
    }

    @Test
    fun missingAssetFailsWithoutDamagingThePreviousInstall() {
        val installer = installer()
        val previousState = installer.ensureInstalled()
        val previousStateText = installer.stateFile.readText()
        val installedShader = File(installer.installedDirectory, "sotc_hd_es2.frag")
        val previousBytes = installedShader.readBytes()

        assets.remove("shaders/sotc_hd_es2.frag")
        installer.stateFile.writeText("corrupt")

        val error = expectThrows(ShaderPackInstallerException::class.java) { installer.ensureInstalled() }
        assertTrue("error must name the asset: ${error.message}", error.message!!.contains("sotc_hd_es2.frag"))
        assertArrayEquals(previousBytes, installedShader.readBytes())
        assertFalse(File(installer.installedDirectory, "sotc_hd_es2.frag.part").exists())
        assertEquals(0, root.listFiles()!!.count { it.name.contains(".stage.") || it.name.endsWith(".part") })

        installer.stateFile.writeText(previousStateText)
        assertTrue(installer.isInstalled())
        assertEquals(previousState, installer.loadState())
    }

    @Test
    fun serializerEscapesQuotesAndBackslashes() {
        val state = HdInstallState(
            packId = "pack\"id",
            version = "v\\1",
            activePreset = "pre\"\\set",
            installedAtMillis = 42L,
            files = mapOf("a\"b\\c" to "de\"ad\\beef")
        )

        val json = HdShaderPackJson.serializeState(state)

        assertTrue(json, json.contains("pack\\\"id"))
        assertTrue(json, json.contains("v\\\\1"))
        assertTrue(json, json.contains("a\\\"b\\\\c"))
        assertEquals(state, HdShaderPackJson.parseState(json))

        val again = HdShaderPackJson.serializeState(state)
        assertEquals("serialization must be deterministic", json, again)
    }

    @Test
    fun manifestValidationAcceptsTheFixtureAndRejectsInvalidPresetValues() {
        val manifest = HdShaderPackJson.parseManifest(MANIFEST_JSON)
        assertEquals("sotc-forbidden-lands-hd-v1", manifest.packId)
        assertEquals(3, manifest.presets.size)
        assertEquals(4, manifest.shaders.size)
        assertEquals("balanced", manifest.defaultPresetId)

        val invalidManifests = listOf(
            manifestWith("\"resolutionScale\": 0.75" to "\"resolutionScale\": 0.1"),
            manifestWith("\"resolutionScale\": 0.75" to "\"resolutionScale\": 2.5"),
            manifestWith("\"rcasSharpness\": 0.15" to "\"rcasSharpness\": 1.5"),
            manifestWith("\"anisotropyLevel\": 1," to "\"anisotropyLevel\": 0,"),
            manifestWith("\"anisotropyLevel\": 1," to "\"anisotropyLevel\": 17,"),
            manifestWith("\"anisotropyLevel\": 1," to "\"anisotropyLevel\": 1.5,"),
            manifestWith("\"bloomIntensity\": 0.0" to "\"bloomIntensity\": -0.1"),
            manifestWith("\"bloomIntensity\": 0.0" to "\"bloomIntensity\": 1.1"),
            manifestWith("\"fxaaEnabled\": false" to "\"fxaaEnabled\": \"false\""),
            MANIFEST_JSON.replace("      \"label\": \"Battery Saver\",\n", "")
        )
        for (text in invalidManifests) {
            expectThrows(IllegalArgumentException::class.java) { HdShaderPackJson.parseManifest(text) }
        }
    }

    @Test
    fun manifestValidationRejectsMalformedDuplicateAndUnsupportedInput() {
        expectThrows(IllegalArgumentException::class.java) { HdShaderPackJson.parseManifest("{") }
        expectThrows(IllegalArgumentException::class.java) { HdShaderPackJson.parseManifest("[]") }
        expectThrows(IllegalArgumentException::class.java) {
            HdShaderPackJson.parseManifest(
                MANIFEST_JSON.replaceFirst("\"packId\"", "\"packId\": \"duplicate\", \"packId\"")
            )
        }
        expectThrows(IllegalArgumentException::class.java) {
            HdShaderPackJson.parseManifest(MANIFEST_JSON.replace("\"es2Vertex\":", "\"es9Vertex\":"))
        }
        expectThrows(IllegalArgumentException::class.java) {
            HdShaderPackJson.parseManifest(
                MANIFEST_JSON.replace(
                    "\"packName\": \"Forbidden Lands HD Shader & Texture Pack\"",
                    "\"packName\": [1, 2]"
                )
            )
        }
    }

    @Test
    fun materializePresetProducesLocaleIndependentJniValuesAndShaderPaths() {
        val installer = installer()

        val beforeInstall = installer.materializePreset("battery")
        assertEquals(8, beforeInstall.size)
        assertEquals("es2", beforeInstall["backend"])
        assertEquals("battery", beforeInstall["preset"])
        assertEquals("0.75", beforeInstall["resolutionScale"])
        assertEquals("0.15", beforeInstall["rcasSharpness"])
        assertEquals("1", beforeInstall["anisotropyLevel"])
        assertEquals("0.0", beforeInstall["bloomIntensity"])
        assertEquals("0", beforeInstall["fxaaEnabled"])
        assertEquals("1", beforeInstall["widescreen16x9"])
        assertFalse(beforeInstall.containsKey("es2Vertex"))

        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            installer.ensureInstalled()
            val values = installer.materializePreset("balanced")
            assertEquals("1.0", values["resolutionScale"])
            assertEquals("0.35", values["rcasSharpness"])
            assertEquals("4", values["anisotropyLevel"])
            assertEquals("0.26", values["bloomIntensity"])
            assertEquals("1", values["fxaaEnabled"])
            for (slot in listOf("es3Vertex", "es3Fragment", "es2Vertex", "es2Fragment")) {
                val path = values[slot] ?: throw AssertionError("missing shader path for $slot")
                assertTrue("$slot must point to an installed file: $path", File(path).isFile)
            }
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun individualOverridesAreClampedAndPushedAsLocaleIndependentValues() {
        val installer = installer()
        installer.ensureInstalled()

        val tuned = installer.materializePreset(
            "balanced",
            HdShaderOverrides(
                resolutionScale = 1.5,
                rcasSharpness = 0.42,
                anisotropyLevel = 32,
                bloomIntensity = 0.9,
                fxaaEnabled = false,
                widescreen16x9 = false
            )
        )

        assertEquals("balanced:custom", tuned["preset"])
        assertEquals("1.5", tuned["resolutionScale"])
        assertEquals("0.42", tuned["rcasSharpness"])
        assertEquals("16", tuned["anisotropyLevel"])
        assertEquals("0.45", tuned["bloomIntensity"])
        assertEquals("0", tuned["fxaaEnabled"])
        assertEquals("0", tuned["widescreen16x9"])

        val defaults = installer.materializePreset("balanced")
        assertEquals("balanced", defaults["preset"])
        assertEquals("1.0", defaults["resolutionScale"])
        assertEquals("0.35", defaults["rcasSharpness"])
        assertEquals("4", defaults["anisotropyLevel"])
        assertEquals("0.26", defaults["bloomIntensity"])
        assertEquals("1", defaults["fxaaEnabled"])
        assertEquals("1", defaults["widescreen16x9"])
        assertTrue(HdShaderOverrides().isEmpty)
        assertFalse(HdShaderOverrides(rcasSharpness = 0.5).isEmpty)
    }

    private fun manifestWith(vararg replacements: Pair<String, String>): String {
        var text = MANIFEST_JSON
        for ((from, to) in replacements) {
            assertTrue("fixture does not contain '$from'", text.contains(from))
            text = text.replace(from, to)
        }
        return text
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(4096)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte ->
            val value = byte.toInt() and 0xFF
            "${HEX[value ushr 4]}${HEX[value and 0x0F]}"
        }
    }

    private fun assertThrowsMessage(fragment: String, block: () -> Unit) {
        val error = expectThrows(ShaderPackInstallerException::class.java, block)
        assertTrue("unexpected message: ${error.message}", error.message!!.contains(fragment))
    }

    private fun <T : Throwable> expectThrows(type: Class<T>, block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (type.isInstance(error)) return type.cast(error)!!
            throw error
        }
        throw AssertionError("Expected ${type.simpleName}")
    }

    private companion object {
        val HEX = "0123456789abcdef".toCharArray()

        // Exact contents of app/src/main/assets/shaders/sotc_hd_pack.json.
        const val MANIFEST_JSON = """{
  "packId": "sotc-forbidden-lands-hd-v1",
  "packName": "Forbidden Lands HD Shader & Texture Pack",
  "version": "1.0.0",
  "presets": {
    "battery": {
      "label": "Battery Saver",
      "resolutionScale": 0.75,
      "rcasSharpness": 0.15,
      "anisotropyLevel": 1,
      "bloomIntensity": 0.0,
      "fxaaEnabled": false,
      "widescreen16x9": true
    },
    "balanced": {
      "label": "Balanced HD",
      "resolutionScale": 1.00,
      "rcasSharpness": 0.35,
      "anisotropyLevel": 4,
      "bloomIntensity": 0.26,
      "fxaaEnabled": true,
      "widescreen16x9": true
    },
    "ultra": {
      "label": "Ultra HD",
      "resolutionScale": 1.50,
      "rcasSharpness": 0.50,
      "anisotropyLevel": 8,
      "bloomIntensity": 0.35,
      "fxaaEnabled": true,
      "widescreen16x9": true
    }
  },
  "shaders": {
    "es3Vertex": "sotc_hd_es3.vert",
    "es3Fragment": "sotc_hd_es3.frag",
    "es2Vertex": "sotc_hd_es2.vert",
    "es2Fragment": "sotc_hd_es2.frag"
  }
}"""
    }
}
