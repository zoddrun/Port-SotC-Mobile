package com.portsotc.mobile.core

import android.content.Context
import com.portsotc.mobile.BuildConfig
import java.io.File

/**
 * Detects whether a separately built recompiled game core is present.
 *
 * This is a filesystem check only. It never claims that the core is compatible
 * or initialized; the native loader performs the ABI and initialization checks
 * when a game session starts.
 */
object CoreProbe {

    const val CORE_LIBRARY_NAME = "libsotc_game.so"

    fun isInstalled(context: Context): Boolean {
        // Android 10 and later only allow executable code to be loaded from
        // the read-only native library directory populated from the APK.
        // A library copied into writable game storage cannot be dlopen'ed.
        val nativeLibraryDirectory = context.applicationInfo.nativeLibraryDir ?: return false
        return File(nativeLibraryDirectory, CORE_LIBRARY_NAME).isFile
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
