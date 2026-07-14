package io.github.hypershell.terminal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

enum class TerminalMode { User, Root }
enum class TerminalRuntime { Termux, Ubuntu }
enum class UbuntuBackend { Chroot, Proot }

sealed interface TerminalLaunch {
    val mode: TerminalMode
    val cwd: String

    data class Interactive(
        override val mode: TerminalMode,
        override val cwd: String,
        val runtime: TerminalRuntime = TerminalRuntime.Termux,
        val ubuntuBackend: UbuntuBackend = UbuntuBackend.Chroot,
    ) : TerminalLaunch
    data class Script(
        val path: String,
        override val mode: TerminalMode,
        override val cwd: String,
    ) : TerminalLaunch
}

sealed interface TerminalStatus {
    data object Idle : TerminalStatus
    data object Starting : TerminalStatus
    data class Running(val mode: TerminalMode, val pid: Int) : TerminalStatus
    data class Exited(val exitCode: Int) : TerminalStatus
    data class Failed(val message: String) : TerminalStatus
}

sealed interface TerminalEvent {
    data class Output(val bytes: ByteArray) : TerminalEvent
}

interface TerminalSession : AutoCloseable {
    val status: StateFlow<TerminalStatus>
    val events: Flow<TerminalEvent>

    suspend fun start(launch: TerminalLaunch, rows: Int = 24, columns: Int = 80)

    suspend fun start(mode: TerminalMode, cwd: String, rows: Int = 24, columns: Int = 80) =
        start(TerminalLaunch.Interactive(mode, cwd), rows, columns)
    suspend fun write(bytes: ByteArray)
    suspend fun resize(rows: Int, columns: Int)
    suspend fun sendSignal(signal: Int)
    suspend fun terminate()

    override fun close()
}

class NativeTerminalSession(
    private val scope: CoroutineScope,
    private val bridge: PtyBridge = PtyBridge(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TerminalSession {
    private val mutex = Mutex()
    private val terminating = AtomicBoolean(false)
    private val _status = MutableStateFlow<TerminalStatus>(TerminalStatus.Idle)
    override val status: StateFlow<TerminalStatus> = _status.asStateFlow()
    private val _events = MutableSharedFlow<TerminalEvent>(extraBufferCapacity = 32)
    override val events: Flow<TerminalEvent> = _events.asSharedFlow()

    private var handle: PtyHandle? = null
    private var readerJob: Job? = null
    private var waiterJob: Job? = null

    override suspend fun start(launch: TerminalLaunch, rows: Int, columns: Int) {
        mutex.withLock {
            terminateLocked()
            terminating.set(false)
            _status.value = TerminalStatus.Starting
            try {
                val command = when (launch) {
                    is TerminalLaunch.Interactive -> when (launch.mode) {
                        TerminalMode.User -> listOf("/system/bin/sh", "-i")
                        TerminalMode.Root -> listOf("su", "-c", "exec /system/bin/sh -i")
                    }
                    is TerminalLaunch.Script -> when (launch.mode) {
                        TerminalMode.User -> listOf("/system/bin/sh", "--", launch.path)
                        TerminalMode.Root -> listOf("su", "-c", "exec /system/bin/sh -- ${quote(launch.path)}")
                    }
                }
                val newHandle = withContext(ioDispatcher) {
                    bridge.start(
                        command = command,
                        cwd = launch.cwd,
                        environment = mapOf(
                            "TERM" to "xterm-256color",
                            "LANG" to "C.UTF-8",
                            "PATH" to "/system/bin:/system/xbin:/vendor/bin:/product/bin",
                        ),
                        rows = rows,
                        columns = columns,
                    )
                }
                handle = newHandle
                _status.value = TerminalStatus.Running(launch.mode, newHandle.pid)
                launchReader(newHandle)
                launchWaiter(newHandle)
            } catch (cancellation: CancellationException) {
                _status.value = TerminalStatus.Idle
                throw cancellation
            } catch (error: Throwable) {
                _status.value = TerminalStatus.Failed(error.message ?: "无法启动终端")
            }
        }
    }

    private fun launchReader(active: PtyHandle) {
        readerJob = scope.launch(ioDispatcher) {
            val buffer = ByteArray(8192)
            try {
                while (true) {
                    val count = bridge.read(active, buffer)
                    if (count <= 0) break
                    _events.emit(TerminalEvent.Output(buffer.copyOf(count)))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                if (!terminating.get()) {
                    _status.value = TerminalStatus.Failed(error.message ?: "终端读取失败")
                }
            }
        }
    }

    private fun launchWaiter(active: PtyHandle) {
        waiterJob = scope.launch(ioDispatcher) {
            val exitCode = bridge.waitFor(active)
            bridge.close(active)
            mutex.withLock {
                if (handle == active) {
                    handle = null
                    if (!terminating.get()) _status.value = TerminalStatus.Exited(exitCode)
                }
            }
        }
    }

    override suspend fun write(bytes: ByteArray) = withContext(ioDispatcher) {
        val active = mutex.withLock { handle } ?: return@withContext
        var offset = 0
        while (offset < bytes.size) {
            val chunk = if (offset == 0) bytes else bytes.copyOfRange(offset, bytes.size)
            val count = bridge.write(active, chunk)
            if (count <= 0) break
            offset += count
        }
    }

    override suspend fun resize(rows: Int, columns: Int) {
        withContext(ioDispatcher) {
            mutex.withLock { handle }?.let { bridge.resize(it, rows, columns) }
        }
    }

    override suspend fun sendSignal(signal: Int) {
        withContext(ioDispatcher) {
            mutex.withLock { handle }?.let { bridge.signal(it, signal) }
        }
    }

    override suspend fun terminate() {
        mutex.withLock { terminateLocked() }
    }

    override fun close() {
        val active = handle ?: return
        terminating.set(true)
        handle = null
        bridge.terminate(active)
        readerJob?.cancel()
        waiterJob?.cancel()
        _status.value = TerminalStatus.Idle
    }

    private suspend fun terminateLocked() {
        val active = handle ?: run {
            _status.value = TerminalStatus.Idle
            return
        }
        terminating.set(true)
        handle = null
        withContext(ioDispatcher) {
            bridge.terminate(active)
        }
        readerJob?.cancel()
        waiterJob?.cancel()
        readerJob = null
        waiterJob = null
        _status.value = TerminalStatus.Idle
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
