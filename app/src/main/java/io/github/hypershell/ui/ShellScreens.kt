package io.github.hypershell.ui

import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.view.TerminalView
import io.github.hypershell.Confirmation
import io.github.hypershell.HyperShellUiState
import io.github.hypershell.HyperShellViewModel
import io.github.hypershell.files.FileKind
import io.github.hypershell.files.RootAccess
import io.github.hypershell.files.RootFileEntry
import io.github.hypershell.files.ZipItem
import io.github.hypershell.settings.AppSettings
import io.github.hypershell.settings.BrightnessMode
import io.github.hypershell.settings.EditorLimit
import io.github.hypershell.settings.FileSortMode
import io.github.hypershell.settings.FontInstallState
import io.github.hypershell.settings.ScriptPermission
import io.github.hypershell.settings.ThemeSource
import io.github.hypershell.settings.TerminalFont
import io.github.hypershell.terminal.TerminalMode
import io.github.hypershell.terminal.TerminalRuntime
import io.github.hypershell.terminal.TerminalStatus
import io.github.hypershell.terminal.TermuxEnvironmentStatus
import io.github.hypershell.ui.kit.util.BlurredBar
import io.github.hypershell.ui.kit.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Unpin
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File as JavaFile

@Composable
private fun KitPage(
    title: String,
    blur: Boolean,
    bottomPadding: Dp,
    navigation: (@Composable () -> Unit)? = null,
    subtitle: String = "",
    header: (@Composable () -> Unit)? = null,
    compact: Boolean = false,
    actions: @Composable () -> Unit = {},
    content: @Composable (Modifier, PaddingValues, Dp, ScrollBehavior, LayerBackdrop?) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop(blur && Build.VERSION.SDK_INT >= 33 && isRenderEffectSupported())
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                Column {
                    if (compact) {
                        SmallTopAppBar(
                            color = barColor,
                            title = title,
                            subtitle = subtitle,
                            scrollBehavior = scrollBehavior,
                            navigationIcon = navigation ?: {},
                            actions = { actions() },
                        )
                    } else {
                        TopAppBar(
                            color = barColor,
                            title = title,
                            subtitle = subtitle,
                            scrollBehavior = scrollBehavior,
                            navigationIcon = navigation ?: {},
                            actions = { actions() },
                        )
                    }
                    header?.invoke()
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        val modifier = if (backdrop != null) Modifier.fillMaxSize().layerBackdrop(backdrop) else Modifier.fillMaxSize()
        content(modifier, innerPadding, bottomPadding, scrollBehavior, backdrop)
    }
}

@Composable
fun FilesPage(state: HyperShellUiState, vm: HyperShellViewModel, bottomPadding: Dp) {
    if (state.document != null || state.textPage != null) {
        EditorPage(state, vm, bottomPadding)
        return
    }
    var pathDialog by remember { mutableStateOf(false) }
    var pathValue by remember(state.currentPath) { mutableStateOf(state.currentPath) }
    val animatedEntries = remember(state.currentPath) { mutableSetOf<String>() }
    val listState = rememberLazyListState()
    KitPage("文件", state.settings.bottomBarBlur, bottomPadding) { modifier, scaffoldPadding, navigationPadding, scroll, _ ->
        LazyColumn(
            state = listState,
            modifier = modifier
                .padding(horizontal = 12.dp)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scroll.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = scaffoldPadding.calculateTopPadding() + 12.dp,
                bottom = navigationPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            overscrollEffect = null,
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = vm::navigateUp) { Icon(MiuixIcons.Back, "上级") }
                        IconButton(onClick = { vm.jumpToPath("/") }) { Icon(MiuixIcons.Home, "根目录") }
                        BasicComponent(
                            modifier = Modifier.weight(1f),
                            title = state.currentPath,
                            onClick = { pathDialog = true },
                        )
                        IconButton(onClick = { vm.toggleBookmark(state.currentPath) }) {
                            Icon(
                                if (state.currentPath in state.settings.bookmarks) MiuixIcons.Unpin else MiuixIcons.Pin,
                                if (state.currentPath in state.settings.bookmarks) "取消文件夹书签" else "添加文件夹书签",
                            )
                        }
                        IconButton(onClick = { vm.loadDirectory(state.currentPath) }) { Icon(MiuixIcons.Refresh, "刷新") }
                    }
                }
            }
            if (state.settings.bookmarks.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column {
                            Text(
                                "书签",
                                modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp),
                                color = MiuixTheme.colorScheme.primary,
                                style = MiuixTheme.textStyles.footnote1,
                            )
                            state.settings.bookmarks.sorted().forEach { path ->
                                BasicComponent(
                                    title = path.substringAfterLast('/').ifEmpty { "/" },
                                    summary = path,
                                    onClick = { vm.openBookmark(path) },
                                    endActions = {
                                        IconButton(onClick = { vm.createShortcut(path) }) { Icon(MiuixIcons.Home, "创建桌面快捷方式") }
                                        IconButton(onClick = { vm.toggleBookmark(path) }) { Icon(MiuixIcons.Unpin, "移除书签") }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item {
                TextField(
                    value = state.searchQuery,
                    onValueChange = vm::setSearchQuery,
                    label = "搜索当前目录",
                    singleLine = true,
                    insideMargin = DpSize(12.dp, 8.dp),
                )
            }
            when (state.rootAccess) {
                RootAccess.Granted -> {
                    if (state.filesLoading) item { Text("正在读取…", color = MiuixTheme.colorScheme.primary, modifier = Modifier.padding(12.dp)) }
                    items(state.entries, key = RootFileEntry::path) { entry ->
                        FileRow(
                            entry = entry,
                            animateRead = state.filesLoading &&
                                !listState.isScrollInProgress &&
                                animatedEntries.add(entry.path),
                        ) { vm.openEntry(entry) }
                    }
                    if (!state.filesLoading && state.entries.isEmpty()) item { EmptyCard("当前目录没有匹配项目") }
                }
                RootAccess.Unknown -> item { RootStateCard("正在检查 Root 文件访问…", vm::requestRootFiles) }
                RootAccess.Denied -> item { RootStateCard("Root 管理器未授予 UID 0", vm::requestRootFiles) }
                RootAccess.Unavailable -> item { RootStateCard("系统未提供可用的 su", vm::requestRootFiles) }
            }
        }
        OverlayDialog(
            show = pathDialog,
            onDismissRequest = { pathDialog = false },
            title = "跳转路径",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(pathValue, { pathValue = it }, label = "绝对路径", singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { pathDialog = false }, modifier = Modifier.weight(1f)) { Text("取消") }
                        Button(onClick = { pathDialog = false; vm.jumpToPath(pathValue) }, modifier = Modifier.weight(1f)) { Text("打开") }
                    }
                }
            },
        )
    }
}

