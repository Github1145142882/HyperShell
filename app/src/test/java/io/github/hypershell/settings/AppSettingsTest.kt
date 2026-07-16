package io.github.hypershell.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.hypershell.onboarding.OnboardingStep

class AppSettingsTest {
    @Test
    fun editorLimitsMapToExactMebibytes() {
        assertEquals(listOf(1, 4, 8, 16), EditorLimit.entries.map { it.mebibytes })
        assertEquals(16 * 1_048_576, EditorLimit.MiB16.bytes)
    }

    @Test
    fun defaultsMatchProductContract() {
        val settings = AppSettings()
        assertEquals(ThemeSource.Standard, settings.themeSource)
        assertEquals(BrightnessMode.System, settings.brightnessMode)
        assertEquals(FileLayoutMode.Dual, settings.fileLayoutMode)
        assertFalse(settings.colorfulFileTheme)
        assertEquals(BottomBarStyle.LiquidGlass, settings.bottomBarStyle)
        assertFalse(settings.bottomBarHdrFeedback)
        assertFalse(settings.showWelcomeOnLaunch)
        assertEquals(DefaultTerminalEnvironment.Termux, settings.defaultTerminalEnvironment)
        assertEquals(EditorLimit.MiB4, settings.editorLimit)
        assertEquals(0.35f, settings.terminalBackgroundDim)
        assertEquals(0f, settings.terminalBackgroundBlur)
        assertEquals(true, settings.terminalHdrHighlight)
    }

    @Test
    fun hdrFeedbackDefaultsOffWithoutOverwritingStoredChoice() {
        assertFalse(decodeBottomBarHdrFeedback(null))
        assertTrue(decodeBottomBarHdrFeedback(true))
        assertFalse(decodeBottomBarHdrFeedback(false))
    }

    @Test
    fun onboardingStepFallsBackSafely() {
        assertEquals(OnboardingStep.Welcome, decodeOnboardingStep(null))
        assertEquals(OnboardingStep.Welcome, decodeOnboardingStep("not-a-step"))
        assertEquals(OnboardingStep.BasicSettings, decodeOnboardingStep("BasicSettings"))
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
