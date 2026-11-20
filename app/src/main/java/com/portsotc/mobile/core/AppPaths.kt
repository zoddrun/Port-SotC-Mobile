package com.portsotc.mobile.core

import android.content.Context
import java.io.File

/** Central definition of the app-private storage layout. */
class AppPaths(context: Context) {

    val filesRoot: File = context.filesDir
    val gamesRoot: File = File(filesRoot, "games")
    val savesRoot: File = File(filesRoot, "saves")
    val logsRoot: File = File(filesRoot, "logs")
    val importCache: File = File(context.cacheDir, "import")
    val touchLayoutFile: File = File(filesRoot, "touch_layout.properties")
    val shaderRoot: File = File(filesRoot, "shaders")
    val shaderPackRoot: File = File(shaderRoot, "hd")

    fun gameDirectory(serial: String): File = File(gamesRoot, serial)

    fun ensureDirectories() {
        gamesRoot.mkdirs()
        savesRoot.mkdirs()
        logsRoot.mkdirs()
        importCache.mkdirs()
        shaderRoot.mkdirs()
        shaderPackRoot.mkdirs()
    }
}
