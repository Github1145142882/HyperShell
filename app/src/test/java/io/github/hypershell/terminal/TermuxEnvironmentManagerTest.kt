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

    @Test
    fun debian13AssetAndOfficialSourcesArePinned() {
        assertEquals(
            "debian/debian-13-slim-arm64-android.tar.gz.bin",
            debianAssetPathForAbis(listOf("arm64-v8a")),
        )
        val sources = debianAptSources()
        assertTrue("URIs: http://deb.debian.org/debian" in sources)
        assertTrue("Suites: trixie trixie-updates" in sources)
        assertTrue("trixie-updates" in sources)
        assertTrue("URIs: http://deb.debian.org/debian-security" in sources)
        assertTrue("Suites: trixie-security" in sources)
        assertTrue("Signed-By: /usr/share/keyrings/debian-archive-keyring.pgp" in sources)
        assertTrue("non-free-firmware" in sources)
        assertFalse("noble" in sources)
    }

    @Test
    fun debianFirstStartPopulatesPackageIndexesBeforeOpeningShell() {
        val command = debianFirstRunCommand()
        assertTrue("/var/lib/apt/lists" in command)
        assertTrue("apt-get update" in command)
        assertTrue("exec /bin/bash --login -i" in command)
    }

    @Test
    fun debianArchiveRejectsTraversal() {
        assertTrue(isSafeArchivePath("./usr/bin/bash"))
        assertFalse(isSafeArchivePath("../../data/local/tmp/payload"))
        assertFalse(isSafeArchivePath("/system/bin/sh"))
    }
}