@Composable
private fun FileRow(
    entry: RootFileEntry,
    modifier: Modifier = Modifier,
    animateRead: Boolean = false,
    onClick: () -> Unit,
) {
    var appeared by remember(entry.path) { mutableStateOf(!animateRead) }
    LaunchedEffect(entry.path) { appeared = true }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.15f,
        animationSpec = tween(140),
        label = "file-read-${entry.path}",
    )
    val icon = if (entry.kind == FileKind.Directory) MiuixIcons.Folder else MiuixIcons.File
    val summary = when (entry.kind) {
        FileKind.Directory -> "文件夹"
        FileKind.SymbolicLink -> "符号链接"
        FileKind.Other -> "特殊文件"
        FileKind.Regular -> if (entry.size >= 0) formatBytes(entry.size) else "文件"
    }
    Card(
        modifier.graphicsLayer { this.alpha = alpha }.fillMaxWidth(),
        onClick = onClick,
        showIndication = true,
    ) {
        BasicComponent(
            title = entry.name,
            summary = summary,
            startAction = { Icon(icon, null, modifier = Modifier.padding(end = 8.dp), tint = MiuixTheme.colorScheme.primary) },
            onClick = onClick,
        )
    }
}

@Composable
private fun RootStateCard(text: String, retry: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        BasicComponent(title = text, summary = "请在 KernelSU / Magisk 管理器中预先授予权限", onClick = retry)
    }
}

