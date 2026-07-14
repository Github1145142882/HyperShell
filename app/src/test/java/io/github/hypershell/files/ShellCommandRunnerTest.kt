package io.github.hypershell.files

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellCommandRunnerTest {
    @Test
    fun shellQuoteProtectsWhitespaceAndSingleQuotes() {
        assertEquals("'a b'\\''c;rm -rf /'", shellQuote("a b'c;rm -rf /"))
    }

    @Test
    fun normalizeAbsolutePathResolvesDotsWithoutEscapingRoot() {
        assertEquals("/system/bin", RootFileRepository.normalizeAbsolutePath("/data/../system/./bin"))
        assertEquals("/etc", RootFileRepository.normalizeAbsolutePath("/../../etc"))
        assertEquals(null, RootFileRepository.normalizeAbsolutePath("relative/path"))
    }
}

