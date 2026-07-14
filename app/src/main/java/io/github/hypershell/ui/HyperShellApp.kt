package io.github.hypershell.ui

import android.content.Intent
import android.app.WallpaperManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.hypershell.AppPage
import io.github.hypershell.AppearanceActivity
import io.github.hypershell.HyperShellEvent
import io.github.hypershell.HyperShellUiState
import io.github.hypershell.HyperShellViewModel
import io.github.hypershell.settings.AppSettings
import io.github.hypershell.settings.colorSchemeMode
import io.github.hypershell.terminal.TerminalHdrController
import io.github.hypershell.ui.kit.component.FloatingBottomBar
import io.github.hypershell.ui.kit.component.FloatingBottomBarItem
import io.github.hypershell.ui.kit.component.MainPagerState
import io.github.hypershell.ui.kit.component.rememberMainPagerState
import io.github.hypershell.ui.kit.util.BlurredBar
import io.github.hypershell.ui.kit.util.rememberBlurBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

internal val LocalMainPagerState = staticCompositionLocalOf<MainPagerState> { error("MainPagerState missing") }

private sealed interface ShellRoute : NavKey {
    data object Main : ShellRoute
    data object ZipPreview : ShellRoute
}

@Composable
internal fun HyperShellTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val mode = settings.colorSchemeMode()
    val paletteStyle = runCatching { ThemePaletteStyle.valueOf(settings.paletteStyle) }
        .getOrDefault(ThemePaletteStyle.TonalSpot)
    val colorSpec = runCatching { ThemeColorSpec.valueOf(settings.colorSpec) }
        .getOrDefault(ThemeColorSpec.Spec2021)
    val wallpaperSeed = remember(context) {
        if (Build.VERSION.SDK_INT >= 27) {
            runCatching {
                WallpaperManager.getInstance(context)
                    .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                    ?.primaryColor
                    ?.toArgb()
            }.getOrNull()
        } else null
    }
    val seedArgb = settings.keyColor.takeIf { it != 0 } ?: wallpaperSeed ?: 0xFF6750A4.toInt()
    val controller = remember(mode, seedArgb, paletteStyle, colorSpec) {
        ThemeController(
            colorSchemeMode = mode,
            keyColor = Color(seedArgb),
            paletteStyle = paletteStyle,
            colorSpec = colorSpec,
        )
    }
    MiuixTheme(controller = controller, content = content)
}

@Composable
fun HyperShellApp(viewModel: HyperShellViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HyperShellTheme(state.settings) {
        val stack = remember { mutableStateListOf<NavKey>(ShellRoute.Main) }
        val snackbar = remember { SnackbarHostState() }
        val clipboard = LocalClipboardManager.current
        val context = LocalContext.current

        fun pop() {
            if (stack.lastOrNull() == ShellRoute.ZipPreview) viewModel.closeZip()
            if (stack.size > 1) stack.removeAt(stack.lastIndex)
        }

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is HyperShellEvent.Message -> snackbar.showSnackbar(event.text)
                    is HyperShellEvent.CopyPath -> {
                        clipboard.setText(AnnotatedString(event.path))
                        snackbar.showSnackbar("路径已复制")
                    }
                    HyperShellEvent.OpenZipPreview -> if (stack.lastOrNull() != ShellRoute.ZipPreview) stack += ShellRoute.ZipPreview
                    HyperShellEvent.CloseSecondary -> pop()
                }
            }
        }

        Scaffold(
            modifier = modifier.fillMaxSize().background(MiuixTheme.colorScheme.background),
            snackbarHost = { SnackbarHost(snackbar) },
        ) {
            NavDisplay(
                backStack = stack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                onBack = ::pop,
                entryProvider = entryProvider {
                    entry<ShellRoute.Main> {
                        MainShell(state, viewModel) {
                            context.startActivity(Intent(context, AppearanceActivity::class.java))
                        }
                    }
                    entry<ShellRoute.ZipPreview> {
                        ZipPreviewPage(state, viewModel, ::pop)
                    }
                },
            )
            ShellConfirmation(state, viewModel)
            FileActionOverlay(state, viewModel)
        }
    }
}

