/*
 * This file is part of HyperShell.
 *
 * The state progression, welcome composition, and setup-complete presentation are
 * adapted from HyperCeiler's provision module at commit 7266aaa0d698ad10795381c5bf23651c2e1719d0.
 *
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 * Copyright (C) 2026 HyperShell Contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package io.github.hypershell.onboarding

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
import io.github.hypershell.onboarding.renderengine.GlowController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HyperShellOnboarding(
    step: OnboardingStep,
    entry: OnboardingEntry,
    preferences: OnboardingPreferences,
    rootVerificationState: RootVerificationState,
    onPreferencesChange: (OnboardingPreferences) -> Unit,
    onVerifyRoot: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onExit: () -> Unit,
    logo: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = { if (step == OnboardingStep.Welcome) onExit() else onBack() },
    )
    Scaffold(modifier = modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = step,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = transition@{
                val forward = targetState.ordinal > initialState.ordinal
                if (initialState == OnboardingStep.Welcome && forward) {
                    return@transition EnterTransition.None.togetherWith(ExitTransition.None)
                }
                val enter = slideInHorizontally(tween(350, easing = HyperCeilerAccelerateDecelerate)) { width -> if (forward) width else -width }
                val exit = slideOutHorizontally(tween(350, easing = HyperCeilerAccelerateDecelerate)) { width -> if (forward) -width else width }
                enter.togetherWith(exit).using(SizeTransform(clip = false))
            },
            label = "HyperShell onboarding page",
        ) { current ->
            when (current) {
                OnboardingStep.Welcome -> WelcomePage(
                    logo = logo,
                    onNext = onNext,
                    revealContent = {
                        EnvironmentPage(
                            rootVerificationState = rootVerificationState,
                            onVerifyRoot = onVerifyRoot,
                            onBack = {},
                            onNext = {},
                        )
                    },
                )
                OnboardingStep.Environment -> EnvironmentPage(
                    rootVerificationState = rootVerificationState,
                    onVerifyRoot = onVerifyRoot,
                    onBack = onBack,
                    onNext = onNext,
                )
                OnboardingStep.BasicSettings -> BasicSettingsPage(
                    preferences = preferences,
                    onPreferencesChange = onPreferencesChange,
                    onBack = onBack,
                    onNext = onNext,
                )
                OnboardingStep.Complete -> CompletePage(
                    entry = entry,
                    logo = logo,
                    onBack = onBack,
                    onComplete = onNext,
                )
            }
        }
    }
}

@Composable
private fun WelcomePage(
    logo: @Composable () -> Unit,
    onNext: () -> Unit,
    revealContent: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val lowRam = remember(context) {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    }
    val supportsOriginalShader = Build.VERSION.SDK_INT >= 33 && !lowRam
    val logoScale = remember { Animatable(if (supportsOriginalShader) 0.5f else 1f) }
    val logoAlpha = remember { Animatable(if (supportsOriginalShader) 0f else 1f) }
    val buttonScale = remember { Animatable(if (supportsOriginalShader) 0.9f else 1f) }
    val buttonAlpha = remember { Animatable(if (supportsOriginalShader) 0f else 1f) }
    val expansion = remember { Animatable(0f) }
    var expanding by remember { mutableStateOf(false) }
    var rootHeight by remember { mutableFloatStateOf(1f) }
    var buttonCenter by remember { mutableStateOf(Offset.Zero) }
    var logoTextCenterY by remember { mutableFloatStateOf(0.5f) }
    var glowController by remember { mutableStateOf<GlowController?>(null) }
    val density = LocalDensity.current
    val buttonRadiusPx = with(density) { 35.dp.toPx() }

    LaunchedEffect(supportsOriginalShader) {
        if (!supportsOriginalShader) return@LaunchedEffect
        launch {
            logoAlpha.animateTo(1f, tween(durationMillis = 390, delayMillis = 60))
        }
        launch {
            logoScale.animateTo(0.95f, tween(440, easing = HyperCeilerSinOut))
            logoScale.animateTo(1f, tween(700, easing = HyperCeilerCubicOut))
        }
        launch {
            delay(1340)
            launch { buttonAlpha.animateTo(1f, tween(450, easing = HyperCeilerCubicOut)) }
            buttonScale.animateTo(1f, tween(450, easing = HyperCeilerCubicOut))
        }
    }
    LaunchedEffect(expanding) {
        if (!expanding) return@LaunchedEffect
        expansion.animateTo(1f, tween(505, easing = HyperCeilerAccelerateDecelerate))
        onNext()
    }
    DisposableEffect(Unit) { onDispose { glowController?.stop() } }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { rootHeight = it.height.toFloat().coerceAtLeast(1f) }
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (supportsOriginalShader) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    android.view.View(viewContext).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        glowController = GlowController(this).also {
                            it.start(true)
                            it.setCircleCenterY(logoTextCenterY)
                        }
                    }
                },
                update = { glowController?.setCircleCenterY(logoTextCenterY) },
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xff9a28b8), Color(0xff9aa8f5), Color(0xff4d4ad7)),
                        center = Offset(size.width * 0.5f, size.height * 0.44f),
                        radius = size.maxDimension,
                    ),
                )
            }
        }

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(30f))
            Box(
                Modifier.size(90.dp).graphicsLayer {
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                    alpha = logoAlpha.value
                },
                contentAlignment = Alignment.Center,
            ) { logo() }
            Box(Modifier.weight(40f).fillMaxWidth()) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp)
                        .size(width = 320.dp, height = 45.28.dp)
                        .onGloballyPositioned {
                            logoTextCenterY = (it.positionInRoot().y + it.size.height / 2f) / rootHeight
                            glowController?.setCircleCenterY(logoTextCenterY)
                        }
                        .graphicsLayer {
                            scaleX = logoScale.value
                            scaleY = logoScale.value
                            alpha = logoAlpha.value
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "HyperShell",
                        color = Color.White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(4.5.dp)
                        .size(70.dp)
                        .onGloballyPositioned {
                            val position = it.positionInRoot()
                            buttonCenter = Offset(position.x + it.size.width / 2f, position.y + it.size.height / 2f)
                        }
                        .graphicsLayer {
                            scaleX = buttonScale.value
                            scaleY = buttonScale.value
                            alpha = buttonAlpha.value
                        }
                        .background(Color.Black.copy(alpha = 0.60f), CircleShape)
                        .clickable(enabled = buttonAlpha.value >= 0.99f && !expanding) { expanding = true }
                        .semantics { contentDescription = "开始设置" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        MiuixIcons.Basic.ArrowRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(width = 31.dp, height = 22.dp),
                    )
                }
            }
            Spacer(Modifier.weight(20f))
        }

        if (expanding) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val farthestX = maxOf(buttonCenter.x, size.width - buttonCenter.x)
                        val farthestY = maxOf(buttonCenter.y, size.height - buttonCenter.y)
                        val finalRadius = kotlin.math.sqrt(farthestX * farthestX + farthestY * farthestY)
                        val radius = buttonRadiusPx + (finalRadius - buttonRadiusPx) * expansion.value
                        val reveal = Path().apply {
                            addOval(
                                Rect(
                                    left = buttonCenter.x - radius,
                                    top = buttonCenter.y - radius,
                                    right = buttonCenter.x + radius,
                                    bottom = buttonCenter.y + radius,
                                ),
                            )
                        }
                        clipPath(reveal) { this@drawWithContent.drawContent() }
                    },
            ) { revealContent() }
        }
    }
}

private val HyperCeilerSinOut = Easing { value -> kotlin.math.sin(value * kotlin.math.PI.toFloat() / 2f) }
private val HyperCeilerCubicOut = Easing { value -> 1f - (1f - value) * (1f - value) * (1f - value) }
private val HyperCeilerAccelerateDecelerate = Easing { value ->
    ((kotlin.math.cos((value + 1f) * kotlin.math.PI.toFloat()) / 2f) + 0.5f)
}

@Composable
private fun EnvironmentPage(
    rootVerificationState: RootVerificationState,
    onVerifyRoot: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    OnboardingListPage(title = "运行环境", onBack = onBack, onNext = onNext) {
        item {
            Text(
                "选择现在要检查的运行条件。其余设置可稍后在应用内修改。",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
            )
        }
        item { ProvisionPermissionItem("内嵌终端", selected = true) }
        item { ProvisionPermissionItem("Root 文件访问", selected = rootVerificationState == RootVerificationState.Available) }
        item {
            Text(
                when (rootVerificationState) {
                    RootVerificationState.Idle -> "Root 不会自动请求。点按下方项目可验证管理器已授予的 UID。"
                    RootVerificationState.Checking -> "正在验证管理器授予的 UID…"
                    RootVerificationState.Available -> "Root 可用，UID 0 验证通过。"
                    RootVerificationState.Unavailable -> "Root 当前不可用；仍可继续并使用普通终端。"
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
            )
        }
        item {
            ProvisionPermissionItem(
                title = if (rootVerificationState == RootVerificationState.Available) "重新验证 Root" else "验证 Root",
                selected = rootVerificationState == RootVerificationState.Available,
                enabled = rootVerificationState != RootVerificationState.Checking,
                onClick = onVerifyRoot,
            )
        }
        item {
            ProvisionPermissionItem(
                title = "数据最小化",
                selected = true,
            )
        }
    }
}

@Composable
private fun ProvisionPermissionItem(
    title: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.headline1,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f),
        )
        Box(
            Modifier
                .size(22.dp)
                .border(2.dp, if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(12.dp).background(MiuixTheme.colorScheme.primary, CircleShape))
        }
    }
}

@Composable
private fun BasicSettingsPage(
    preferences: OnboardingPreferences,
    onPreferencesChange: (OnboardingPreferences) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    OnboardingListPage(title = "基础设置", onBack = onBack, onNext = onNext) {
        item {
            OverlayDropdownPreference(
                    items = listOf("标准 Miuix", "Monet 动态色"),
                    selectedIndex = preferences.themeSource.ordinal,
                    title = "配色来源",
                    onSelectedIndexChange = { index ->
                        onPreferencesChange(preferences.copy(themeSource = OnboardingThemeSource.entries[index]))
                    },
                )
        }
        item {
            OverlayDropdownPreference(
                    items = listOf("跟随系统", "浅色", "深色"),
                    selectedIndex = preferences.brightness.ordinal,
                    title = "明暗模式",
                    onSelectedIndexChange = { index ->
                        onPreferencesChange(preferences.copy(brightness = OnboardingBrightness.entries[index]))
                    },
                )
        }
        item {
            OverlayDropdownPreference(
                    items = listOf("单窗格", "双窗格"),
                    selectedIndex = preferences.fileLayout.ordinal,
                    title = "文件布局",
                    onSelectedIndexChange = { index ->
                        onPreferencesChange(preferences.copy(fileLayout = OnboardingFileLayout.entries[index]))
                    },
                )
        }
        item {
            SwitchPreference(
                    checked = preferences.showHiddenFiles,
                    onCheckedChange = { onPreferencesChange(preferences.copy(showHiddenFiles = it)) },
                    title = "显示隐藏文件",
                )
        }
        item {
            SwitchPreference(
                    checked = preferences.scriptUseRoot,
                    onCheckedChange = { onPreferencesChange(preferences.copy(scriptUseRoot = it)) },
                    title = "脚本默认使用 Root",
                    summary = "每次执行仍会显示操作面板",
                )
        }
        item {
            SwitchPreference(
                    checked = preferences.keepTerminalInBackground,
                    onCheckedChange = { onPreferencesChange(preferences.copy(keepTerminalInBackground = it)) },
                    title = "切到后台时保留终端",
                    summary = "关闭时应用进入后台会终止 PTY 进程组",
                )
        }
    }
}

@Composable
private fun CompletePage(
    entry: OnboardingEntry,
    logo: @Composable () -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surfaceContainer).windowInsetsPadding(WindowInsets.safeDrawing)) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 20.dp, top = 8.dp).size(40.dp)) {
            Icon(MiuixIcons.Back, contentDescription = "返回")
        }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(18f))
            Column(
                Modifier.weight(40f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(90.dp), contentAlignment = Alignment.Center) { logo() }
                Text(
                    "HyperShell",
                    modifier = Modifier.padding(top = 20.dp),
                    style = MiuixTheme.textStyles.title1,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "设置完毕",
                    modifier = Modifier.padding(top = 30.dp),
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    fontSize = 24.sp,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .widthIn(max = 336.dp)
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(MiuixTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                        .clickable(onClick = onComplete),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (entry == OnboardingEntry.Replay) "返回设置" else "开始使用",
                        color = MiuixTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.height(44.dp))
            }
            Spacer(Modifier.weight(0.2f))
        }
    }
}

@Composable
private fun OnboardingListPage(
    title: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(MiuixIcons.Back, contentDescription = "返回") }
            Text(title, modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.title2)
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 336.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(50.dp)
                .background(MiuixTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                .clickable(onClick = onNext),
            contentAlignment = Alignment.Center,
        ) { Text("继续", color = MiuixTheme.colorScheme.onPrimary) }
        Spacer(Modifier.height(44.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun HyperShellOnboardingPreview() {
    MiuixTheme {
        HyperShellOnboarding(
            step = OnboardingStep.BasicSettings,
            entry = OnboardingEntry.FirstRun,
            preferences = OnboardingPreferences(
                OnboardingThemeSource.Monet,
                OnboardingBrightness.System,
                OnboardingFileLayout.Dual,
                showHiddenFiles = true,
                scriptUseRoot = true,
                keepTerminalInBackground = false,
                showWelcomeOnLaunch = false,
                bottomBarStyle = OnboardingBottomBarStyle.LiquidGlass,
                bottomBarHdrFeedback = false,
                terminalHdrHighlight = true,
                defaultEnvironment = OnboardingDefaultEnvironment.Termux,
            ),
            rootVerificationState = RootVerificationState.Idle,
            onPreferencesChange = {},
            onVerifyRoot = {},
            onNext = {},
            onBack = {},
            onExit = {},
            logo = { Box(Modifier.size(72.dp).background(MiuixTheme.colorScheme.primary, CircleShape)) },
        )
    }
}
