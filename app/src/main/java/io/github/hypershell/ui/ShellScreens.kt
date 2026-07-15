package io.github.hypershell.ui

import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
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
import io.github.hypershell.files.FilePaneId
import io.github.hypershell.files.FilePaneState
import io.github.hypershell.files.RootAccess
import io.github.hypershell.files.RootFileEntry
import io.github.hypershell.files.ZipItem
import io.github.hypershell.settings.AppSettings
import io.github.hypershell.settings.BrightnessMode
import io.github.hypershell.settings.BottomBarStyle
import io.github.hypershell.settings.EditorLimit
import io.github.hypershell.settings.FileSortMode
import io.github.hypershell.settings.FileLayoutMode
import io.github.hypershell.settings.FontInstallState
import io.github.hypershell.settings.ScriptPermission
import io.github.hypershell.settings.ThemeSource
import io.github.hypershell.settings.TerminalFont
import io.github.hypershell.terminal.TerminalMode
import io.github.hypershell.terminal.TerminalRuntime
import io.github.hypershell.terminal.TerminalStatus
import io.github.hypershell.terminal.TerminalTypefaceResolver
import io.github.hypershell.terminal.TermuxEnvironmentStatus
import io.github.hypershell.terminal.LinuxBackend
import io.github.hypershell.ui.kit.util.BlurredBar
import io.github.hypershell.ui.kit.util.rememberBlurBackdrop
import io.github.hypershell.ui.kit.component.FloatingBottomBar
import io.github.hypershell.ui.kit.component.FloatingBottomBarItem
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
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
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.MoveFile
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Unpin
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
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
    compactTitleContent: (@Composable () -> Unit)? = null,
    backdropOverride: LayerBackdrop? = null,
    captureBackdrop: Boolean = true,
    actions: @Composable () -> Unit = {},
    content: @Composable (Modifier, PaddingValues, Dp, ScrollBehavior, LayerBackdrop?) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val ownedBackdrop = rememberBlurBackdrop(blur && Build.VERSION.SDK_INT >= 33 && isRenderEffectSupported())
    val backdrop = backdropOverride ?: ownedBackdrop
    val statusBarCenterOffset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() / 2
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                Column {
                    if (compact) {
                        Box(Modifier.fillMaxWidth()) {
                            SmallTopAppBar(
                                color = barColor,
                                title = if (compactTitleContent == null) title else "",
                                subtitle = if (compactTitleContent == null) subtitle else "",
                                scrollBehavior = scrollBehavior,
                                navigationIcon = navigation ?: {},
                                actions = { actions() },
                            )
                            if (compactTitleContent != null) {
                                Box(
                                    Modifier
                                        .align(Alignment.CenterStart)
                                        .fillMaxWidth()
                                        .offset(y = statusBarCenterOffset)
                                        .padding(start = 20.dp, end = 124.dp),
                                ) {
                                    compactTitleContent()
                                }
                            }
                        }
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
        val modifier = if (captureBackdrop && backdrop != null) {
            Modifier.fillMaxSize().layerBackdrop(backdrop)
        } else {
            Modifier.fillMaxSize()
        }
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
    var searchVisible by remember { mutableStateOf(false) }
    var createDirectory by remember { mutableStateOf<Boolean?>(null) }
    var createName by remember { mutableStateOf("") }
    var archiveDialog by remember { mutableStateOf(false) }
    var archiveName by remember { mutableStateOf("archive") }
    val activePane = state.fileBrowser.pane(state.fileBrowser.activePane)
    var pathValue by remember(activePane.path) { mutableStateOf(activePane.path) }
    val folderCount = activePane.entries.count { it.kind == FileKind.Directory }
    val fileCount = activePane.entries.size - folderCount
    val fileSurface = MiuixTheme.colorScheme.surface
    val fileBackdrop = rememberLayerBackdrop {
        drawRect(fileSurface)
        drawContent()
    }
    KitPage(
        title = activePane.path,
        subtitle = "$folderCount 个文件夹  ·  $fileCount 个文件",
        blur = state.settings.pageTopBarBlur,
        bottomPadding = bottomPadding,
        compact = true,
        compactTitleContent = {
            Column(
                modifier = Modifier.clickable { pathDialog = true },
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    activePane.path,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    "$folderCount 个文件夹 · $fileCount 个文件",
                    maxLines = 1,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        },
        backdropOverride = if (
            state.settings.pageTopBarBlur &&
                Build.VERSION.SDK_INT >= 33 && isRenderEffectSupported()
        ) fileBackdrop else null,
        captureBackdrop = false,
        actions = {
            IconButton(onClick = { searchVisible = !searchVisible }) { Icon(MiuixIcons.Search, "搜索") }
            val currentBookmarked = activePane.path in state.settings.bookmarks
            val menuEntries = buildList {
                add(
                    DropdownEntry(
                        items = listOf(
                            DropdownItem(
                                text = if (currentBookmarked) "取消当前路径书签" else "收藏当前路径",
                                summary = activePane.path,
                                onClick = { vm.toggleBookmark(activePane.path) },
                                icon = { iconModifier ->
                                    Icon(
                                        if (currentBookmarked) MiuixIcons.Unpin else MiuixIcons.Pin,
                                        null,
                                        modifier = iconModifier,
                                    )
                                },
                            ),
                            DropdownItem(
                                text = "打开根目录",
                                summary = "在当前窗格打开 /",
                                onClick = { vm.jumpToPath(activePane.id, "/") },
                                icon = { iconModifier -> Icon(MiuixIcons.Home, null, modifier = iconModifier) },
                            ),
                        ),
                    ),
                )
                if (state.settings.bookmarks.isNotEmpty()) {
                    add(
                        DropdownEntry(
                            items = state.settings.bookmarks.sorted().map { bookmark ->
                                DropdownItem(
                                    text = bookmark.substringAfterLast('/').ifEmpty { "/" },
                                    summary = bookmark,
                                    onClick = { vm.openBookmark(bookmark) },
                                    icon = { iconModifier -> Icon(MiuixIcons.Folder, null, modifier = iconModifier) },
                                )
                            },
                        ),
                    )
                }
            }
            OverlayIconDropdownMenu(entries = menuEntries) {
                Icon(MiuixIcons.More, "更多")
            }
        },
        header = {
            if (searchVisible) {
                TextField(
                    value = activePane.searchQuery,
                    onValueChange = { vm.setPaneSearchQuery(state.fileBrowser.activePane, it) },
                    label = "搜索当前窗格",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                )
            }
        },
    ) { modifier, scaffoldPadding, navigationPadding, _, _ ->
        BoxWithConstraints(
            modifier = modifier,
        ) {
            val density = LocalDensity.current
            val dual = state.settings.fileLayoutMode == FileLayoutMode.Dual
            val toolbarHeight = 72.dp
            val listTop = scaffoldPadding.calculateTopPadding() + 4.dp
            val listBottom = navigationPadding + toolbarHeight + 8.dp
            val containerWidth = maxWidth
            Box(Modifier.fillMaxSize().layerBackdrop(fileBackdrop)) {
                if (!dual) {
                    FilePane(
                        pane = activePane,
                        active = true,
                        rootAccess = state.rootAccess,
                        vm = vm,
                        contentTop = listTop,
                        contentBottom = listBottom,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Row(Modifier.fillMaxSize()) {
                        FilePane(
                            pane = state.fileBrowser.left,
                            active = state.fileBrowser.activePane == FilePaneId.Left,
                            rootAccess = state.rootAccess,
                            vm = vm,
                            contentTop = listTop,
                            contentBottom = listBottom,
                            modifier = Modifier.weight(state.fileBrowser.splitFraction),
                        )
                        val totalPx = with(density) { containerWidth.toPx() }.coerceAtLeast(1f)
                        Box(
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .pointerInput(state.fileBrowser.splitFraction) {
                                    detectHorizontalDragGestures { _, delta ->
                                        vm.setFileSplitFraction(state.fileBrowser.splitFraction + delta / totalPx)
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(onDoubleTap = { vm.setFileSplitFraction(0.5f) })
                                }
                                .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.12f)),
                        )
                        FilePane(
                            pane = state.fileBrowser.right,
                            active = state.fileBrowser.activePane == FilePaneId.Right,
                            rootAccess = state.rootAccess,
                            vm = vm,
                            contentTop = listTop,
                            contentBottom = listBottom,
                            modifier = Modifier.weight(1f - state.fileBrowser.splitFraction),
                        )
                    }
                }
            }
            FileManagerToolbar(
                pane = activePane,
                backdrop = fileBackdrop,
                hdrPulseEnabled = state.settings.bottomBarHdrFeedback,
                clipboardAvailable = state.fileBrowser.clipboard != null,
                onBack = { vm.navigatePaneBack(state.fileBrowser.activePane) },
                onForward = { vm.navigatePaneForward(state.fileBrowser.activePane) },
                onCreate = { createName = ""; createDirectory = false },
                onSync = vm::synchronizeFilePanes,
                onRefresh = { vm.loadDirectory(activePane.path, recordHistory = false) },
                onCopy = { vm.stageSelectedFiles(activePane.id, false) },
                onMove = { vm.stageSelectedFiles(activePane.id, true) },
                onDelete = { vm.requestDeleteSelected(activePane.id) },
                onArchive = { archiveName = "archive"; archiveDialog = true },
                onPaste = { vm.pasteIntoPane(activePane.id) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = navigationPadding + 8.dp),
            )
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
                        Button(onClick = { pathDialog = false; vm.jumpToPath(state.fileBrowser.activePane, pathValue) }, modifier = Modifier.weight(1f)) { Text("打开") }
                    }
                }
            },
        )
        OverlayDialog(
            show = createDirectory != null,
            onDismissRequest = { createDirectory = null },
            title = if (createDirectory == true) "新建文件夹" else "新建文件",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(createName, { createName = it }, label = "名称", singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { createDirectory = !(createDirectory ?: false) },
                            modifier = Modifier.weight(1f),
                        ) { Text(if (createDirectory == true) "类型：文件夹" else "类型：文件") }
                        Button(
                            onClick = {
                                val directory = createDirectory ?: false
                                vm.createInPane(activePane.id, createName, directory)
                                createDirectory = null
                            },
                            modifier = Modifier.weight(1f),
                            enabled = createName.isNotBlank(),
                        ) { Text("创建") }
                    }
                }
            },
        )
        OverlayDialog(
            show = archiveDialog,
            onDismissRequest = { archiveDialog = false },
            title = "创建归档",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(archiveName, { archiveName = it }, label = "文件名", singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { archiveDialog = false; vm.archiveSelected(activePane.id, archiveName, "zip") }, modifier = Modifier.weight(1f)) { Text("ZIP") }
                        Button(onClick = { archiveDialog = false; vm.archiveSelected(activePane.id, archiveName, "tar.gz") }, modifier = Modifier.weight(1f)) { Text("TAR.GZ") }
                    }
                }
            },
        )
    }
}

