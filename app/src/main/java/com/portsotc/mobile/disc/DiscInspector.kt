package com.portsotc.mobile.disc

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * Produces a reproducible report for an imported PlayStation 2 image.
 *
 * All identifiers are computed from the image contents, never from its file
 * name. The PS2 boot identifier follows the public RetroAchievements game
 * identification rule: MD5 over the primary executable name from SYSTEM.CNF
 * followed by the executable bytes. This build does not contact any achievement
 * service or claim that the identifier is registered there.
 */
class DiscInspector {

    data class Report(
        val imagePath: String,
        val imageSize: Long,
        val imageSha256: String,
        val volumeIdentifier: String,
        val systemIdentifier: String,
        val declaredSectors: Long,
        val sectorCount: Long,
        val fileCount: Int,
        val bootPath: String?,
        val bootName: String?,
        val serial: String?,
        val title: String?,
        val supported: Boolean,
        val bootSize: Long?,
        val bootMd5: String?,
        val bootElfValid: Boolean,
        val bootElfDetail: String,
        val modules: List<DiscFileSummary>,
        val issues: List<DiscIssue>
    ) {
        val bootIdentifierAvailable: Boolean get() = bootMd5 != null

        fun format(): String = buildString {
            appendLine("IMAGE       $imagePath")
            appendLine("SIZE        $imageSize bytes / $sectorCount sectors")
            appendLine("SHA-256     $imageSha256")
            appendLine("VOLUME      $volumeIdentifier")
            appendLine("SYSTEM      $systemIdentifier")
            appendLine("DECLARED    $declaredSectors sectors")
            appendLine("FILES       $fileCount")
            appendLine("BOOT PATH   ${bootPath ?: "not found"}")
            appendLine("BOOT NAME   ${bootName ?: "not found"}")
            appendLine("SERIAL      ${serial ?: "unknown"}")
            appendLine("TITLE       ${title ?: "unknown"}")
            appendLine("SUPPORTED   ${if (supported) "SCUS-97472" else "no"}")
            appendLine("BOOT SIZE   ${bootSize ?: "unknown"}")
            appendLine("BOOT MD5    ${bootMd5 ?: "not available"}")
            appendLine("BOOT ELF    $bootElfDetail")
            appendLine("MODULES     ${modules.joinToString(", ") { "${it.path} (${it.size} bytes)" }}")
            if (issues.isEmpty()) {
                appendLine("ISSUES      none")
            } else {
                issues.forEach { issue ->
                    appendLine("ISSUE       ${issue.severity} ${issue.code}: ${issue.message}")
                }
            }
        }
    }

    fun inspect(file: File): Report {
        val validation = DiscValidator().validate(file)
        val digest = try {
            hashFile(file, "SHA-256")
        } catch (_: Exception) {
            ""
        }

        var volumeIdentifier = ""
        var systemIdentifier = ""
        var declaredSectors = 0L
        var sectorCount = 0L
        var fileCount = 0
        var bootPath: String? = null
        var bootName: String? = null
        var bootSize: Long? = null
        var bootMd5: String? = null
        var bootElfValid = false
        var bootElfDetail = "not inspected"
        val issues = validation.issues.toMutableList()

        val bootFile = validation.bootFile
        if (bootFile != null) {
            bootPath = bootFile
            bootName = SystemCnf.primaryExecutableNameFromBootPath(bootFile)
        }

        try {
            Iso9660Image(file).use { image ->
                volumeIdentifier = image.volume.volumeIdentifier
                systemIdentifier = image.volume.systemIdentifier
                declaredSectors = image.volume.volumeSpaceSize
                sectorCount = image.sectorCount
                fileCount = image.entries.count { !it.isDirectory }

                val entry = bootFile?.let { image.find(it) ?: image.find("/$it") }
                if (entry != null) {
                    bootSize = entry.size
                    val name = bootName ?: entry.name
                    bootName = name
                    bootMd5 = hashBootExecutable(image, entry, name)
                    bootElfDetail = try {
                        val header = image.openFile(entry).use { it.readAtMost(ELF_INSPECT_BYTES) }
                        val elf = Ps2Elf.inspect(header)
                        bootElfValid = true
                        "valid ELF32 MIPS ET_EXEC - entry 0x${String.format(Locale.US, "%08x", elf.entryPoint)} - " +
                            "${elf.loadableSegments.size} loadable segments"
                    } catch (error: Exception) {
                        "invalid: ${error.message ?: error.javaClass.simpleName}"
                    }
                } else if (bootFile != null) {
                    issues.add(
                        DiscIssue(
                            IssueSeverity.ERROR,
                            "BOOT_FILE_MISSING",
                            "SYSTEM.CNF names $bootFile but the executable is not present in the image"
                        )
                    )
                }
            }
        } catch (error: Exception) {
            issues.add(
                DiscIssue(
                    IssueSeverity.ERROR,
                    "INSPECTION_FAILED",
                    "Unable to inspect the image: ${error.message ?: error.javaClass.simpleName}"
                )
            )
        }

        return Report(
            imagePath = file.absolutePath,
            imageSize = validation.imageSize,
            imageSha256 = digest,
            volumeIdentifier = volumeIdentifier,
            systemIdentifier = systemIdentifier,
            declaredSectors = declaredSectors,
            sectorCount = sectorCount,
            fileCount = fileCount,
            bootPath = bootPath,
            bootName = bootName,
            serial = validation.serial,
            title = validation.title,
            supported = validation.valid,
            bootSize = bootSize,
            bootMd5 = bootMd5,
            bootElfValid = bootElfValid,
            bootElfDetail = bootElfDetail,
            modules = validation.modules,
            issues = issues
        )
    }

    private fun hashBootExecutable(image: Iso9660Image, entry: IsoFile, name: String): String {
        val digest = MessageDigest.getInstance("MD5")
        digest.update(name.toByteArray(Charsets.US_ASCII))
        image.openFile(entry).use { input ->
            val buffer = ByteArray(HASH_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    private fun hashFile(file: File, algorithm: String): String {
        if (!file.isFile) return ""
        val digest = MessageDigest.getInstance(algorithm)
        FileInputStream(file).use { input ->
            val buffer = ByteArray(HASH_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 0xFF) }

    companion object {
        private const val HASH_BUFFER_SIZE = 1 shl 20
        private const val ELF_INSPECT_BYTES = 1 shl 20
    }
}
