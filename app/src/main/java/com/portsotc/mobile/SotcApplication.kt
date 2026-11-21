package com.portsotc.mobile

import android.app.Application
import android.util.Log
import com.portsotc.mobile.core.AppPaths
import com.portsotc.mobile.core.GameLibrary
import com.portsotc.mobile.core.HdPresetId
import com.portsotc.mobile.core.HdShaderOverrides
import com.portsotc.mobile.core.InspectionRecordStore
import com.portsotc.mobile.core.LocalAchievementStore
import com.portsotc.mobile.core.MemoryCardVault
import com.portsotc.mobile.core.NativeDiagnosticsCache
import com.portsotc.mobile.core.SettingsStore
import com.portsotc.mobile.core.ShaderPackInstaller
import com.portsotc.mobile.input.TouchLayoutStore
import com.portsotc.mobile.nativebridge.NativeCore
import java.io.File

class SotcApplication : Application() {

    companion object {
        private const val TAG = "SotcApplication"
        lateinit var instance: SotcApplication
            private set
    }

    lateinit var paths: AppPaths
        private set
    lateinit var library: GameLibrary
        private set
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var touchLayoutStore: TouchLayoutStore
        private set
    lateinit var shaderPackInstaller: ShaderPackInstaller
        private set
    lateinit var inspectionStore: InspectionRecordStore
        private set
    lateinit var localAchievementStore: LocalAchievementStore
        private set
    lateinit var vault: MemoryCardVault
        private set
    lateinit var nativeDiagnosticsCache: NativeDiagnosticsCache
        private set

    @Volatile
    var shaderPackReady: Boolean = false
        private set

    @Volatile
    var shaderPackDetail: String = "not installed"
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        paths = AppPaths(this)
        paths.ensureDirectories()
        library = GameLibrary(paths)
        library.recoverInterruptedImports()
        settingsStore = SettingsStore(this)
        touchLayoutStore = TouchLayoutStore(paths.touchLayoutFile)
        shaderPackInstaller = ShaderPackInstaller(assets::open, paths.shaderPackRoot)
        inspectionStore = InspectionRecordStore(this)
        localAchievementStore = LocalAchievementStore(this)
        vault = MemoryCardVault(File(paths.filesRoot, "vault"))
        nativeDiagnosticsCache = NativeDiagnosticsCache(File(paths.filesRoot, "diagnostics"))
        refreshHdShaderPack()
    }

    /**
     * Verifies the installed pack and publishes the selected preset to the
     * native diagnostics engine. A broken pack never blocks the launcher: the
     * last honest status is kept and the default render path stays in use.
     */
    fun refreshHdShaderPack() {
        try {
            val state = shaderPackInstaller.ensureInstalled()
            val settings = settingsStore.load()
            val presetId = HdPresetId.fromId(settings.hdShaderPreset).id
            val active = shaderPackInstaller.selectPreset(presetId)
            val values = shaderPackInstaller.materializePreset(presetId, settings.hdShaderOverrides)
            val report = NativeCore.configureHdShaderPack(
                shaderPackInstaller.installedDirectory.absolutePath,
                active.version,
                active.activePreset,
                values.getValue("es3Vertex"),
                values.getValue("es3Fragment"),
                values.getValue("es2Vertex"),
                values.getValue("es2Fragment"),
                values.getValue("rcasSharpness").toFloat(),
                values.getValue("bloomIntensity").toFloat(),
                values.getValue("anisotropyLevel").toInt(),
                values.getValue("fxaaEnabled") == "1",
                values.getValue("widescreen16x9") == "1"
            )
            shaderPackReady = true
            shaderPackDetail = "v${state.version} · $presetId · $report"
            Log.i(TAG, "HD shader pack ready: $shaderPackDetail")
        } catch (error: Throwable) {
            shaderPackReady = false
            shaderPackDetail = error.message ?: error.javaClass.simpleName
            Log.w(TAG, "HD shader pack unavailable: $shaderPackDetail")
        }
    }

    /**
     * Applies a shipped preset and refreshes the native pipeline. Overrides are
     * cleared when switching preset so the named preset is reproduced exactly.
     */
    fun selectHdShaderPreset(presetId: String) {
        val id = HdPresetId.fromId(presetId).id
        settingsStore.edit { it.copy(hdShaderPreset = id, hdShaderOverrides = HdShaderOverrides()) }
        refreshHdShaderPack()
    }

    /** Merges individual parameter overrides into the current preset. */
    fun updateHdShaderOverrides(update: (HdShaderOverrides) -> HdShaderOverrides) {
        settingsStore.edit { settings ->
            settings.copy(hdShaderOverrides = update(settings.hdShaderOverrides).sanitized())
        }
        refreshHdShaderPack()
    }

    fun clearHdShaderOverrides() {
        settingsStore.edit { it.copy(hdShaderOverrides = HdShaderOverrides()) }
        refreshHdShaderPack()
    }

    /** App-private directory reported to the recompiled runtime, if verified. */
    fun installedShaderPackDirectory(): String? =
        installedShaderPackDirectoryOrNull()

    private fun installedShaderPackDirectoryOrNull(): String? {
        if (!shaderPackReady) return null
        return try {
            shaderPackInstaller.installedDirectory.absolutePath.takeIf {
                shaderPackInstaller.isInstalled()
            }
        } catch (_: Throwable) {
            null
        }
    }
}
