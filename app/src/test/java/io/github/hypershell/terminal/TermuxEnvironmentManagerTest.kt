package io.github.hypershell.terminal

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxEnvironmentManagerTest {
    @Test
    fun windowsEncodedDebianEpochIsRestoredOnAndroid() {
        assertEquals(
            "openssl_1:3.6.3_aarch64.deb",
            androidRepositoryFileName("openssl_1\uF03A3.6.3_aarch64.deb"),
        )
    }

    @Test
    fun localIndexUpdateCannotReadRemoteSources() {
        val command = localAptUpdateCommand(File("/prefix/bin/apt-get"), File("/prefix/etc/apt/local.list"))
        assertTrue(command.any { it.startsWith("Dir::Etc::sourcelist=") && it.replace('\\', '/').endsWith("/prefix/etc/apt/local.list") })
        assertTrue(command.contains("Dir::Etc::sourceparts=-"))
        assertFalse(command.any { "github" in it })
    }
}
