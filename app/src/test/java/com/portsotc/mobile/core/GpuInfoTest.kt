package com.portsotc.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuInfoTest {

    private val fullReport = """
        available=1
        backend=OpenGL ES 3.0
        vendor=Qualcomm
        renderer=Adreno (TM) 640
        version=OpenGL ES 3.2 V@053.0 (GIT@a1b2c3=4d5e6f)
        glsl=OpenGL ES GLSL ES 3.20
        maxTextureSize=16384
        maxVertexAttribs=16
        extensions=142
        error=
    """.trimIndent()

    @Test
    fun parsesACompleteReport() {
        val info = GpuInfo.parse(fullReport)

        assertTrue(info.available)
        assertEquals("OpenGL ES 3.0", info.backend)
        assertEquals("Qualcomm", info.vendor)
        assertEquals("Adreno (TM) 640", info.renderer)
        assertEquals("OpenGL ES 3.2 V@053.0 (GIT@a1b2c3=4d5e6f)", info.version)
        assertEquals("OpenGL ES GLSL ES 3.20", info.shadingLanguage)
        assertEquals(16384, info.maxTextureSize)
        assertEquals(16, info.maxVertexAttribs)
        assertEquals(142, info.extensionCount)
        assertEquals("", info.error)
    }

    @Test
    fun anEqualsSignInsideAValueDoesNotSplitTheField() {
        val info = GpuInfo.parse("available=1\nrenderer=Mali-G78=MP14\n")

        assertEquals("Mali-G78=MP14", info.renderer)
    }

    @Test
    fun anUnavailableReportKeepsTheReason() {
        val info = GpuInfo.parse("available=0\nerror=No EGL pbuffer config for OpenGL ES 3.0\n")

        assertFalse(info.available)
        assertEquals("No EGL pbuffer config for OpenGL ES 3.0", info.error)
        assertTrue(info.summary().contains("unavailable"))
        assertTrue(info.summary().contains("No EGL pbuffer config"))
    }

    @Test
    fun missingOrMalformedInputNeverThrows() {
        val missing = GpuInfo.parse(null)
        assertFalse(missing.available)
        assertTrue(missing.error.isNotBlank())

        val malformed = GpuInfo.parse("available=1\nmaxTextureSize=many\njunk line\n")
        assertTrue(malformed.available)
        assertEquals(0, malformed.maxTextureSize)
        assertEquals("", malformed.renderer)
    }

    @Test
    fun summaryListsTheDriverFacts() {
        val summary = GpuInfo.parse(fullReport).summary()

        assertTrue(summary.contains("OpenGL ES 3.0"))
        assertTrue(summary.contains("Adreno (TM) 640"))
        assertTrue(summary.contains("16384"))
        assertTrue(summary.contains("142"))
    }
}
