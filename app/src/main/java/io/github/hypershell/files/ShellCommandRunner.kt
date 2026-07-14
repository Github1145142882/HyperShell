package io.github.hypershell.files

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.io.File
import java.io.InputStream

data class ShellResult(
    val exitCode: Int,
    val stdout: ByteArray,
    val stderr: String,
    val denied: Boolean = false,
    val timedOut: Boolean = false,
) {
    val successful: Boolean get() = exitCode == 0 && !timedOut
}

class ShellCommandRunner(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun runRootStreaming(
        command: String,
        timeoutMillis: Long = 30_000,
        consumeStdout: suspend (InputStream) -> Unit,
    ): ShellResult = withContext(ioDispatcher) {
        var process: Process? = null
        try {
            coroutineScope {
                process = ProcessBuilder("su", "-c", command).start()
                val active = requireNotNull(process)
                active.outputStream.close()
                val stdout = async(ioDispatcher) {
                    active.inputStream.buffered().use { consumeStdout(it) }
                }
                val stderr = async(ioDispatcher) {
                    active.errorStream.bufferedReader().use { it.readText() }
                }
                val finished = active.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
                if (!finished) active.destroyForcibly()
                stdout.await()
                val errorText = stderr.await()
                ShellResult(
                    exitCode = if (finished) active.exitValue() else -1,
                    stdout = byteArrayOf(),
                    stderr = errorText,
                    denied = errorText.contains("denied", ignoreCase = true) ||
                        errorText.contains("not allowed", ignoreCase = true),
                    timedOut = !finished,
                )
            }
        } catch (cancellation: CancellationException) {
            process?.destroyForcibly()
            throw cancellation
        } catch (error: Throwable) {
            process?.destroyForcibly()
            ShellResult(
                exitCode = -1,
                stdout = byteArrayOf(),
                stderr = error.message ?: "无法启动 su",
            )
        }
    }

    suspend fun runRootToFile(command: String, output: File, timeoutMillis: Long = 120_000): ShellResult =
        withContext(ioDispatcher) {
            var process: Process? = null
            try {
                coroutineScope {
                    process = ProcessBuilder("su", "-c", command).start()
                    val active = requireNotNull(process)
                    active.outputStream.close()
                    val stdout = async(ioDispatcher) { active.inputStream.use { input -> output.outputStream().use(input::copyTo) } }
                    val stderr = async(ioDispatcher) { active.errorStream.bufferedReader().use { it.readText() } }
                    val finished = active.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
                    if (!finished) active.destroyForcibly()
                    stdout.await()
                    val error = stderr.await()
                    ShellResult(if (finished) active.exitValue() else -1, byteArrayOf(), error,
                        error.contains("denied", true) || error.contains("not allowed", true), !finished)
                }
            } catch (cancellation: CancellationException) {
                process?.destroyForcibly(); throw cancellation
            } catch (error: Throwable) {
                process?.destroyForcibly(); ShellResult(-1, byteArrayOf(), error.message ?: "无法启动 su")
            }
        }

    suspend fun runRootFromFile(command: String, input: File, timeoutMillis: Long = 120_000): ShellResult =
        withContext(ioDispatcher) {
            var process: Process? = null
            try {
                coroutineScope {
                    process = ProcessBuilder("su", "-c", command).start()
                    val active = requireNotNull(process)
                    val writer = async(ioDispatcher) { active.outputStream.use { output -> input.inputStream().use { it.copyTo(output) } } }
                    val stdout = async(ioDispatcher) { active.inputStream.use { it.readBytes() } }
                    val stderr = async(ioDispatcher) { active.errorStream.bufferedReader().use { it.readText() } }
                    val finished = active.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
                    if (!finished) active.destroyForcibly()
                    writer.await(); val out = stdout.await(); val error = stderr.await()
                    ShellResult(if (finished) active.exitValue() else -1, out, error,
                        error.contains("denied", true) || error.contains("not allowed", true), !finished)
                }
            } catch (cancellation: CancellationException) {
                process?.destroyForcibly(); throw cancellation
            } catch (error: Throwable) {
                process?.destroyForcibly(); ShellResult(-1, byteArrayOf(), error.message ?: "无法启动 su")
            }
        }

    suspend fun runRoot(
        command: String,
        stdin: ByteArray? = null,
        timeoutMillis: Long = 30_000,
    ): ShellResult = withContext(ioDispatcher) {
        var process: Process? = null
        try {
            coroutineScope {
                process = ProcessBuilder("su", "-c", command).start()
                val active = requireNotNull(process)
                val stdout = async(ioDispatcher) { active.inputStream.use { it.readBytes() } }
                val stderr = async(ioDispatcher) { active.errorStream.bufferedReader().use { it.readText() } }
                val writer = async(ioDispatcher) {
                    active.outputStream.use { output ->
                        if (stdin != null) output.write(stdin)
                    }
                }
                val finished = active.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
                if (!finished) active.destroyForcibly()
                writer.await()
                val errorText = stderr.await()
                ShellResult(
                    exitCode = if (finished) active.exitValue() else -1,
                    stdout = stdout.await(),
                    stderr = errorText,
                    denied = errorText.contains("denied", ignoreCase = true) ||
                        errorText.contains("not allowed", ignoreCase = true),
                    timedOut = !finished,
                )
            }
        } catch (cancellation: CancellationException) {
            process?.destroyForcibly()
            throw cancellation
        } catch (error: Throwable) {
            process?.destroyForcibly()
            ShellResult(
                exitCode = -1,
                stdout = byteArrayOf(),
                stderr = error.message ?: "无法启动 su",
                denied = false,
            )
        }
    }
}

fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
