package com.portsotc.mobile.core

/**
 * Pure launcher state used by the refined Compose screen.
 *
 * The activity owns Android lookups; this model only decides which honest
 * status is shown and whether the single primary action can start a session.
 */
data class SotcLauncherState(
    val gameInstalled: Boolean,
    val runtimeInstalled: Boolean,
    val discTreeReady: Boolean,
    val shaderPackReady: Boolean,
    val shaderPackDetail: String,
    val hdPresetId: String,
    val buildSummary: String,
    val serial: String?,
    val title: String?
)

enum class SotcLauncherStatusKey {
    NOT_IMPORTED,
    RUNTIME_MISSING,
    PREPARE_REQUIRED,
    READY
}

data class SotcLauncherStatus(
    val key: SotcLauncherStatusKey,
    val playEnabled: Boolean,
    val showDetails: Boolean
)

object SotcLauncherModel {

    /** Applies the same gate order as the launcher: data, runtime, extracted tree. */
    fun evaluate(state: SotcLauncherState): SotcLauncherStatus = when {
        !state.gameInstalled -> SotcLauncherStatus(
            SotcLauncherStatusKey.NOT_IMPORTED,
            playEnabled = false,
            showDetails = false
        )

        !state.runtimeInstalled -> SotcLauncherStatus(
            SotcLauncherStatusKey.RUNTIME_MISSING,
            playEnabled = false,
            showDetails = true
        )

        !state.discTreeReady -> SotcLauncherStatus(
            SotcLauncherStatusKey.PREPARE_REQUIRED,
            playEnabled = true,
            showDetails = false
        )

        else -> SotcLauncherStatus(
            SotcLauncherStatusKey.READY,
            playEnabled = true,
            showDetails = false
        )
    }

    /** Monospace caption; never claims visual results on hardware. */
    fun hdCaption(state: SotcLauncherState): String {
        val label = HdPresetId.fromId(state.hdPresetId).label
        return "PRESET $label · ${state.shaderPackDetail}"
    }

    /** Short machine-readable identity line, or the required serial when absent. */
    fun identityLine(state: SotcLauncherState): String =
        if (state.serial.isNullOrBlank()) {
            "REQUIRED · SCUS-97472"
        } else {
            "${state.serial} · ${state.title ?: "Unknown title"}"
        }
}
