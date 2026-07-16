package io.github.hypershell.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.hypershell.onboarding.CURRENT_ONBOARDING_VERSION
import io.github.hypershell.onboarding.OnboardingStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.hyperShellSettings by preferencesDataStore(name = "settings")

data class SettingsSnapshot(
    val settings: AppSettings,
    val completedOnboardingVersion: Int,
    val pendingOnboardingStep: OnboardingStep,
)

class SettingsRepository(private val context: Context) {
    val snapshot: Flow<SettingsSnapshot> = context.hyperShellSettings.data.map { preferences ->
        SettingsSnapshot(
            settings = decode(preferences),
            completedOnboardingVersion = preferences[Keys.completedOnboardingVersion] ?: 0,
            pendingOnboardingStep = decodeOnboardingStep(preferences[Keys.pendingOnboardingStep]),
        )
    }
    val settings: Flow<AppSettings> = snapshot.map { it.settings }.distinctUntilChanged()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.hyperShellSettings.edit { preferences -> encode(preferences, transform(decode(preferences))) }
    }

    suspend fun updatePendingOnboardingStep(step: OnboardingStep) {
        context.hyperShellSettings.edit { preferences ->
            preferences[Keys.pendingOnboardingStep] = step.name
        }
    }

    suspend fun completeOnboarding() {
        context.hyperShellSettings.edit { preferences ->
            preferences[Keys.completedOnboardingVersion] = CURRENT_ONBOARDING_VERSION
            preferences[Keys.pendingOnboardingStep] = OnboardingStep.Welcome.name
        }
    }

    private fun decode(p: Preferences) = AppSettings(
        showHiddenFiles = p[Keys.showHiddenFiles] ?: true,
        colorfulFileTheme = p[Keys.colorfulFileTheme] ?: false,
        fileLayoutMode = enumValueOrDefault(p[Keys.fileLayoutMode], FileLayoutMode.Dual),
        fileSortMode = enumValueOrDefault(p[Keys.fileSortMode], FileSortMode.Name),
        editorLimit = enumValueOrDefault(p[Keys.editorLimit], EditorLimit.MiB4),
        scriptUseRoot = p[Keys.scriptUseRoot] ?: true,
        scriptPermission = enumValueOrDefault(p[Keys.scriptPermission], ScriptPermission.Unchanged),
        themeSource = enumValueOrDefault(p[Keys.themeSource], ThemeSource.Standard),
        brightnessMode = enumValueOrDefault(p[Keys.brightnessMode], BrightnessMode.System),
        terminalFontSize = (p[Keys.terminalFontSize] ?: 13f).coerceIn(9f, 28f),
        pageTopBarBlur = p[Keys.pageTopBarBlur] ?: p[Keys.bottomBarBlur] ?: true,
        keyColor = p[Keys.keyColor] ?: 0,
        paletteStyle = p[Keys.paletteStyle] ?: "TonalSpot",
        colorSpec = p[Keys.colorSpec] ?: "Spec2021",
        bottomBarStyle = decodeBottomBarStyle(p),
        bottomBarHdrFeedback = decodeBottomBarHdrFeedback(p[Keys.bottomBarHdrFeedback]),
        showWelcomeOnLaunch = p[Keys.showWelcomeOnLaunch] ?: false,
        defaultTerminalEnvironment = enumValueOrDefault(
            p[Keys.defaultTerminalEnvironment],
            DefaultTerminalEnvironment.Termux,
        ),
        terminalTopBlur = p[Keys.terminalTopBlur] ?: true,
        keepTerminalInBackground = p[Keys.keepTerminalInBackground] ?: false,
        terminalBackgroundColor = p[Keys.terminalBackgroundColor] ?: 0xFF000000.toInt(),
        terminalBackgroundImagePath = p[Keys.terminalBackgroundImagePath],
        terminalBackgroundDim = (p[Keys.terminalBackgroundDim] ?: 0.35f).coerceIn(0f, 0.8f),
        terminalBackgroundBlur = (p[Keys.terminalBackgroundBlur] ?: 0f).coerceIn(0f, 32f),
        terminalHdrHighlight = p[Keys.terminalHdrHighlight] ?: true,
        terminalFont = decodeTerminalFont(p[Keys.terminalFont]),
        customTerminalFontPath = p[Keys.customTerminalFontPath],
        bookmarks = p[Keys.bookmarks] ?: emptySet(),
    )

    private fun encode(p: androidx.datastore.preferences.core.MutablePreferences, s: AppSettings) {
        p[Keys.showHiddenFiles] = s.showHiddenFiles
        p[Keys.colorfulFileTheme] = s.colorfulFileTheme
        p[Keys.fileLayoutMode] = s.fileLayoutMode.name
        p[Keys.fileSortMode] = s.fileSortMode.name
        p[Keys.editorLimit] = s.editorLimit.name
        p[Keys.scriptUseRoot] = s.scriptUseRoot
        p[Keys.scriptPermission] = s.scriptPermission.name
        p[Keys.themeSource] = s.themeSource.name
        p[Keys.brightnessMode] = s.brightnessMode.name
        p[Keys.terminalFontSize] = s.terminalFontSize
        p[Keys.pageTopBarBlur] = s.pageTopBarBlur
        p[Keys.keyColor] = s.keyColor
        p[Keys.paletteStyle] = s.paletteStyle
        p[Keys.colorSpec] = s.colorSpec
        p[Keys.bottomBarStyle] = s.bottomBarStyle.name
        p[Keys.bottomBarHdrFeedback] = s.bottomBarHdrFeedback
        p[Keys.showWelcomeOnLaunch] = s.showWelcomeOnLaunch
        p[Keys.defaultTerminalEnvironment] = s.defaultTerminalEnvironment.name
        p[Keys.terminalTopBlur] = s.terminalTopBlur
        p[Keys.keepTerminalInBackground] = s.keepTerminalInBackground
        p[Keys.terminalBackgroundColor] = s.terminalBackgroundColor
        s.terminalBackgroundImagePath?.let { p[Keys.terminalBackgroundImagePath] = it }
            ?: p.remove(Keys.terminalBackgroundImagePath)
        p[Keys.terminalBackgroundDim] = s.terminalBackgroundDim.coerceIn(0f, 0.8f)
        p[Keys.terminalBackgroundBlur] = s.terminalBackgroundBlur.coerceIn(0f, 32f)
        p[Keys.terminalHdrHighlight] = s.terminalHdrHighlight
        p[Keys.terminalFont] = s.terminalFont.name
        s.customTerminalFontPath?.let { p[Keys.customTerminalFontPath] = it }
            ?: p.remove(Keys.customTerminalFontPath)
        p[Keys.bookmarks] = s.bookmarks
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private fun decodeBottomBarStyle(p: Preferences): BottomBarStyle {
        return migrateBottomBarStyle(
            p[Keys.bottomBarStyle],
            p[Keys.floatingBottomBar] ?: true,
            p[Keys.floatingBottomBarGlass] ?: true,
        )
    }

    private object Keys {
        val scrollbackLines = intPreferencesKey("scrollback_lines")
        val commandHistoryLimit = intPreferencesKey("command_history_limit")
        val showHiddenFiles = booleanPreferencesKey("show_hidden_files")
        val colorfulFileTheme = booleanPreferencesKey("colorful_file_theme")
        val fileLayoutMode = stringPreferencesKey("file_layout_mode")
        val fileSortMode = stringPreferencesKey("file_sort_mode")
        val editorLimit = stringPreferencesKey("editor_limit")
        val scriptUseRoot = booleanPreferencesKey("script_use_root")
        val scriptPermission = stringPreferencesKey("script_permission")
        val themeSource = stringPreferencesKey("theme_source")
        val brightnessMode = stringPreferencesKey("brightness_mode")
        val terminalFontSize = floatPreferencesKey("terminal_font_size")
        val bottomBarBlur = booleanPreferencesKey("bottom_bar_blur")
        val pageTopBarBlur = booleanPreferencesKey("page_top_bar_blur")
        val blurRadius = floatPreferencesKey("blur_radius")
        val keyColor = intPreferencesKey("key_color")
        val paletteStyle = stringPreferencesKey("palette_style")
        val colorSpec = stringPreferencesKey("color_spec")
        val floatingBottomBar = booleanPreferencesKey("floating_bottom_bar")
        val floatingBottomBarGlass = booleanPreferencesKey("floating_bottom_bar_glass")
        val bottomBarStyle = stringPreferencesKey("bottom_bar_style")
        val bottomBarHdrFeedback = booleanPreferencesKey("bottom_bar_hdr_feedback")
        val showWelcomeOnLaunch = booleanPreferencesKey("show_welcome_on_launch")
        val defaultTerminalEnvironment = stringPreferencesKey("default_terminal_environment")
        val terminalTopBlur = booleanPreferencesKey("terminal_top_blur")
        val keepTerminalInBackground = booleanPreferencesKey("keep_terminal_in_background")
        val terminalBackgroundColor = intPreferencesKey("terminal_background_color")
        val terminalBackgroundImagePath = stringPreferencesKey("terminal_background_image_path")
        val terminalBackgroundDim = floatPreferencesKey("terminal_background_dim")
        val terminalBackgroundBlur = floatPreferencesKey("terminal_background_blur")
        val terminalHdrHighlight = booleanPreferencesKey("terminal_hdr_highlight")
        val terminalFont = stringPreferencesKey("terminal_font")
        val customTerminalFontPath = stringPreferencesKey("custom_terminal_font_path")
        val bookmarks = stringSetPreferencesKey("bookmarks")
        val completedOnboardingVersion = intPreferencesKey("completed_onboarding_version")
        val pendingOnboardingStep = stringPreferencesKey("pending_onboarding_step")
    }
}

internal fun decodeBottomBarHdrFeedback(value: Boolean?): Boolean = value ?: false

internal fun decodeOnboardingStep(value: String?): OnboardingStep =
    value?.let { runCatching { enumValueOf<OnboardingStep>(it) }.getOrNull() }
        ?: OnboardingStep.Welcome

internal fun migrateBottomBarStyle(stored: String?, floating: Boolean, glass: Boolean): BottomBarStyle {
    stored?.let { runCatching { enumValueOf<BottomBarStyle>(it) }.getOrNull()?.let { value -> return value } }
    return when {
        !floating -> BottomBarStyle.StandardNavigation
        glass -> BottomBarStyle.LiquidGlass
        else -> BottomBarStyle.FloatingSolid
    }
}

internal fun decodeTerminalFont(value: String?): TerminalFont = when (value) {
    "PingFang" -> TerminalFont.MiSans
    else -> value?.let { runCatching { enumValueOf<TerminalFont>(it) }.getOrNull() }
        ?: TerminalFont.SystemMono
}
