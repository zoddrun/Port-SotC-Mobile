package com.portsotc.mobile.disc

import java.io.IOException
import java.io.InputStream

/**
 * PlayStation 2 ELF inspection.
 *
 * The boot executable on a PS2 disc is a 32-bit little-endian MIPS ELF
 * (ET_EXEC, EM_MIPS). Validating the header before handing a file to the
 * recompiler prevents a renamed or corrupted file from being treated as a
 * usable boot image.
 */
object Ps2Elf {

    const val EM_MIPS = 8
    const val ET_EXEC = 2
    const val PT_LOAD = 1
    private const val ELF_HEADER_SIZE = 52
    private const val PROGRAM_HEADER_SIZE = 32
    private const val MAX_PROGRAM_HEADERS = 64

    fun inspect(stream: InputStream, maxRead: Int = 1 shl 20): Ps2ElfInfo {
        val bytes = stream.readAtMost(maxRead)
        return inspect(bytes)
    }

    fun inspect(bytes: ByteArray): Ps2ElfInfo {
        if (bytes.size < ELF_HEADER_SIZE) {
            throw Ps2ElfException("ELF file is shorter than the 52-byte ELF32 header")
        }
        if (bytes[0] != 0x7F.toByte() || bytes[1] != 'E'.code.toByte() ||
            bytes[2] != 'L'.code.toByte() || bytes[3] != 'F'.code.toByte()
        ) {
            throw Ps2ElfException("Missing ELF magic")
        }
        val elfClass = bytes[4].toInt() and 0xFF
        if (elfClass != 1) {
            throw Ps2ElfException("ELF is not 32-bit (class $elfClass)")
        }
        val dataEncoding = bytes[5].toInt() and 0xFF
        if (dataEncoding != 1) {
            throw Ps2ElfException("ELF is not little-endian (encoding $dataEncoding)")
        }
        if ((bytes[6].toInt() and 0xFF) != 1) {
            throw Ps2ElfException("Unsupported ELF identification version")
        }

        val type = littleEndianUnsignedShort(bytes, 16)
        if (type != ET_EXEC) {
            throw Ps2ElfException("ELF type $type is not ET_EXEC")
        }
        val machine = littleEndianUnsignedShort(bytes, 18)
        if (machine != EM_MIPS) {
            throw Ps2ElfException("ELF machine $machine is not EM_MIPS")
        }
        if (littleEndianUnsignedInt(bytes, 20) != 1L) {
            throw Ps2ElfException("Unsupported ELF version field")
        }
        val entry = littleEndianUnsignedInt(bytes, 24)
        if (entry == 0L) {
            throw Ps2ElfException("ELF entry point is zero")
        }
        val programHeaderOffset = littleEndianUnsignedInt(bytes, 28)
        val sectionHeaderOffset = littleEndianUnsignedInt(bytes, 32)
        val headerSize = littleEndianUnsignedShort(bytes, 40)
        if (headerSize != ELF_HEADER_SIZE) {
            throw Ps2ElfException("ELF header size $headerSize does not match ELF32")
        }
        val programHeaderEntrySize = littleEndianUnsignedShort(bytes, 42)
        val programHeaderCount = littleEndianUnsignedShort(bytes, 44)
        if (programHeaderCount <= 0 || programHeaderCount > MAX_PROGRAM_HEADERS) {
            throw Ps2ElfException("ELF has an invalid program header count ($programHeaderCount)")
        }
        if (programHeaderEntrySize != PROGRAM_HEADER_SIZE) {
            throw Ps2ElfException("ELF program header size $programHeaderEntrySize does not match ELF32")
        }
        val programHeadersEnd = programHeaderOffset + programHeaderEntrySize.toLong() * programHeaderCount
        if (programHeadersEnd > bytes.size) {
            throw Ps2ElfException(
                "ELF program headers extend past the inspected data " +
                    "($programHeadersEnd > ${bytes.size}); file may be truncated"
            )
        }

        val segments = ArrayList<Ps2ElfSegment>(programHeaderCount)
        for (index in 0 until programHeaderCount) {
            val base = (programHeaderOffset + index.toLong() * programHeaderEntrySize).toInt()
            val segmentType = littleEndianUnsignedInt(bytes, base)
            val offset = littleEndianUnsignedInt(bytes, base + 4)
            val virtualAddress = littleEndianUnsignedInt(bytes, base + 8)
            val physicalAddress = littleEndianUnsignedInt(bytes, base + 12)
            val fileSize = littleEndianUnsignedInt(bytes, base + 16)
            val memorySize = littleEndianUnsignedInt(bytes, base + 20)
            val flags = littleEndianUnsignedInt(bytes, base + 24)
            val alignment = littleEndianUnsignedInt(bytes, base + 28)
            if (segmentType == PT_LOAD.toLong()) {
                if (fileSize > 0 && memorySize < fileSize) {
                    throw Ps2ElfException("ELF load segment $index has memsz < filesz")
                }
                segments.add(
                    Ps2ElfSegment(
                        index = index,
                        fileOffset = offset,
                        virtualAddress = virtualAddress,
                        physicalAddress = physicalAddress,
                        fileSize = fileSize,
                        memorySize = memorySize,
                        flags = flags,
                        alignment = alignment
                    )
                )
            }
        }
        val loadable = segments.filter { it.fileSize > 0 }
        if (loadable.isEmpty()) {
            throw Ps2ElfException("ELF contains no loadable program segment")
        }
        return Ps2ElfInfo(
            entryPoint = entry,
            programHeaderOffset = programHeaderOffset,
            sectionHeaderOffset = sectionHeaderOffset,
            flags = littleEndianUnsignedInt(bytes, 36),
            segments = segments
        )
    }
}

data class Ps2ElfInfo(
    val entryPoint: Long,
    val programHeaderOffset: Long,
    val sectionHeaderOffset: Long,
    val flags: Long,
    val segments: List<Ps2ElfSegment>
) {
    val loadableSegments: List<Ps2ElfSegment> get() = segments.filter { it.fileSize > 0 }
    val totalFileBytes: Long get() = loadableSegments.sumOf { it.fileSize }
}

data class Ps2ElfSegment(
    val index: Int,
    val fileOffset: Long,
    val virtualAddress: Long,
    val physicalAddress: Long,
    val fileSize: Long,
    val memorySize: Long,
    val flags: Long,
    val alignment: Long
)

class Ps2ElfException(message: String, cause: Throwable? = null) : IOException(message, cause)
