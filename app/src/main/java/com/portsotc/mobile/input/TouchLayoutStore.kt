package com.portsotc.mobile.input

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/**
 * Persists the on-screen control layout to app-private storage as a plain
 * property file, so it survives reinstall-free updates and can be inspected or
 * repaired without a database.
 */
class TouchLayoutStore(private val file: File) {

    fun load(): TouchLayout {
        val layout = TouchLayout.defaults()
        if (!file.isFile) return layout
        return try {
            val properties = Properties()
            FileInputStream(file).use { properties.load(it) }
            for (control in layout.controls) {
                val prefix = "${control.id}."
                properties.getProperty("${prefix}cx")?.toFloatOrNull()?.let { control.centerX = it }
                properties.getProperty("${prefix}cy")?.toFloatOrNull()?.let { control.centerY = it }
                properties.getProperty("${prefix}radius")?.toFloatOrNull()?.let { control.radius = it }
                properties.getProperty("${prefix}alpha")?.toFloatOrNull()?.let { control.alpha = it }
                properties.getProperty("${prefix}hidden")?.toBooleanStrictOrNull()
                    ?.let { control.hidden = it }
                control.centerX = control.centerX.coerceIn(0f, 1f)
                control.centerY = control.centerY.coerceIn(0f, 1f)
                control.radius = control.radius.coerceIn(0.02f, 0.25f)
                control.alpha = control.alpha.coerceIn(0.1f, 1f)
            }
            layout
        } catch (_: Exception) {
            TouchLayout.defaults()
        }
    }

    fun save(layout: TouchLayout) {
        val properties = Properties()
        for (control in layout.controls) {
            val prefix = "${control.id}."
            properties.setProperty("${prefix}cx", control.centerX.toString())
            properties.setProperty("${prefix}cy", control.centerY.toString())
            properties.setProperty("${prefix}radius", control.radius.toString())
            properties.setProperty("${prefix}alpha", control.alpha.toString())
            properties.setProperty("${prefix}hidden", control.hidden.toString())
        }
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.part")
        FileOutputStream(temporary).use { properties.store(it, "Port SotC Mobile touch layout") }
        if (!temporary.renameTo(file)) {
            file.delete()
            if (!temporary.renameTo(file)) {
                throw java.io.IOException("Unable to save touch layout")
            }
        }
    }

    fun reset() {
        file.delete()
    }
}
