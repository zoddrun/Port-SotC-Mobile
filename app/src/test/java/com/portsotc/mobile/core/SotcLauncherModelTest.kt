package com.portsotc.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SotcLauncherModelTest {
    private fun state(
        gameInstalled: Boolean = true,
        runtimeInstalled: Boolean = true,
        discTreeReady: Boolean = true
    ) = SotcLauncherState(
        gameInstalled = gameInstalled,
        runtimeInstalled = runtimeInstalled,
        discTreeReady = discTreeReady,
        shaderPackReady = true,
        shaderPackDetail = "installed",
        hdPresetId = HdPresetId.BALANCED.id,
        buildSummary = "build",
        serial = null,
        title = null
    )

    @Test
    fun readyStateEnablesPlay() {
        val status = SotcLauncherModel.evaluate(state())

        assertEquals(SotcLauncherStatusKey.READY, status.key)
        assertTrue(status.playEnabled)
        assertFalse(status.showDetails)
    }

    @Test
    fun missingGameIsTheFirstGate() {
        val status = SotcLauncherModel.evaluate(
            state(gameInstalled = false, runtimeInstalled = false, discTreeReady = false)
        )

        assertEquals(SotcLauncherStatusKey.NOT_IMPORTED, status.key)
        assertFalse(status.playEnabled)
        assertFalse(status.showDetails)
    }

    @Test
    fun missingRuntimeShowsDetailsButBlocksPlay() {
        val status = SotcLauncherModel.evaluate(state(runtimeInstalled = false))

        assertEquals(SotcLauncherStatusKey.RUNTIME_MISSING, status.key)
        assertFalse(status.playEnabled)
        assertTrue(status.showDetails)
    }

    @Test
    fun discPreparationStillAllowsPlay() {
        val status = SotcLauncherModel.evaluate(state(discTreeReady = false))

        assertEquals(SotcLauncherStatusKey.PREPARE_REQUIRED, status.key)
        assertTrue(status.playEnabled)
        assertFalse(status.showDetails)
    }

    @Test
    fun hdCaptionNamesTheActivePreset() {
        val caption = SotcLauncherModel.hdCaption(state())

        assertTrue(caption.startsWith("PRESET Balanced HD"))
    }

    @Test
    fun identityLineNamesTheRequiredSerialWhenNoDiscIsPresent() {
        assertEquals("REQUIRED · SCUS-97472", SotcLauncherModel.identityLine(state()))
    }
}
