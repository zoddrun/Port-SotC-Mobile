package com.portsotc.mobile.disc

import java.io.File
import java.io.RandomAccessFile

/**
 * Builds a small but structurally valid ISO9660 image for unit tests. The
 * layout mirrors a PlayStation 2 DVD closely enough to exercise the production
 * parser: primary volume descriptor, root directory records, multi-sector file
 * extents and a PS2 boot ELF.
 */
object SyntheticDisc {

    const val SECTOR = 2048
    private const val PVD_SECTOR = 16
    private const val TERMINATOR_SECTOR = 17
    private const val ROOT_SECTOR = 18
    private const val FIRST_FILE_SECTOR = 20
    private const val TOTAL_SECTORS = 128

    fun defaultCnf(bootFile: String = "SCUS_974.72"): String =
        "BOOT2 = cdrom0:\\$bootFile;1\r\nVER = 1.00\r\nVMODE = NTSC\r\n"

    fun defaultModules(magic: String = "xff2"): Map<String, ByteArray> = linkedMapOf(
        "KERNEL.XFF" to moduleBytes(magic, 512),
        "MANAGER.XFF" to moduleBytes(magic, 1024),
        "GAMECORE.XFF" to moduleBytes(magic, 2048)
    )

    fun validElf(): ByteArray {
        val headerSize = 52
        val programHeaderSize = 32
        val codeSize = 16
        val bytes = ByteArray(headerSize + programHeaderSize + codeSize)
        bytes[0] = 0x7F
        bytes[1] = 'E'.code.toByte()
        bytes[2] = 'L'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[4] = 1 // ELFCLASS32
        bytes[5] = 1 // ELFDATA2LSB
        bytes[6] = 1 // EV_CURRENT
        putShort(bytes, 16, 2) // ET_EXEC
        putShort(bytes, 18, 8) // EM_MIPS
        putInt(bytes, 20, 1) // EV_CURRENT
        putInt(bytes, 24, 0x00100000) // entry
        putInt(bytes, 28, headerSize.toLong()) // phoff
        putInt(bytes, 32, 0) // shoff
        putInt(bytes, 36, 0x20924001) // flags
        putShort(bytes, 40, headerSize.toLong())
        putShort(bytes, 42, programHeaderSize.toLong())
        putShort(bytes, 44, 1) // phnum
        putShort(bytes, 46, 0) // shentsize
        putShort(bytes, 48, 0) // shnum
        putShort(bytes, 50, 0) // shstrndx

        val ph = headerSize
        putInt(bytes, ph, 1) // PT_LOAD
        putInt(bytes, ph + 4, (headerSize + programHeaderSize).toLong()) // p_offset
        putInt(bytes, ph + 8, 0x00100000) // p_vaddr
        putInt(bytes, ph + 12, 0x00100000) // p_paddr
        putInt(bytes, ph + 16, codeSize.toLong()) // p_filesz
        putInt(bytes, ph + 20, codeSize.toLong()) // p_memsz
        putInt(bytes, ph + 24, 5) // PF_R | PF_X
        putInt(bytes, ph + 28, 16) // p_align
        for (index in 0 until codeSize) {
            bytes[headerSize + programHeaderSize + index] = (0x90 + index).toByte()
        }
        return bytes
    }

    fun moduleBytes(magic: String, size: Int): ByteArray {
        val bytes = ByteArray(size)
        val magicBytes = magic.toByteArray(Charsets.US_ASCII)
        magicBytes.copyInto(bytes, 0, 0, magicBytes.size.coerceAtMost(size))
        for (index in magicBytes.size until size) {
            bytes[index] = (index * 31).toByte()
        }
        return bytes
    }

    fun create(
        target: File,
        bootFile: String = "SCUS_974.72",
        cnf: String = defaultCnf(bootFile),
        includeSystemCnf: Boolean = true,
        bootElf: ByteArray = validElf(),
        modules: Map<String, ByteArray> = defaultModules(),
        declaredSectors: Long = TOTAL_SECTORS.toLong()
    ): File {
        val files = ArrayList<Pair<String, ByteArray>>()
        if (includeSystemCnf) {
            files.add("SYSTEM.CNF" to cnf.toByteArray(Charsets.US_ASCII))
        }
        files.add(bootFile to bootElf)
        for ((name, content) in modules) {
            files.add(name to content)
        }

        val image = ByteArray(TOTAL_SECTORS * SECTOR)
        var nextSector = FIRST_FILE_SECTOR
        val locations = ArrayList<Pair<String, Pair<Long, Int>>>()
        for ((name, content) in files) {
            val extent = nextSector.toLong()
            content.copyInto(image, nextSector * SECTOR)
            locations.add(name to (extent to content.size))
            nextSector += (content.size + SECTOR - 1) / SECTOR
        }

        writePvd(image, declaredSectors)
        writeTerminator(image)
        writeRootDirectory(image, locations)

        target.parentFile?.mkdirs()
        RandomAccessFile(target, "rw").use { handle ->
            handle.setLength(0)
            handle.write(image)
        }
        return target
    }