@Composable
fun TerminalPage(state: HyperShellUiState, vm: HyperShellViewModel, bottomPadding: Dp) {
    LaunchedEffect(Unit) { vm.ensureTerminal() }
    var ctrlArmed by remember { mutableStateOf(false) }
    var altArmed by remember { mutableStateOf(false) }
    val sendInput: (String) -> Unit = { raw ->
        var value = raw
        if (ctrlArmed && value.isNotEmpty()) {
            val first = value.first()
            val control = when {
                first in 'a'..'z' -> (first.code - 'a'.code + 1).toChar()
                first in 'A'..'Z' -> (first.code - 'A'.code + 1).toChar()
                first == ' ' -> '\u0000'
                else -> first
            }
            value = control + value.drop(1)
            ctrlArmed = false
        }
        if (altArmed && value.isNotEmpty()) {
            value = "\u001b$value"
            altArmed = false
        }
        vm.sendRawInput(value)
    }
    KitPage(
        title = "终端",
        subtitle = terminalSubtitle(state.terminalStatus, state.termuxEnvironmentStatus, state.terminalRuntime),
        blur = state.settings.terminalTopBlur,
        bottomPadding = bottomPadding,
        compact = true,
        actions = {
            IconButton(onClick = vm::toggleTerminalRuntime) {
                Icon(
                    MiuixIcons.Home,
                    if (state.terminalRuntime == TerminalRuntime.Termux) "切换到 Ubuntu" else "切换到 Termux",
                    tint = if (state.terminalRuntime == TerminalRuntime.Ubuntu) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                )
            }
            val keep = state.settings.keepTerminalInBackground
            IconButton(onClick = { vm.updateSettings { it.copy(keepTerminalInBackground = !keep) } }) {
                Icon(
                    if (keep) MiuixIcons.Pin else MiuixIcons.Unpin,
                    if (keep) "关闭后台保留" else "开启后台保留",
                    tint = if (keep) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                )
            }
        },
    ) { modifier, scaffoldPadding, _, _, _ ->
        val density = LocalDensity.current
        val imeBottom = with(density) { WindowInsets.ime.getBottom(this).toDp() }
        val contentBottom = maxOf(bottomPadding, imeBottom)
        Box(
            modifier = modifier
                .padding(start = 12.dp, end = 12.dp)
                .padding(bottom = contentBottom),
        ) {
            TermuxTerminalCanvas(
                vm = vm,
                fontSize = state.settings.terminalFontSize,
                backgroundColor = state.settings.terminalBackgroundColor,
                backgroundImagePath = state.settings.terminalBackgroundImagePath,
                backgroundDim = state.settings.terminalBackgroundDim,
                backgroundBlur = state.settings.terminalBackgroundBlur,
                terminalFont = state.settings.terminalFont,
                customFontPath = state.settings.customTerminalFontPath,
                contentTopInset = scaffoldPadding.calculateTopPadding() + 12.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 66.dp),
            )
            TerminalKeys(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 12.dp),
                ctrlArmed = ctrlArmed,
                altArmed = altArmed,
                toggleCtrl = { ctrlArmed = !ctrlArmed },
                toggleAlt = { altArmed = !altArmed },
                sendInput = sendInput,
                vm = vm,
            )
        }
    }
}

@Composable
private fun TermuxTerminalCanvas(
    vm: HyperShellViewModel,
    fontSize: Float,
    backgroundColor: Int,
    backgroundImagePath: String?,
    backgroundDim: Float,
    backgroundBlur: Float,
    terminalFont: TerminalFont,
    customFontPath: String?,
    contentTopInset: Dp,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val textSizePx = with(density) { fontSize.sp.toPx().toInt() }
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    LaunchedEffect(textSizePx) { vm.updateTerminalViewTextSize(textSizePx) }
    val hasBackgroundImage = backgroundImagePath?.let(::JavaFile)?.isFile == true
    LaunchedEffect(backgroundColor, terminalFont, customFontPath, hasBackgroundImage) {
        vm.updateTerminalAppearance(backgroundColor, terminalFont, customFontPath, hasBackgroundImage)
    }
    DisposableEffect(vm) {
        onDispose { terminalView?.let(vm::detachTerminalView) }
    }
    Card(modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(backgroundColor))) {
            val widthPx = constraints.maxWidth.coerceAtLeast(1)
            val heightPx = constraints.maxHeight.coerceAtLeast(1)
            val density = LocalDensity.current
            val blurPx = with(density) { backgroundBlur.dp.toPx() }
            val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                initialValue = null,
                backgroundImagePath,
                widthPx,
                heightPx,
                backgroundBlur,
            ) {
                value = backgroundImagePath
                    ?.let(::JavaFile)
                    ?.takeIf(JavaFile::isFile)
                    ?.let { file ->
                        withContext(Dispatchers.Default) {
                            decodeTerminalBackground(
                                file,
                                widthPx,
                                heightPx,
                                if (Build.VERSION.SDK_INT < 31) blurPx.toInt() else 0,
                            )?.asImageBitmap()
                        }
                    }
            }
            image?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (Build.VERSION.SDK_INT >= 31 && blurPx > 0f) {
                                renderEffect = BlurEffect(blurPx, blurPx, TileMode.Decal)
                            }
                        },
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = backgroundDim.coerceIn(0f, 0.8f))),
                )
            }
            AndroidView(
                factory = { context ->
                    TerminalView(context, null).also { view ->
                        view.setBackgroundColor(if (hasBackgroundImage) android.graphics.Color.TRANSPARENT else backgroundColor)
                        terminalView = view
                        vm.attachTerminalView(
                            view,
                            textSizePx,
                            backgroundColor,
                            terminalFont,
                            customFontPath,
                            hasBackgroundImage,
                        )
                    }
                },
                update = { view ->
                    vm.attachTerminalView(
                        view,
                        textSizePx,
                        backgroundColor,
                        terminalFont,
                        customFontPath,
                        hasBackgroundImage,
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = contentTopInset),
            )
        }
    }
}

private suspend fun decodeTerminalBackground(
    file: JavaFile,
    targetWidth: Int,
    targetHeight: Int,
    blurRadius: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetWidth && bounds.outHeight / (sample * 2) >= targetHeight) {
        sample *= 2
    }
    val decoded = BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    ) ?: return null
    if (blurRadius <= 0) return decoded
    return boxBlur(decoded, blurRadius.coerceIn(1, 32))
}

