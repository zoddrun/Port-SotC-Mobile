package com.portsotc.mobile.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TouchLayoutStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun defaultLayoutHasUniqueControlsAndCoreButtons() {
        val layout = TouchLayout.defaults()
        val ids = layout.controls.map { it.id }
        assertEquals(ids.size, ids.toSet().size)

        assertNotNull(layout.byId("lstick"))
        assertNotNull(layout.byId("rstick"))
        assertNotNull(layout.byId("dpad"))
        assertNotNull(layout.byId("cross"))
        assertNotNull(layout.byId("circle"))
        assertNotNull(layout.byId("square"))
        assertNotNull(layout.byId("triangle"))
        assertNotNull(layout.byId("start"))
        assertNotNull(layout.byId("select"))

        val masks = layout.controls.filter { it.kind == TouchControlKind.BUTTON }
            .map { it.buttonMask }
        assertEquals(masks.size, masks.toSet().size)
        assertTrue(layout.controls.all {
            it.centerX in 0f..1f && it.centerY in 0f..1f && it.radius > 0f
        })
    }

    @Test
    fun savesAndRestoresEditedLayout() {
        val file = temporaryFolder.newFile("touch_layout.properties")
        val store = TouchLayoutStore(file)
        val layout = TouchLayout.defaults()
        val cross = layout.byId("cross")!!
        cross.centerX = 0.42f
        cross.centerY = 0.33f
        cross.radius = 0.11f
        cross.alpha = 0.66f
        layout.byId("start")!!.hidden = true

        store.save(layout)
        val loaded = store.load()

        assertEquals(0.42f, loaded.byId("cross")!!.centerX, 0.0001f)
        assertEquals(0.33f, loaded.byId("cross")!!.centerY, 0.0001f)
        assertEquals(0.11f, loaded.byId("cross")!!.radius, 0.0001f)
        assertEquals(0.66f, loaded.byId("cross")!!.alpha, 0.0001f)
        assertTrue(loaded.byId("start")!!.hidden)
    }

    @Test
    fun resetReturnsToDefaults() {
        val file = temporaryFolder.newFile("reset.properties")
        val store = TouchLayoutStore(file)
        val layout = TouchLayout.defaults()
        layout.byId("cross")!!.centerX = 0.9f
        store.save(layout)

        store.reset()
        val loaded = store.load()
        val defaultCross = TouchLayout.defaults().byId("cross")!!
        assertEquals(defaultCross.centerX, loaded.byId("cross")!!.centerX, 0.0001f)
    }

    @Test
    fun missingFileLoadsDefaults() {
        val store = TouchLayoutStore(temporaryFolder.root.resolve("absent.properties"))
        val layout = store.load()
        assertNotNull(layout.byId("dpad"))
        assertNull(layout.byId("unknown"))
    }

    @Test
    fun copyIsIndependentFromSource() {
        val layout = TouchLayout.defaults()
        val copy = layout.copyOf()
        copy.byId("cross")!!.centerX = 0.01f
        assertEquals(
            TouchLayout.defaults().byId("cross")!!.centerX,
            layout.byId("cross")!!.centerX,
            0.0001f
        )
    }
}