@Composable
private fun FilePane(
    pane: FilePaneState,
    active: Boolean,
    rootAccess: RootAccess,
    vm: HyperShellViewModel,
    contentTop: Dp,
    contentBottom: Dp,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val animatedEntries = remember(pane.path, pane.generation) { mutableSetOf<String>() }
    val activeOverlayAlpha by animateFloatAsState(
        targetValue = if (active) 0.055f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "active-pane-${pane.id}",
    )
    Box(
        modifier = modifier
            .background(
                MiuixTheme.colorScheme.onSurface.copy(alpha = activeOverlayAlpha),
            )
            .pointerInput(pane.id) {
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Initial)
                    vm.activateFilePane(pane.id)
                }
            }
            .combinedClickable(
                onClick = { vm.activateFilePane(pane.id) },
                onLongClick = { vm.activateFilePane(pane.id) },
            ),
    ) {
        when (rootAccess) {
            RootAccess.Granted -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical(),
                contentPadding = PaddingValues(top = contentTop, bottom = contentBottom),
                overscrollEffect = null,
            ) {
                if (pane.path != "/") {
                    item(key = "${pane.id}-parent-${pane.path}") {
                        ParentDirectoryRow { vm.activateFilePane(pane.id); vm.navigatePaneUp(pane.id) }
                    }
                }
                items(pane.entries, key = RootFileEntry::path) { entry ->
                    FileRow(
                        entry = entry,
                        selected = entry.path in pane.selectedPaths,
                        animateRead = pane.loading && !listState.isScrollInProgress && animatedEntries.add(entry.path),
                        onLongClick = { vm.toggleFileSelection(pane.id, entry.path) },
                    ) {
                        vm.activateFilePane(pane.id)
                        if (pane.selectedPaths.isNotEmpty()) vm.toggleFileSelection(pane.id, entry.path) else vm.openEntry(entry)
                    }
                }
                if (!pane.loading && pane.entries.isEmpty()) item { EmptyFilePane() }
            }
            RootAccess.Unknown -> Box(Modifier.padding(top = contentTop, start = 8.dp, end = 8.dp)) { RootStateCard("正在检查 Root 文件访问…", vm::requestRootFiles) }
            RootAccess.Denied -> Box(Modifier.padding(top = contentTop, start = 8.dp, end = 8.dp)) { RootStateCard("Root 管理器未授予 UID 0", vm::requestRootFiles) }
            RootAccess.Unavailable -> Box(Modifier.padding(top = contentTop, start = 8.dp, end = 8.dp)) { RootStateCard("系统未提供可用的 su", vm::requestRootFiles) }
        }
    }
}

