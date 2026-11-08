package com.portsotc.mobile.disc

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Minimal, read-only ISO9660 reader tailored to PlayStation 2 DVD images.
 *
 * PS2 disc images are normally stored as 2048-byte user-data sectors ("ISO"
 * format). Raw 2352-byte CD images are intentionally not accepted: the retail
 * Shadow of the Colossus release is a DVD title and must be dumped as an ISO.
 *
 * The reader implements the parts of ECMA-119 that matter for locating and
 * reading files: primary volume descriptor parsing, recursive directory record
 * parsing (including multi-extent files and multi-sector directories) and
 * streaming reads across extents. It does not implement Rock Ridge or Joliet
 * because PS2 software addresses files through plain ISO9660 names.
 */
class Iso9660Image(val file: File) : Closeable {

    companion object {
        const val SECTOR_SIZE = 2048
        private const val VOLUME_DESCRIPTOR_START = 16L
        private const val VOLUME_DESCRIPTOR_LIMIT = 64L
        private const val DIRECTORY_RECORD_HEADER = 33
        private const val MAX_DIRECTORY_DEPTH = 32
        private const val MAX_DIRECTORY_RECORDS = 200_000
    }

    private val randomAccess = RandomAccessFile(file, "r")

    val imageSize: Long = randomAccess.length()
    val sectorCount: Long = imageSize / SECTOR_SIZE

    val volume: VolumeDescriptor
    val entries: List<IsoFile>
    private val entryIndex: Map<String, IsoFile>

    init {
        if (imageSize < (VOLUME_DESCRIPTOR_START + 1) * SECTOR_SIZE) {
            throw IsoFormatException("File is too small to contain an ISO9660 volume descriptor")
        }
        if (imageSize % SECTOR_SIZE != 0L) {
            // The trailing partial sector is not fatal; many dump tools pad the
            // image with arbitrary bytes. It is recorded through imageSize and
            // reported by the validator when it affects the declared volume.
        }
        volume = readVolumeDescriptor()
        if (volume.logicalBlockSize != SECTOR_SIZE) {
            throw IsoFormatException(
                "Unsupported ISO9660 logical block size ${volume.logicalBlockSize}; expected $SECTOR_SIZE"
            )
        }

        val collected = LinkedHashMap<String, IsoFile>()
        val rootEntry = IsoFile(
            path = "/",
            name = "",
            extents = listOf(Extent(volume.rootExtentLba, volume.rootSize)),
            size = volume.rootSize,
            isDirectory = true
        )
        collected["/"] = rootEntry
        readDirectory(rootEntry, 0, collected, HashSet())
        entries = collected.values.toList()
        entryIndex = collected
    }

    /** Finds an entry using a PS2-style path such as `cdrom0:\SYSTEM.CNF;1`. */
    fun find(path: String): IsoFile? = entryIndex[normalizePath(path)]

    fun require(path: String): IsoFile =
        find(path) ?: throw IsoFormatException("Required file not found on disc: $path")

    /**
     * Opens a stream over a single ISO file. Each stream owns its own file
     * handle so callers may read independently from the shared index.
     */
    fun openFile(entry: IsoFile): InputStream {
        if (entry.isDirectory) {
            throw IsoFormatException("${entry.path} is a directory")
        }
        return ExtentInputStream(file, entry.extents, entry.size)
    }

    fun readFile(entry: IsoFile, maxBytes: Int = entry.size.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()): ByteArray {
        val buffer = ByteArray(entry.size.coerceAtMost(maxBytes.toLong()).toInt())
        openFile(entry).use { stream ->
            var read = 0
            while (read < buffer.size) {
                val count = stream.read(buffer, read, buffer.size - read)
                if (count < 0) break
                read += count
            }
            if (read != buffer.size) {
                throw IsoFormatException("Unexpected end of file while reading ${entry.path}")
            }
        }
        return buffer
    }

    private fun readVolumeDescriptor(): VolumeDescriptor {
        val buffer = ByteArray(SECTOR_SIZE)
        var sector = VOLUME_DESCRIPTOR_START
        while (sector < VOLUME_DESCRIPTOR_LIMIT) {
            readFully(sector * SECTOR_SIZE, buffer)
            val type = buffer[0].toInt() and 0xFF
            val identifier = String(buffer, 1, 5, StandardCharsets.US_ASCII)
            if (identifier != "CD001") {
                throw IsoFormatException("Not an ISO9660 image: missing CD001 signature at sector $sector")
            }
            if (type == 1) {
                return parsePrimaryVolumeDescriptor(buffer)
            }
            if (type == 255) {
                break
            }
            sector++
        }
        throw IsoFormatException("No ISO9660 primary volume descriptor found")
    }

