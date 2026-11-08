package com.portsotc.mobile

import android.app.Application
import com.portsotc.mobile.core.AppPaths
import com.portsotc.mobile.core.GameLibrary
import com.portsotc.mobile.core.SettingsStore
import com.portsotc.mobile.input.TouchLayoutStore

class SotcApplication : Application() {

    lateinit var paths: AppPaths
        private set
    lateinit var library: GameLibrary
        private set
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var touchLayoutStore: TouchLayoutStore
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
    }

    companion object {
        lateinit var instance: SotcApplication
            private set
    }
}
