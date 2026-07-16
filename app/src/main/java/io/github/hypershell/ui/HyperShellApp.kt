package io.github.hypershell.ui

import android.content.Intent
import android.app.WallpaperManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import io.github.hypershell.R
import io.github.hypershell.onboarding.HyperShellOnboarding
import io.github.hypershell.onboarding.OnboardingBrightness
import io.github.hypershell.onboarding.OnboardingBottomBarStyle
import io.github.hypershell.onboarding.OnboardingDefaultEnvironment
import io.github.hypershell.onboarding.OnboardingFileLayout
import io.github.hypershell.onboarding.OnboardingPreferences
import io.github.hypershell.onboarding.OnboardingThemeSource
import io.github.hypershell.settings.AppSettings
import io.github.hypershell.settings.BottomBarStyle
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
    if (!state.settingsLoaded || (state.onboardingEntry != null && Build.VERSION.SDK_INT >= 35)) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black))
        return
    }
    HyperShellTheme(state.settings) {
        val onboardingEntry = state.onboardingEntry
        if (onboardingEntry != null) {
            val activity = LocalActivity.current
            HyperShellOnboarding(
                step = state.onboardingStep,
                entry = onboardingEntry,
                preferences = state.settings.toOnboardingPreferences(),
                rootVerificationState = state.rootVerificationState,
                onPreferencesChange = viewModel::updateOnboardingPreferences,
                onVerifyRoot = viewModel::verifyRootForOnboarding,
                onNext = viewModel::advanceOnboarding,
                onBack = viewModel::backOnboarding,
                onExit = { activity?.finish() },
                modifier = modifier,
                logo = {
                    Image(
                        painter = painterResource(R.drawable.ic_onboarding_logo),
                        contentDescription = "HyperShell",
                        modifier = Modifier.size(96.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f),
                            blendMode = androidx.compose.ui.graphics.BlendMode.SrcIn,
                        ),
                    )
                },
            )
            return@HyperShellTheme
        }
        val fileActionBlurEnabled = Build.VERSION.SDK_INT >= 33 && isRenderEffectSupported()
        val pageSurface = MiuixTheme.colorScheme.surface
        val fileActionBackdrop = if (fileActionBlurEnabled) {
            rememberLayerBackdrop {
                drawRect(pageSurface)
                drawContent()
            }
        } else {
            null
        }
        val stack = remember { mutableStateListOf<NavKey>(ShellRoute.Main) }
        val snackbar = remember { SnackbarHostState() }
        val clipboard = LocalClipboardManager.current
        val context = LocalContext.current
        fun pop() {
            when (stack.lastOrNull()) {
                ShellRoute.ZipPreview -> viewModel.closeZip()
                else -> Unit
            }
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
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (fileActionBackdrop != null) {
                            Modifier.layerBackdrop(fileActionBackdrop)
                        } else {
                            Modifier
                        },
                    ),
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
                            MainShell(
                                state = state,
                                viewModel = viewModel,
                                onAppearance = { context.startActivity(Intent(context, AppearanceActivity::class.java)) },
                                onOnboarding = viewModel::startOnboardingReplay,
                            )
                        }
                        entry<ShellRoute.ZipPreview> {
                            ZipPreviewPage(state, viewModel, ::pop)
                        }
                    },
                )
            }
            ShellConfirmation(state, viewModel)
            FileActionOverlay(state, viewModel, fileActionBackdrop)
        }
    }
}

