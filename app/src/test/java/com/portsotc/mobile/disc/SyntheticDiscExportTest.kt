package com.portsotc.mobile.disc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Materializes the synthetic disc used by unit tests as a file on disk.
 *
 * The image is structurally valid but contains no copyrighted game data. It is
 * written to the module build directory so device QA can exercise the
 * production import flow (document picker, staged copy, validation, module
 * extraction and atomic publish) without a real game disc.
 *
 * Run with:
 *   gradlew testDebugUnitTest --tests '*SyntheticDiscExportTest*'
 * The fixture is then available at app/build/synthetic-disc/SCUS-97472.iso.
 */
class SyntheticDiscExportTest {

    @Test
    fun writesImportFixtureForDeviceQa() {
        val output = File(System.getProperty("user.dir"), "build/synthetic-disc/SCUS-97472.iso")
        SyntheticDisc.create(output)

        val validation = DiscValidator().validate(output)
        assertTrue(validation.errorSummary(), validation.valid)
        assertEquals("SCUS-97472", validation.serial)
        assertEquals(3, validation.modules.size)
    }
}
