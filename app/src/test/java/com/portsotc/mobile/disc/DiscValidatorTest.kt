package com.portsotc.mobile.disc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiscValidatorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val validator = DiscValidator()

    private fun codes(validation: DiscValidation): Set<String> =
        validation.issues.map { it.code }.toSet()

    @Test
    fun acceptsSupportedUsaDisc() {
        val image = SyntheticDisc.create(temporaryFolder.newFile("supported.iso"))
        val validation = validator.validate(image)

        assertTrue(validation.errorSummary(), validation.valid)
        assertEquals("SCUS-97472", validation.serial)
        assertEquals("SCUS_974.72", validation.bootFile)
        assertEquals("Shadow of the Colossus (USA)", validation.title)
        assertEquals(3, validation.modules.size)
        assertNotNull(validation.bootElf)
        assertTrue(codes(validation).contains("DISC_OK"))
    }

    @Test
    fun rejectsUnsupportedSerial() {
        val image = SyntheticDisc.create(
            temporaryFolder.newFile("unsupported.iso"),
            bootFile = "SLUS_123.45",
            cnf = SyntheticDisc.defaultCnf("SLUS_123.45")
        )
        val validation = validator.validate(image)

        assertFalse(validation.valid)
        assertEquals("SLUS-12345", validation.serial)
        assertTrue(codes(validation).contains("UNSUPPORTED_SERIAL"))
    }

    @Test
    fun rejectsMissingSystemCnf() {
        val image = SyntheticDisc.create(
            temporaryFolder.newFile("no-cnf.iso"),
            includeSystemCnf = false
        )
        val validation = validator.validate(image)

        assertFalse(validation.valid)
        assertTrue(codes(validation).contains("MISSING_SYSTEM_CNF"))
    }

    @Test
    fun rejectsInvalidModuleMagic() {
        val image = SyntheticDisc.create(
            temporaryFolder.newFile("bad-magic.iso"),
            modules = SyntheticDisc.defaultModules(magic = "bad!")
        )
        val validation = validator.validate(image)

        assertFalse(validation.valid)
        assertTrue(codes(validation).contains("INVALID_MODULE_MAGIC"))
    }

    @Test
    fun rejectsMissingModule() {
        val modules = SyntheticDisc.defaultModules().toMutableMap()
        modules.remove("MANAGER.XFF")
        val image = SyntheticDisc.create(
            temporaryFolder.newFile("missing-module.iso"),
            modules = modules
        )
        val validation = validator.validate(image)

        assertFalse(validation.valid)
        assertTrue(codes(validation).contains("MISSING_MODULE"))
    }

    @Test
    fun rejectsInvalidBootElf() {
        val image = SyntheticDisc.create(
            temporaryFolder.newFile("bad-elf.iso"),
            bootElf = ByteArray(64)
        )
        val validation = validator.validate(image)

        assertFalse(validation.valid)
        assertTrue(codes(validation).contains("INVALID_BOOT_ELF"))
    }

    @Test
    fun rejectsTruncatedVolume() {
        val image = SyntheticDisc.create(
            temporaryFolder.newFile("truncated.iso"),
            declaredSectors = 256
        )
        val validation = validator.validate(image)

        assertFalse(validation.valid)
        assertTrue(codes(validation).contains("TRUNCATED_IMAGE"))
    }

    @Test
    fun reportsUnreadableImageWithoutThrowing() {
        val file = temporaryFolder.newFile("garbage.iso")
        file.writeBytes(ByteArray(40 * 1024) { 0x41 })
        val validation = validator.validate(file)

        assertFalse(validation.valid)
        assertTrue(codes(validation).contains("INVALID_ISO"))
    }

    @Test
    fun reportsMissingFileWithoutThrowing() {
        val validation = validator.validate(temporaryFolder.root.resolve("absent.iso"))
        assertFalse(validation.valid)
        assertTrue(codes(validation).contains("FILE_MISSING"))
    }
}
