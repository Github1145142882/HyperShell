package io.github.hypershell

import android.app.Application
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.hypershell.settings.AppSettings
import io.github.hypershell.settings.OfficialFontInstaller
import io.github.hypershell.settings.SettingsRepository
import io.github.hypershell.terminal.TerminalTypefaceResolver
import io.github.hypershell.terminal.TerminalHdrController
import io.github.hypershell.ui.AppearancePage
import io.github.hypershell.ui.HyperShellTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class AppearanceActivity : ComponentActivity() {
    private val viewModel: AppearanceSettingsViewModel by viewModels()
    private val fontPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importTerminalFont)
    }
    private val backgroundPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importTerminalBackground)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings = viewModel.settings.collectAsStateWithLifecycle().value
            val fontInstallState = viewModel.fontInstallState.collectAsStateWithLifecycle().value
            val error = viewModel.error.collectAsStateWithLifecycle().value
            HyperShellTheme(settings) {
                AppearancePage(
                    settings = settings,
                    fontInstallState = fontInstallState,
                    error = error,
                    updateSettings = viewModel::update,
                    importCustomFont = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf")) },
                    installMiSans = viewModel::installMiSans,
                    removeMiSans = viewModel::removeMiSans,
                    importBackground = { backgroundPicker.launch(arrayOf("image/*")) },
                    removeBackground = viewModel::removeTerminalBackground,
                    onTerminalHdrChanged = { enabled ->
                        if (!enabled || TerminalHdrController.isSupported(this@AppearanceActivity)) {
                            viewModel.update { it.copy(terminalHdrHighlight = enabled) }
                        } else {
                            viewModel.reportError(TerminalHdrController.unsupportedReason(this@AppearanceActivity))
                        }
                    },
                    openMiSansLicense = {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OfficialFontInstaller.LICENSE_URL)))
                    },
                    dismissError = viewModel::dismissError,
                    back = ::finish,
                )
            }
        }
    }
}

internal class AppearanceSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val fontInstaller = OfficialFontInstaller(application)
    val fontInstallState = fontInstaller.state
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    val settings = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    fun importTerminalFont(uri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val application = getApplication<Application>()
            val target = java.io.File(application.filesDir, "fonts/terminal-custom")
            target.parentFile?.mkdirs()
            val temporary = java.io.File(application.cacheDir, "terminal-font-import")
            runCatching {
                application.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "无法读取字体" }
                    temporary.outputStream().use { output ->
                        val copied = input.copyTo(output)
                        require(copied in 1..MAX_FONT_BYTES) { "字体文件须小于 16 MiB" }
                    }
                }
                val typeface = Typeface.createFromFile(temporary)
                require(TerminalTypefaceResolver.isMonospaced(typeface)) {
                    "终端只支持等宽字体；比例字体会导致字符被横向拉伸"
                }
                check(temporary.renameTo(target) || temporary.copyTo(target, overwrite = true).exists())
                repository.update {
                    it.copy(terminalFont = io.github.hypershell.settings.TerminalFont.Custom, customTerminalFontPath = target.absolutePath)
                }
            }.onFailure { _error.value = it.message ?: "字体导入失败" }
            temporary.delete()
        }
    }

    fun installMiSans() {
        viewModelScope.launch {
            fontInstaller.installMiSans().onSuccess { font ->
                repository.update { it.copy(terminalFont = io.github.hypershell.settings.TerminalFont.MiSans) }
            }.onFailure { _error.value = it.message ?: "MiSans 安装失败" }
        }
    }

    fun removeMiSans() {
        viewModelScope.launch {
            fontInstaller.removeMiSans()
            repository.update {
                if (it.terminalFont == io.github.hypershell.settings.TerminalFont.MiSans) {
                    it.copy(terminalFont = io.github.hypershell.settings.TerminalFont.SystemMono)
                } else it
            }
        }
    }

    fun importTerminalBackground(uri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val application = getApplication<Application>()
            val temporary = java.io.File(application.cacheDir, "terminal-background-import")
            val target = java.io.File(application.filesDir, "appearance/terminal-background")
            runCatching {
                val mime = application.contentResolver.getType(uri).orEmpty()
                require(mime.startsWith("image/")) { "请选择图片文件" }
                application.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "无法读取背景图片" }
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_BACKGROUND_BYTES) { "背景图片须小于 32 MiB" }
                            output.write(buffer, 0, count)
                        }
                        require(total > 0) { "背景图片为空" }
                    }
                }
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(temporary.absolutePath, options)
                require(options.outWidth > 0 && options.outHeight > 0) { "背景图片格式不受支持" }
                require(options.outWidth.toLong() * options.outHeight <= MAX_BACKGROUND_PIXELS) {
                    "背景图片分辨率不能超过 32 MP"
                }
                target.parentFile?.mkdirs()
                moveAtomically(temporary, target)
                repository.update { it.copy(terminalBackgroundImagePath = target.absolutePath) }
            }.onFailure { _error.value = it.message ?: "背景图片导入失败" }
            temporary.delete()
        }
    }

    fun removeTerminalBackground() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val application = getApplication<Application>()
            java.io.File(application.filesDir, "appearance/terminal-background").delete()
            repository.update { it.copy(terminalBackgroundImagePath = null) }
        }
    }

    fun dismissError() { _error.value = null }

    fun reportError(message: String) { _error.value = message }

    private companion object {
        const val MAX_FONT_BYTES = 16L * 1024L * 1024L
        const val MAX_BACKGROUND_BYTES = 32L * 1024L * 1024L
        const val MAX_BACKGROUND_PIXELS = 32_000_000L
    }

    private fun moveAtomically(source: java.io.File, destination: java.io.File) {
        runCatching {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.recoverCatching {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse { error ->
            throw IllegalStateException("无法保存背景图片", error)
        }
    }
}
