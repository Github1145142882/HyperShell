package io.github.hypershell

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hypershell.terminal.PtyBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PtyBridgeInstrumentedTest {
    @Test
    fun nativePtyStartsResizesReadsAndReapsShell() {
        val bridge = PtyBridge()
        val handle = bridge.start(
            command = listOf("/system/bin/sh", "-c", "printf 'pty-ok\\n'"),
            cwd = "/",
            environment = mapOf("TERM" to "xterm-256color"),
            rows = 24,
            columns = 80,
        )

        bridge.resize(handle, rows = 40, columns = 120)
        val output = buildList<Byte> {
            val buffer = ByteArray(256)
            while (true) {
                val count = bridge.read(handle, buffer)
                if (count <= 0) break
                addAll(buffer.copyOf(count).toList())
            }
        }.toByteArray().toString(Charsets.UTF_8)
        val exitCode = bridge.waitFor(handle)
        bridge.close(handle)

        assertEquals(0, exitCode)
        assertTrue(output.contains("pty-ok"))
    }
}
