package io.github.hypershell.terminal

internal object PtyNative {
    init {
        System.loadLibrary("hypershell_pty")
    }

    external fun nativeStart(
        command: Array<String>,
        cwd: String,
        environment: Array<String>,
        rows: Int,
        columns: Int,
    ): LongArray

    external fun nativeRead(fd: Int, buffer: ByteArray, offset: Int, length: Int): Int
    external fun nativeWrite(fd: Int, buffer: ByteArray, offset: Int, length: Int): Int
    external fun nativeResize(fd: Int, rows: Int, columns: Int)
    external fun nativeWait(pid: Int): Int
    external fun nativeSignal(pid: Int, signal: Int)
    external fun nativeTerminate(pid: Int)
    external fun nativeClose(fd: Int)
}

data class PtyHandle(val fd: Int, val pid: Int)

class PtyBridge {
    fun start(
        command: List<String>,
        cwd: String,
        environment: Map<String, String>,
        rows: Int,
        columns: Int,
    ): PtyHandle {
        val result = PtyNative.nativeStart(
            command = command.toTypedArray(),
            cwd = cwd,
            environment = environment.map { (key, value) -> "$key=$value" }.toTypedArray(),
            rows = rows,
            columns = columns,
        )
        check(result.size == 2) { "Invalid PTY result" }
        if (result[0] < 0) {
            throw PtyException(errorNumber = (-result[1]).toInt())
        }
        return PtyHandle(fd = result[0].toInt(), pid = result[1].toInt())
    }

    fun read(handle: PtyHandle, buffer: ByteArray): Int =
        PtyNative.nativeRead(handle.fd, buffer, 0, buffer.size).also { result ->
            if (result < 0) throw PtyException(-result)
        }

    fun write(handle: PtyHandle, bytes: ByteArray): Int =
        PtyNative.nativeWrite(handle.fd, bytes, 0, bytes.size).also { result ->
            if (result < 0) throw PtyException(-result)
        }

    fun resize(handle: PtyHandle, rows: Int, columns: Int) =
        PtyNative.nativeResize(handle.fd, rows, columns)

    fun waitFor(handle: PtyHandle): Int = PtyNative.nativeWait(handle.pid)

    fun signal(handle: PtyHandle, signal: Int) = PtyNative.nativeSignal(handle.pid, signal)

    fun terminate(handle: PtyHandle) = PtyNative.nativeTerminate(handle.pid)

    fun close(handle: PtyHandle) = PtyNative.nativeClose(handle.fd)
}

class PtyException(val errorNumber: Int) : Exception("PTY operation failed (errno=$errorNumber)")

