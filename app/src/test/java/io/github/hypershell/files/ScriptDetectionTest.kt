package io.github.hypershell.files

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptDetectionTest {
    @Test
    fun extensionIsCaseInsensitive() {
        assertTrue(RootFileRepository.isShellScript("/data/Test.SH", null))
    }

    @Test
    fun supportedShellShebangsAreDetected() {
        assertTrue(RootFileRepository.isShellScript("/data/run", "#!/system/bin/sh\necho ok"))
        assertTrue(RootFileRepository.isShellScript("/data/run", "#!/usr/bin/zsh -f\n"))
        assertFalse(RootFileRepository.isShellScript("/data/run", "#!/usr/bin/python3\n"))
    }
}
