package com.portsotc.mobile.disc

import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Table of disc releases this port can currently validate. A single retail
 * revision is supported: the USA release of Shadow of the Colossus.
 *
 * Additional revisions must not be added until the recompiled game core and
 * asset offsets for them have been produced and verified.
 */
object SupportedDiscs {

    data class Release(
        val serial: String,
        val title: String,
        val bootFile: String,
        val region: String,
        val requiredModules: List<String> = listOf("KERNEL.XFF", "MANAGER.XFF", "GAMECORE.XFF")
    )

    val RELEASES: List<Release> = listOf(
        Release(
            serial = "SCUS-97472",
            title = "Shadow of the Colossus (USA)",
            bootFile = "SCUS_974.72",
            region = "NTSC-U"
        )
    )

    fun bySerial(serial: String?): Release? =
        RELEASES.firstOrNull { it.serial.equals(serial, ignoreCase = true) }

    fun byBootFile(bootFile: String): Release? =
        RELEASES.firstOrNull { it.bootFile.equals(bootFile, ignoreCase = true) }
}

enum class IssueSeverity { INFO, WARNING, ERROR }

data class DiscIssue(
    val severity: IssueSeverity,
    val code: String,
    val message: String
)

data class DiscFileSummary(
    val path: String,
    val size: Long
)

data class DiscValidation(
    val valid: Boolean,
    val serial: String?,
    val title: String?,
    val bootFile: String?,
    val volumeLabel: String,
    val imageSize: Long,
    val sectorCount: Long,
    val declaredSectors: Long,
    val bootElf: DiscFileSummary?,
    val modules: List<DiscFileSummary>,
    val issues: List<DiscIssue>
) {
    val errors: List<DiscIssue> get() = issues.filter { it.severity == IssueSeverity.ERROR }
    val warnings: List<DiscIssue> get() = issues.filter { it.severity == IssueSeverity.WARNING }

    fun errorSummary(): String =
        errors.joinToString(separator = "\n") { it.message }.ifEmpty { "Disc validation failed" }
}

/**
 * Validates a candidate PlayStation 2 ISO image without trusting the file name.
 *
 * Checks performed:
 * 1. ISO9660 primary volume descriptor and directory structure.
 * 2. `SYSTEM.CNF` presence and BOOT2 boot path.
 * 3. Boot executable exists and is a 32-bit little-endian MIPS ET_EXEC ELF.
 * 4. The disc serial maps to a release this port supports.
 * 5. Every required XFF module exists and starts with the `xff2` magic.
 * 6. Declared volume size matches the image size closely enough to read it.
 */
class DiscValidator {

