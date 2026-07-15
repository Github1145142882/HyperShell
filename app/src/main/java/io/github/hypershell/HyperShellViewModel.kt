package io.github.hypershell

import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.hypershell.files.FileKind
import io.github.hypershell.files.FileBrowserState
import io.github.hypershell.files.FileClipboard
import io.github.hypershell.files.FileClipboardMode
import io.github.hypershell.files.FilePaneId
import io.github.hypershell.files.isCriticalRootPath
import io.github.hypershell.files.FileOperationResult
import io.github.hypershell.files.FileProbe
import io.github.hypershell.files.PermissionRecovery
import io.github.hypershell.files.PermissionRecoveryJournal
import io.github.hypershell.files.RootAccess
import io.github.hypershell.files.RootFileEntry
import io.github.hypershell.files.RootFileRepository
import io.github.hypershell.files.TextDocument
import io.github.hypershell.files.TextPage
import io.github.hypershell.files.ZipArchive
import io.github.hypershell.files.ZipItem
import io.github.hypershell.files.ZipRepository
import io.github.hypershell.settings.AppSettings
import io.github.hypershell.settings.FileSortMode
import io.github.hypershell.settings.ScriptExecutionOptions
import io.github.hypershell.settings.ScriptPermission
import io.github.hypershell.settings.SettingsRepository
import io.github.hypershell.settings.TerminalFont
import io.github.hypershell.terminal.TerminalLaunch
import io.github.hypershell.terminal.TerminalMode
import io.github.hypershell.terminal.TerminalRuntime
import io.github.hypershell.terminal.LinuxBackend
import io.github.hypershell.terminal.TermuxEnvironmentManager
import io.github.hypershell.terminal.TermuxEnvironmentStatus
import io.github.hypershell.terminal.TerminalSession
import io.github.hypershell.terminal.TerminalStatus
import io.github.hypershell.terminal.TermuxTerminalSession
import com.termux.view.TerminalView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

enum class AppPage { Terminal, Files, Settings, Appearance }

sealed interface Confirmation {
    data class SaveFile(val path: String) : Confirmation
    data class ExtractZip(val name: String, val destination: String) : Confirmation
    data class DeleteFiles(val paths: List<String>, val critical: Boolean, val secondStep: Boolean = false) : Confirmation
    data class DebianProotFallback(val reason: String) : Confirmation
    data object MigrateUbuntuToDebian : Confirmation
    data class DisableUnsupportedHdr(val reason: String) : Confirmation
}

data class FileActionState(
    val entry: RootFileEntry,
    val probe: FileProbe,
    val options: ScriptExecutionOptions,
)

private data class ActivePaneProjection(
    val id: FilePaneId,
    val path: String,
    val entries: List<RootFileEntry>,
    val searchQuery: String,
    val loading: Boolean,
)

data class HyperShellUiState(
    val page: AppPage = AppPage.Terminal,
    val settings: AppSettings = AppSettings(),
    val terminalStatus: TerminalStatus = TerminalStatus.Idle,
    val terminalRuntime: TerminalRuntime = TerminalRuntime.Termux,
    val linuxBackend: LinuxBackend = LinuxBackend.Chroot,
    val termuxEnvironmentStatus: TermuxEnvironmentStatus = TermuxEnvironmentStatus.Checking,
    val rootAccess: RootAccess = RootAccess.Unknown,
    val currentPath: String = "/storage/emulated/0",
    val entries: List<RootFileEntry> = emptyList(),
    val searchQuery: String = "",
    val filesLoading: Boolean = false,
    val fileBrowser: FileBrowserState = FileBrowserState(),
    val fileAction: FileActionState? = null,
    val document: TextDocument? = null,
    val documentReadOnly: Boolean = false,
    val editorText: String = "",
    val textPage: TextPage? = null,
    val confirmation: Confirmation? = null,
    val zipArchive: ZipArchive? = null,
    val zipDirectory: String = "",
    val zipEntries: List<ZipItem> = emptyList(),
    val zipLoading: Boolean = false,
) {
    val editorDirty: Boolean get() = document != null && document.text != editorText
}

sealed interface HyperShellEvent {
    data class Message(val text: String) : HyperShellEvent
    data class CopyPath(val path: String) : HyperShellEvent
    data object OpenZipPreview : HyperShellEvent
    data object CloseSecondary : HyperShellEvent
}

