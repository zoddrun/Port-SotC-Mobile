package com.portsotc.mobile.input

import com.portsotc.mobile.core.TouchPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchLayoutPresetTest {

    private val presets = listOf(
        TouchPreset.CLASSIC,
        TouchPreset.MODERN,
        TouchPreset.COMPACT,
        TouchPreset.CUSTOM
    )

    @Test
    fun everyPresetKeepsTheSameControlsAndButtonMasks() {
        val reference = TouchLayout.preset(TouchPreset.CLASSIC)
        val referenceMasks = reference.controls.associate { it.id to it.buttonMask }
        val referenceKinds = reference.controls.associate { it.id to it.kind }

        for (preset in presets) {
            val layout = TouchLayout.preset(preset)
            assertEquals(
                "Control ids changed for ${preset.id}",
                reference.controls.map { it.id },
                layout.controls.map { it.id }
            )
            assertEquals(
                "Button masks changed for ${preset.id}",
                referenceMasks,
                layout.controls.associate { it.id to it.buttonMask }
            )
            assertEquals(
                "Control kinds changed for ${preset.id}",
                referenceKinds,
                layout.controls.associate { it.id to it.kind }
            )
        }
    }

    @Test
    fun presetGeometryStaysInsideNormalizedBounds() {
        for (preset in presets) {
            for (control in TouchLayout.preset(preset).controls) {
                assertTrue(
                    "${preset.id}/${control.id} centerX out of range",
                    control.centerX in 0f..1f
                )
                assertTrue(
                    "${preset.id}/${control.id} centerY out of range",
                    control.centerY in 0f..1f
                )
                assertTrue(
                    "${preset.id}/${control.id} radius out of range",
                    control.radius in 0.02f..0.25f
                )
            }
        }
    }

    @Test
    fun modernAndCompactDifferFromClassic() {
        val classic = TouchLayout.preset(TouchPreset.CLASSIC)
        val modern = TouchLayout.preset(TouchPreset.MODERN)
        val compact = TouchLayout.preset(TouchPreset.COMPACT)

        assertNotEquals(
            classic.byId("lstick")!!.centerX,
            modern.byId("lstick")!!.centerX
        )
        assertNotEquals(
            classic.byId("cross")!!.centerX,
            modern.byId("cross")!!.centerX
        )
        assertTrue(
            "Compact must shrink the stick",
            compact.byId("lstick")!!.radius < classic.byId("lstick")!!.radius
        )
    }

    @Test
    fun customStartsFromTheClassicGeometry() {
        val classic = TouchLayout.preset(TouchPreset.CLASSIC)
        val custom = TouchLayout.preset(TouchPreset.CUSTOM)

        assertEquals(
            classic.controls.map { it.id to (it.centerX to it.centerY) },
            custom.controls.map { it.id to (it.centerX to it.centerY) }
        )
    }
}