    private fun parsePrimaryVolumeDescriptor(buffer: ByteArray): VolumeDescriptor {
        val logicalBlockSize = littleEndianUnsignedShort(buffer, 128)
        val volumeSpaceSize = littleEndianUnsignedInt(buffer, 80)
        val rootRecordOffset = 156
        val rootRecordLength = buffer[rootRecordOffset].toInt() and 0xFF
        if (rootRecordLength < DIRECTORY_RECORD_HEADER) {
            throw IsoFormatException("Primary volume descriptor has an invalid root directory record")
        }
        val rootExtent = littleEndianUnsignedInt(buffer, rootRecordOffset + 2)
        val rootSize = littleEndianUnsignedInt(buffer, rootRecordOffset + 10)
        return VolumeDescriptor(
            systemIdentifier = asciiField(buffer, 8, 32),
            volumeIdentifier = asciiField(buffer, 40, 32),
            volumeSetIdentifier = asciiField(buffer, 190, 128),
            publisherIdentifier = asciiField(buffer, 318, 128),
            applicationIdentifier = asciiField(buffer, 574, 128),
            logicalBlockSize = logicalBlockSize,
            volumeSpaceSize = volumeSpaceSize,
            rootExtentLba = rootExtent,
            rootSize = rootSize
        )
    }

    private fun readDirectory(
        directory: IsoFile,
        depth: Int,
        into: MutableMap<String, IsoFile>,
        visitedExtents: MutableSet<Long>
    ) {
        if (depth > MAX_DIRECTORY_DEPTH) {
            throw IsoFormatException("Directory nesting exceeds the supported depth at ${directory.path}")
        }
        if (directory.size <= 0L) {
            return
        }
        val data = readExtents(directory.extents, directory.size)
        var offset = 0
        var recordCount = 0
        var pendingName: String? = null
        val pendingExtents = ArrayList<Extent>()
        var pendingSize = 0L
        var pendingFlags = 0

        fun flushPending() {
            val name = pendingName ?: return
            if (pendingExtents.isEmpty()) {
                pendingName = null
                return
            }
            val parent = directory.path
            val path = if (parent == "/") "/$name" else "$parent/$name"
            val child = IsoFile(
                path = path,
                name = name,
                extents = pendingExtents.toList(),
                size = pendingSize,
                isDirectory = pendingFlags and 0x02 != 0
            )
            into[normalizePath(path)] = child
            pendingName = null
            pendingExtents.clear()
            pendingSize = 0L
            pendingFlags = 0
        }

        while (offset + DIRECTORY_RECORD_HEADER <= data.size) {
            val recordLength = data[offset].toInt() and 0xFF
            if (recordLength == 0) {
                // Zero-length record: the remainder of this sector is padding.
                offset = ((offset / SECTOR_SIZE) + 1) * SECTOR_SIZE
                continue
            }
            if (recordLength < DIRECTORY_RECORD_HEADER || offset + recordLength > data.size) {
                throw IsoFormatException("Malformed directory record in ${directory.path}")
            }
            recordCount++
            if (recordCount > MAX_DIRECTORY_RECORDS) {
                throw IsoFormatException("Directory record limit exceeded in ${directory.path}")
            }

            val flags = data[offset + 25].toInt() and 0xFF
            val extentLba = littleEndianUnsignedInt(data, offset + 2)
            val extentLength = littleEndianUnsignedInt(data, offset + 10)
            val nameLength = data[offset + 32].toInt() and 0xFF
            val nameStart = offset + DIRECTORY_RECORD_HEADER
            val nameEnd = nameStart + nameLength
            if (nameEnd > offset + recordLength || nameLength <= 0) {
                offset += recordLength
                continue
            }

            val isDotEntry = nameLength == 1 &&
                (data[nameStart].toInt() == 0 || data[nameStart].toInt() == 1)
            if (!isDotEntry) {
                val name = String(data, nameStart, nameLength, StandardCharsets.US_ASCII)
                    .substringBefore(';')
                    .trim()
                if (name.isNotEmpty() && name != "." && name != ".." && isSafeComponent(name)) {
                    if (pendingName != null && pendingName != name) {
                        flushPending()
                    }
                    if (pendingName == null) {
                        pendingName = name
                        pendingFlags = flags
                    }
                    pendingExtents.add(Extent(extentLba, extentLength))
                    pendingSize += extentLength
                    if (flags and 0x80 == 0) {
                        flushPending()
                    }
                }
            }
            offset += recordLength
        }
        flushPending()

        // Recurse after the map has been fully populated so that every child
        // directory is discoverable regardless of record ordering.
        val directories = into.values.filter {
            it.isDirectory && it.path != "/" && parentOf(it.path) == directory.path
        }
        for (child in directories) {
            if (child.size <= 0L) continue
            if (!visitedExtents.add(child.extents.first().lba)) continue
            readDirectory(child, depth + 1, into, visitedExtents)
        }
    }

