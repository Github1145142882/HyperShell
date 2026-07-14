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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.hyperShellSettings by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.hyperShellSettings.data.map(::decode)

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.hyperShellSettings.edit { preferences -> encode(preferences, transform(decode(preferences))) }
    }

    private fun decode(p: Preferences) = AppSettings(
        scrollbackLines = p[Keys.scrollbackLines] ?: 2_000,
        commandHistoryLimit = p[Keys.commandHistoryLimit] ?: 100,
        showHiddenFiles = p[Keys.showHiddenFiles] ?: true,
        fileSortMode = enumValueOrDefault(p[Keys.fileSortMode], FileSortMode.Name),
        editorLimit = enumValueOrDefault(p[Keys.editorLimit], EditorLimit.MiB4),
        scriptUseRoot = p[Keys.scriptUseRoot] ?: true,
        scriptPermission = enumValueOrDefault(p[Keys.scriptPermission], ScriptPermission.Unchanged),
        themeSource = enumValueOrDefault(p[Keys.themeSource], ThemeSource.Monet),
        brightnessMode = enumValueOrDefault(p[Keys.brightnessMode], BrightnessMode.System),
        terminalFontSize = (p[Keys.terminalFontSize] ?: 13f).coerceIn(9f, 28f),
        bottomBarBlur = p[Keys.bottomBarBlur] ?: true,
        blurRadius = (p[Keys.blurRadius] ?: 20f).coerceIn(0f, 40f),
        keyColor = p[Keys.keyColor] ?: 0,
        paletteStyle = p[Keys.paletteStyle] ?: "TonalSpot",
        colorSpec = p[Keys.colorSpec] ?: "Spec2021",
        floatingBottomBar = p[Keys.floatingBottomBar] ?: true,
        floatingBottomBarGlass = p[Keys.floatingBottomBarGlass] ?: true,
        terminalTopBlur = p[Keys.terminalTopBlur] ?: true,
        keepTerminalInBackground = p[Keys.keepTerminalInBackground] ?: false,
        terminalBackgroundColor = p[Keys.terminalBackgroundColor] ?: 0xFF000000.toInt(),
        terminalBackgroundImagePath = p[Keys.terminalBackgroundImagePath],
        terminalBackgroundDim = (p[Keys.terminalBackgroundDim] ?: 0.35f).coerceIn(0f, 0.8f),
        terminalBackgroundBlur = (p[Keys.terminalBackgroundBlur] ?: 0f).coerceIn(0f, 32f),
        terminalFont = decodeTerminalFont(p[Keys.terminalFont]),
        customTerminalFontPath = p[Keys.customTerminalFontPath],
        bookmarks = p[Keys.bookmarks] ?: emptySet(),
    )

    private fun encode(p: androidx.datastore.preferences.core.MutablePreferences, s: AppSettings) {
        p[Keys.scrollbackLines] = s.scrollbackLines
        p[Keys.commandHistoryLimit] = s.commandHistoryLimit
        p[Keys.showHiddenFiles] = s.showHiddenFiles
        p[Keys.fileSortMode] = s.fileSortMode.name
        p[Keys.editorLimit] = s.editorLimit.name
        p[Keys.scriptUseRoot] = s.scriptUseRoot
        p[Keys.scriptPermission] = s.scriptPermission.name
        p[Keys.themeSource] = s.themeSource.name
        p[Keys.brightnessMode] = s.brightnessMode.name
        p[Keys.terminalFontSize] = s.terminalFontSize
        p[Keys.bottomBarBlur] = s.bottomBarBlur
        p[Keys.blurRadius] = s.blurRadius
        p[Keys.keyColor] = s.keyColor
        p[Keys.paletteStyle] = s.paletteStyle
        p[Keys.colorSpec] = s.colorSpec
        p[Keys.floatingBottomBar] = s.floatingBottomBar
        p[Keys.floatingBottomBarGlass] = s.floatingBottomBarGlass
        p[Keys.terminalTopBlur] = s.terminalTopBlur
        p[Keys.keepTerminalInBackground] = s.keepTerminalInBackground
        p[Keys.terminalBackgroundColor] = s.terminalBackgroundColor
        s.terminalBackgroundImagePath?.let { p[Keys.terminalBackgroundImagePath] = it }
            ?: p.remove(Keys.terminalBackgroundImagePath)
        p[Keys.terminalBackgroundDim] = s.terminalBackgroundDim.coerceIn(0f, 0.8f)
        p[Keys.terminalBackgroundBlur] = s.terminalBackgroundBlur.coerceIn(0f, 32f)
        p[Keys.terminalFont] = s.terminalFont.name
        s.customTerminalFontPath?.let { p[Keys.customTerminalFontPath] = it }
            ?: p.remove(Keys.customTerminalFontPath)
        p[Keys.bookmarks] = s.bookmarks
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private object Keys {
        val scrollbackLines = intPreferencesKey("scrollback_lines")
        val commandHistoryLimit = intPreferencesKey("command_history_limit")
        val showHiddenFiles = booleanPreferencesKey("show_hidden_files")
        val fileSortMode = stringPreferencesKey("file_sort_mode")
        val editorLimit = stringPreferencesKey("editor_limit")
        val scriptUseRoot = booleanPreferencesKey("script_use_root")
        val scriptPermission = stringPreferencesKey("script_permission")
        val themeSource = stringPreferencesKey("theme_source")
        val brightnessMode = stringPreferencesKey("brightness_mode")
        val terminalFontSize = floatPreferencesKey("terminal_font_size")
        val bottomBarBlur = booleanPreferencesKey("bottom_bar_blur")
        val blurRadius = floatPreferencesKey("blur_radius")
        val keyColor = intPreferencesKey("key_color")
        val paletteStyle = stringPreferencesKey("palette_style")
        val colorSpec = stringPreferencesKey("color_spec")
        val floatingBottomBar = booleanPreferencesKey("floating_bottom_bar")
        val floatingBottomBarGlass = booleanPreferencesKey("floating_bottom_bar_glass")
        val terminalTopBlur = booleanPreferencesKey("terminal_top_blur")
        val keepTerminalInBackground = booleanPreferencesKey("keep_terminal_in_background")
        val terminalBackgroundColor = intPreferencesKey("terminal_background_color")
        val terminalBackgroundImagePath = stringPreferencesKey("terminal_background_image_path")
        val terminalBackgroundDim = floatPreferencesKey("terminal_background_dim")
        val terminalBackgroundBlur = floatPreferencesKey("terminal_background_blur")
        val terminalFont = stringPreferencesKey("terminal_font")
        val customTerminalFontPath = stringPreferencesKey("custom_terminal_font_path")
        val bookmarks = stringSetPreferencesKey("bookmarks")
    }
}

internal fun decodeTerminalFont(value: String?): TerminalFont = when (value) {
    "PingFang" -> TerminalFont.MiSans
    else -> value?.let { runCatching { enumValueOf<TerminalFont>(it) }.getOrNull() }
        ?: TerminalFont.SystemMono
}