    private fun writePvd(image: ByteArray, declaredSectors: Long) {
        val offset = PVD_SECTOR * SECTOR
        image[offset] = 1
        "CD001".toByteArray(Charsets.US_ASCII).copyInto(image, offset + 1)
        image[offset + 6] = 1
        fillField(image, offset + 8, 32, "PLAYSTATION")
        fillField(image, offset + 40, 32, "SOTC_TEST")
        putInt(image, offset + 80, declaredSectors)
        putIntBigEndian(image, offset + 84, declaredSectors)
        putShort(image, offset + 120, 1)
        putShort(image, offset + 122, 1)
        putShort(image, offset + 124, 1)
        putShort(image, offset + 126, 1)
        putShort(image, offset + 128, SECTOR.toLong())
        putShortBigEndian(image, offset + 130, SECTOR)
        fillField(image, offset + 190, 128, "SOTC_TEST_SET")
        fillField(image, offset + 318, 128, "PORT_SOTC_MOBILE")
        fillField(image, offset + 446, 128, "PORT_SOTC_MOBILE")
        fillField(image, offset + 574, 128, "PORT_SOTC_MOBILE")
        fillField(image, offset + 702, 37, "2026010100000000")
        fillField(image, offset + 739, 37, "2026010100000000")
        image[offset + 881] = 1
        writeDirectoryRecord(
            image = image,
            offset = offset + 156,
            extent = ROOT_SECTOR.toLong(),
            size = SECTOR.toLong(),
            flags = 2,
            name = byteArrayOf(0)
        )
    }

    private fun writeTerminator(image: ByteArray) {
        val offset = TERMINATOR_SECTOR * SECTOR
        image[offset] = 255.toByte()
        "CD001".toByteArray(Charsets.US_ASCII).copyInto(image, offset + 1)
        image[offset + 6] = 1
    }

    private fun writeRootDirectory(
        image: ByteArray,
        locations: List<Pair<String, Pair<Long, Int>>>
    ) {
        var offset = ROOT_SECTOR * SECTOR
        offset = writeDirectoryRecord(
            image,
            offset,
            ROOT_SECTOR.toLong(),
            SECTOR.toLong(),
            2,
            byteArrayOf(0)
        )
        offset = writeDirectoryRecord(
            image,
            offset,
            ROOT_SECTOR.toLong(),
            SECTOR.toLong(),
            2,
            byteArrayOf(1)
        )
        for ((name, location) in locations) {
            val recordName = "$name;1".toByteArray(Charsets.US_ASCII)
            offset = writeDirectoryRecord(
                image,
                offset,
                location.first,
                location.second.toLong(),
                0,
                recordName
            )
        }
    }

    private fun writeDirectoryRecord(
        image: ByteArray,
        offset: Int,
        extent: Long,
        size: Long,
        flags: Int,
        name: ByteArray
    ): Int {
        var length = 33 + name.size
        if (length % 2 == 1) length++
        image[offset] = length.toByte()
        image[offset + 1] = 0
        putInt(image, offset + 2, extent)
        putIntBigEndian(image, offset + 6, extent)
        putInt(image, offset + 10, size)
        putIntBigEndian(image, offset + 14, size)
        image[offset + 18] = 126 // 2026
        image[offset + 19] = 1
        image[offset + 20] = 1
        image[offset + 21] = 0
        image[offset + 22] = 0
        image[offset + 23] = 0
        image[offset + 24] = 0
        image[offset + 25] = flags.toByte()
        putShort(image, offset + 28, 1)
        putShortBigEndian(image, offset + 30, 1)
        image[offset + 32] = name.size.toByte()
        name.copyInto(image, offset + 33)
        return offset + length
    }

    private fun fillField(image: ByteArray, offset: Int, length: Int, value: String) {
        for (index in 0 until length) image[offset + index] = ' '.code.toByte()
        val bytes = value.toByteArray(Charsets.US_ASCII)
        bytes.copyInto(image, offset, 0, bytes.size.coerceAtMost(length))
    }

    private fun putShort(target: ByteArray, offset: Int, value: Long) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun putShortBigEndian(target: ByteArray, offset: Int, value: Int) {
        target[offset] = ((value shr 8) and 0xFF).toByte()
        target[offset + 1] = (value and 0xFF).toByte()
    }

    private fun putInt(target: ByteArray, offset: Int, value: Long) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value shr 8) and 0xFF).toByte()
        target[offset + 2] = ((value shr 16) and 0xFF).toByte()
        target[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun putIntBigEndian(target: ByteArray, offset: Int, value: Long) {
        target[offset] = ((value shr 24) and 0xFF).toByte()
        target[offset + 1] = ((value shr 16) and 0xFF).toByte()
        target[offset + 2] = ((value shr 8) and 0xFF).toByte()
        target[offset + 3] = (value and 0xFF).toByte()
    }
}
