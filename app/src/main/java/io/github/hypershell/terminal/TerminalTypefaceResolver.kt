package io.github.hypershell.terminal

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import io.github.hypershell.settings.TerminalFont
import java.io.File
import kotlin.math.abs

object TerminalTypefaceResolver {
    fun requested(context: Context, font: TerminalFont, customPath: String?): Typeface = when (font) {
        TerminalFont.SystemMono -> Typeface.MONOSPACE
        TerminalFont.SansMono -> Typeface.create("sans-serif-monospace", Typeface.NORMAL)
        TerminalFont.SerifMono -> Typeface.create("serif-monospace", Typeface.NORMAL)
        TerminalFont.MiSans -> File(context.filesDir, "fonts/misans-regular.ttf")
            .takeIf(File::isFile)
            ?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }
            ?: Typeface.MONOSPACE
        TerminalFont.Custom -> customPath
            ?.let(::File)
            ?.takeIf(File::isFile)
            ?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }
            ?: Typeface.MONOSPACE
    }

    fun terminal(context: Context, font: TerminalFont, customPath: String?): Typeface =
        requested(context, font, customPath).takeIf(::isMonospaced) ?: Typeface.MONOSPACE

    fun isMonospaced(typeface: Typeface): Boolean {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 64f
            this.typeface = typeface
        }
        val widths = MONOSPACE_PROBES.map(paint::measureText)
        val widest = widths.maxOrNull() ?: return false
        val narrowest = widths.minOrNull() ?: return false
        return widest > 0f && abs(widest - narrowest) / widest <= 0.03f
    }

    private val MONOSPACE_PROBES = listOf("i", "l", "W", "M", "0", "@", " ")
}
