package com.portsotc.mobile.core

/** Presentation-only decisions for the in-session quick settings overlay. */
object SotcQuickSettingsModel {

    data class State(
        val settings: Settings,
        val gamepadConnected: Boolean,
        val touchRestored: Boolean,
        val shaderPackReady: Boolean,
        val shaderPackDetail: String
    )

    /**
     * Touch controls are hidden while a gamepad is connected only when the
     * player has asked for that behaviour. A deliberate touch restore wins until
     * the gamepad disconnects, so a controller hotplug cannot strand the player
     * without a way back.
     */
    fun touchControlsVisible(state: State): Boolean {
        if (!state.settings.touchControlsEnabled) return false
        if (!state.settings.hideTouchWithGamepad || !state.gamepadConnected) return true
        return state.touchRestored
    }

    fun restoreAvailable(state: State): Boolean =
        state.gamepadConnected && state.settings.hideTouchWithGamepad && !state.touchRestored

    fun frameLimitLabel(framesPerSecond: Int): String =
        if (framesPerSecond == 0) "UNLIMITED" else "$framesPerSecond FPS"

    fun presetLabel(presetId: String): String = HdPresetId.fromId(presetId).label

    fun hdCaption(state: State): String = buildString {
        append(if (state.shaderPackReady) "PACK VERIFIED" else "PACK UNAVAILABLE")
        append(" · ")
        append(presetLabel(state.settings.hdShaderPreset))
        if (!state.settings.hdShaderOverrides.isEmpty) append(" · CUSTOM")
        append(" · ")
        append(state.shaderPackDetail)
    }

    /** Never promise that an already-running runner picked up a changed value. */
    fun sessionNotice(): String =
        "Host diagnostics updates immediately. The recompiled runner reads these values at the next launch."
}
