/*
 * HyperShell adapter for HyperCeiler's provision PreferenceDataStore.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package io.github.hypershell.onboarding

import com.sevtinge.hyperceiler.common.utils.PrefsBridge
import io.github.hypershell.settings.AppSettings
import io.github.hypershell.settings.BrightnessMode
import io.github.hypershell.settings.FileLayoutMode
import io.github.hypershell.settings.ThemeSource

object OriginalProvisionSettings {
    private const val THEME = "prefs_key_hypershell_onboarding_theme_source"
    private const val BRIGHTNESS = "prefs_key_hypershell_onboarding_brightness"
    private const val FILE_LAYOUT = "prefs_key_hypershell_onboarding_file_layout"
    private const val SHOW_HIDDEN = "prefs_key_hypershell_onboarding_show_hidden"
    private const val SCRIPT_ROOT = "prefs_key_hypershell_onboarding_script_root"
    private const val KEEP_BACKGROUND = "prefs_key_hypershell_onboarding_keep_background"
    private const val SHOW_WELCOME = "prefs_key_hypershell_onboarding_show_welcome"
    private const val BOTTOM_BAR_STYLE = "prefs_key_hypershell_onboarding_bottom_bar_style"
    private const val BOTTOM_BAR_HDR = "prefs_key_hypershell_onboarding_bottom_bar_hdr"
    private const val TERMINAL_HDR = "prefs_key_hypershell_onboarding_terminal_hdr"
    private const val DEFAULT_ENVIRONMENT = "prefs_key_hypershell_onboarding_default_environment"

    fun seed(settings: AppSettings) {
        PrefsBridge.putString(THEME, if (settings.themeSource == ThemeSource.Standard) "0" else "1")
        PrefsBridge.putString(BRIGHTNESS, settings.brightnessMode.ordinal.toString())
        PrefsBridge.putString(FILE_LAYOUT, if (settings.fileLayoutMode == FileLayoutMode.Single) "0" else "1")
        PrefsBridge.putBoolean(SHOW_HIDDEN, settings.showHiddenFiles)
        PrefsBridge.putBoolean(SCRIPT_ROOT, settings.scriptUseRoot)
        PrefsBridge.putBoolean(KEEP_BACKGROUND, settings.keepTerminalInBackground)
        PrefsBridge.putBoolean(SHOW_WELCOME, settings.showWelcomeOnLaunch)
        PrefsBridge.putString(BOTTOM_BAR_STYLE, settings.bottomBarStyle.ordinal.toString())
        PrefsBridge.putBoolean(BOTTOM_BAR_HDR, settings.bottomBarHdrFeedback)
        PrefsBridge.putBoolean(TERMINAL_HDR, settings.terminalHdrHighlight)
        PrefsBridge.putString(DEFAULT_ENVIRONMENT, settings.defaultTerminalEnvironment.ordinal.toString())
    }

    fun read(): OnboardingPreferences = OnboardingPreferences(
        themeSource = if (PrefsBridge.getString(THEME, "0") == "1") {
            OnboardingThemeSource.Monet
        } else {
            OnboardingThemeSource.Standard
        },
        brightness = when (PrefsBridge.getString(BRIGHTNESS, "0")) {
            "1" -> OnboardingBrightness.Light
            "2" -> OnboardingBrightness.Dark
            else -> OnboardingBrightness.System
        },
        fileLayout = if (PrefsBridge.getString(FILE_LAYOUT, "1") == "0") {
            OnboardingFileLayout.Single
        } else {
            OnboardingFileLayout.Dual
        },
        showHiddenFiles = PrefsBridge.getBoolean(SHOW_HIDDEN, true),
        scriptUseRoot = PrefsBridge.getBoolean(SCRIPT_ROOT, true),
        keepTerminalInBackground = PrefsBridge.getBoolean(KEEP_BACKGROUND, false),
        showWelcomeOnLaunch = PrefsBridge.getBoolean(SHOW_WELCOME, false),
        bottomBarStyle = when (PrefsBridge.getString(BOTTOM_BAR_STYLE, "0")) {
            "1" -> OnboardingBottomBarStyle.FloatingSolid
            "2" -> OnboardingBottomBarStyle.StandardNavigation
            else -> OnboardingBottomBarStyle.LiquidGlass
        },
        bottomBarHdrFeedback = PrefsBridge.getBoolean(BOTTOM_BAR_HDR, false),
        terminalHdrHighlight = PrefsBridge.getBoolean(TERMINAL_HDR, true),
        defaultEnvironment = if (PrefsBridge.getString(DEFAULT_ENVIRONMENT, "0") == "1") {
            OnboardingDefaultEnvironment.Debian
        } else {
            OnboardingDefaultEnvironment.Termux
        },
    )
}