    fun validate(file: File): DiscValidation {
        val issues = ArrayList<DiscIssue>()
        if (!file.isFile) {
            issues.add(error("FILE_MISSING", "The selected file no longer exists"))
            return invalid(issues, file.length())
        }
        if (file.length() == 0L) {
            issues.add(error("FILE_EMPTY", "The selected file is empty"))
            return invalid(issues, 0L)
        }

        return try {
        Iso9660Image(file).use { image ->
            val volume = image.volume
            val declaredSectors = volume.volumeSpaceSize
            val declaredBytes = declaredSectors * Iso9660Image.SECTOR_SIZE
            if (declaredSectors <= 0) {
                issues.add(error("INVALID_VOLUME_SIZE", "ISO9660 volume declares no sectors"))
            }
            if (declaredBytes > image.imageSize) {
                issues.add(
                    error(
                        "TRUNCATED_IMAGE",
                        "ISO9660 volume declares $declaredSectors sectors but the image only contains " +
                            "${image.sectorCount}; the dump is truncated"
                    )
                )
            } else if (image.imageSize > declaredBytes + Iso9660Image.SECTOR_SIZE) {
                issues.add(
                    warning(
                        "TRAILING_DATA",
                        "Image contains ${image.imageSize - declaredBytes} bytes beyond the declared ISO9660 volume"
                    )
                )
            }

            val systemCnf = image.find("SYSTEM.CNF")
            if (systemCnf == null) {
                issues.add(
                    error(
                        "MISSING_SYSTEM_CNF",
                        "SYSTEM.CNF is missing from the disc root; this is not a PlayStation 2 game disc"
                    )
                )
                return@use finish(image, issues)
            }

            val cnfEntries = try {
                val text = image.readFile(systemCnf, 64 * 1024).toString(Charsets.US_ASCII)
                SystemCnf.parse(text)
            } catch (exception: IOException) {
                issues.add(
                    error("UNREADABLE_SYSTEM_CNF", "Unable to read SYSTEM.CNF: ${exception.message}")
                )
                return@use finish(image, issues)
            }
            val bootFile = SystemCnf.bootFile(cnfEntries)
            if (bootFile.isNullOrBlank()) {
                issues.add(
                    error(
                        "INVALID_SYSTEM_CNF",
                        "SYSTEM.CNF does not declare a BOOT2 or BOOT executable"
                    )
                )
                return@use finish(image, issues)
            }

            val serial = SystemCnf.serialFromBootFile(bootFile)
            if (serial == null) {
                issues.add(
                    error(
                        "INVALID_SERIAL",
                        "Unable to derive a disc serial from boot executable name `$bootFile`"
                    )
                )
                return@use finish(image, issues, bootFile = bootFile)
            }

            val release = SupportedDiscs.bySerial(serial)
            if (release == null) {
                issues.add(
                    error(
                        "UNSUPPORTED_SERIAL",
                        "Disc serial $serial is not supported. This port currently supports only " +
                            SupportedDiscs.RELEASES.joinToString { "${it.serial} (${it.title})" }
                    )
                )
            } else if (!release.bootFile.equals(bootFile, ignoreCase = true)) {
                issues.add(
                    error(
                        "BOOT_FILE_MISMATCH",
                        "SYSTEM.CNF boots `$bootFile` but the ${release.serial} release requires " +
                            "`${release.bootFile}`"
                    )
                )
            }

            val bootEntry = image.find(bootFile)
            var bootSummary: DiscFileSummary? = null
            if (bootEntry == null) {
                issues.add(
                    error("MISSING_BOOT_ELF", "Boot executable `$bootFile` is missing from the disc")
                )
            } else {
                bootSummary = DiscFileSummary(bootEntry.path, bootEntry.size)
                try {
                    image.openFile(bootEntry).use { stream ->
                        val elf = Ps2Elf.inspect(stream)
                        if (elf.loadableSegments.sumOf { it.memorySize } == 0L) {
                            issues.add(
                                error(
                                    "EMPTY_BOOT_ELF",
                                    "Boot executable `$bootFile` contains no loadable memory"
                                )
                            )
                        }
                    }
                } catch (exception: IOException) {
                    issues.add(
                        error(
                            "INVALID_BOOT_ELF",
                            "Boot executable `$bootFile` is not a valid PlayStation 2 ELF: " +
                                (exception.message ?: "unknown error")
                        )
                    )
                }
            }

            val moduleSummaries = ArrayList<DiscFileSummary>()
            val requiredModules = release?.requiredModules
                ?: listOf("KERNEL.XFF", "MANAGER.XFF", "GAMECORE.XFF")
            for (moduleName in requiredModules) {
                val entry = image.find(moduleName)
                if (entry == null) {
                    issues.add(
                        error("MISSING_MODULE", "Required engine module `$moduleName` is missing from the disc")
                    )
                    continue
                }
                moduleSummaries.add(DiscFileSummary(entry.path, entry.size))
                try {
                    val magic = image.openFile(entry).use { it.readExactly(4) }
                    if (magic.size < 4 || magic.decodeToString(throwOnInvalidSequence = false) != "xff2") {
                        issues.add(
                            error(
                                "INVALID_MODULE_MAGIC",
                                "Engine module `$moduleName` does not start with the expected xff2 magic"
                            )
                        )
                    }
                } catch (exception: IOException) {
                    issues.add(
                        error(
                            "UNREADABLE_MODULE",
                            "Unable to read engine module `$moduleName`: ${exception.message}"
                        )
                    )
                }
            }

            if (issues.none { it.severity == IssueSeverity.ERROR }) {
                issues.add(
                    info(
                        "DISC_OK",
                        "ISO9660 structure, boot ELF and engine modules verified"
                    )
                )
            }

            return finish(
                image = image,
                issues = issues,
                serial = serial,
                title = release?.title ?: "Unknown PlayStation 2 title",
                bootFile = bootFile,
                bootSummary = bootSummary,
                modules = moduleSummaries
            )
        }
        } catch (exception: IsoFormatException) {
            issues.add(
                error(
                    "INVALID_ISO",
                    "The selected file is not a readable ISO9660 image: ${exception.message}"
                )
            )
            invalid(issues, file.length())
        } catch (exception: IOException) {
            issues.add(
                error("IO_ERROR", "Unable to read the disc image: ${exception.message}")
            )
            invalid(issues, file.length())
        }
    }

    private fun finish(
        image: Iso9660Image,
        issues: List<DiscIssue>,
        serial: String? = null,
        title: String? = null,
        bootFile: String? = null,
        bootSummary: DiscFileSummary? = null,
        modules: List<DiscFileSummary> = emptyList()
    ): DiscValidation {
        val valid = issues.none { it.severity == IssueSeverity.ERROR }
        return DiscValidation(
            valid = valid,
            serial = serial,
            title = title,
            bootFile = bootFile,
            volumeLabel = image.volume.volumeIdentifier.ifBlank { "UNLABELLED" },
            imageSize = image.imageSize,
            sectorCount = image.sectorCount,
            declaredSectors = image.volume.volumeSpaceSize,
            bootElf = bootSummary,
            modules = modules,
            issues = issues
        )
    }

    private fun invalid(issues: List<DiscIssue>, imageSize: Long): DiscValidation = DiscValidation(
        valid = false,
        serial = null,
        title = null,
        bootFile = null,
        volumeLabel = "",
        imageSize = imageSize,
        sectorCount = imageSize / Iso9660Image.SECTOR_SIZE,
        declaredSectors = 0,
        bootElf = null,
        modules = emptyList(),
        issues = issues
    )

    private fun error(code: String, message: String) =
        DiscIssue(IssueSeverity.ERROR, code, message)

    private fun warning(code: String, message: String) =
        DiscIssue(IssueSeverity.WARNING, code, message)

    private fun info(code: String, message: String) =
        DiscIssue(IssueSeverity.INFO, code, message)
}
