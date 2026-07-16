package io.github.hypershell.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipFile

class RootFileRepositoryTest {
    @Test
    fun directoryProtocolSupportsNewlinesAndSortsDirectoriesFirst() {
        val output = buildList<Byte> {
            addAll("/tmp/a\nfile".toByteArray().toList())
            add(0)
            addAll("regular file|12|100|644|root|root\n".toByteArray().toList())
            addAll("/tmp/dir".toByteArray().toList())
            add(0)
            addAll("directory|0|101|755|root|root\n".toByteArray().toList())
        }.toByteArray()

        val entries = RootFileRepository().parseDirectoryOutput(output)

        assertEquals(FileKind.Directory, entries[0].kind)
        assertEquals("a\nfile", entries[1].name)
        assertEquals(12, entries[1].size)
    }

    @Test
    fun fastDirectoryProtocolIsNulSafeAndKeepsTypes() {
        val output = buildList<Byte> {
            addAll("f".toByteArray().toList())
            add(0)
            addAll("/tmp/a\nfile".toByteArray().toList())
            add(0)
            addAll("d".toByteArray().toList())
            add(0)
            addAll("/tmp/folder".toByteArray().toList())
            add(0)
        }.toByteArray()

        val entries = RootFileRepository().parseFastDirectoryOutput(output)

        assertEquals("a\nfile", entries[0].name)
        assertEquals(FileKind.Regular, entries[0].kind)
        assertEquals(FileKind.Directory, entries[1].kind)
    }

    @Test
    fun archiveEntriesCannotEscapeDestination() {
        assertTrue(RootFileRepository.isSafeArchiveEntry("folder/script.sh"))
        assertTrue(RootFileRepository.isSafeArchiveEntry("./folder/file.txt"))
        assertFalse(RootFileRepository.isSafeArchiveEntry("../../data/adb/module"))
        assertFalse(RootFileRepository.isSafeArchiveEntry("/system/bin/sh"))
        assertFalse(RootFileRepository.isSafeArchiveEntry("folder//file"))
        assertFalse(RootFileRepository.isSafeArchiveEntry(""))
    }

    @Test
    fun tarStreamConvertsToUtf8ZipWithoutExternalZipCommand() {
        val tarBytes = ByteArrayOutputStream()
        TarArchiveOutputStream(tarBytes).use { tar ->
            tar.putArchiveEntry(TarArchiveEntry("folder/").apply { mode = 0b111101101 })
            tar.closeArchiveEntry()

            val content = "HyperShell 归档".toByteArray()
            tar.putArchiveEntry(TarArchiveEntry("folder/中文 file.txt").apply {
                size = content.size.toLong()
                mode = 0b110100100
            })
            tar.write(content)
            tar.closeArchiveEntry()

            tar.putArchiveEntry(TarArchiveEntry("folder/link", TarConstants.LF_SYMLINK).apply {
                linkName = "中文 file.txt"
                mode = 0b111111111
            })
            tar.closeArchiveEntry()
        }

        val output = Files.createTempFile("hypershell-archive-test", ".zip").toFile()
        try {
            RootFileRepository().convertTarToZip(ByteArrayInputStream(tarBytes.toByteArray()), output)
            ZipFile(output).use { zip ->
                val text = zip.getEntry("folder/中文 file.txt")
                assertEquals("HyperShell 归档", zip.getInputStream(text).readBytes().toString(Charsets.UTF_8))
                val link = zip.getEntry("folder/link")
                assertEquals("中文 file.txt", zip.getInputStream(link).readBytes().toString(Charsets.UTF_8))
            }
        } finally {
            output.delete()
        }
    }
}