private suspend fun boxBlur(source: Bitmap, radius: Int): Bitmap {
    val bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
    if (bitmap !== source) source.recycle()
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    val scratch = IntArray(pixels.size)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    for (y in 0 until height) {
        currentCoroutineContext().ensureActive()
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (x in 0 until width) {
            if (x == 0) {
                for (sampleX in 0..radius.coerceAtMost(width - 1)) {
                    val color = pixels[y * width + sampleX]
                    a += color ushr 24
                    r += color ushr 16 and 0xff
                    g += color ushr 8 and 0xff
                    b += color and 0xff
                }
            } else {
                val removed = x - radius - 1
                if (removed >= 0) {
                    val color = pixels[y * width + removed]
                    a -= color ushr 24
                    r -= color ushr 16 and 0xff
                    g -= color ushr 8 and 0xff
                    b -= color and 0xff
                }
                val added = x + radius
                if (added < width) {
                    val color = pixels[y * width + added]
                    a += color ushr 24
                    r += color ushr 16 and 0xff
                    g += color ushr 8 and 0xff
                    b += color and 0xff
                }
            }
            val left = (x - radius).coerceAtLeast(0)
            val right = (x + radius).coerceAtMost(width - 1)
            val count = right - left + 1
            scratch[y * width + x] =
                (a / count shl 24) or (r / count shl 16) or (g / count shl 8) or (b / count)
        }
    }
    for (x in 0 until width) {
        currentCoroutineContext().ensureActive()
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (y in 0 until height) {
            if (y == 0) {
                for (sampleY in 0..radius.coerceAtMost(height - 1)) {
                    val color = scratch[sampleY * width + x]
                    a += color ushr 24
                    r += color ushr 16 and 0xff
                    g += color ushr 8 and 0xff
                    b += color and 0xff
                }
            } else {
                val removed = y - radius - 1
                if (removed >= 0) {
                    val color = scratch[removed * width + x]
                    a -= color ushr 24
                    r -= color ushr 16 and 0xff
                    g -= color ushr 8 and 0xff
                    b -= color and 0xff
                }
                val added = y + radius
                if (added < height) {
                    val color = scratch[added * width + x]
                    a += color ushr 24
                    r += color ushr 16 and 0xff
                    g += color ushr 8 and 0xff
                    b += color and 0xff
                }
            }
            val top = (y - radius).coerceAtLeast(0)
            val bottom = (y + radius).coerceAtMost(height - 1)
            val count = bottom - top + 1
            pixels[y * width + x] =
                (a / count shl 24) or (r / count shl 16) or (g / count shl 8) or (b / count)
        }
    }
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}

private fun terminalSubtitle(
    status: TerminalStatus,
    environment: TermuxEnvironmentStatus,
    runtime: TerminalRuntime,
): String = when (environment) {
    TermuxEnvironmentStatus.Checking -> "正在检查 Termux 环境"
    TermuxEnvironmentStatus.Installing -> "正在安装 Termux bootstrap"
    is TermuxEnvironmentStatus.Failed -> "Termux 环境不可用"
    is TermuxEnvironmentStatus.Ready -> when (status) {
        TerminalStatus.Idle -> "${runtime.displayName} · Bash 已就绪"
        TerminalStatus.Starting -> "正在启动 ${runtime.displayName}"
        is TerminalStatus.Running -> "${runtime.displayName} · PID ${status.pid}"
        is TerminalStatus.Exited -> "已退出 · ${status.exitCode}"
        is TerminalStatus.Failed -> "启动失败"
    }
}

private val TerminalRuntime.displayName: String
    get() = if (this == TerminalRuntime.Termux) "Termux" else "Ubuntu"

@Composable
private fun TerminalKeys(
    modifier: Modifier = Modifier,
    ctrlArmed: Boolean,
    altArmed: Boolean,
    toggleCtrl: () -> Unit,
    toggleAlt: () -> Unit,
    sendInput: (String) -> Unit,
    vm: HyperShellViewModel,
) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(onClick = { sendInput("\u001b") }) { Text("ESC") }
        Button(onClick = toggleCtrl) { Text(if (ctrlArmed) "CTRL ●" else "CTRL") }
        Button(onClick = toggleAlt) { Text(if (altArmed) "ALT ●" else "ALT") }
        Button(onClick = { sendInput("\t") }) { Text("TAB") }
        Button(onClick = { sendInput("-") }) { Text("-") }
        Button(onClick = { sendInput("/") }) { Text("/") }
        Button(onClick = { sendInput("|") }) { Text("|") }
        Button(onClick = { vm.interruptTerminal() }) { Text("CTRL+C") }
        listOf(
            "HOME" to "\u001b[H",
            "↑" to "\u001b[A",
            "END" to "\u001b[F",
            "PGUP" to "\u001b[5~",
            "←" to "\u001b[D",
            "↓" to "\u001b[B",
            "→" to "\u001b[C",
            "PGDN" to "\u001b[6~",
        ).forEach { (label, sequence) ->
            Button(onClick = { vm.sendTerminalKey(sequence) }) { Text(label) }
        }
    }
}

