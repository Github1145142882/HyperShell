package io.github.hypershell.settings

import top.yukonga.miuix.kmp.theme.ColorSchemeMode

enum class ThemeSource { Standard, Monet }
enum class BrightnessMode { System, Light, Dark }
enum class FileSortMode { Name, Modified, Size }
enum class TerminalInputMode { CommandEditor, RawPty }
enum class TerminalFont { SystemMono, SansMono, SerifMono, MiSans, Custom }
enum class EditorLimit(val mebibytes: Int) {
    MiB1(1), MiB4(4), MiB8(8), MiB16(16);

    val bytes: Int get() = mebibytes * 1_048_576
}

enum class ScriptPermission { Unchanged, Temporary0777 }

data class ScriptExecutionOptions(
    val useRoot: Boolean = true,
    val temporaryPermission: ScriptPermission = ScriptPermission.Unchanged,
    val rememberAsDefault: Boolean = false,
)

data class AppSettings(
    val scrollbackLines: Int = 2_000,
    val commandHistoryLimit: Int = 100,
    val showHiddenFiles: Boolean = true,
    val fileSortMode: FileSortMode = FileSortMode.Name,
    val editorLimit: EditorLimit = EditorLimit.MiB4,
    val scriptUseRoot: Boolean = true,
    val scriptPermission: ScriptPermission = ScriptPermission.Unchanged,
    val themeSource: ThemeSource = ThemeSource.Monet,
    val brightnessMode: BrightnessMode = BrightnessMode.System,
    val terminalFontSize: Float = 13f,
    val bottomBarBlur: Boolean = true,
    val blurRadius: Float = 20f,
    val keyColor: Int = 0,
    val paletteStyle: String = "TonalSpot",
    val colorSpec: String = "Spec2021",
    val floatingBottomBar: Boolean = true,
    val floatingBottomBarGlass: Boolean = true,
    val terminalTopBlur: Boolean = true,
    val keepTerminalInBackground: Boolean = false,
    val terminalBackgroundColor: Int = 0xFF000000.toInt(),
    val terminalBackgroundImagePath: String? = null,
    val terminalBackgroundDim: Float = 0.35f,
    val terminalBackgroundBlur: Float = 0f,
    val terminalHdrHighlight: Boolean = true,
    val terminalFont: TerminalFont = TerminalFont.SystemMono,
    val customTerminalFontPath: String? = null,
    val bookmarks: Set<String> = emptySet(),
)

fun AppSettings.colorSchemeMode(): ColorSchemeMode = when (themeSource to brightnessMode) {
    ThemeSource.Standard to BrightnessMode.System -> ColorSchemeMode.System
    ThemeSource.Standard to BrightnessMode.Light -> ColorSchemeMode.Light
    ThemeSource.Standard to BrightnessMode.Dark -> ColorSchemeMode.Dark
    ThemeSource.Monet to BrightnessMode.System -> ColorSchemeMode.MonetSystem
    ThemeSource.Monet to BrightnessMode.Light -> ColorSchemeMode.MonetLight
    ThemeSource.Monet to BrightnessMode.Dark -> ColorSchemeMode.MonetDark
    else -> ColorSchemeMode.MonetSystem
}