@Composable
private fun MainShell(
    state: HyperShellUiState,
    viewModel: HyperShellViewModel,
    onAppearance: () -> Unit,
    onOnboarding: () -> Unit,
) {
    val initial = when (state.page) {
        AppPage.Files -> 0
        AppPage.Terminal -> 1
        AppPage.Settings, AppPage.Appearance -> 2
    }
    val pager = rememberPagerState(initialPage = initial, pageCount = { 3 })
    val mainState = rememberMainPagerState(pager)
    val blurEnabled = state.settings.bottomBarStyle == BottomBarStyle.LiquidGlass &&
        Build.VERSION.SDK_INT >= 33 && isRenderEffectSupported()
    val blurBackdrop = rememberBlurBackdrop(blurEnabled)
    val surface = MiuixTheme.colorScheme.surface
    val glassBackdrop = rememberLayerBackdrop { drawRect(surface); drawContent() }

    LaunchedEffect(pager.settledPage) {
        viewModel.selectPage(listOf(AppPage.Files, AppPage.Terminal, AppPage.Settings)[pager.settledPage])
    }
    LaunchedEffect(pager.currentPage) { mainState.syncPage() }
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hdrResumeGeneration by remember(lifecycleOwner) { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hdrResumeGeneration++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(
        activity,
        state.settings.terminalHdrHighlight,
        mainState.selectedPage,
        pager.settledPage,
        hdrResumeGeneration,
    ) {
        activity ?: return@LaunchedEffect
        // Use the real pager position, not the asynchronously mirrored ViewModel page.
        // Requiring both values also disables HDR for the whole duration of a page transition.
        val terminalPageHdr = mainState.selectedPage == 1 && pager.settledPage == 1
        val terminalTextHdr = state.settings.terminalHdrHighlight && terminalPageHdr
        // v21 drives both terminal glyphs and the Miuix liquid-glass interaction from the
        // Activity HDR surface. The glass feedback is intentionally available on every page
        // (including the file toolbar), while terminalTextEnabled remains terminal-only.
        val windowHdrEnabled = terminalTextHdr || state.settings.bottomBarHdrFeedback
        TerminalHdrController.apply(
            activity = activity,
            enabled = windowHdrEnabled,
            terminalTextEnabled = terminalTextHdr,
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
                    // Page navigation is intentionally bar-only. This prevents file-pane and
                    // terminal gestures from accidentally switching the whole application page.
                    userScrollEnabled = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (state.settings.bottomBarStyle == BottomBarStyle.LiquidGlass) {
                                Modifier.layerBackdrop(glassBackdrop)
                            } else Modifier,
                        ),
                ) { page ->
                    val bottom = innerPadding.calculateBottomPadding()
                    when (page) {
                        0 -> FilesPage(state, viewModel, bottom)
                        1 -> TerminalPage(state, viewModel, bottom)
                        2 -> SettingsPage(state.settings, viewModel, bottom, onAppearance, onOnboarding)
                    }
                }
            }
            if (blurBackdrop != null) Box(Modifier.layerBackdrop(blurBackdrop)) { content() } else content()
        }
    }
}

private fun AppSettings.toOnboardingPreferences(): OnboardingPreferences = OnboardingPreferences(
    themeSource = when (themeSource) {
        io.github.hypershell.settings.ThemeSource.Standard -> OnboardingThemeSource.Standard
        io.github.hypershell.settings.ThemeSource.Monet -> OnboardingThemeSource.Monet
    },
    brightness = when (brightnessMode) {
        io.github.hypershell.settings.BrightnessMode.System -> OnboardingBrightness.System
        io.github.hypershell.settings.BrightnessMode.Light -> OnboardingBrightness.Light
        io.github.hypershell.settings.BrightnessMode.Dark -> OnboardingBrightness.Dark
    },
    fileLayout = when (fileLayoutMode) {
        io.github.hypershell.settings.FileLayoutMode.Single -> OnboardingFileLayout.Single
        io.github.hypershell.settings.FileLayoutMode.Dual -> OnboardingFileLayout.Dual
    },
    showHiddenFiles = showHiddenFiles,
    scriptUseRoot = scriptUseRoot,
    keepTerminalInBackground = keepTerminalInBackground,
    showWelcomeOnLaunch = showWelcomeOnLaunch,
    bottomBarStyle = when (bottomBarStyle) {
        BottomBarStyle.LiquidGlass -> OnboardingBottomBarStyle.LiquidGlass
        BottomBarStyle.FloatingSolid -> OnboardingBottomBarStyle.FloatingSolid
        BottomBarStyle.StandardNavigation -> OnboardingBottomBarStyle.StandardNavigation
    },
    bottomBarHdrFeedback = bottomBarHdrFeedback,
    terminalHdrHighlight = terminalHdrHighlight,
    defaultEnvironment = when (defaultTerminalEnvironment) {
        io.github.hypershell.settings.DefaultTerminalEnvironment.Termux -> OnboardingDefaultEnvironment.Termux
        io.github.hypershell.settings.DefaultTerminalEnvironment.Debian -> OnboardingDefaultEnvironment.Debian
    },
)

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
    if (state.settings.bottomBarStyle == BottomBarStyle.StandardNavigation) {
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
            isBlurEnabled = state.settings.bottomBarStyle == BottomBarStyle.LiquidGlass,
            hdrPulseEnabled = state.settings.bottomBarHdrFeedback &&
                state.settings.bottomBarStyle == BottomBarStyle.LiquidGlass,
        ) {
            items.forEachIndexed { index, item ->
                FloatingBottomBarItem(
                    onClick = { mainState.animateToPage(index) },
                    modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                ) {
                    Icon(item.first, contentDescription = item.second, tint = MiuixTheme.colorScheme.onSurface)
                    Text(
                        item.second,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        color = MiuixTheme.colorScheme.onSurface,
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