@Composable
fun SettingsPage(settings: AppSettings, vm: HyperShellViewModel, bottomPadding: Dp, openAppearance: () -> Unit) {
    KitPage("设置", settings.bottomBarBlur, bottomPadding) { modifier, scaffoldPadding, navigationPadding, scroll, _ ->
        LazyColumn(
            modifier = modifier.fillMaxHeight().padding(horizontal = 12.dp).scrollEndHaptic().overScrollVertical().nestedScroll(scroll.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = scaffoldPadding.calculateTopPadding() + 12.dp,
                bottom = navigationPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            overscrollEffect = null,
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    OverlayDropdownPreference(listOf("500", "2000", "5000", "10000"), listOf(500, 2000, 5000, 10000).indexOf(settings.scrollbackLines), "PTY 滚屏", onSelectedIndexChange = { i -> vm.updateSettings { it.copy(scrollbackLines = listOf(500, 2000, 5000, 10000)[i]) } })
                    OverlayDropdownPreference(listOf("20", "50", "100", "200"), listOf(20, 50, 100, 200).indexOf(settings.commandHistoryLimit), "会话命令历史", onSelectedIndexChange = { i -> vm.updateSettings { it.copy(commandHistoryLimit = listOf(20, 50, 100, 200)[i]) } })
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    SwitchPreference(settings.showHiddenFiles, { v -> vm.updateSettings { it.copy(showHiddenFiles = v) } }, "显示隐藏文件")
                    OverlayDropdownPreference(listOf("名称", "修改时间", "大小"), settings.fileSortMode.ordinal, "默认排序", onSelectedIndexChange = { i -> vm.updateSettings { it.copy(fileSortMode = FileSortMode.entries[i]) } })
                    OverlayDropdownPreference(EditorLimit.entries.map { "${it.mebibytes} MiB" }, settings.editorLimit.ordinal, "编辑上限", onSelectedIndexChange = { i -> vm.updateSettings { it.copy(editorLimit = EditorLimit.entries[i]) } })
                    SwitchPreference(settings.scriptUseRoot, { v -> vm.updateSettings { it.copy(scriptUseRoot = v) } }, "脚本默认使用 Root")
                    SwitchPreference(settings.scriptPermission == ScriptPermission.Temporary0777, { v -> vm.updateSettings { it.copy(scriptPermission = if (v) ScriptPermission.Temporary0777 else ScriptPermission.Unchanged) } }, "脚本默认临时 0777")
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    ArrowPreference(title = "外观", summary = "主题、配色、模糊和悬浮底栏", onClick = openAppearance)
                }
            }
        }
    }
}