    private fun readExtents(extents: List<Extent>, size: Long): ByteArray {
        if (size > Int.MAX_VALUE) {
            throw IsoFormatException("Directory is too large to index: $size bytes")
        }
        val data = ByteArray(size.toInt())
        var written = 0
        for (extent in extents) {
            var remaining = extent.length
            var position = extent.lba * SECTOR_SIZE
            while (remaining > 0 && written < data.size) {
                val chunk = minOf(remaining, SECTOR_SIZE.toLong()).toInt()
                val count = minOf(chunk, data.size - written)
                readFully(position, data, written, count)
                written += count
                position += chunk
                remaining -= chunk
            }
            if (written >= data.size) break
        }
        return data
    }

    private fun readFully(position: Long, destination: ByteArray, offset: Int = 0, length: Int = destination.size) {
        synchronized(randomAccess) {
            randomAccess.seek(position)
            randomAccess.readFully(destination, offset, length)
        }
    }

    private fun normalizePath(path: String): String {
        var value = path.trim()
        val colon = value.indexOf(':')
        if (colon >= 0) value = value.substring(colon + 1)
        value = value.replace('\\', '/')
        value = value.substringBefore(';')
        while (value.startsWith("/")) value = value.substring(1)
        value = value.trimEnd('/')
        if (value.isEmpty()) return "/"
        return "/" + value.uppercase(Locale.US)
    }

    private fun parentOf(path: String): String {
        val index = path.lastIndexOf('/')
        return if (index <= 0) "/" else path.substring(0, index)
    }

    private fun isSafeComponent(name: String): Boolean {
        if (name == "." || name == "..") return false
        return name.none { it == '/' || it == '\\' || it.code < 0x20 }
    }

    private fun asciiField(buffer: ByteArray, offset: Int, length: Int): String {
        val raw = String(buffer, offset, length, StandardCharsets.US_ASCII)
        return raw.trimEnd(' ', '\u0000')
    }

    override fun close() {
        try {
            randomAccess.close()
        } catch (_: IOException) {
            // Closing a read-only handle cannot meaningfully fail.
        }
    }

    class ExtentInputStream(
        private val file: File,
        extents: List<Extent>,
        private val declaredSize: Long
    ) : InputStream() {
        private val handle = RandomAccessFile(file, "r")
        private val extents = extents.toList()
        private var extentIndex = 0
        private var byteInExtent = 0L
        private var consumed = 0L
        private var closed = false

        override fun read(): Int {
            val single = ByteArray(1)
            val count = read(single, 0, 1)
            return if (count < 0) -1 else single[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (closed) throw IOException("Stream is closed")
            if (length == 0) return 0
            if (consumed >= declaredSize) return -1
            var total = 0
            while (total < length && consumed < declaredSize) {
                while (extentIndex < extents.size && byteInExtent >= extents[extentIndex].length) {
                    extentIndex++
                    byteInExtent = 0L
                }
                if (extentIndex >= extents.size) break
                val extent = extents[extentIndex]
                val available = minOf(
                    extent.length - byteInExtent,
                    length.toLong() - total,
                    declaredSize - consumed
                ).toInt()
                if (available <= 0) break
                synchronized(handle) {
                    handle.seek(extent.lba * SECTOR_SIZE + byteInExtent)
                    handle.readFully(buffer, offset + total, available)
                }
                total += available
                byteInExtent += available
                consumed += available
            }
            return if (total == 0) -1 else total
        }

        override fun skip(count: Long): Long {
            var remaining = count
            val scratch = ByteArray(4096)
            while (remaining > 0) {
                val read = read(scratch, 0, minOf(remaining, scratch.size.toLong()).toInt())
                if (read < 0) break
                remaining -= read
            }
            return count - remaining
        }

        override fun available(): Int {
            val left = declaredSize - consumed
            return left.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        }

        override fun close() {
            if (closed) return
            closed = true
            try {
                handle.close()
            } catch (_: IOException) {
                // Read-only handle; ignore close failures.
            }
        }
    }
}

data class VolumeDescriptor(
    val systemIdentifier: String,
    val volumeIdentifier: String,
    val volumeSetIdentifier: String,
    val publisherIdentifier: String,
    val applicationIdentifier: String,
    val logicalBlockSize: Int,
    val volumeSpaceSize: Long,
    val rootExtentLba: Long,
    val rootSize: Long
)

data class Extent(val lba: Long, val length: Long)

data class IsoFile(
    val path: String,
    val name: String,
    val extents: List<Extent>,
    val size: Long,
    val isDirectory: Boolean
)

class IsoFormatException(message: String, cause: Throwable? = null) : IOException(message, cause)

internal fun littleEndianUnsignedShort(buffer: ByteArray, offset: Int): Int =
    (buffer[offset].toInt() and 0xFF) or ((buffer[offset + 1].toInt() and 0xFF) shl 8)

internal fun littleEndianUnsignedInt(buffer: ByteArray, offset: Int): Long =
    (buffer[offset].toLong() and 0xFF) or
        ((buffer[offset + 1].toLong() and 0xFF) shl 8) or
        ((buffer[offset + 2].toLong() and 0xFF) shl 16) or
        ((buffer[offset + 3].toLong() and 0xFF) shl 24)
