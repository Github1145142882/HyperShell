package io.github.hypershell.terminal

import kotlin.math.max
import kotlin.math.min

data class TerminalStyle(
    val foreground: Int? = null,
    val background: Int? = null,
    val bold: Boolean = false,
    val inverse: Boolean = false,
)

data class TerminalCell(val text: String = " ", val style: TerminalStyle = TerminalStyle())

data class TerminalSnapshot(
    val lines: List<List<TerminalCell>>,
    val cursorRow: Int,
    val cursorColumn: Int,
    val columns: Int,
)

class TerminalBuffer(
    rows: Int = 24,
    columns: Int = 80,
    private var maxScrollback: Int = 2_000,
) {
    private var rowCount = max(1, rows)
    private var columnCount = max(1, columns)
    private var screen = MutableList(rowCount) { blankLine() }
    private val scrollback = ArrayDeque<List<TerminalCell>>()
    private var row = 0
    private var column = 0
    private var savedRow = 0
    private var savedColumn = 0
    private var style = TerminalStyle()
    private var parserState = ParserState.Normal
    private val csi = StringBuilder()
    private val utf8 = ArrayList<Byte>(4)
    private var expectedUtf8Bytes = 0

    @Synchronized
    fun clear() {
        screen = MutableList(rowCount) { blankLine() }
        scrollback.clear()
        row = 0
        column = 0
        style = TerminalStyle()
        parserState = ParserState.Normal
        csi.clear()
        utf8.clear()
        expectedUtf8Bytes = 0
    }

    @Synchronized
    fun setMaxScrollback(value: Int) {
        maxScrollback = value.coerceAtLeast(0)
        while (scrollback.size > maxScrollback) scrollback.removeFirst()
    }

    @Synchronized
    fun resize(rows: Int, columns: Int) {
        val newRows = max(1, rows)
        val newColumns = max(1, columns)
        val resized = MutableList(newRows) { MutableList(newColumns) { TerminalCell() } }
        val rowsToCopy = min(rowCount, newRows)
        val columnsToCopy = min(columnCount, newColumns)
        for (sourceRow in 0 until rowsToCopy) {
            for (sourceColumn in 0 until columnsToCopy) {
                resized[sourceRow][sourceColumn] = screen[sourceRow][sourceColumn]
            }
        }
        rowCount = newRows
        columnCount = newColumns
        screen = resized
        row = row.coerceIn(0, rowCount - 1)
        column = column.coerceIn(0, columnCount - 1)
    }

    @Synchronized
    fun accept(bytes: ByteArray): TerminalSnapshot {
        bytes.forEach { byte -> acceptByte(byte.toInt() and 0xff) }
        return snapshot()
    }

    @Synchronized
    fun snapshot(): TerminalSnapshot {
        val allLines = buildList {
            addAll(scrollback)
            addAll(screen.map { it.toList() })
        }
        return TerminalSnapshot(
            lines = allLines,
            cursorRow = scrollback.size + row,
            cursorColumn = column,
            columns = columnCount,
        )
    }

    private fun acceptByte(value: Int) {
        if (expectedUtf8Bytes > 0) {
            if (value and 0xC0 == 0x80) {
                utf8 += value.toByte()
                if (utf8.size == expectedUtf8Bytes) flushUtf8()
                return
            }
            writeCharacter("�")
            utf8.clear()
            expectedUtf8Bytes = 0
        }

        when (parserState) {
            ParserState.Escape -> when (value.toChar()) {
                '[' -> {
                    parserState = ParserState.Csi
                    csi.clear()
                }
                '7' -> {
                    savedRow = row
                    savedColumn = column
                    parserState = ParserState.Normal
                }
                '8' -> {
                    row = savedRow.coerceIn(0, rowCount - 1)
                    column = savedColumn.coerceIn(0, columnCount - 1)
                    parserState = ParserState.Normal
                }
                'c' -> {
                    clear()
                    parserState = ParserState.Normal
                }
                else -> parserState = ParserState.Normal
            }
            ParserState.Csi -> {
                if (value in 0x40..0x7e) {
                    executeCsi(value.toChar(), csi.toString())
                    csi.clear()
                    parserState = ParserState.Normal
                } else if (csi.length < 64) {
                    csi.append(value.toChar())
                } else {
                    csi.clear()
                    parserState = ParserState.Normal
                }
            }
            ParserState.Normal -> when (value) {
                0x1b -> parserState = ParserState.Escape
                0x07 -> Unit
                0x08 -> column = max(0, column - 1)
                0x09 -> column = min(columnCount - 1, ((column / 8) + 1) * 8)
                0x0a, 0x0b, 0x0c -> lineFeed()
                0x0d -> column = 0
                in 0x00..0x1f -> Unit
                in 0x20..0x7e -> writeCharacter(value.toChar().toString())
                else -> beginUtf8(value)
            }
        }
    }

    private fun beginUtf8(value: Int) {
        expectedUtf8Bytes = when {
            value and 0xE0 == 0xC0 -> 2
            value and 0xF0 == 0xE0 -> 3
            value and 0xF8 == 0xF0 -> 4
            else -> 0
        }
        if (expectedUtf8Bytes == 0) {
            writeCharacter("�")
        } else {
            utf8 += value.toByte()
        }
    }

    private fun flushUtf8() {
        val text = utf8.toByteArray().toString(Charsets.UTF_8)
        text.codePoints().forEach { codePoint -> writeCharacter(String(Character.toChars(codePoint))) }
        utf8.clear()
        expectedUtf8Bytes = 0
    }

    private fun writeCharacter(text: String) {
        if (column >= columnCount) {
            column = 0
            lineFeed()
        }
        val wide = isWide(text)
        screen[row][column] = TerminalCell(text, style)
        if (wide && column + 1 < columnCount) screen[row][column + 1] = TerminalCell("", style)
        column += if (wide) 2 else 1
        if (column > columnCount) column = columnCount
    }

    private fun isWide(text: String): Boolean {
        val codePoint = text.codePointAt(0)
        return codePoint in 0x1100..0x115f ||
            codePoint in 0x2e80..0xa4cf ||
            codePoint in 0xac00..0xd7a3 ||
            codePoint in 0xf900..0xfaff ||
            codePoint in 0x1f300..0x1faff
    }

    private fun lineFeed() {
        if (row == rowCount - 1) {
            scrollback.addLast(screen.removeAt(0).toList())
            while (scrollback.size > maxScrollback) scrollback.removeFirst()
            screen.add(blankLine())
        } else {
            row++
        }
    }

    private fun executeCsi(command: Char, raw: String) {
        val normalized = raw.trimStart('?', '>')
        val parameters = if (normalized.isEmpty()) emptyList() else normalized.split(';').map { it.toIntOrNull() ?: 0 }
        fun parameter(index: Int, default: Int = 1): Int = parameters.getOrNull(index)?.takeIf { it != 0 } ?: default
        when (command) {
            'A' -> row = max(0, row - parameter(0))
            'B' -> row = min(rowCount - 1, row + parameter(0))
            'C' -> column = min(columnCount - 1, column + parameter(0))
            'D' -> column = max(0, column - parameter(0))
            'E' -> {
                row = min(rowCount - 1, row + parameter(0))
                column = 0
            }
            'F' -> {
                row = max(0, row - parameter(0))
                column = 0
            }
            'G' -> column = (parameter(0) - 1).coerceIn(0, columnCount - 1)
            'H', 'f' -> {
                row = (parameter(0) - 1).coerceIn(0, rowCount - 1)
                column = (parameter(1) - 1).coerceIn(0, columnCount - 1)
            }
            'J' -> eraseDisplay(parameters.firstOrNull() ?: 0)
            'K' -> eraseLine(parameters.firstOrNull() ?: 0)
            'm' -> applyGraphicRendition(parameters.ifEmpty { listOf(0) })
            's' -> {
                savedRow = row
                savedColumn = column
            }
            'u' -> {
                row = savedRow.coerceIn(0, rowCount - 1)
                column = savedColumn.coerceIn(0, columnCount - 1)
            }
        }
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseLine(0)
                for (index in row + 1 until rowCount) screen[index] = blankLine()
            }
            1 -> {
                eraseLine(1)
                for (index in 0 until row) screen[index] = blankLine()
            }
            2, 3 -> {
                screen = MutableList(rowCount) { blankLine() }
                if (mode == 3) scrollback.clear()
            }
        }
    }

    private fun eraseLine(mode: Int) {
        when (mode) {
            0 -> for (index in column until columnCount) screen[row][index] = TerminalCell()
            1 -> for (index in 0..column.coerceAtMost(columnCount - 1)) screen[row][index] = TerminalCell()
            2 -> screen[row] = blankLine()
        }
    }

    private fun applyGraphicRendition(parameters: List<Int>) {
        var index = 0
        while (index < parameters.size) {
            when (val code = parameters[index]) {
                0 -> style = TerminalStyle()
                1 -> style = style.copy(bold = true)
                22 -> style = style.copy(bold = false)
                7 -> style = style.copy(inverse = true)
                27 -> style = style.copy(inverse = false)
                39 -> style = style.copy(foreground = null)
                49 -> style = style.copy(background = null)
                in 30..37 -> style = style.copy(foreground = ansiColor(code - 30))
                in 40..47 -> style = style.copy(background = ansiColor(code - 40))
                in 90..97 -> style = style.copy(foreground = ansiColor(code - 90 + 8))
                in 100..107 -> style = style.copy(background = ansiColor(code - 100 + 8))
                38, 48 -> {
                    val foreground = code == 38
                    when (parameters.getOrNull(index + 1)) {
                        5 -> parameters.getOrNull(index + 2)?.let { colorIndex ->
                            style = if (foreground) style.copy(foreground = color256(colorIndex))
                            else style.copy(background = color256(colorIndex))
                            index += 2
                        }
                        2 -> if (index + 4 < parameters.size) {
                            val color = argb(parameters[index + 2], parameters[index + 3], parameters[index + 4])
                            style = if (foreground) style.copy(foreground = color) else style.copy(background = color)
                            index += 4
                        }
                    }
                }
            }
            index++
        }
    }

    private fun blankLine() = MutableList(columnCount) { TerminalCell() }

    private enum class ParserState { Normal, Escape, Csi }

    companion object {
        private val baseColors = intArrayOf(
            0xff000000.toInt(), 0xffcd3131.toInt(), 0xff0dbc79.toInt(), 0xffe5e510.toInt(),
            0xff2472c8.toInt(), 0xffbc3fbc.toInt(), 0xff11a8cd.toInt(), 0xffe5e5e5.toInt(),
            0xff666666.toInt(), 0xfff14c4c.toInt(), 0xff23d18b.toInt(), 0xfff5f543.toInt(),
            0xff3b8eea.toInt(), 0xffd670d6.toInt(), 0xff29b8db.toInt(), 0xffffffff.toInt(),
        )

        fun color256(index: Int): Int = when (index.coerceIn(0, 255)) {
            in 0..15 -> baseColors[index.coerceIn(0, 15)]
            in 16..231 -> {
                val value = index - 16
                val red = value / 36
                val green = (value % 36) / 6
                val blue = value % 6
                fun component(component: Int) = if (component == 0) 0 else 55 + component * 40
                argb(component(red), component(green), component(blue))
            }
            else -> {
                val gray = 8 + (index - 232) * 10
                argb(gray, gray, gray)
            }
        }

        private fun ansiColor(index: Int) = baseColors[index.coerceIn(0, 15)]

        private fun argb(red: Int, green: Int, blue: Int): Int =
            (0xff shl 24) or (red.coerceIn(0, 255) shl 16) or
                (green.coerceIn(0, 255) shl 8) or blue.coerceIn(0, 255)
    }
}