@Composable
fun AppearancePage(
    settings: AppSettings,
    fontInstallState: FontInstallState,
    error: String?,
    updateSettings: (((AppSettings) -> AppSettings) -> Unit),
    importCustomFont: () -> Unit,
    installMiSans: () -> Unit,
    removeMiSans: () -> Unit,
    importBackground: () -> Unit,
    removeBackground: () -> Unit,
    openMiSansLicense: () -> Unit,
    dismissError: () -> Unit,
    back: () -> Unit,
) {
    var colorDialog by remember { mutableStateOf(false) }
    var miSansLicenseDialog by remember { mutableStateOf(false) }
    var miSansActionsDialog by remember { mutableStateOf(false) }
    var backgroundActionsDialog by remember { mutableStateOf(false) }
    var selectedTerminalColor by remember(settings.terminalBackgroundColor) {
        mutableStateOf(Color(settings.terminalBackgroundColor))
    }
    KitPage("外观", settings.bottomBarBlur, 0.dp, navigation = { IconButton(onClick = back) { Icon(MiuixIcons.Back, "返回") } }) { modifier, scaffoldPadding, navigationPadding, scroll, _ ->
        LazyColumn(
            modifier = modifier.fillMaxHeight().padding(horizontal = 12.dp).scrollEndHaptic().overScrollVertical().nestedScroll(scroll.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = scaffoldPadding.calculateTopPadding() + 12.dp,
                bottom = navigationPadding + 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            overscrollEffect = null,
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("HyperShell", style = MiuixTheme.textStyles.title2)
                        Text("${settings.paletteStyle} · ${settings.colorSpec}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(MiuixTheme.colorScheme.primary, MiuixTheme.colorScheme.secondary, MiuixTheme.colorScheme.surfaceContainer).forEach { color ->
                                Box(Modifier.weight(1f).height(44.dp).background(color, androidx.compose.foundation.shape.CircleShape))
                            }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    OverlayDropdownPreference(listOf("标准 Miuix", "Monet 动态色"), settings.themeSource.ordinal, "配色来源", onSelectedIndexChange = { i -> updateSettings { it.copy(themeSource = ThemeSource.entries[i]) } })
                    OverlayDropdownPreference(listOf("跟随系统", "浅色", "深色"), settings.brightnessMode.ordinal, "明暗模式", onSelectedIndexChange = { i -> updateSettings { it.copy(brightnessMode = BrightnessMode.entries[i]) } })
                    OverlayDropdownPreference(ThemePaletteStyle.entries.map { it.name }, ThemePaletteStyle.entries.indexOfFirst { it.name == settings.paletteStyle }.coerceAtLeast(0), "调色盘风格", onSelectedIndexChange = { i -> updateSettings { it.copy(paletteStyle = ThemePaletteStyle.entries[i].name) } })
                    OverlayDropdownPreference(ThemeColorSpec.entries.map { it.name }, ThemeColorSpec.entries.indexOfFirst { it.name == settings.colorSpec }.coerceAtLeast(0), "颜色规范", onSelectedIndexChange = { i -> updateSettings { it.copy(colorSpec = ThemeColorSpec.entries[i].name) } })
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    SliderPreference(settings.terminalFontSize, { v -> updateSettings { it.copy(terminalFontSize = v) } }, title = "终端字体", valueText = "${settings.terminalFontSize.toInt()} sp", valueRange = 9f..28f, steps = 18)
                    OverlayDropdownPreference(
                        listOf("系统等宽", "无衬线等宽", "衬线等宽", "MiSans", "自定义字体"),
                        settings.terminalFont.ordinal,
                        "终端字体预设",
                        onSelectedIndexChange = { i ->
                            val selected = TerminalFont.entries[i]
                            if (selected == TerminalFont.MiSans && fontInstallState !is FontInstallState.Installed) {
                                miSansLicenseDialog = true
                            } else {
                                updateSettings { it.copy(terminalFont = selected) }
                            }
                        },
                    )
                    ArrowPreference(
                        title = "MiSans 字体",
                        summary = when (fontInstallState) {
                            FontInstallState.NotInstalled -> "未安装 · 点击从小米官方下载"
                            is FontInstallState.Downloading -> "正在下载 ${(fontInstallState.progress * 100).toInt()}%"
                            is FontInstallState.Installed -> "已安装 · MiSans Regular"
                            is FontInstallState.Failed -> fontInstallState.message
                        },
                        onClick = {
                            if (fontInstallState is FontInstallState.Installed) miSansActionsDialog = true
                            else miSansLicenseDialog = true
                        },
                    )
                    ArrowPreference(
                        title = "导入自定义字体",
                        summary = settings.customTerminalFontPath?.substringAfterLast('/') ?: "支持 TTF / OTF，最多 16 MiB",
                        onClick = importCustomFont,
                    )
                    ArrowPreference(
                        title = "终端背景颜色",
                        summary = "#%08X".format(settings.terminalBackgroundColor),
                        onClick = {
                            selectedTerminalColor = Color(settings.terminalBackgroundColor)
                            colorDialog = true
                        },
                    )
                    ArrowPreference(
                        title = "终端背景图片",
                        summary = settings.terminalBackgroundImagePath?.let { "已选择 · 居中裁切" } ?: "未选择",
                        onClick = {
                            if (settings.terminalBackgroundImagePath == null) importBackground()
                            else backgroundActionsDialog = true
                        },
                    )
                    SliderPreference(
                        settings.terminalBackgroundDim,
                        { value -> updateSettings { it.copy(terminalBackgroundDim = value) } },
                        title = "背景压暗",
                        valueText = "${(settings.terminalBackgroundDim * 100).toInt()}%",
                        valueRange = 0f..0.8f,
                        steps = 15,
                        enabled = settings.terminalBackgroundImagePath != null,
                    )
                    SliderPreference(
                        settings.terminalBackgroundBlur,
                        { value -> updateSettings { it.copy(terminalBackgroundBlur = value) } },
                        title = "背景模糊",
                        valueText = "${settings.terminalBackgroundBlur.toInt()} dp",
                        valueRange = 0f..32f,
                        steps = 15,
                        enabled = settings.terminalBackgroundImagePath != null,
                    )
                    SwitchPreference(settings.bottomBarBlur, { v -> updateSettings { it.copy(bottomBarBlur = v) } }, "全局栏模糊")
                    SwitchPreference(settings.terminalTopBlur, { v -> updateSettings { it.copy(terminalTopBlur = v) } }, "终端顶栏模糊")
                    SwitchPreference(settings.floatingBottomBar, { v -> updateSettings { it.copy(floatingBottomBar = v) } }, "悬浮底栏")
                    SwitchPreference(settings.floatingBottomBarGlass, { v -> updateSettings { it.copy(floatingBottomBarGlass = v) } }, "液态玻璃", enabled = settings.floatingBottomBar)
                    SliderPreference(settings.blurRadius, { v -> updateSettings { it.copy(blurRadius = v) } }, title = "模糊半径", valueText = "${settings.blurRadius.toInt()} dp", valueRange = 12f..48f, steps = 8)
                }
            }
        }
        OverlayDialog(
            show = colorDialog,
            onDismissRequest = { colorDialog = false },
            title = "终端背景颜色",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.fillMaxWidth().height(72.dp).background(
                            selectedTerminalColor,
                            androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                        ),
                    )
                    Text("预设颜色", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf(
                            Color(0xFF000000),
                            Color(0xFF101318),
                            Color(0xFF13211B),
                            Color(0xFF171529),
                            Color(0xFF241414),
                            Color(0xFFF4F4F4),
                        ).forEach { preset ->
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .background(preset, androidx.compose.foundation.shape.CircleShape)
                                    .clickable { selectedTerminalColor = preset },
                            )
                        }
                    }
                    Text("自定义调色盘", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    ColorPalette(
                        color = selectedTerminalColor,
                        onColorChanged = { selectedTerminalColor = it.copy(alpha = 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { colorDialog = false }, modifier = Modifier.weight(1f)) { Text("取消") }
                        Button(
                            onClick = {
                                updateSettings {
                                    it.copy(terminalBackgroundColor = selectedTerminalColor.copy(alpha = 1f).toArgb())
                                }
                                colorDialog = false
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("应用") }
                    }
                }
            },
        )
        OverlayDialog(
            show = miSansLicenseDialog,
            onDismissRequest = { miSansLicenseDialog = false },
            title = "安装 MiSans",
            summary = "字体将直接从小米官方服务器分段下载。安装即表示你接受 MiSans 字体知识产权许可协议；HyperShell 不会再分发字体文件。",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = openMiSansLicense, modifier = Modifier.fillMaxWidth()) { Text("查看许可协议") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { miSansLicenseDialog = false }, modifier = Modifier.weight(1f)) { Text("取消") }
                        Button(
                            onClick = {
                                miSansLicenseDialog = false
                                installMiSans()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("接受并安装") }
                    }
                }
            },
        )
        OverlayDialog(
            show = miSansActionsDialog,
            onDismissRequest = { miSansActionsDialog = false },
            title = "MiSans 字体",
            summary = "MiSans Regular · 由小米官方提供",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            updateSettings { it.copy(terminalFont = TerminalFont.MiSans) }
                            miSansActionsDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("设为终端字体") }
                    Button(
                        onClick = {
                            removeMiSans()
                            miSansActionsDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("删除字体") }
                }
            },
        )
        OverlayDialog(
            show = backgroundActionsDialog,
            onDismissRequest = { backgroundActionsDialog = false },
            title = "终端背景图片",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            backgroundActionsDialog = false
                            importBackground()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("替换图片") }
                    Button(
                        onClick = {
                            removeBackground()
                            backgroundActionsDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("移除图片") }
                }
            },
        )
        OverlayDialog(
            show = error != null,
            onDismissRequest = dismissError,
            title = "操作失败",
            summary = error.orEmpty(),
            content = {
                Button(onClick = dismissError, modifier = Modifier.fillMaxWidth()) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun EditorPage(state: HyperShellUiState, vm: HyperShellViewModel, bottomPadding: Dp) {
    val title = state.document?.path?.substringAfterLast('/') ?: state.textPage?.path?.substringAfterLast('/') ?: "文本"
    KitPage(title, state.settings.bottomBarBlur, bottomPadding, navigation = { IconButton(onClick = vm::closeDocument) { Icon(MiuixIcons.Back, "关闭") } }) { modifier, scaffoldPadding, navigationPadding, _, _ ->
        Column(
            modifier.padding(
                start = 12.dp,
                top = scaffoldPadding.calculateTopPadding() + 12.dp,
                end = 12.dp,
                bottom = navigationPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.document != null) {
                TextField(state.editorText, vm::setEditorText, label = if (state.documentReadOnly) "只读" else "编辑", modifier = Modifier.weight(1f), enabled = !state.documentReadOnly)
                if (!state.documentReadOnly) Button(onClick = vm::requestSaveDocument, modifier = Modifier.fillMaxWidth(), enabled = state.editorDirty) { Text("保存") }
            } else {
                Card(Modifier.weight(1f).fillMaxWidth()) { SelectionContainer { Text(state.textPage?.text.orEmpty(), modifier = Modifier.padding(14.dp).verticalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.loadAdjacentPage(false) }, modifier = Modifier.weight(1f), enabled = state.textPage?.previousOffset != null) { Text("上一页") }
                    Button(onClick = { vm.loadAdjacentPage(true) }, modifier = Modifier.weight(1f), enabled = state.textPage?.nextOffset != null) { Text("下一页") }
                }
            }
        }
    }
}

@Composable
fun ZipPreviewPage(state: HyperShellUiState, vm: HyperShellViewModel, back: () -> Unit) {
    KitPage(state.zipArchive?.name ?: "ZIP 预览", state.settings.bottomBarBlur, 0.dp, navigation = { IconButton(onClick = back) { Icon(MiuixIcons.Back, "返回") } }) { modifier, scaffoldPadding, navigationPadding, scroll, _ ->
        LazyColumn(
            modifier = modifier.padding(horizontal = 12.dp).scrollEndHaptic().overScrollVertical().nestedScroll(scroll.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = scaffoldPadding.calculateTopPadding() + 12.dp,
                bottom = navigationPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            overscrollEffect = null,
        ) {
            if (state.zipDirectory.isNotEmpty()) item { Card { BasicComponent(title = "返回上级", onClick = vm::navigateUpZip) } }
            if (state.zipLoading) item { Text("正在读取 ZIP…", modifier = Modifier.padding(12.dp)) }
            items(state.zipEntries, key = ZipItem::path) { item ->
                Card(Modifier.fillMaxWidth(), onClick = { vm.openZipItem(item) }, showIndication = true) {
                    BasicComponent(
                        title = item.name,
                        summary = if (item.directory) "文件夹" else formatBytes(item.size),
                        startAction = { Icon(if (item.directory) MiuixIcons.Folder else MiuixIcons.File, null, modifier = Modifier.padding(end = 8.dp), tint = MiuixTheme.colorScheme.primary) },
                        onClick = { vm.openZipItem(item) },
                    )
                }
            }
        }
    }
}

@Composable
fun ShellConfirmation(state: HyperShellUiState, vm: HyperShellViewModel) {
    val confirmation = state.confirmation
    OverlayDialog(
        show = confirmation != null,
        onDismissRequest = vm::dismissConfirmation,
        title = when (confirmation) {
            is Confirmation.SaveFile -> "确认 Root 写入"
            is Confirmation.ExtractZip -> "确认解压"
            null -> "确认"
        },
        summary = when (confirmation) {
            is Confirmation.SaveFile -> confirmation.path
            is Confirmation.ExtractZip -> "${confirmation.name}\n→ ${confirmation.destination}"
            null -> ""
        },
        content = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::dismissConfirmation, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = vm::confirmAction, modifier = Modifier.weight(1f)) { Text("继续") }
            }
        },
    )
}

