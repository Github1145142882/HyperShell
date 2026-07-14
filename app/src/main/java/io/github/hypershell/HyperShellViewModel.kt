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
import io.github.hypershell.settings.TerminalInputMode
import io.github.hypershell.settings.TerminalFont
import io.github.hypershell.terminal.TerminalBuffer
import io.github.hypershell.terminal.TerminalEvent
import io.github.hypershell.terminal.TerminalLaunch
import io.github.hypershell.terminal.TerminalMode
import io.github.hypershell.terminal.TerminalRuntime
import io.github.hypershell.terminal.UbuntuBackend
import io.github.hypershell.terminal.TermuxEnvironmentManager
import io.github.hypershell.terminal.TermuxEnvironmentStatus
import io.github.hypershell.terminal.TerminalSession
import io.github.hypershell.terminal.TerminalSnapshot
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

enum class AppPage { Terminal, Files, Settings, Appearance }

sealed interface Confirmation {
    data class SaveFile(val path: String) : Confirmation
    data class ExtractZip(val name: String, val destination: String) : Confirmation
    data class UbuntuProotFallback(val reason: String) : Confirmation
    data class DisableUnsupportedHdr(val reason: String) : Confirmation
}

data class FileActionState(
    val entry: RootFileEntry,
    val probe: FileProbe,
    val options: ScriptExecutionOptions,
)

