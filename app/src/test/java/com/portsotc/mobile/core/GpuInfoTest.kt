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

    @Test
    fun parsesTheExtendedCapabilityReport() {
        val report = """
            available=1
            backend=OpenGL ES 3.0
            eglVendor=The Khronos Group Inc.
            eglVersion=EGL 1.5
            eglExtensions=EGL_ANDROID_native_fence_sync EGL_KHR_surfaceless_context
            renderer=Mali-G78 MP14
            glExtensions=GL_EXT_disjoint_timer_query GL_OES_texture_buffer
            maxRenderbufferSize=16384
            maxTextureImageUnits=16
            maxVertexTextureImageUnits=16
            maxDrawBuffers=4
            maxSamples=4
            timerQueryAdvertised=1
            fenceSyncAdvertised=1
            selfTest=shader=1 rgba8=1 rgb565=1 depth16=1 depth24s8=1 rtt=1
        """.trimIndent()

        val info = GpuInfo.parse(report)

        assertEquals("The Khronos Group Inc.", info.eglVendor)
        assertEquals("EGL 1.5", info.eglVersion)
        assertTrue(info.eglExtensions.contains("native_fence_sync"))
        assertTrue(info.glExtensions.contains("disjoint_timer_query"))
        assertEquals(16384, info.maxRenderbufferSize)
        assertEquals(16, info.maxTextureImageUnits)
        assertEquals(16, info.maxVertexTextureImageUnits)
        assertEquals(4, info.maxDrawBuffers)
        assertEquals(4, info.maxSamples)
        assertTrue(info.timerQueryAdvertised)
        assertTrue(info.fenceSyncAdvertised)
        assertEquals(GpuInfo.Tier.ES3_ENHANCED, info.tier())
    }

    @Test
    fun aFailedProbeNeverProducesAHigherTier() {
        val es3WithFailedProbe = GpuInfo.parse(
            "available=1\nbackend=OpenGL ES 3.0\nselfTest=shader=1 rgba8=0 rtt=0\n"
        )
        assertEquals(GpuInfo.Tier.UNKNOWN, es3WithFailedProbe.tier())

        val noContext = GpuInfo.parse("available=0\nerror=no context\n")
        assertEquals(GpuInfo.Tier.UNKNOWN, noContext.tier())
    }

    @Test
    fun anEs2TierRequiresPassingProbes() {
        val safe = GpuInfo.parse("available=1\nbackend=OpenGL ES 2.0\nselfTest=shader=1 rgba8=1 rtt=1\n")
        assertEquals(GpuInfo.Tier.ES2_SAFE, safe.tier())

        val es3 = GpuInfo.parse("available=1\nbackend=OpenGL ES 3.0\nselfTest=shader=1 rgba8=1 rtt=1\n")
        assertEquals(GpuInfo.Tier.ES3_BASE, es3.tier())
    }

    @Test
    fun aRendererNameAloneNeverSelectsATier() {
        val software = GpuInfo.parse(
            "available=1\nbackend=OpenGL ES 2.0\nrenderer=SwiftShader\nselfTest=shader=1 rgba8=1 rtt=1\n"
        )
        // The tier reflects probes, not the renderer string. Software rendering
        // still needs a real device test before any performance claim.
        assertEquals(GpuInfo.Tier.ES2_SAFE, software.tier())
        assertFalse(software.timerQueryAdvertised)
    }
}
