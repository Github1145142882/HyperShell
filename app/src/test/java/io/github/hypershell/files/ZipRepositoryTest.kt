package io.github.hypershell.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ZipRepositoryTest {
    @Test
    fun safeRelativePathsAreNormalized() {
        assertEquals("scripts/install.sh", ZipRepository.requireSafePath("scripts/install.sh"))
        assertEquals("folder/", ZipRepository.requireSafePath("folder/"))
    }

    @Test
    fun traversalAndAbsolutePathsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ZipRepository.requireSafePath("../outside.sh") }
        assertThrows(IllegalArgumentException::class.java) { ZipRepository.requireSafePath("folder/../../outside") }
        assertThrows(IllegalArgumentException::class.java) { ZipRepository.requireSafePath("/system/bin/sh") }
        assertThrows(IllegalArgumentException::class.java) { ZipRepository.requireSafePath("C:/Windows/file") }
    }

    @Test
    fun directoryPrefixIsCanonical() {
        assertEquals("", ZipRepository.normalizeDirectory("/"))
        assertEquals("a/b/", ZipRepository.normalizeDirectory("/a/b/"))
    }
}
