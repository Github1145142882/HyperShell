package io.github.hypershell.settings

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class OfficialFontInstallerTest {
    @Test
    fun parsesPinnedMiSansEntry() {
        val name = OfficialFontInstaller.TARGET_ENTRY.toByteArray()
        val central = ByteBuffer.allocate(46 + name.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x02014b50)
            putShort(20)
            putShort(20)
            putShort(0x800.toShort())
            putShort(8)
            putShort(0)
            putShort(0)
            putInt(0x729d48cb)
            putInt(OfficialFontInstaller.EXPECTED_COMPRESSED_SIZE)
            putInt(OfficialFontInstaller.EXPECTED_FONT_SIZE)
            putShort(name.size.toShort())
            putShort(0)
            putShort(0)
            putShort(0)
            putShort(0)
            putInt(0)
            putInt(64_826_916)
            put(name)
        }.array()

        val entry = OfficialFontInstaller.parseCentralDirectory(central, OfficialFontInstaller.TARGET_ENTRY)
        assertEquals(OfficialFontInstaller.EXPECTED_COMPRESSED_SIZE, entry.compressedSize)
        assertEquals(OfficialFontInstaller.EXPECTED_FONT_SIZE, entry.uncompressedSize)
        assertEquals(0x729d48cbL, entry.crc32)
        assertEquals(64_826_916L, entry.localHeaderOffset)
    }

    @Test
    fun rejectsMissingTargetAndReadsEocd() {
        val tail = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x06054b50)
            repeat(4) { putShort(0) }
            putInt(1_024)
            putInt(9_876)
            putShort(0)
        }.array()
        assertEquals(9_876L to 1_024L, OfficialFontInstaller.parseEndOfCentralDirectory(tail))
        try {
            OfficialFontInstaller.parseCentralDirectory(ByteArray(0), OfficialFontInstaller.TARGET_ENTRY)
            fail("missing target must fail")
        } catch (_: IllegalStateException) {
            // Expected.
        }
    }
}