@Composable
private fun FileRow(
    entry: RootFileEntry,
    modifier: Modifier = Modifier,
    animateRead: Boolean = false,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
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
    val detail = buildString {
        if (entry.modifiedAt != java.time.Instant.EPOCH) append(entry.modifiedAt.toString().take(16).replace('T', ' '))
        if (entry.kind == FileKind.Regular && entry.size >= 0) {
            if (isNotEmpty()) append(" · ")
            append(formatBytes(entry.size))
        }
        if (isEmpty()) append(if (entry.kind == FileKind.Directory) "文件夹" else "文件")
    }
    Row(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, modifier = Modifier.size(34.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Column(Modifier.padding(start = 9.dp).weight(1f)) {
            Text(
                entry.name,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MiuixTheme.textStyles.body1,
            )
            Text(detail, maxLines = 1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote2)
        }
    }
}

@Composable
private fun ParentDirectoryRow(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MiuixIcons.Folder, null, modifier = Modifier.size(34.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text("..", modifier = Modifier.padding(start = 9.dp), style = MiuixTheme.textStyles.body1)
    }
}

@Composable
private fun EmptyFilePane() {
    Text(
        "此目录为空",
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        style = MiuixTheme.textStyles.body2,
    )
}

@Composable
private fun FileManagerToolbar(
    pane: FilePaneState,
    backdrop: Backdrop,
    hdrPulseEnabled: Boolean,
    clipboardAvailable: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onCreate: () -> Unit,
    onSync: () -> Unit,
    onRefresh: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectionMode = pane.selectedPaths.isNotEmpty()
    FloatingBottomBar(
        modifier = modifier,
        selectedIndex = { 2 },
        onSelected = {},
        backdrop = backdrop,
        tabsCount = 5,
        isBlurEnabled = true,
        hdrPulseEnabled = hdrPulseEnabled,
        lockedIndex = 2,
        lockedOnClick = if (selectionMode) onMove else onCreate,
        glassContainerColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.38f),
        vibrancyEnabled = false,
    ) {
        if (!selectionMode) {
            FloatingBottomBarItem(
                onClick = onBack,
                modifier = Modifier.defaultMinSize(minWidth = 58.dp),
            ) { Icon(MiuixIcons.Back, "后退") }
            FloatingBottomBarItem(
                onClick = onForward,
                modifier = Modifier.defaultMinSize(minWidth = 58.dp),
            ) { Icon(MiuixIcons.Basic.ArrowRight, "前进") }
            FloatingBottomBarItem(
                onClick = {},
                modifier = Modifier.defaultMinSize(minWidth = 58.dp),
            ) { Icon(MiuixIcons.Add, "新建") }
            FloatingBottomBarItem(
                onClick = {
                    if (clipboardAvailable) onPaste() else onSync()
                },
                modifier = Modifier.defaultMinSize(minWidth = 58.dp),
            ) { Icon(MiuixIcons.Copy, if (clipboardAvailable) "粘贴" else "同步双窗格路径") }
            FloatingBottomBarItem(
                onClick = onRefresh,
                modifier = Modifier.defaultMinSize(minWidth = 58.dp),
            ) { Icon(MiuixIcons.Refresh, "刷新") }
        } else {
            FloatingBottomBarItem(
                onClick = {},
                modifier = Modifier.defaultMinSize(minWidth = 58.dp),
            ) { Text("${pane.selectedPaths.size} 项", style = MiuixTheme.textStyles.footnote1) }
            FloatingBottomBarItem(
                onClick = onCopy,
                modifier = Modifier.defaultMinSize(minWidth = 58.dp),
            ) { Icon(MiuixIcons.Copy, "复制") }
            FloatingBottomBarItem(
                onClick = {},
                modifier = Modifier.defaultMinSize(minWidth = 58.dp),
            ) { Icon(MiuixIcons.MoveFile, "剪切") }
            FloatingBottomBarItem(
                onClick = onArchive,
                modifier = Modifier.defaultMinSize(minWidth = 58.dp),
            ) { Icon(MiuixIcons.Folder, "归档") }
            FloatingBottomBarItem(
                onClick = onDelete,
                modifier = Modifier.defaultMinSize(minWidth = 58.dp),
            ) { Icon(MiuixIcons.Delete, "永久删除") }
        }
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
        subtitle = terminalSubtitle(
            state.terminalStatus,
            state.termuxEnvironmentStatus,
            state.terminalRuntime,
            state.linuxBackend,
        ),
        blur = state.settings.terminalTopBlur,
        bottomPadding = bottomPadding,
        compact = true,
        actions = {
            val keep = state.settings.keepTerminalInBackground
            val usingDebian = state.terminalRuntime == TerminalRuntime.Debian
            val terminalMenu = listOf(
                DropdownEntry(
                    items = listOf(
                        DropdownItem(
                            text = if (usingDebian) "切换到 Termux" else "切换到 Debian 13",
                            summary = if (usingDebian) "当前使用 Debian 13" else "当前使用 Termux",
                            onClick = vm::toggleTerminalRuntime,
                            icon = { iconModifier -> Icon(MiuixIcons.Home, null, modifier = iconModifier) },
                        ),
                        DropdownItem(
                            text = if (keep) "关闭后台保留" else "开启后台保留",
                            summary = if (keep) "切到后台时继续保留终端会话" else "切到后台时终止终端会话",
                            selected = keep,
                            onClick = { vm.updateSettings { it.copy(keepTerminalInBackground = !keep) } },
                            icon = { iconModifier ->
                                Icon(if (keep) MiuixIcons.Pin else MiuixIcons.Unpin, null, modifier = iconModifier)
                            },
                        ),
                    ),
                ),
            )
            OverlayIconDropdownMenu(entries = terminalMenu) {
                Icon(MiuixIcons.More, "更多")
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
            val backgroundAlpha by animateFloatAsState(
                targetValue = if (image != null) 1f else 0f,
                animationSpec = tween(durationMillis = 180),
                label = "terminalBackgroundFadeIn",
            )
            image?.let { bitmap ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = backgroundAlpha },
                ) {
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
                        view.clearFocus()
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
    linuxBackend: LinuxBackend,
): String = when (environment) {
    TermuxEnvironmentStatus.Checking -> "正在检查 Termux 环境"
    TermuxEnvironmentStatus.Installing -> "正在安装 Termux bootstrap"
    is TermuxEnvironmentStatus.Failed -> "Termux 环境不可用"
    is TermuxEnvironmentStatus.Ready -> when (status) {
        TerminalStatus.Idle -> "${runtime.displayName(linuxBackend)} · Bash 已就绪"
        TerminalStatus.Starting -> "正在启动 ${runtime.displayName(linuxBackend)}"
        is TerminalStatus.Running -> "${runtime.displayName(linuxBackend)} · PID ${status.pid}"
        is TerminalStatus.Exited -> "已退出 · ${status.exitCode}"
        is TerminalStatus.Failed -> "启动失败"
    }
}

private fun TerminalRuntime.displayName(backend: LinuxBackend): String = when (this) {
    TerminalRuntime.Termux -> "Termux"
    TerminalRuntime.Debian -> if (backend == LinuxBackend.Chroot) "Debian chroot" else "Debian proot"
}

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
    KitPage("设置", settings.pageTopBarBlur, bottomPadding) { modifier, scaffoldPadding, navigationPadding, scroll, _ ->
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
                    SwitchPreference(settings.showHiddenFiles, { v -> vm.updateSettings { it.copy(showHiddenFiles = v) } }, "显示隐藏文件")
                    OverlayDropdownPreference(listOf("单窗格", "双窗格"), settings.fileLayoutMode.ordinal, "文件布局", onSelectedIndexChange = { i -> vm.updateSettings { it.copy(fileLayoutMode = FileLayoutMode.entries[i]) } })
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
    onTerminalHdrChanged: (Boolean) -> Unit,
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
    KitPage("外观", settings.pageTopBarBlur, 0.dp, navigation = { IconButton(onClick = back) { Icon(MiuixIcons.Back, "返回") } }) { modifier, scaffoldPadding, navigationPadding, scroll, _ ->
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
                        summary = settings.customTerminalFontPath?.substringAfterLast('/') ?: "仅支持等宽 TTF / OTF，最多 16 MiB",
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
                    SwitchPreference(
                        settings.terminalHdrHighlight,
                        onTerminalHdrChanged,
                        "终端 HDR 高亮",
                        summary = "HDR 窗口与扩展线性亮度字形；需要 HDR 屏幕",
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
                    SwitchPreference(settings.pageTopBarBlur, { v -> updateSettings { it.copy(pageTopBarBlur = v) } }, "页面顶栏模糊")
                    SwitchPreference(settings.terminalTopBlur, { v -> updateSettings { it.copy(terminalTopBlur = v) } }, "终端顶栏模糊")
                    OverlayDropdownPreference(
                        listOf("液态玻璃", "悬浮纯色", "标准导航栏"),
                        settings.bottomBarStyle.ordinal,
                        "底栏样式",
                        onSelectedIndexChange = { i -> updateSettings { it.copy(bottomBarStyle = BottomBarStyle.entries[i]) } },
                    )
                    SwitchPreference(
                        settings.bottomBarHdrFeedback,
                        { enabled -> updateSettings { it.copy(bottomBarHdrFeedback = enabled) } },
                        "液态玻璃HDR点击反馈-沉浸光感",
                        summary = "作用于文件工具栏；液态玻璃底栏启用时也作用于底栏（Beta）",
                    )
                }
            }
            item { TerminalFontPreview(settings) }
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
private fun TerminalFontPreview(settings: AppSettings) {
    val context = LocalContext.current
    val requested = remember(settings.terminalFont, settings.customTerminalFontPath) {
        TerminalTypefaceResolver.requested(context, settings.terminalFont, settings.customTerminalFontPath)
    }
    val compatible = remember(requested) { TerminalTypefaceResolver.isMonospaced(requested) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("字体渲染预览", style = MiuixTheme.textStyles.title4)
            Text(
                if (compatible) "终端等宽检查通过" else "该字体为比例字体；终端将自动使用系统等宽字体，避免字符拉伸",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
            Text(
                "~ \$ echo HyperShell\n0123456789  ABC xyz\n中文测试  |  →  ✓",
                fontFamily = FontFamily(requested),
                fontSize = settings.terminalFontSize.sp,
                lineHeight = (settings.terminalFontSize * 1.45f).sp,
            )
        }
    }
}

@Composable
private fun EditorPage(state: HyperShellUiState, vm: HyperShellViewModel, bottomPadding: Dp) {
    val title = state.document?.path?.substringAfterLast('/') ?: state.textPage?.path?.substringAfterLast('/') ?: "文本"
    KitPage(title, state.settings.pageTopBarBlur, bottomPadding, navigation = { IconButton(onClick = vm::closeDocument) { Icon(MiuixIcons.Back, "关闭") } }) { modifier, scaffoldPadding, navigationPadding, _, _ ->
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
    KitPage(state.zipArchive?.name ?: "ZIP 预览", state.settings.pageTopBarBlur, 0.dp, navigation = { IconButton(onClick = back) { Icon(MiuixIcons.Back, "返回") } }) { modifier, scaffoldPadding, navigationPadding, scroll, _ ->
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
            is Confirmation.DeleteFiles -> if (confirmation.secondStep) "再次确认永久删除" else "永久删除？"
            is Confirmation.DebianProotFallback -> "切换到 proot 兼容模式？"
            Confirmation.MigrateUbuntuToDebian -> "迁移到 Debian 13？"
            is Confirmation.DisableUnsupportedHdr -> "终端真 HDR 不可用"
            null -> "确认"
        },
        summary = when (confirmation) {
            is Confirmation.SaveFile -> confirmation.path
            is Confirmation.ExtractZip -> "${confirmation.name}\n→ ${confirmation.destination}"
            is Confirmation.DeleteFiles -> buildString {
                append("将永久删除 ${confirmation.paths.size} 项，此操作无法撤销。")
                if (confirmation.critical) append("\n\n选择中包含系统关键路径。")
                if (confirmation.secondStep) append("\n\n这是第二次确认，请核对后再继续。")
                append("\n").append(confirmation.paths.take(5).joinToString("\n"))
            }
            is Confirmation.DebianProotFallback ->
                "Root chroot 启动条件不满足：\n${confirmation.reason}\n\nproot 性能较低，但无需挂载权限。本次只有确认后才会回退。"
            Confirmation.MigrateUbuntuToDebian ->
                "检测到旧 Ubuntu 环境。继续将永久删除旧 Ubuntu 数据，并安装 Debian 13。"
            is Confirmation.DisableUnsupportedHdr ->
                "${confirmation.reason}\n\n未启用调色板模拟。是否关闭终端 HDR 设置？"
            null -> ""
        },
        content = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::dismissConfirmation, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = vm::confirmAction, modifier = Modifier.weight(1f)) {
                    Text(
                        when (confirmation) {
                            is Confirmation.DebianProotFallback -> "使用 proot"
                            Confirmation.MigrateUbuntuToDebian -> "删除并迁移"
                            is Confirmation.DeleteFiles -> if (confirmation.critical && !confirmation.secondStep) "继续确认" else "永久删除"
                            is Confirmation.DisableUnsupportedHdr -> "关闭 HDR"
                            else -> "继续"
                        },
                    )
                }
            }
        },
    )
}

private data class FileQuickAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

@Composable
fun FileActionOverlay(
    state: HyperShellUiState,
    vm: HyperShellViewModel,
    backdrop: Backdrop? = null,
) {
    val liveAction = state.fileAction
    var retainedAction by remember { mutableStateOf(liveAction) }
    var dismissRequested by remember { mutableStateOf(false) }
    LaunchedEffect(liveAction?.entry?.path) {
        if (liveAction != null) {
            retainedAction = liveAction
            dismissRequested = false
        }
    }
    // OverlayBottomSheet keeps composing while its exit transition runs. Keep the last action
    // available for that transition so dismissing does not briefly reveal a large empty panel.
    val action = liveAction ?: retainedAction
    var renameDialog by remember { mutableStateOf(false) }
    var renameValue by remember(action?.entry?.path) { mutableStateOf(action?.entry?.name.orEmpty()) }
    var permissionDialog by remember { mutableStateOf(false) }
    var modeValue by remember(action?.entry?.path) { mutableStateOf(action?.entry?.mode.orEmpty().ifBlank { "0644" }) }
    var ownershipDialog by remember { mutableStateOf(false) }
    var ownerValue by remember(action?.entry?.path) { mutableStateOf(action?.entry?.owner.orEmpty().ifBlank { "root" }) }
    var groupValue by remember(action?.entry?.path) { mutableStateOf(action?.entry?.group.orEmpty().ifBlank { "root" }) }
    val sheetShape = remember { RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) }
    val sheetSurface = MiuixTheme.colorScheme.surfaceContainer
    val sheetModifier = if (backdrop != null) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { sheetShape },
            effects = { blur(24.dp.toPx(), 24.dp.toPx()) },
            onDrawSurface = { drawRect(sheetSurface.copy(alpha = 0.78f)) },
        )
    } else {
        Modifier.background(sheetSurface, sheetShape)
    }
    OverlayBottomSheet(
        show = liveAction != null && !dismissRequested,
        title = null,
        backgroundColor = Color.Transparent,
        cornerRadius = 0.dp,
        dragHandleColor = Color.Transparent,
        insideMargin = DpSize(0.dp, 0.dp),
        // Keep ViewModel state and every visual layer intact until Miuix finishes the exit
        // translation. Clearing state at gesture-up made the backdrop and controls leave on
        // different frames, which looked like two separate panels closing.
        onDismissRequest = { dismissRequested = true },
        onDismissFinished = {
            if (dismissRequested) vm.dismissFileAction()
            dismissRequested = false
            if (state.fileAction == null) retainedAction = null
        },
    ) {
        if (action == null) return@OverlayBottomSheet
        val bookmarked = action.entry.path in state.settings.bookmarks
        val quickActions = buildList {
            if (action.probe.validUtf8Text) {
                add(FileQuickAction(MiuixIcons.File, "查看") { vm.openSelectedFile(false) })
                add(FileQuickAction(MiuixIcons.Edit, "编辑") { vm.openSelectedFile(true) })
            }
            add(FileQuickAction(if (bookmarked) MiuixIcons.Unpin else MiuixIcons.Pin, if (bookmarked) "取消书签" else "添加书签") {
                vm.toggleBookmark(action.entry.path)
            })
            add(FileQuickAction(MiuixIcons.Copy, "复制路径", vm::copySelectedPath))
        }
        // Miuix appends its translationY after the public modifier. A backdrop attached to that
        // public modifier therefore stays outside the translated graphics layer. Render the
        // complete visual panel inside the content slot instead: the library's parent transform
        // now moves the blur, handle, title and controls as one unit during drag and exit.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pullUpIntoBottomSheetHeader(FILE_ACTION_SHEET_HEADER_HEIGHT)
                .then(sheetModifier),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .width(45.dp)
                            .height(4.dp)
                            .background(
                                MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.28f),
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        action.entry.name,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.Medium,
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (action.entry.kind == FileKind.Directory) MiuixIcons.Folder else MiuixIcons.File,
                                    null,
                                    modifier = Modifier.size(34.dp),
                                    tint = MiuixTheme.colorScheme.primary,
                                )
                                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        action.entry.path,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        style = MiuixTheme.textStyles.body2,
                                    )
                                    Text(
                                        "${action.entry.kind} · ${action.entry.mode.ifBlank { "权限未知" }} · ${action.entry.owner.ifBlank { "所有者未知" }}:${action.entry.group.ifBlank { "组未知" }}",
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        style = MiuixTheme.textStyles.footnote1,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    if (action.probe.shellScript) {
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                SwitchPreference(action.options.useRoot, vm::setScriptUseRoot, "使用 Root")
                                SwitchPreference(action.options.temporaryPermission == ScriptPermission.Temporary0777, vm::setScriptTemporary0777, "临时 chmod 0777")
                                SwitchPreference(action.options.rememberAsDefault, vm::setRememberScriptDefaults, "记住为全局默认")
                                Button(
                                    onClick = vm::executeSelectedScript,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    enabled = !state.editorDirty,
                                ) { Text("执行脚本") }
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            quickActions.chunked(4).forEach { rowActions ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowActions.forEach { quickAction ->
                                        FileActionTile(
                                            action = quickAction,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(4 - rowActions.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            FileActionRow(MiuixIcons.Home, "创建桌面快捷方式", "固定此路径到桌面") {
                                vm.createShortcut(action.entry.path)
                            }
                            FileActionDivider()
                            FileActionRow(MiuixIcons.Edit, "重命名", "修改文件或目录名称") { renameDialog = true }
                            FileActionDivider()
                            FileActionRow(MiuixIcons.Settings, "修改权限", "chmod · ${action.entry.mode.ifBlank { "未知" }}") {
                                permissionDialog = true
                            }
                            FileActionDivider()
                            FileActionRow(MiuixIcons.Settings, "修改所有者", "chown · ${action.entry.owner.ifBlank { "未知" }}:${action.entry.group.ifBlank { "未知" }}") {
                                ownershipDialog = true
                            }
                            FileActionDivider()
                            FileActionRow(MiuixIcons.Delete, "永久删除", "此操作无法撤销", vm::requestDeleteSelectedFile)
                        }
                    }
                }
            }
        }
    }
    OverlayDialog(
        show = renameDialog && action != null,
        onDismissRequest = { renameDialog = false },
        title = "重命名",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(renameValue, { renameValue = it }, label = "新名称", singleLine = true)
                Button(onClick = { renameDialog = false; vm.renameSelectedFile(renameValue) }, modifier = Modifier.fillMaxWidth(), enabled = renameValue.isNotBlank()) { Text("应用") }
            }
        },
    )
    OverlayDialog(
        show = permissionDialog && action != null,
        onDismissRequest = { permissionDialog = false },
        title = "修改权限",
        summary = action?.entry?.path.orEmpty(),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(modeValue, { modeValue = it }, label = "八进制模式，例如 0755", singleLine = true)
                Button(onClick = { permissionDialog = false; vm.chmodSelectedFile(modeValue) }, modifier = Modifier.fillMaxWidth()) { Text("应用") }
            }
        },
    )
    OverlayDialog(
        show = ownershipDialog && action != null,
        onDismissRequest = { ownershipDialog = false },
        title = "修改所有者",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(ownerValue, { ownerValue = it }, label = "用户", singleLine = true)
                TextField(groupValue, { groupValue = it }, label = "组", singleLine = true)
                Button(onClick = { ownershipDialog = false; vm.chownSelectedFile(ownerValue, groupValue) }, modifier = Modifier.fillMaxWidth()) { Text("应用") }
            }
        },
    )
}

