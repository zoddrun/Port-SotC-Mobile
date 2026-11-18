package com.portsotc.mobile.core

import android.content.Context
import android.os.Build
import com.portsotc.mobile.BuildConfig
import java.io.File
import java.util.zip.ZipFile

/**
 * Detects whether a separately built recompiled game core is present.
 *
 * This is a filesystem check only. It never claims that the core is compatible
 * or initialized; the native loader performs the ABI and initialization checks
 * when a game session starts.
 */
object CoreProbe {

    const val CORE_LIBRARY_NAME = "libsotc_game.so"

    /**
     * The gameplay runtime is the recompiled runner packaged as a raylib
     * NativeActivity library (D18). It is a different artifact from the
     * ABI-v1 core below.
     */
    const val RUNTIME_LIBRARY_NAME = "libps2EntryRunner.so"

    fun isInstalled(context: Context): Boolean =
        isNativeLibraryPresent(context, CORE_LIBRARY_NAME)

    fun isRuntimeInstalled(context: Context): Boolean =
        isNativeLibraryPresent(context, RUNTIME_LIBRARY_NAME)

    private fun isNativeLibraryPresent(context: Context, name: String): Boolean {
        // Android 10 and later only allow executable code to be loaded from
        // the read-only native library directory populated from the APK.
        // A library copied into writable game storage cannot be dlopen'ed.
        val nativeLibraryDirectory = context.applicationInfo.nativeLibraryDir
        if (nativeLibraryDirectory != null && File(nativeLibraryDirectory, name).isFile) {
            return true
        }
        // With uncompressed native libraries the APK is the container and the
        // files are not extracted into nativeLibraryDir, so inspect the APK
        // itself for a library matching one of the device's ABIs.
        val apk = context.applicationInfo.sourceDir?.let(::File) ?: return false
        return containsNativeLibrary(apk, name, Build.SUPPORTED_ABIS)
    }

    /**
     * True when [apk] packages [name] under `lib/<abi>/` for any of [abis].
     * Kept separate from [Context] so it can be unit-tested with a plain zip.
     */
    internal fun containsNativeLibrary(
        apk: File,
        name: String,
        abis: Array<String>
    ): Boolean {
        if (!apk.isFile) return false
        return try {
            ZipFile(apk).use { zip ->
                abis.any { abi -> zip.getEntry("lib/$abi/$name") != null }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun expectedAbi(): String = BuildConfig.SUPPORTED_ABI

    fun buildSummary(context: Context): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val version = packageInfo.versionName ?: "unknown"
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) {
            packageInfo.longVersionCode.toString()
        } else {
            "unknown"
        }
        return "v$version ($code) · ${BuildConfig.SUPPORTED_ABI} · SDK ${android.os.Build.VERSION.SDK_INT}"
    }
}
