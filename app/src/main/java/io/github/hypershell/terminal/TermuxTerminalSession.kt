package io.github.hypershell.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.system.Os
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.termux.terminal.TerminalSession as CoreSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TerminalColors
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import io.github.hypershell.settings.TerminalFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Termux's official terminal-emulator and terminal-view wired to HyperShell's lifecycle contract.
 * Background retention is controlled by AppSettings; no foreground service is created.
 */
class TermuxTerminalSession(
    private val context: Context,
    private val environmentManager: TermuxEnvironmentManager,
) : TerminalSession, TerminalSessionClient, TerminalViewClient {
    private val _status = MutableStateFlow<TerminalStatus>(TerminalStatus.Idle)
    override val status = _status.asStateFlow()
    private val _events = MutableSharedFlow<TerminalEvent>()
    override val events: Flow<TerminalEvent> = _events.asSharedFlow()

    private var coreSession: CoreSession? = null
    private var terminalView: TerminalView? = null
    private var mode: TerminalMode = TerminalMode.User
    private var textSizePx = 28
    private var backgroundColor = 0xFF000000.toInt()
    private var terminalFont = TerminalFont.SystemMono
    private var customFontPath: String? = null
    private var transparentBackground = false
    private var onFontSizeChanged: ((Float) -> Unit)? = null

    override suspend fun start(launch: TerminalLaunch, rows: Int, columns: Int) {
        withContext(Dispatchers.Main.immediate) {
            terminateCurrent(updateStatus = false)
            mode = launch.mode
            _status.value = TerminalStatus.Starting
            val command = commandFor(launch)
            val session = CoreSession(
                command.first(),
                launch.cwd,
                command.toTypedArray(),
                environment(),
                10_000,
                this@TermuxTerminalSession,
            )
            coreSession = session
            // Termux starts the PTY when its first size is known. This also lets scripts start
            // while the terminal page is not currently composed.
            session.updateSize(columns.coerceAtLeast(1), rows.coerceAtLeast(1), 0, 0)
            if (terminalView != null) attachViewInternal(session)
        }
    }

    fun attachView(
        view: TerminalView,
        textSizePx: Int,
        backgroundColor: Int,
        terminalFont: TerminalFont,
        customFontPath: String?,
        transparentBackground: Boolean,
        onFontSizeChanged: (Float) -> Unit,
    ) {
        this.textSizePx = textSizePx.coerceAtLeast(8)
        terminalView = view
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.setTerminalViewClient(this)
        view.setTextSize(this.textSizePx)
        this.onFontSizeChanged = onFontSizeChanged
        updateAppearance(backgroundColor, terminalFont, customFontPath, transparentBackground)
        coreSession?.let(::attachViewInternal)
    }

    fun detachView(view: TerminalView) {
        if (terminalView === view) terminalView = null
    }

    fun updateTextSize(textSizePx: Int) {
        this.textSizePx = textSizePx.coerceAtLeast(8)
        terminalView?.setTextSize(this.textSizePx)
    }

    fun updateAppearance(
        backgroundColor: Int,
        terminalFont: TerminalFont,
        customFontPath: String?,
        transparentBackground: Boolean,
    ) {
        this.transparentBackground = transparentBackground
        val opaqueBackground = backgroundColor or (0xFF shl 24)
        this.backgroundColor = if (transparentBackground) 0x00000000 else opaqueBackground
        this.terminalFont = terminalFont
        this.customFontPath = customFontPath
        val foreground = if (!transparentBackground && TerminalColors.getPerceivedBrightnessOfColor(opaqueBackground) >= 150) {
            0xFF101010.toInt()
        } else {
            0xFFF5F5F5.toInt()
        }
        TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND] = this.backgroundColor
        TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_FOREGROUND] = foreground
        TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_CURSOR] = foreground
        coreSession?.emulator?.mColors?.mCurrentColors?.let { colors ->
            colors[TextStyle.COLOR_INDEX_BACKGROUND] = this.backgroundColor
            colors[TextStyle.COLOR_INDEX_FOREGROUND] = foreground
            colors[TextStyle.COLOR_INDEX_CURSOR] = foreground
        }
        terminalView?.apply {
            setBackgroundColor(this@TermuxTerminalSession.backgroundColor)
            setTypeface(typefaceFor(terminalFont, customFontPath))
            onScreenUpdated(true)
        }
    }

    private fun attachViewInternal(session: CoreSession) {
        terminalView?.apply {
            setTerminalViewClient(this@TermuxTerminalSession)
            setTextSize(textSizePx)
            setBackgroundColor(backgroundColor)
            setTypeface(typefaceFor(terminalFont, customFontPath))
            attachSession(session)
        }
    }

    override suspend fun write(bytes: ByteArray) {
        coreSession?.write(bytes, 0, bytes.size)
    }

    override suspend fun resize(rows: Int, columns: Int) {
        if (terminalView == null) {
            coreSession?.updateSize(columns.coerceAtLeast(1), rows.coerceAtLeast(1), 0, 0)
        }
    }

    override suspend fun sendSignal(signal: Int) {
        coreSession?.pid?.takeIf { it > 0 }?.let { pid ->
            runCatching { Os.kill(-pid, signal) }.recoverCatching { Os.kill(pid, signal) }
        }
    }

    override suspend fun terminate() = withContext(Dispatchers.Main.immediate) {
        terminateCurrent(updateStatus = true)
    }

    override fun close() {
        terminateCurrent(updateStatus = true)
    }

    private fun terminateCurrent(updateStatus: Boolean) {
        coreSession?.let { session ->
            session.pid.takeIf { it > 0 }?.let { pid ->
                runCatching { Os.kill(-pid, 9) }
            }
            session.finishIfRunning()
        }
        coreSession = null
        if (updateStatus) _status.value = TerminalStatus.Idle
    }

    private fun commandFor(launch: TerminalLaunch): List<String> = when (launch) {
        is TerminalLaunch.Interactive -> when (launch.runtime) {
            TerminalRuntime.Termux -> when (launch.mode) {
                TerminalMode.User -> listOf(environmentManager.bash.absolutePath, "-l")
                TerminalMode.Root -> listOf("su", "-c", rootCommand(environmentManager.bash.absolutePath, "-l"))
            }
            TerminalRuntime.Ubuntu -> environmentManager.ubuntuCommand()
        }
        is TerminalLaunch.Script -> when (launch.mode) {
            TerminalMode.User -> listOf(environmentManager.bash.absolutePath, launch.path)
            TerminalMode.Root -> listOf(
                "su",
                "-c",
                rootCommand(environmentManager.bash.absolutePath, quote(launch.path)),
            )
        }
    }

    private fun environment() = environmentManager.environment()

    private fun rootCommand(executable: String, arguments: String): String {
        val assignments = environment().joinToString(" ") { assignment ->
            val separator = assignment.indexOf('=')
            "${assignment.substring(0, separator)}=${quote(assignment.substring(separator + 1))}"
        }
        return "exec env $assignments ${quote(executable)} $arguments"
    }

    override fun onTextChanged(changedSession: CoreSession) = terminalView?.onScreenUpdated() ?: Unit
    override fun onTitleChanged(changedSession: CoreSession) = terminalView?.invalidate() ?: Unit
    override fun onSessionFinished(finishedSession: CoreSession) {
        if (coreSession === finishedSession) {
            _status.value = TerminalStatus.Exited(finishedSession.exitStatus)
            terminalView?.onScreenUpdated()
        }
    }

    override fun onCopyTextToClipboard(session: CoreSession, text: String) {
        clipboard().setPrimaryClip(ClipData.newPlainText("terminal", text))
    }

    override fun onPasteTextFromClipboard(session: CoreSession?) {
        val text = clipboard().primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: return
        session?.write(text)
    }

    override fun onBell(session: CoreSession) {
        terminalView?.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
    }

    override fun onColorsChanged(session: CoreSession) = terminalView?.onScreenUpdated() ?: Unit
    override fun onTerminalCursorStateChange(state: Boolean) = Unit
    override fun setTerminalShellPid(session: CoreSession, pid: Int) {
        if (coreSession === session) _status.value = TerminalStatus.Running(mode, pid)
    }

    override fun getTerminalCursorStyle(): Int? = null
    override fun onScale(scale: Float): Float {
        if (scale in 0.9f..1.1f) return scale
        val scaledDensity = context.resources.displayMetrics.scaledDensity
        val step = scaledDensity.coerceAtLeast(1f).toInt()
        val next = (textSizePx + if (scale > 1f) step else -step)
            .coerceIn((9f * scaledDensity).toInt(), (28f * scaledDensity).toInt())
        updateTextSize(next)
        onFontSizeChanged?.invoke(next / scaledDensity)
        return 1f
    }

    override fun onSingleTapUp(e: MotionEvent) {
        terminalView?.let { view ->
            view.post {
                view.requestFocusFromTouch()
                val input = context.getSystemService(InputMethodManager::class.java)
                input?.restartInput(view)
                input?.showSoftInput(view, 0)
            }
        }
    }

    override fun shouldBackButtonBeMappedToEscape() = false
    override fun shouldEnforceCharBasedInput() = true
    override fun shouldUseCtrlSpaceWorkaround() = true
    override fun isTerminalViewSelected() = terminalView != null
    override fun copyModeChanged(copyMode: Boolean) = Unit
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: CoreSession) = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent) = false
    override fun onLongPress(event: MotionEvent) = false
    override fun readControlKey() = false
    override fun readAltKey() = false
    override fun readShiftKey() = false
    override fun readFnKey() = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: CoreSession) = false
    override fun onEmulatorSet() = Unit

    override fun logError(tag: String, message: String) { Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
    override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
    override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, e.message, e) }

    private fun clipboard() = context.getSystemService(ClipboardManager::class.java)
    private fun typefaceFor(font: TerminalFont, customPath: String?): Typeface = when (font) {
        TerminalFont.SystemMono -> Typeface.MONOSPACE
        TerminalFont.SansMono -> Typeface.create("sans-serif-monospace", Typeface.NORMAL)
        TerminalFont.SerifMono -> Typeface.create("serif-monospace", Typeface.NORMAL)
        TerminalFont.MiSans -> File(context.filesDir, "fonts/misans-regular.ttf")
            .takeIf(File::isFile)
            ?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }
            ?: Typeface.MONOSPACE
        TerminalFont.Custom -> customPath
            ?.let(::File)
            ?.takeIf(File::isFile)
            ?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }
            ?: Typeface.MONOSPACE
    }
    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
