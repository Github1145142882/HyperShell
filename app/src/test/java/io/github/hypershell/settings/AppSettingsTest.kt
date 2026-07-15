package io.github.hypershell.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {
    @Test
    fun editorLimitsMapToExactMebibytes() {
        assertEquals(listOf(1, 4, 8, 16), EditorLimit.entries.map { it.mebibytes })
        assertEquals(16 * 1_048_576, EditorLimit.MiB16.bytes)
    }

    @Test
    fun defaultsMatchProductContract() {
        val settings = AppSettings()
        assertEquals(ThemeSource.Monet, settings.themeSource)
        assertEquals(BrightnessMode.System, settings.brightnessMode)
        assertEquals(FileLayoutMode.Dual, settings.fileLayoutMode)
        assertEquals(BottomBarStyle.LiquidGlass, settings.bottomBarStyle)
        assertEquals(true, settings.bottomBarHdrFeedback)
        assertEquals(EditorLimit.MiB4, settings.editorLimit)
        assertEquals(0.35f, settings.terminalBackgroundDim)
        assertEquals(0f, settings.terminalBackgroundBlur)
        assertEquals(true, settings.terminalHdrHighlight)
    }

    @Test
    fun legacyPingFangMigratesToMiSans() {
        assertEquals(TerminalFont.MiSans, decodeTerminalFont("PingFang"))
        assertEquals(TerminalFont.SystemMono, decodeTerminalFont("not-a-font"))
    }

    @Test
    fun legacyBottomBarFlagsMigrateToSingleStyle() {
        assertEquals(BottomBarStyle.StandardNavigation, migrateBottomBarStyle(null, floating = false, glass = true))
        assertEquals(BottomBarStyle.FloatingSolid, migrateBottomBarStyle(null, floating = true, glass = false))
        assertEquals(BottomBarStyle.LiquidGlass, migrateBottomBarStyle(null, floating = true, glass = true))
        assertEquals(BottomBarStyle.FloatingSolid, migrateBottomBarStyle("FloatingSolid", floating = false, glass = true))
    }
}