@Composable
private fun MainShell(
    state: HyperShellUiState,
    viewModel: HyperShellViewModel,
    onAppearance: () -> Unit,
) {
    val initial = when (state.page) {
        AppPage.Files -> 0
        AppPage.Terminal -> 1
        AppPage.Settings, AppPage.Appearance -> 2
    }
    val pager = rememberPagerState(initialPage = initial, pageCount = { 3 })
    val mainState = rememberMainPagerState(pager)
    val blurEnabled = state.settings.bottomBarBlur && Build.VERSION.SDK_INT >= 33 && isRenderEffectSupported()
    val blurBackdrop = rememberBlurBackdrop(blurEnabled)
    val surface = MiuixTheme.colorScheme.surface
    val glassBackdrop = rememberLayerBackdrop { drawRect(surface); drawContent() }

    LaunchedEffect(pager.settledPage) {
        viewModel.selectPage(listOf(AppPage.Files, AppPage.Terminal, AppPage.Settings)[pager.settledPage])
    }
    LaunchedEffect(pager.currentPage) { mainState.syncPage() }
    val activity = LocalActivity.current
    LaunchedEffect(
        activity,
        state.settings.terminalHdrHighlight,
        state.settings.floatingBottomBarGlass,
        mainState.selectedPage,
        pager.settledPage,
    ) {
        activity ?: return@LaunchedEffect
        // Use the real pager position, not the asynchronously mirrored ViewModel page.
        // Requiring both values also disables HDR for the whole duration of a page transition.
        val terminalPageHdr = mainState.selectedPage == 1 && pager.settledPage == 1
        // The local F16/scRGB navigation surface still needs an HDR-capable parent window.
        // Keep that capability across all three pages, but only feed extended-range glyphs to
        // the renderer while the terminal page is settled. Ordinary Compose top bars remain SDR.
        val windowHdrEnabled = state.settings.terminalHdrHighlight &&
            (terminalPageHdr || state.settings.floatingBottomBarGlass)
        TerminalHdrController.apply(
            activity = activity,
            enabled = windowHdrEnabled,
            terminalTextEnabled = terminalPageHdr,
        ).onFailure { error ->
            if (windowHdrEnabled) viewModel.reportHdrUnavailable(error.message ?: "当前设备无法输出真 HDR")
        }
    }
    DisposableEffect(activity) {
        onDispose { activity?.let { TerminalHdrController.apply(it, false) } }
    }
    MainPagerBackHandler(mainState)

    CompositionLocalProvider(LocalMainPagerState provides mainState) {
        Scaffold(
            bottomBar = {
                ShellBottomBar(
                    state = state,
                    blurBackdrop = blurBackdrop,
                    glassBackdrop = glassBackdrop,
                )
            },
        ) { innerPadding ->
            val content = @Composable {
                HorizontalPager(
                    state = pager,
                    beyondViewportPageCount = 1,
                    // Termux owns horizontal/vertical touch gestures for transcript scrolling,
                    // selection and mouse tracking. Navigation remains available from the bar.
                    userScrollEnabled = pager.currentPage != 1,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (state.settings.floatingBottomBar && state.settings.floatingBottomBarGlass) {
                                Modifier.layerBackdrop(glassBackdrop)
                            } else Modifier,
                        ),
                ) { page ->
                    val bottom = innerPadding.calculateBottomPadding()
                    when (page) {
                        0 -> FilesPage(state, viewModel, bottom)
                        1 -> TerminalPage(state, viewModel, bottom)
                        2 -> SettingsPage(state.settings, viewModel, bottom, onAppearance)
                    }
                }
            }
            if (blurBackdrop != null) Box(Modifier.layerBackdrop(blurBackdrop)) { content() } else content()
        }
    }
}

@Composable
private fun ShellBottomBar(
    state: HyperShellUiState,
    blurBackdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop?,
    glassBackdrop: top.yukonga.miuix.kmp.blur.Backdrop,
) {
    val mainState = LocalMainPagerState.current
    val items = listOf(
        Triple(MiuixIcons.Folder, "文件", AppPage.Files),
        Triple(MiuixIcons.Play, "终端", AppPage.Terminal),
        Triple(MiuixIcons.Settings, "设置", AppPage.Settings),
    )
    if (!state.settings.floatingBottomBar) {
        BlurredBar(blurBackdrop) {
            NavigationBar(color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface) {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        modifier = Modifier.weight(1f),
                        selected = mainState.selectedPage == index,
                        onClick = { mainState.animateToPage(index) },
                        icon = item.first,
                        label = item.second,
                    )
                }
            }
        }
        return
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        FloatingBottomBar(
            modifier = Modifier
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            selectedIndex = { mainState.selectedPage },
            onSelected = mainState::animateToPage,
            backdrop = glassBackdrop,
            tabsCount = items.size,
            isBlurEnabled = state.settings.floatingBottomBarGlass && state.settings.bottomBarBlur,
            hdrPulseEnabled = state.settings.terminalHdrHighlight && state.settings.floatingBottomBarGlass,
        ) { selectedIndex, hdrIntensity ->
            items.forEachIndexed { index, item ->
                val baseColor = MiuixTheme.colorScheme.onSurface
                val itemColor = if (index == selectedIndex && hdrIntensity > 0.001f) {
                    val linearBase = baseColor.convert(ColorSpaces.LinearExtendedSrgb)
                    Color(
                        red = linearBase.red + (4f - linearBase.red) * hdrIntensity,
                        green = linearBase.green + (4f - linearBase.green) * hdrIntensity,
                        blue = linearBase.blue + (4f - linearBase.blue) * hdrIntensity,
                        alpha = linearBase.alpha,
                        colorSpace = ColorSpaces.LinearExtendedSrgb,
                    )
                } else {
                    baseColor
                }
                FloatingBottomBarItem(
                    onClick = { mainState.animateToPage(index) },
                    modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                ) {
                    Icon(item.first, contentDescription = item.second, tint = itemColor)
                    Text(
                        item.second,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        color = itemColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun MainPagerBackHandler(state: MainPagerState) {
    val enabled by remember { derivedStateOf { state.selectedPage != 0 } }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = enabled,
        onBackCompleted = { state.animateToPage(0) },
    )
}