@Composable
fun FileActionOverlay(state: HyperShellUiState, vm: HyperShellViewModel) {
    val action = state.fileAction
    OverlayBottomSheet(show = action != null, onDismissRequest = vm::dismissFileAction) {
        if (action == null) return@OverlayBottomSheet
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(action.entry.name, style = MiuixTheme.textStyles.title3)
            Text(action.entry.path, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            if (action.probe.shellScript) {
                SwitchPreference(action.options.useRoot, vm::setScriptUseRoot, "使用 Root")
                SwitchPreference(action.options.temporaryPermission == ScriptPermission.Temporary0777, vm::setScriptTemporary0777, "临时 chmod 0777")
                SwitchPreference(action.options.rememberAsDefault, vm::setRememberScriptDefaults, "记住为全局默认")
                Button(onClick = vm::executeSelectedScript, modifier = Modifier.fillMaxWidth(), enabled = !state.editorDirty) { Text("执行脚本") }
            }
            if (action.probe.validUtf8Text) {
                Button(onClick = { vm.openSelectedFile(false) }, modifier = Modifier.fillMaxWidth()) { Text("查看") }
                Button(onClick = { vm.openSelectedFile(true) }, modifier = Modifier.fillMaxWidth()) { Text("编辑") }
            }
            Button(onClick = { vm.toggleBookmark(action.entry.path) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (action.entry.path in state.settings.bookmarks) "取消书签" else "添加书签")
            }
            Button(onClick = { vm.createShortcut(action.entry.path) }, modifier = Modifier.fillMaxWidth()) { Text("创建桌面快捷方式") }
            Button(onClick = vm::copySelectedPath, modifier = Modifier.fillMaxWidth()) { Text("复制路径") }
        }
    }
}

@Composable
private fun EmptyCard(text: String) { Card(Modifier.fillMaxWidth()) { Text(text, modifier = Modifier.padding(20.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary) } }

private fun formatBytes(bytes: Long): String = when {
    bytes < 0 -> "文件"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KiB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MiB".format(bytes / (1024.0 * 1024))
    else -> "%.1f GiB".format(bytes / (1024.0 * 1024 * 1024))
}