@Composable
private fun FileActionTile(action: FileQuickAction, modifier: Modifier = Modifier) {
    Card(
        modifier
            .height(74.dp)
            .clickable(onClick = action.onClick),
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(action.icon, action.label, modifier = Modifier.size(24.dp), tint = MiuixTheme.colorScheme.primary)
            Text(action.label, style = MiuixTheme.textStyles.footnote1, maxLines = 1)
        }
    }
}

@Composable
private fun FileActionRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, title, modifier = Modifier.size(24.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MiuixTheme.textStyles.body1)
            Text(
                summary,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Icon(MiuixIcons.Basic.ArrowRight, null, modifier = Modifier.size(18.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun FileActionDivider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
    )
}

@Composable
private fun EmptyCard(text: String) { Card(Modifier.fillMaxWidth()) { Text(text, modifier = Modifier.padding(20.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary) } }

// Miuix 0.9.3 always reserves 24 dp for its drag handle and 18 dp for its title row.
// FileActionOverlay renders both visuals inside the translated content layer so its backdrop and
// controls share one transform. Pulling that content into the reserved space keeps the visible
// panel flush with the top while preserving Miuix's native drag gesture hit target.
private val FILE_ACTION_SHEET_HEADER_HEIGHT = 42.dp

private fun Modifier.pullUpIntoBottomSheetHeader(distance: Dp): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val distancePx = distance.roundToPx().coerceAtMost(placeable.height)
        layout(placeable.width, (placeable.height - distancePx).coerceAtLeast(0)) {
            placeable.placeRelative(0, -distancePx)
        }
    }

private fun formatBytes(bytes: Long): String = when {
    bytes < 0 -> "文件"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KiB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MiB".format(bytes / (1024.0 * 1024))
    else -> "%.1f GiB".format(bytes / (1024.0 * 1024 * 1024))
}