data class HyperShellUiState(
    val page: AppPage = AppPage.Terminal,
    val settings: AppSettings = AppSettings(),
    val terminalStatus: TerminalStatus = TerminalStatus.Idle,
    val terminalSnapshot: TerminalSnapshot = TerminalBuffer().snapshot(),
    val terminalInput: String = "",
    val terminalInputMode: TerminalInputMode = TerminalInputMode.CommandEditor,
    val terminalRuntime: TerminalRuntime = TerminalRuntime.Termux,
    val ubuntuBackend: UbuntuBackend = UbuntuBackend.Chroot,
    val termuxEnvironmentStatus: TermuxEnvironmentStatus = TermuxEnvironmentStatus.Checking,
    val rootAccess: RootAccess = RootAccess.Unknown,
    val currentPath: String = "/",
    val entries: List<RootFileEntry> = emptyList(),
    val searchQuery: String = "",
    val filesLoading: Boolean = false,
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
    private val terminalBuffer = TerminalBuffer()
    private val terminalSession = terminalSessionFactory(viewModelScope)
    private val _uiState = MutableStateFlow(HyperShellUiState(terminalSnapshot = terminalBuffer.snapshot()))
    val uiState: StateFlow<HyperShellUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<HyperShellEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<HyperShellEvent> = _events.asSharedFlow()

    private val commandHistory = ArrayDeque<String>()
    private var historyIndex: Int? = null
    private var historyDraft = ""
    private var appForeground = true
    private var rootAuthorizationDeadline = 0L
    private var backgroundTerminationJob: Job? = null
    private var fileOperationJob: Job? = null
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
                terminalBuffer.setMaxScrollback(settings.scrollbackLines)
                trimHistory(settings.commandHistoryLimit)
                _uiState.update { state ->
                    state.copy(settings = settings, entries = filterAndSort(allEntries, state.searchQuery, settings))
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
            terminalSession.status.collect { status -> _uiState.update { it.copy(terminalStatus = status) } }
        }
        viewModelScope.launch {
            termuxEnvironment.status.collect { status ->
                _uiState.update { it.copy(termuxEnvironmentStatus = status) }
            }
        }
        viewModelScope.launch {
            terminalSession.events.collect { event ->
                if (event is TerminalEvent.Output) {
                    val snapshot = withContext(computationDispatcher) { terminalBuffer.accept(event.bytes) }
                    _uiState.update { it.copy(terminalSnapshot = snapshot) }
                }
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
            TerminalRuntime.Ubuntu
        } else {
            TerminalRuntime.Termux
        }
        viewModelScope.launch {
            if (target == TerminalRuntime.Ubuntu) {
                message("正在准备 Ubuntu chroot 环境…")
                termuxEnvironment.ensureUbuntuInstalled(requireProot = false).getOrElse { error ->
                    message(error.message ?: "Ubuntu 环境不可用")
                    return@launch
                }
                termuxEnvironment.checkChrootSupport().onFailure { error ->
                    _uiState.update {
                        it.copy(confirmation = Confirmation.UbuntuProotFallback(error.message ?: "Root chroot 不可用"))
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
            _uiState.update { it.copy(terminalRuntime = target, ubuntuBackend = UbuntuBackend.Chroot) }
            terminalSession.start(
                TerminalLaunch.Interactive(
                    TerminalMode.User,
                    termuxEnvironment.home.absolutePath,
                    target,
                    UbuntuBackend.Chroot,
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

    fun setTerminalInputMode(mode: TerminalInputMode) {
        _uiState.update { it.copy(terminalInputMode = mode) }
    }

    fun setTerminalInput(value: String) {
        historyIndex = null
        _uiState.update { it.copy(terminalInput = value) }
    }

    fun sendTerminalInput() {
        val input = _uiState.value.terminalInput
        if (input.isBlank()) return
        rememberCommand(input)
        historyIndex = null
        _uiState.update { it.copy(terminalInput = "") }
        writeTerminal((input + "\n").toByteArray())
    }

    fun previousCommand() {
        if (commandHistory.isEmpty()) return
        val next = if (historyIndex == null) {
            historyDraft = _uiState.value.terminalInput
            commandHistory.lastIndex
        } else (historyIndex!! - 1).coerceAtLeast(0)
        historyIndex = next
        _uiState.update { it.copy(terminalInput = commandHistory.elementAt(next)) }
    }

    fun nextCommand() {
        val index = historyIndex ?: return
        if (index >= commandHistory.lastIndex) {
            historyIndex = null
            _uiState.update { it.copy(terminalInput = historyDraft) }
        } else {
            historyIndex = index + 1
            _uiState.update { it.copy(terminalInput = commandHistory.elementAt(index + 1)) }
        }
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
        terminalBuffer.resize(rows, columns)
        _uiState.update { it.copy(terminalSnapshot = terminalBuffer.snapshot()) }
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
            is Confirmation.UbuntuProotFallback -> {
                dismissConfirmation()
                startUbuntuProotFallback()
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

    fun loadDirectory(path: String) {
        if (_uiState.value.rootAccess != RootAccess.Granted) return
        val normalized = RootFileRepository.normalizeAbsolutePath(path) ?: return message("请输入绝对路径")
        val previousPath = _uiState.value.currentPath
        val previousEntries = allEntries
        val cached = directoryCache[normalized]
        fileOperationJob?.cancel()
        fileOperationJob = viewModelScope.launch {
            if (cached != null) allEntries = cached
            _uiState.update {
                it.copy(
                    currentPath = normalized,
                    entries = filterAndSort(cached.orEmpty(), it.searchQuery, it.settings),
                    filesLoading = true,
                    document = null,
                    textPage = null,
                    editorText = "",
                )
            }
            val discovered = mutableListOf<RootFileEntry>()
            var receivedEntry = false
            var lastPublishedAt = 0L
            suspend fun publishDiscovered(force: Boolean = false) {
                val now = SystemClock.elapsedRealtimeNanos()
                if (!force && lastPublishedAt != 0L && now - lastPublishedAt < FILE_STREAM_FRAME_NS) return
                lastPublishedAt = now
                val snapshot = discovered.toList()
                withContext(Dispatchers.Main.immediate) {
                    if (_uiState.value.currentPath != normalized) throw CancellationException()
                    allEntries = snapshot
                    val currentState = _uiState.value
                    val visibleEntries = snapshot.filter {
                        isVisible(it, currentState.searchQuery, currentState.settings)
                    }
                    _uiState.update { state ->
                        if (state.currentPath == normalized) {
                            state.copy(entries = visibleEntries, filesLoading = true)
                        } else state
                    }
                }
            }
            when (val result = fileRepository.listStreaming(normalized) { entry ->
                if (_uiState.value.currentPath != normalized) throw CancellationException()
                if (!receivedEntry) {
                    receivedEntry = true
                    discovered.clear()
                }
                discovered += entry
                publishDiscovered()
            }) {
                is FileOperationResult.Success -> {
                    if (receivedEntry) publishDiscovered(force = true) else allEntries = emptyList()
                    directoryCache[normalized] = allEntries
                    val currentState = _uiState.value
                    val visibleEntries = withContext(computationDispatcher) {
                        filterAndSort(allEntries, currentState.searchQuery, currentState.settings)
                    }
                    _uiState.update {
                        it.copy(
                            currentPath = normalized,
                            entries = visibleEntries,
                            filesLoading = false,
                        )
                    }
                    when (val detailed = fileRepository.listDetailed(normalized)) {
                        is FileOperationResult.Success -> {
                            if (_uiState.value.currentPath != normalized) return@launch
                            allEntries = detailed.value
                            directoryCache[normalized] = detailed.value
                            val refreshedState = _uiState.value
                            val detailedEntries = withContext(computationDispatcher) {
                                filterAndSort(detailed.value, refreshedState.searchQuery, refreshedState.settings)
                            }
                            _uiState.update { state ->
                                if (state.currentPath == normalized) state.copy(entries = detailedEntries) else state
                            }
                        }
                        is FileOperationResult.Failure -> Unit
                    }
                }
                is FileOperationResult.Failure -> {
                    allEntries = previousEntries
                    _uiState.update {
                        it.copy(
                            currentPath = previousPath,
                            entries = filterAndSort(previousEntries, it.searchQuery, it.settings),
                            filesLoading = false,
                        )
                    }
                    message(result.message)
                }
            }
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
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    private fun startTerminal(mode: TerminalMode) {
        scriptExecutionJob?.cancel()
        clearCommandHistory()
        terminalBuffer.clear()
        _uiState.update { it.copy(terminalSnapshot = terminalBuffer.snapshot(), page = AppPage.Terminal) }
        viewModelScope.launch {
            val installed = termuxEnvironment.ensureInstalled()
            if (installed.isFailure) {
                message(installed.exceptionOrNull()?.message ?: "Termux 环境不可用")
                return@launch
            }
            val runtime = _uiState.value.terminalRuntime
            if (runtime == TerminalRuntime.Ubuntu) {
                val backend = _uiState.value.ubuntuBackend
                termuxEnvironment.ensureUbuntuInstalled(requireProot = backend == UbuntuBackend.Proot).getOrElse { error ->
                    message(error.message ?: "Ubuntu 环境不可用")
                    return@launch
                }
                if (backend == UbuntuBackend.Chroot) {
                    termuxEnvironment.checkChrootSupport().onFailure { error ->
                        _uiState.update {
                            it.copy(confirmation = Confirmation.UbuntuProotFallback(error.message ?: "Root chroot 不可用"))
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
                    _uiState.value.ubuntuBackend,
                ),
            )
        }
    }

    private fun startUbuntuProotFallback() {
        viewModelScope.launch {
            message("正在准备 proot 兼容模式…")
            termuxEnvironment.ensureUbuntuInstalled(requireProot = true).getOrElse { error ->
                message(error.message ?: "proot 兼容模式不可用")
                return@launch
            }
            terminalSession.terminate()
            _uiState.update { it.copy(terminalRuntime = TerminalRuntime.Ubuntu, ubuntuBackend = UbuntuBackend.Proot) }
            terminalSession.start(
                TerminalLaunch.Interactive(
                    TerminalMode.User,
                    termuxEnvironment.home.absolutePath,
                    TerminalRuntime.Ubuntu,
                    UbuntuBackend.Proot,
                ),
            )
        }
    }

    private fun runScript(path: String, options: ScriptExecutionOptions) {
        scriptExecutionJob?.cancel()
        clearCommandHistory()
        scriptExecutionJob = viewModelScope.launch {
            val installed = termuxEnvironment.ensureInstalled()
            if (installed.isFailure) {
                message(installed.exceptionOrNull()?.message ?: "Termux 环境不可用")
                return@launch
            }
            terminalSession.terminate()
            terminalBuffer.clear()
            _uiState.update { it.copy(page = AppPage.Terminal, terminalSnapshot = terminalBuffer.snapshot()) }
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
                    if (path != null) openBookmarkedPath(path) else loadDirectory(_uiState.value.currentPath)
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

    private fun rememberCommand(command: String) {
        if (commandHistory.lastOrNull() != command) commandHistory.addLast(command)
        trimHistory(_uiState.value.settings.commandHistoryLimit)
    }

    private fun trimHistory(limit: Int) {
        while (commandHistory.size > limit) commandHistory.removeFirst()
    }

    private fun clearCommandHistory() {
        commandHistory.clear()
        historyIndex = null
        historyDraft = ""
        _uiState.update { it.copy(terminalInput = "") }
    }

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
        zipRepository.close(_uiState.value.zipArchive)
        terminalSession.close()
    }

    companion object {
        private const val ROOT_AUTHORIZATION_GRACE_MS = 20_000L
        private const val FILE_STREAM_FRAME_NS = 16_000_000L
    }
}
