package io.github.hypershell.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalBufferTest {
    @Test
    fun utf8SplitAcrossChunksIsDecodedOnce() {
        val buffer = TerminalBuffer(rows = 2, columns = 10)
        val bytes = "终端".toByteArray()

        buffer.accept(bytes.copyOfRange(0, 2))
        val snapshot = buffer.accept(bytes.copyOfRange(2, bytes.size))

        assertEquals("终", snapshot.lines.last { it.any { cell -> cell.text != " " } }[0].text)
        assertEquals("端", snapshot.lines.last { it.any { cell -> cell.text != " " } }[2].text)
    }

    @Test
    fun sgr256ColorAndResetAreApplied() {
        val buffer = TerminalBuffer(rows = 2, columns = 10)

        val snapshot = buffer.accept("\u001b[38;5;196mR\u001b[0mN".toByteArray())
        val line = snapshot.lines.last { it.any { cell -> cell.text != " " } }

        assertEquals(TerminalBuffer.color256(196), line[0].style.foreground)
        assertEquals(null, line[1].style.foreground)
    }

    @Test
    fun cursorMovementOverwritesExistingCell() {
        val buffer = TerminalBuffer(rows = 2, columns = 10)

        val snapshot = buffer.accept("abc\u001b[2DZ".toByteArray())
        val line = snapshot.lines.last { it.any { cell -> cell.text != " " } }

        assertEquals("aZc", line.take(3).joinToString("") { it.text })
    }

    @Test
    fun scrollbackIsBounded() {
        val buffer = TerminalBuffer(rows = 1, columns = 8, maxScrollback = 2)

        val snapshot = buffer.accept("1\n2\n3\n4".toByteArray())

        assertEquals(3, snapshot.lines.size)
        assertTrue(snapshot.lines.last().any { it.text == "4" })
    }
}

