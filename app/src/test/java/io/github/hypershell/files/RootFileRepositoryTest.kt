package io.github.hypershell.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