class HyperShellViewModel @JvmOverloads constructor(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val fileRepository: RootFileRepository = RootFileRepository(ioDispatcher = ioDispatcher),
    private val termuxEnvironment: TermuxEnvironmentManager = TermuxEnvironmentManager(application, ioDispatcher),
    terminalSessionFactory: (kotlinx.coroutines.CoroutineScope) -> TerminalSession = {
        TermuxTerminalSession(application, termuxEnvironment)
    },
) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val recoveryJournal = PermissionRecoveryJournal(application, ioDispatcher)
    private val zipRepository = ZipRepository(application, ioDispatcher = ioDispatcher)
    private val terminalSession = terminalSessionFactory(viewModelScope)
    private val _uiState = MutableStateFlow(HyperShellUiState())
    val uiState: StateFlow<HyperShellUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<HyperShellEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<HyperShellEvent> = _events.asSharedFlow()

    private var appForeground = true
    private var rootAuthorizationDeadline = 0L
    private var backgroundTerminationJob: Job? = null
    private var fileOperationJob: Job? = null
    private val paneDirectoryJobs = mutableMapOf<FilePaneId, Job>()
    private val paneEntries = mutableMapOf<FilePaneId, List<RootFileEntry>>(
        FilePaneId.Left to emptyList(),
        FilePaneId.Right to emptyList(),
    )
    private var fileFilterJob: Job? = null
    private var scriptExecutionJob: Job? = null
    private var pendingZipExtraction: ZipItem? = null
    private var pendingOpenPath: String? = null
    private var allEntries: List<RootFileEntry> = emptyList()
    private val directoryCache = object : LinkedHashMap<String, List<RootFileEntry>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<RootFileEntry>>): Boolean = size > 16
    }

    init {
        cleanupSessionCache()
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { state ->
                    val browser = FilePaneId.entries.fold(state.fileBrowser) { current, id ->
                        current.updatePane(id) { pane ->
                            pane.copy(entries = filterAndSort(paneEntries[id].orEmpty(), pane.searchQuery, settings))
                        }
                    }
                    val active = browser.pane(browser.activePane)
                    state.copy(
                        settings = settings,
                        fileBrowser = browser,
                        entries = active.entries,
                        searchQuery = active.searchQuery,
                    )
                }
                (terminalSession as? TermuxTerminalSession)?.updateAppearance(
                    settings.terminalBackgroundColor,
                    settings.terminalFont,
                    settings.customTerminalFontPath,
                    settings.terminalBackgroundImagePath != null,
                )
            }
        }
        viewModelScope.launch {
            uiState.map { state ->
                ActivePaneProjection(
                    state.fileBrowser.activePane,
                    state.currentPath,
                    state.entries,
                    state.searchQuery,
                    state.filesLoading,
                )
            }.distinctUntilChanged().collect { projection ->
                _uiState.update { state ->
                    state.copy(fileBrowser = state.fileBrowser.updatePane(projection.id) { pane ->
                        pane.copy(
                            path = projection.path,
                            entries = projection.entries,
                            searchQuery = projection.searchQuery,
                            loading = projection.loading,
                        )
                    })
                }
            }
        }
        viewModelScope.launch {
            terminalSession.status.collect { status -> _uiState.update { it.copy(terminalStatus = status) } }
        }
        viewModelScope.launch {
            termuxEnvironment.status.collect { status ->
                _uiState.update { it.copy(termuxEnvironmentStatus = status) }
            }
        }
        viewModelScope.launch(ioDispatcher) { restoreInterruptedPermissionChange() }
        authorizeFiles()
    }

    fun selectPage(page: AppPage) {
        _uiState.update { it.copy(page = page) }
        if (page == AppPage.Files && _uiState.value.rootAccess == RootAccess.Unknown) {
            authorizeFiles()
        }
    }

    fun navigateBackFromAppearance() = selectPage(AppPage.Settings)

    fun requestTerminal(mode: TerminalMode) {
        if (mode == TerminalMode.Root) {
            rootAuthorizationDeadline = SystemClock.elapsedRealtime() + ROOT_AUTHORIZATION_GRACE_MS
        }
        startTerminal(mode)
    }

    fun ensureTerminal() {
        if (_uiState.value.terminalStatus == TerminalStatus.Idle) startTerminal(TerminalMode.User)
    }

    fun toggleTerminalRuntime() {
        val target = if (_uiState.value.terminalRuntime == TerminalRuntime.Termux) {
            TerminalRuntime.Debian
        } else {
            TerminalRuntime.Termux
        }
        viewModelScope.launch {
            if (target == TerminalRuntime.Debian) {
                if (termuxEnvironment.hasLegacyUbuntu()) {
                    _uiState.update { it.copy(confirmation = Confirmation.MigrateUbuntuToDebian) }
                    return@launch
                }
                message("正在准备 Debian 13 chroot 环境…")
                termuxEnvironment.ensureDebianInstalled(requireProot = false).getOrElse { error ->
                    message(error.message ?: "Debian 环境不可用")
                    return@launch
                }
                termuxEnvironment.checkChrootSupport().onFailure { error ->
                    _uiState.update {
                        it.copy(confirmation = Confirmation.DebianProotFallback(error.message ?: "Root chroot 不可用"))
                    }
                    return@launch
                }
            } else {
                termuxEnvironment.ensureInstalled().getOrElse { error ->
                    message(error.message ?: "Termux 环境不可用")
                    return@launch
                }
            }
            terminalSession.terminate()
            _uiState.update { it.copy(terminalRuntime = target, linuxBackend = LinuxBackend.Chroot) }
            terminalSession.start(
                TerminalLaunch.Interactive(
                    TerminalMode.User,
                    termuxEnvironment.home.absolutePath,
                    target,
                    LinuxBackend.Chroot,
                ),
            )
        }
    }

    fun attachTerminalView(
        view: TerminalView,
        textSizePx: Int,
        backgroundColor: Int,
        terminalFont: TerminalFont,
        customFontPath: String?,
        transparentBackground: Boolean,
    ): Boolean {
        val termuxSession = terminalSession as? TermuxTerminalSession ?: return false
        termuxSession.attachView(
            view,
            textSizePx,
            backgroundColor,
            terminalFont,
            customFontPath,
            transparentBackground,
        ) { size -> updateSettings { it.copy(terminalFontSize = size.coerceIn(9f, 28f)) } }
        return true
    }

    fun detachTerminalView(view: TerminalView) {
        (terminalSession as? TermuxTerminalSession)?.detachView(view)
    }

    fun updateTerminalViewTextSize(textSizePx: Int) {
        (terminalSession as? TermuxTerminalSession)?.updateTextSize(textSizePx)
    }

    fun updateTerminalAppearance(
        backgroundColor: Int,
        terminalFont: TerminalFont,
        customFontPath: String?,
        transparentBackground: Boolean,
    ) {
        (terminalSession as? TermuxTerminalSession)?.updateAppearance(
            backgroundColor,
            terminalFont,
            customFontPath,
            transparentBackground,
        )
    }

    val usesTermuxTerminal: Boolean get() = terminalSession is TermuxTerminalSession

    fun stopTerminal() {
        viewModelScope.launch { terminalSession.terminate() }
    }

    fun sendRawInput(value: String) {
        if (value.isNotEmpty()) writeTerminal(value.toByteArray())
    }

    fun sendTerminalKey(sequence: String) = writeTerminal(sequence.toByteArray())

    fun interruptTerminal() {
        viewModelScope.launch { terminalSession.sendSignal(2) }
    }

    fun resizeTerminal(rows: Int, columns: Int) {
        if (rows <= 0 || columns <= 0) return
        viewModelScope.launch { terminalSession.resize(rows, columns) }
    }

    fun dismissConfirmation() = _uiState.update { it.copy(confirmation = null) }

    fun confirmAction() {
        when (val confirmation = _uiState.value.confirmation) {
            is Confirmation.SaveFile -> {
                dismissConfirmation()
                saveDocument()
            }
            is Confirmation.ExtractZip -> {
                dismissConfirmation()
                extractPendingZipItem()
            }
            is Confirmation.DeleteFiles -> {
                if (confirmation.critical && !confirmation.secondStep) {
                    _uiState.update { it.copy(confirmation = confirmation.copy(secondStep = true)) }
                } else {
                    dismissConfirmation()
                    deleteFiles(confirmation.paths)
                }
            }
            is Confirmation.DebianProotFallback -> {
                dismissConfirmation()
                startDebianProotFallback()
            }
            Confirmation.MigrateUbuntuToDebian -> {
                dismissConfirmation()
                migrateUbuntuToDebian()
            }
            is Confirmation.DisableUnsupportedHdr -> {
                dismissConfirmation()
                updateSettings { it.copy(terminalHdrHighlight = false) }
            }
            null -> Unit
        }
    }

    fun reportHdrUnavailable(reason: String) {
        _uiState.update { state ->
            if (!state.settings.terminalHdrHighlight || state.confirmation is Confirmation.DisableUnsupportedHdr) state
            else state.copy(confirmation = Confirmation.DisableUnsupportedHdr(reason))
        }
    }

    fun requestRootFiles() = authorizeFiles()

    fun setSearchQuery(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
        val source = allEntries
        val settings = _uiState.value.settings
        fileFilterJob?.cancel()
        fileFilterJob = viewModelScope.launch(computationDispatcher) {
            val filtered = filterAndSort(source, value, settings)
            _uiState.update { state ->
                if (state.searchQuery == value) state.copy(entries = filtered) else state
            }
        }
    }

    fun activateFilePane(id: FilePaneId) {
        val state = _uiState.value
        if (state.fileBrowser.activePane == id) return
        val pane = state.fileBrowser.pane(id)
        allEntries = directoryCache[pane.path] ?: pane.entries
        _uiState.update {
            it.copy(
                fileBrowser = it.fileBrowser.copy(activePane = id),
                currentPath = pane.path,
                entries = pane.entries,
                searchQuery = pane.searchQuery,
                filesLoading = pane.loading,
            )
        }
        if (pane.entries.isEmpty() && state.rootAccess == RootAccess.Granted) loadDirectory(pane.path, recordHistory = false)
    }

    fun setFileSplitFraction(value: Float) = _uiState.update {
        it.copy(fileBrowser = it.fileBrowser.copy(splitFraction = value.coerceIn(0.35f, 0.65f)))
    }

    fun synchronizeFilePanes() {
        val browser = _uiState.value.fileBrowser
        val source = browser.activePane
        val target = if (source == FilePaneId.Left) FilePaneId.Right else FilePaneId.Left
        val path = browser.pane(source).path
        activateFilePane(target)
        loadDirectory(path)
        activateFilePane(source)
    }

    fun setPaneSearchQuery(id: FilePaneId, value: String) {
        val raw = paneEntries[id].orEmpty()
        _uiState.update { state ->
            val visible = filterAndSort(raw, value, state.settings)
            val browser = state.fileBrowser.copy(activePane = id).updatePane(id) { pane ->
                pane.copy(searchQuery = value, entries = visible)
            }
            state.copy(
                fileBrowser = browser,
                currentPath = browser.pane(id).path,
                entries = visible,
                searchQuery = value,
                filesLoading = browser.pane(id).loading,
            )
        }
    }

    fun jumpToPath(id: FilePaneId, path: String) {
        activateFilePane(id)
        jumpToPath(path)
    }

    fun navigatePaneUp(id: FilePaneId) {
        activateFilePane(id)
        navigateUp()
    }

    fun navigatePaneBack(id: FilePaneId) {
        val state = _uiState.value
        val pane = state.fileBrowser.pane(id)
        val target = pane.backStack.lastOrNull() ?: return
        activateFilePane(id)
        _uiState.update {
            it.copy(fileBrowser = it.fileBrowser.updatePane(id) { current ->
                current.copy(backStack = current.backStack.dropLast(1), forwardStack = current.forwardStack + current.path)
            })
        }
        loadDirectory(target, recordHistory = false)
    }

    fun navigatePaneForward(id: FilePaneId) {
        val state = _uiState.value
        val pane = state.fileBrowser.pane(id)
        val target = pane.forwardStack.lastOrNull() ?: return
        activateFilePane(id)
        _uiState.update {
            it.copy(fileBrowser = it.fileBrowser.updatePane(id) { current ->
                current.copy(forwardStack = current.forwardStack.dropLast(1), backStack = current.backStack + current.path)
            })
        }
        loadDirectory(target, recordHistory = false)
    }

    fun toggleFileSelection(id: FilePaneId, path: String) {
        _uiState.update {
            it.copy(fileBrowser = it.fileBrowser.copy(activePane = id).updatePane(id) { pane ->
                pane.copy(selectedPaths = if (path in pane.selectedPaths) pane.selectedPaths - path else pane.selectedPaths + path)
            })
        }
    }

    fun selectAllFiles(id: FilePaneId) = _uiState.update {
        it.copy(fileBrowser = it.fileBrowser.updatePane(id) { pane -> pane.copy(selectedPaths = pane.entries.mapTo(mutableSetOf()) { entry -> entry.path }) })
    }

    fun invertFileSelection(id: FilePaneId) = _uiState.update {
        it.copy(fileBrowser = it.fileBrowser.updatePane(id) { pane ->
            val all = pane.entries.mapTo(mutableSetOf()) { entry -> entry.path }
            pane.copy(selectedPaths = all - pane.selectedPaths)
        })
    }

    fun stageSelectedFiles(id: FilePaneId, move: Boolean) {
        val pane = _uiState.value.fileBrowser.pane(id)
        if (pane.selectedPaths.isEmpty()) return message("请先选择文件")
        _uiState.update {
            it.copy(fileBrowser = it.fileBrowser.copy(
                clipboard = FileClipboard(pane.selectedPaths.toList(), if (move) FileClipboardMode.Move else FileClipboardMode.Copy),
            ).updatePane(id) { current -> current.copy(selectedPaths = emptySet()) })
        }
        message(if (move) "已剪切 ${pane.selectedPaths.size} 项" else "已复制 ${pane.selectedPaths.size} 项")
    }

    fun pasteIntoPane(id: FilePaneId) {
        val browser = _uiState.value.fileBrowser
        val clipboard = browser.clipboard ?: return message("剪贴板为空")
        val destination = browser.pane(id).path
        fileOperationJob?.cancel()
        paneDirectoryJobs.values.forEach(Job::cancel)
        fileOperationJob = viewModelScope.launch {
            val errors = mutableListOf<String>()
            clipboard.paths.forEach { source ->
                val target = "$destination/${source.substringAfterLast('/')}".replace("//", "/")
                val result = if (clipboard.mode == FileClipboardMode.Move) {
                    fileRepository.move(source, target, io.github.hypershell.files.ConflictPolicy.AutoRename)
                } else {
                    fileRepository.copy(source, target, io.github.hypershell.files.ConflictPolicy.AutoRename)
                }
                if (result is FileOperationResult.Failure) errors += "${source.substringAfterLast('/')}: ${result.message}"
            }
            if (clipboard.mode == FileClipboardMode.Move && errors.isEmpty()) {
                _uiState.update { it.copy(fileBrowser = it.fileBrowser.copy(clipboard = null)) }
            }
            activateFilePane(id)
            loadDirectory(destination, recordHistory = false)
            message(if (errors.isEmpty()) "操作完成" else "完成，但有 ${errors.size} 项失败")
        }
    }

    fun createInPane(id: FilePaneId, name: String, directory: Boolean) {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || cleanName == "." || cleanName == ".." || '/' in cleanName || '\u0000' in cleanName) {
            return message("名称无效")
        }
        val pane = _uiState.value.fileBrowser.pane(id)
        val target = "${pane.path}/$cleanName".replace("//", "/")
        fileOperationJob = viewModelScope.launch {
            when (val result = fileRepository.create(target, directory)) {
                is FileOperationResult.Success -> {
                    activateFilePane(id)
                    loadDirectory(pane.path, recordHistory = false)
                }
                is FileOperationResult.Failure -> message(result.message)
            }
        }
    }

    fun archiveSelected(id: FilePaneId, name: String, format: String) {
        val pane = _uiState.value.fileBrowser.pane(id)
        if (pane.selectedPaths.isEmpty()) return message("请先选择文件")
        val clean = name.trim()
        if (clean.isEmpty() || clean == "." || clean == ".." || '/' in clean || '\u0000' in clean) {
            return message("归档名称无效")
        }
        val suffix = when (format.lowercase()) {
            "zip" -> ".zip"
            "tar.gz", "tgz" -> ".tar.gz"
            else -> return message("不支持的归档格式")
        }
        val fileName = if (clean.endsWith(suffix, ignoreCase = true)) clean else clean + suffix
        val destination = "${pane.path}/$fileName".replace("//", "/")
        fileOperationJob?.cancel()
        fileOperationJob = viewModelScope.launch {
            when (val result = fileRepository.archive(pane.selectedPaths.toList(), destination, format)) {
                is FileOperationResult.Success -> {
                    _uiState.update {
                        it.copy(fileBrowser = it.fileBrowser.updatePane(id) { current -> current.copy(selectedPaths = emptySet()) })
                    }
                    activateFilePane(id)
                    loadDirectory(pane.path, recordHistory = false)
                    message("已创建 $fileName")
                }
                is FileOperationResult.Failure -> message(result.message)
            }
        }
    }

    fun requestDeleteSelected(id: FilePaneId) {
        val paths = _uiState.value.fileBrowser.pane(id).selectedPaths.toList()
        if (paths.isEmpty()) return message("请先选择文件")
        val critical = paths.any(::isCriticalRootPath)
        _uiState.update { it.copy(confirmation = Confirmation.DeleteFiles(paths, critical)) }
    }

    private fun deleteFiles(paths: List<String>) {
        val active = _uiState.value.fileBrowser.activePane
        val panePath = _uiState.value.fileBrowser.pane(active).path
        fileOperationJob = viewModelScope.launch {
            when (val result = fileRepository.delete(paths)) {
                is FileOperationResult.Success -> {
                    _uiState.update { it.copy(fileBrowser = it.fileBrowser.updatePane(active) { pane -> pane.copy(selectedPaths = emptySet()) }) }
                    loadDirectory(panePath, recordHistory = false)
                    message("已永久删除 ${paths.size} 项")
                }
                is FileOperationResult.Failure -> message(result.message)
            }
        }
    }


    fun loadDirectory(path: String, recordHistory: Boolean = true) {
        if (_uiState.value.rootAccess != RootAccess.Granted) return
        val normalized = RootFileRepository.normalizeAbsolutePath(path) ?: return message("请输入绝对路径")
        val paneId = _uiState.value.fileBrowser.activePane
        val previousPane = _uiState.value.fileBrowser.pane(paneId)
        val previousEntries = paneEntries[paneId].orEmpty()
        val cached = directoryCache[normalized]
        paneDirectoryJobs.remove(paneId)?.cancel()
        if (recordHistory && normalized != previousPane.path) {
            _uiState.update {
                it.copy(fileBrowser = it.fileBrowser.updatePane(paneId) { pane ->
                    pane.copy(backStack = pane.backStack + previousPane.path, forwardStack = emptyList())
                })
            }
        }
        val job = viewModelScope.launch {
            if (cached != null) paneEntries[paneId] = cached
            publishPane(paneId, normalized, cached.orEmpty(), loading = true, resetEditor = true)
            val discovered = mutableListOf<RootFileEntry>()
            var receivedEntry = false
            var lastPublishedAt = 0L
            suspend fun publishDiscovered(force: Boolean = false) {
                val now = SystemClock.elapsedRealtimeNanos()
                if (!force && lastPublishedAt != 0L && now - lastPublishedAt < FILE_STREAM_FRAME_NS) return
                lastPublishedAt = now
                val snapshot = discovered.toList()
                withContext(Dispatchers.Main.immediate) {
                    if (_uiState.value.fileBrowser.pane(paneId).path != normalized) throw CancellationException()
                    paneEntries[paneId] = snapshot
                    publishPane(paneId, normalized, snapshot, loading = true)
                }
            }
            when (val result = fileRepository.listStreaming(normalized) { entry ->
                if (_uiState.value.fileBrowser.pane(paneId).path != normalized) throw CancellationException()
                if (!receivedEntry) {
                    receivedEntry = true
                    discovered.clear()
                }
                discovered += entry
                publishDiscovered()
            }) {
                is FileOperationResult.Success -> {
                    if (receivedEntry) publishDiscovered(force = true) else paneEntries[paneId] = emptyList()
                    directoryCache[normalized] = paneEntries[paneId].orEmpty()
                    publishPane(paneId, normalized, paneEntries[paneId].orEmpty(), loading = false)
                    when (val detailed = fileRepository.listDetailed(normalized)) {
                        is FileOperationResult.Success -> {
                            if (_uiState.value.fileBrowser.pane(paneId).path != normalized) return@launch
                            paneEntries[paneId] = detailed.value
                            directoryCache[normalized] = detailed.value
                            publishPane(paneId, normalized, detailed.value, loading = false, incrementGeneration = true)
                        }
                        is FileOperationResult.Failure -> Unit
                    }
                }
                is FileOperationResult.Failure -> {
                    paneEntries[paneId] = previousEntries
                    publishPane(paneId, previousPane.path, previousEntries, loading = false)
                    message(result.message)
                }
            }
        }
        paneDirectoryJobs[paneId] = job
    }

    private fun publishPane(
        id: FilePaneId,
        path: String,
        rawEntries: List<RootFileEntry>,
        loading: Boolean,
        resetEditor: Boolean = false,
        incrementGeneration: Boolean = false,
    ) {
        _uiState.update { state ->
            val query = state.fileBrowser.pane(id).searchQuery
            val visible = filterAndSort(rawEntries, query, state.settings)
            val browser = state.fileBrowser.updatePane(id) { pane ->
                pane.copy(
                    path = path,
                    entries = visible,
                    loading = loading,
                    generation = if (incrementGeneration) pane.generation + 1 else pane.generation,
                )
            }
            if (browser.activePane == id) {
                state.copy(
                    fileBrowser = browser,
                    currentPath = path,
                    entries = visible,
                    searchQuery = query,
                    filesLoading = loading,
                    document = if (resetEditor) null else state.document,
                    textPage = if (resetEditor) null else state.textPage,
                    editorText = if (resetEditor) "" else state.editorText,
                )
            } else state.copy(fileBrowser = browser)
        }
    }

    fun jumpToPath(path: String) {
        val normalized = RootFileRepository.normalizeAbsolutePath(path)
        if (normalized == null) message("请输入绝对路径") else loadDirectory(normalized)
    }

    fun navigateUp() {
        val current = _uiState.value.currentPath
        if (current != "/") loadDirectory(current.substringBeforeLast('/').ifEmpty { "/" })
    }

    fun openEntry(entry: RootFileEntry) {
        if (entry.kind == FileKind.Directory) {
            loadDirectory(entry.path)
            return
        }
        if (entry.kind != FileKind.Regular) {
            _uiState.update {
                it.copy(fileAction = FileActionState(entry, FileProbe(entry.path, entry.kind, entry.size, false, false), defaultScriptOptions()))
            }
            return
        }
        if (entry.name.endsWith(".zip", ignoreCase = true)) {
            openZip(entry.path)
            return
        }
        fileOperationJob?.cancel()
        fileOperationJob = viewModelScope.launch {
            _uiState.update { it.copy(filesLoading = true) }
            when (val result = fileRepository.probe(entry.path, _uiState.value.settings.editorLimit.bytes)) {
                is FileOperationResult.Success -> _uiState.update {
                    it.copy(filesLoading = false, fileAction = FileActionState(entry, result.value, defaultScriptOptions()))
                }
                is FileOperationResult.Failure -> {
                    _uiState.update { it.copy(filesLoading = false) }
                    message(result.message)
                }
            }
        }
    }

    fun dismissFileAction() = _uiState.update { it.copy(fileAction = null) }

    fun setScriptUseRoot(value: Boolean) = _uiState.update {
        it.copy(fileAction = it.fileAction?.copy(options = it.fileAction.options.copy(useRoot = value)))
    }

    fun setScriptTemporary0777(value: Boolean) = _uiState.update {
        it.copy(fileAction = it.fileAction?.copy(options = it.fileAction.options.copy(
            temporaryPermission = if (value) ScriptPermission.Temporary0777 else ScriptPermission.Unchanged,
        )))
    }

    fun setRememberScriptDefaults(value: Boolean) = _uiState.update {
        it.copy(fileAction = it.fileAction?.copy(options = it.fileAction.options.copy(rememberAsDefault = value)))
    }

    fun copySelectedPath() {
        _uiState.value.fileAction?.entry?.path?.let { _events.tryEmit(HyperShellEvent.CopyPath(it)) }
    }

    fun renameSelectedFile(newName: String) {
        val action = _uiState.value.fileAction ?: return
        val clean = newName.trim()
        if (clean.isEmpty() || '/' in clean || clean == "." || clean == "..") return message("名称无效")
        val parent = action.entry.path.substringBeforeLast('/', "/").ifEmpty { "/" }
        val target = "$parent/$clean".replace("//", "/")
        dismissFileAction()
        fileOperationJob = viewModelScope.launch {
            when (val result = fileRepository.rename(action.entry.path, target)) {
                is FileOperationResult.Success -> loadDirectory(parent, recordHistory = false)
                is FileOperationResult.Failure -> message(result.message)
            }
        }
    }

    fun chmodSelectedFile(mode: String) {
        val action = _uiState.value.fileAction ?: return
        fileOperationJob = viewModelScope.launch {
            when (val result = fileRepository.chmod(action.entry.path, mode)) {
                is FileOperationResult.Success -> {
                    dismissFileAction()
                    loadDirectory(_uiState.value.currentPath, recordHistory = false)
                }
                is FileOperationResult.Failure -> message(result.message)
            }
        }
    }

    fun chownSelectedFile(owner: String, group: String) {
        val action = _uiState.value.fileAction ?: return
        fileOperationJob = viewModelScope.launch {
            when (val result = fileRepository.chown(action.entry.path, owner, group)) {
                is FileOperationResult.Success -> {
                    dismissFileAction()
                    loadDirectory(_uiState.value.currentPath, recordHistory = false)
                }
                is FileOperationResult.Failure -> message(result.message)
            }
        }
    }

    fun requestDeleteSelectedFile() {
        val path = _uiState.value.fileAction?.entry?.path ?: return
        dismissFileAction()
        _uiState.update { it.copy(confirmation = Confirmation.DeleteFiles(listOf(path), isCriticalRootPath(path))) }
    }

    fun toggleBookmark(path: String) {
        val normalized = RootFileRepository.normalizeAbsolutePath(path) ?: return
        updateSettings { settings ->
            settings.copy(
                bookmarks = if (normalized in settings.bookmarks) {
                    settings.bookmarks - normalized
                } else {
                    settings.bookmarks + normalized
                },
            )
        }
    }

    fun openBookmark(path: String) {
        val normalized = RootFileRepository.normalizeAbsolutePath(path) ?: return message("书签路径无效")
        _uiState.update { it.copy(page = AppPage.Files) }
        if (_uiState.value.rootAccess != RootAccess.Granted) {
            pendingOpenPath = normalized
            authorizeFiles()
            return
        }
        openBookmarkedPath(normalized)
    }

    fun createShortcut(path: String) {
        val normalized = RootFileRepository.normalizeAbsolutePath(path) ?: return message("快捷方式路径无效")
        val application = getApplication<Application>()
        val manager = application.getSystemService(ShortcutManager::class.java)
        if (!manager.isRequestPinShortcutSupported) return message("当前桌面不支持固定快捷方式")
        val label = normalized.substringAfterLast('/').ifEmpty { "/" }.take(40)
        val intent = Intent(application, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(MainActivity.EXTRA_SHORTCUT_PATH, normalized)
        val shortcut = ShortcutInfo.Builder(application, "path-${normalized.hashCode().toUInt().toString(16)}")
            .setShortLabel(label)
            .setLongLabel(normalized.take(100))
            .setIcon(Icon.createWithResource(application, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        if (manager.requestPinShortcut(shortcut, null)) message("已请求桌面创建快捷方式")
        else message("桌面拒绝创建快捷方式")
    }

    fun openSelectedFile(edit: Boolean) {
        val action = _uiState.value.fileAction ?: return
        dismissFileAction()
        if (!action.probe.validUtf8Text) {
            message("该文件不是可查看的 UTF-8 普通文本")
            return
        }
        if (action.probe.size > _uiState.value.settings.editorLimit.bytes) {
            loadTextPage(action.probe.path, action.probe.size, 0)
        } else {
            openDocument(action.probe.path, readOnly = !edit)
        }
    }

    fun loadAdjacentPage(next: Boolean) {
        val page = _uiState.value.textPage ?: return
        val offset = if (next) page.nextOffset else page.previousOffset
        if (offset != null) {
            val size = _uiState.value.fileAction?.probe?.size ?: allEntries.firstOrNull { it.path == page.path }?.size ?: Long.MAX_VALUE
            loadTextPage(page.path, size, offset)
        }
    }

    fun closeDocument() = _uiState.update {
        it.copy(document = null, textPage = null, editorText = "", documentReadOnly = false)
    }

    fun setEditorText(value: String) = _uiState.update { it.copy(editorText = value) }

    fun requestSaveDocument() {
        _uiState.value.document?.let { document ->
            _uiState.update { it.copy(confirmation = Confirmation.SaveFile(document.path)) }
        }
    }

    fun executeSelectedScript() {
        val action = _uiState.value.fileAction ?: return
        if (!action.probe.shellScript) return
        val options = action.options
        dismissFileAction()
        if (options.rememberAsDefault) updateSettings {
            it.copy(scriptUseRoot = options.useRoot, scriptPermission = options.temporaryPermission)
        }
        runScript(action.entry.path, options)
    }

    fun openZipItem(item: ZipItem) {
        if (item.directory) {
            loadZipDirectory(item.path)
        } else if (item.shellScript) {
            executeZipScript(item)
        } else {
            pendingZipExtraction = item
            _uiState.update {
                it.copy(confirmation = Confirmation.ExtractZip(item.name, it.currentPath))
            }
        }
    }

    fun navigateUpZip() {
        val current = _uiState.value.zipDirectory.trimEnd('/')
        if (current.isEmpty()) return
        loadZipDirectory(current.substringBeforeLast('/', ""))
    }

    fun closeZip() {
        fileOperationJob?.cancel()
        zipRepository.close(_uiState.value.zipArchive)
        pendingZipExtraction = null
        _uiState.update { it.copy(zipArchive = null, zipDirectory = "", zipEntries = emptyList(), zipLoading = false) }
    }

    fun onAppForegrounded() {
        appForeground = true
        backgroundTerminationJob?.cancel()
        backgroundTerminationJob = null
    }

    fun onAppBackgrounded() {
        appForeground = false
        backgroundTerminationJob?.cancel()
        if (!_uiState.value.settings.keepTerminalInBackground) {
            backgroundTerminationJob = viewModelScope.launch {
                val remaining = rootAuthorizationDeadline - SystemClock.elapsedRealtime()
                if (remaining > 0) delay(remaining)
                if (!appForeground) terminalSession.terminate()
            }
        }
        fileOperationJob?.cancel()
        paneDirectoryJobs.values.forEach(Job::cancel)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    private fun startTerminal(mode: TerminalMode) {
        scriptExecutionJob?.cancel()
        _uiState.update { it.copy(page = AppPage.Terminal) }
        viewModelScope.launch {
            val installed = termuxEnvironment.ensureInstalled()
            if (installed.isFailure) {
                message(installed.exceptionOrNull()?.message ?: "Termux 环境不可用")
                return@launch
            }
            val runtime = _uiState.value.terminalRuntime
            if (runtime == TerminalRuntime.Debian) {
                val backend = _uiState.value.linuxBackend
                termuxEnvironment.ensureDebianInstalled(requireProot = backend == LinuxBackend.Proot).getOrElse { error ->
                    message(error.message ?: "Debian 环境不可用")
                    return@launch
                }
                if (backend == LinuxBackend.Chroot) {
                    termuxEnvironment.checkChrootSupport().onFailure { error ->
                        _uiState.update {
                            it.copy(confirmation = Confirmation.DebianProotFallback(error.message ?: "Root chroot 不可用"))
                        }
                        return@launch
                    }
                }
            }
            terminalSession.start(
                TerminalLaunch.Interactive(
                    mode,
                    termuxEnvironment.home.absolutePath,
                    runtime,
                    _uiState.value.linuxBackend,
                ),
            )
        }
    }

    private fun startDebianProotFallback() {
        viewModelScope.launch {
            message("正在准备 proot 兼容模式…")
            termuxEnvironment.ensureDebianInstalled(requireProot = true).getOrElse { error ->
                message(error.message ?: "proot 兼容模式不可用")
                return@launch
            }
            terminalSession.terminate()
            _uiState.update { it.copy(terminalRuntime = TerminalRuntime.Debian, linuxBackend = LinuxBackend.Proot) }
            terminalSession.start(
                TerminalLaunch.Interactive(
                    TerminalMode.User,
                    termuxEnvironment.home.absolutePath,
                    TerminalRuntime.Debian,
                    LinuxBackend.Proot,
                ),
            )
        }
    }

    private fun migrateUbuntuToDebian() {
        viewModelScope.launch {
            message("正在移除旧 Ubuntu 并准备 Debian 13…")
            termuxEnvironment.deleteLegacyUbuntu()
            termuxEnvironment.ensureDebianInstalled(requireProot = false).getOrElse { error ->
                message(error.message ?: "Debian 环境不可用")
                return@launch
            }
            terminalSession.terminate()
            _uiState.update { it.copy(terminalRuntime = TerminalRuntime.Debian, linuxBackend = LinuxBackend.Chroot) }
            startTerminal(TerminalMode.User)
        }
    }

    private fun runScript(path: String, options: ScriptExecutionOptions) {
        scriptExecutionJob?.cancel()
        scriptExecutionJob = viewModelScope.launch {
            val installed = termuxEnvironment.ensureInstalled()
            if (installed.isFailure) {
                message(installed.exceptionOrNull()?.message ?: "Termux 环境不可用")
                return@launch
            }
            terminalSession.terminate()
            _uiState.update { it.copy(page = AppPage.Terminal) }
            var recovery: PermissionRecovery? = null
            try {
                if (options.temporaryPermission == ScriptPermission.Temporary0777) {
                    val mode = when (val result = fileRepository.readMode(path)) {
                        is FileOperationResult.Success -> result.value
                        is FileOperationResult.Failure -> return@launch message(result.message)
                    }
                    recovery = PermissionRecovery(path, mode)
                    recoveryJournal.write(recovery)
                    when (val chmod = fileRepository.chmod(path, "0777")) {
                        is FileOperationResult.Failure -> return@launch message(chmod.message)
                        is FileOperationResult.Success -> Unit
                    }
                }
                val mode = if (options.useRoot) TerminalMode.Root else TerminalMode.User
                terminalSession.start(TerminalLaunch.Script(path, mode, path.substringBeforeLast('/').ifEmpty { "/" }))
                terminalSession.status.first { it is TerminalStatus.Exited || it is TerminalStatus.Failed || it is TerminalStatus.Idle }
            } finally {
                recovery?.let { value ->
                    withContext(NonCancellable) {
                        when (fileRepository.chmod(value.path, value.originalMode)) {
                            is FileOperationResult.Success -> recoveryJournal.clear()
                            is FileOperationResult.Failure -> message("临时 0777 已结束，但原权限恢复失败；下次启动会重试")
                        }
                    }
                }
            }
        }
    }

    private fun openZip(path: String) {
        fileOperationJob?.cancel()
        fileOperationJob = viewModelScope.launch {
            _uiState.update { it.copy(filesLoading = true) }
            when (val opened = zipRepository.open(path)) {
                is FileOperationResult.Success -> {
                    zipRepository.close(_uiState.value.zipArchive)
                    _uiState.update {
                        it.copy(zipArchive = opened.value, zipDirectory = "", zipEntries = emptyList(), filesLoading = false, zipLoading = true)
                    }
                    when (val listed = zipRepository.list(opened.value, "")) {
                        is FileOperationResult.Success -> {
                            _uiState.update { it.copy(zipEntries = listed.value, zipLoading = false) }
                            _events.emit(HyperShellEvent.OpenZipPreview)
                        }
                        is FileOperationResult.Failure -> {
                            closeZip()
                            message(listed.message)
                        }
                    }
                }
                is FileOperationResult.Failure -> {
                    _uiState.update { it.copy(filesLoading = false) }
                    message(opened.message)
                }
            }
        }
    }

    private fun loadZipDirectory(directory: String) {
        val archive = _uiState.value.zipArchive ?: return
        fileOperationJob?.cancel()
        fileOperationJob = viewModelScope.launch {
            _uiState.update { it.copy(zipDirectory = ZipRepository.normalizeDirectory(directory).trimEnd('/'), zipLoading = true) }
            when (val result = zipRepository.list(archive, directory)) {
                is FileOperationResult.Success -> _uiState.update { it.copy(zipEntries = result.value, zipLoading = false) }
                is FileOperationResult.Failure -> {
                    _uiState.update { it.copy(zipLoading = false) }
                    message(result.message)
                }
            }
        }
    }

    private fun executeZipScript(item: ZipItem) {
        val archive = _uiState.value.zipArchive ?: return
        fileOperationJob?.cancel()
        fileOperationJob = viewModelScope.launch {
            _uiState.update { it.copy(zipLoading = true) }
            when (val result = zipRepository.extractScript(archive, item)) {
                is FileOperationResult.Success -> {
                    _uiState.update { it.copy(zipLoading = false) }
                    _events.emit(HyperShellEvent.CloseSecondary)
                    runScript(result.value, defaultScriptOptions().copy(temporaryPermission = ScriptPermission.Unchanged))
                }
                is FileOperationResult.Failure -> {
                    _uiState.update { it.copy(zipLoading = false) }
                    message(result.message)
                }
            }
        }
    }

    private fun extractPendingZipItem() {
        val item = pendingZipExtraction ?: return
        val archive = _uiState.value.zipArchive ?: return
        pendingZipExtraction = null
        fileOperationJob = viewModelScope.launch {
            _uiState.update { it.copy(zipLoading = true) }
            when (val result = zipRepository.extractToRoot(archive, item, _uiState.value.currentPath)) {
                is FileOperationResult.Success -> message("已解压到 ${result.value}")
                is FileOperationResult.Failure -> message(result.message)
            }
            _uiState.update { it.copy(zipLoading = false) }
        }
    }

    private fun writeTerminal(bytes: ByteArray) {
        viewModelScope.launch { terminalSession.write(bytes) }
    }

    private fun authorizeFiles() {
        rootAuthorizationDeadline = SystemClock.elapsedRealtime() + ROOT_AUTHORIZATION_GRACE_MS
        fileOperationJob = viewModelScope.launch {
            _uiState.update { it.copy(filesLoading = true) }
            val access = fileRepository.checkRoot()
            _uiState.update { it.copy(rootAccess = access, filesLoading = false) }
            when (access) {
                RootAccess.Granted -> {
                    val path = pendingOpenPath
                    pendingOpenPath = null
                    if (path != null) {
                        openBookmarkedPath(path)
                    } else {
                        var browser = _uiState.value.fileBrowser
                        if (
                            browser.left.path == "/" &&
                            browser.left.backStack.isEmpty() &&
                            browser.left.forwardStack.isEmpty() &&
                            browser.left.entries.isEmpty()
                        ) {
                            browser = browser.copy(
                                left = browser.left.copy(path = "/storage/emulated/0"),
                            )
                            _uiState.update { state ->
                                state.copy(
                                    fileBrowser = browser,
                                    currentPath = if (browser.activePane == FilePaneId.Left) {
                                        "/storage/emulated/0"
                                    } else {
                                        state.currentPath
                                    },
                                )
                            }
                        }
                        val original = browser.activePane
                        val paneIds = if (_uiState.value.settings.fileLayoutMode == io.github.hypershell.settings.FileLayoutMode.Dual) {
                            listOf(FilePaneId.Left, FilePaneId.Right)
                        } else {
                            listOf(original)
                        }
                        paneIds.forEach { id ->
                            activateFilePane(id)
                            loadDirectory(browser.pane(id).path, recordHistory = false)
                        }
                        activateFilePane(original)
                    }
                }
                RootAccess.Denied -> message("su 未获得 Root，请检查管理器中的应用授权")
                RootAccess.Unavailable -> message("未找到可用的 Magisk/KernelSU su")
                RootAccess.Unknown -> Unit
            }
        }
    }

    private fun openBookmarkedPath(path: String) {
        fileOperationJob?.cancel()
        fileOperationJob = viewModelScope.launch {
            _uiState.update { it.copy(filesLoading = true) }
            when (val result = fileRepository.probe(path, _uiState.value.settings.editorLimit.bytes)) {
                is FileOperationResult.Success -> {
                    val probe = result.value
                    val entry = RootFileEntry(
                        path = path,
                        name = path.substringAfterLast('/').ifEmpty { "/" },
                        kind = probe.kind,
                        size = probe.size,
                        modifiedAt = Instant.EPOCH,
                        mode = "",
                        owner = "",
                        group = "",
                    )
                    _uiState.update { it.copy(filesLoading = false) }
                    when {
                        probe.kind == FileKind.Directory -> loadDirectory(path)
                        entry.name.endsWith(".zip", ignoreCase = true) -> openZip(path)
                        else -> _uiState.update {
                            it.copy(fileAction = FileActionState(entry, probe, defaultScriptOptions()))
                        }
                    }
                }
                is FileOperationResult.Failure -> {
                    _uiState.update { it.copy(filesLoading = false) }
                    message(result.message)
                }
            }
        }
    }

    private fun openDocument(path: String, readOnly: Boolean) {
        fileOperationJob = viewModelScope.launch {
            _uiState.update { it.copy(filesLoading = true) }
            when (val result = fileRepository.readText(path, _uiState.value.settings.editorLimit.bytes)) {
                is FileOperationResult.Success -> _uiState.update {
                    it.copy(document = result.value, editorText = result.value.text, documentReadOnly = readOnly, filesLoading = false)
                }
                is FileOperationResult.Failure -> {
                    _uiState.update { it.copy(filesLoading = false) }
                    message(result.message)
                }
            }
        }
    }

    private fun loadTextPage(path: String, size: Long, offset: Long) {
        fileOperationJob = viewModelScope.launch {
            _uiState.update { it.copy(filesLoading = true, fileAction = null) }
            when (val result = fileRepository.readTextPage(path, offset, size)) {
                is FileOperationResult.Success -> _uiState.update { it.copy(textPage = result.value, filesLoading = false) }
                is FileOperationResult.Failure -> {
                    _uiState.update { it.copy(filesLoading = false) }
                    message(result.message)
                }
            }
        }
    }

    private fun saveDocument() {
        val state = _uiState.value
        val document = state.document ?: return
        fileOperationJob = viewModelScope.launch {
            _uiState.update { it.copy(filesLoading = true) }
            when (val result = fileRepository.saveText(document, state.editorText, state.settings.editorLimit.bytes)) {
                is FileOperationResult.Success -> {
                    _uiState.update {
                        it.copy(filesLoading = false, document = document.copy(
                            text = state.editorText,
                            originalBytes = state.editorText.toByteArray(),
                        ))
                    }
                    message("已保存并校验 ${document.path}")
                }
                is FileOperationResult.Failure -> {
                    _uiState.update { it.copy(filesLoading = false) }
                    message(result.message)
                }
            }
        }
    }

    private fun defaultScriptOptions() = ScriptExecutionOptions(
        useRoot = _uiState.value.settings.scriptUseRoot,
        temporaryPermission = _uiState.value.settings.scriptPermission,
    )

    private fun filterAndSort(entries: List<RootFileEntry>, query: String, settings: AppSettings): List<RootFileEntry> {
        val comparator = when (settings.fileSortMode) {
            FileSortMode.Name -> compareBy<RootFileEntry> { it.name.lowercase() }
            FileSortMode.Modified -> compareByDescending { it.modifiedAt }
            FileSortMode.Size -> compareByDescending { it.size }
        }
        return entries.asSequence()
            .filter { settings.showHiddenFiles || !it.name.startsWith('.') }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .sortedWith(compareBy<RootFileEntry> { it.kind != FileKind.Directory }.then(comparator))
            .toList()
    }

    private fun isVisible(entry: RootFileEntry, query: String, settings: AppSettings): Boolean =
        (settings.showHiddenFiles || !entry.name.startsWith('.')) &&
            (query.isBlank() || entry.name.contains(query, ignoreCase = true))

    private suspend fun restoreInterruptedPermissionChange() {
        val recovery = recoveryJournal.read() ?: return
        when (fileRepository.chmod(recovery.path, recovery.originalMode)) {
            is FileOperationResult.Success -> {
                recoveryJournal.clear()
                message("已恢复上次中断脚本的文件权限")
            }
            is FileOperationResult.Failure -> message("检测到未恢复的临时 0777；Root 可用后将于下次启动重试")
        }
    }

    private fun cleanupSessionCache() {
        viewModelScope.launch(ioDispatcher) {
            getApplication<Application>().cacheDir.listFiles()
                ?.filter { it.name.startsWith("hypershell-") && it.name != "hypershell-permission-recovery" }
                ?.forEach { file -> if (file.isDirectory) file.deleteRecursively() else file.delete() }
        }
    }

    private fun message(text: String) {
        _events.tryEmit(HyperShellEvent.Message(text))
    }

    override fun onCleared() {
        paneDirectoryJobs.values.forEach(Job::cancel)
        zipRepository.close(_uiState.value.zipArchive)
        terminalSession.close()
    }

    companion object {
        private const val ROOT_AUTHORIZATION_GRACE_MS = 20_000L
        private const val FILE_STREAM_FRAME_NS = 16_000_000L
    }
}
