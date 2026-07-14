package io.github.hypershell.settings

import android.content.Context
import android.graphics.Typeface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

sealed interface FontInstallState {
    data object NotInstalled : FontInstallState
    data class Downloading(val progress: Float) : FontInstallState
    data class Installed(val path: String) : FontInstallState
    data class Failed(val message: String) : FontInstallState
}

internal data class RemoteZipEntry(
    val compression: Int,
    val crc32: Long,
    val compressedSize: Int,
    val uncompressedSize: Int,
    val localHeaderOffset: Long,
)

class OfficialFontInstaller(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext
    private val target = File(appContext.filesDir, "fonts/misans-regular.ttf")
    private val temporary = File(appContext.cacheDir, "misans-regular.part")
    private val _state = MutableStateFlow<FontInstallState>(
        if (target.isFile) FontInstallState.Installed(target.absolutePath) else FontInstallState.NotInstalled,
    )
    val state: StateFlow<FontInstallState> = _state.asStateFlow()

    suspend fun installMiSans(): Result<File> = withContext(ioDispatcher) {
        try {
            target.parentFile?.mkdirs()
            temporary.delete()
            _state.value = FontInstallState.Downloading(0f)
            val length = contentLength()
            val tailStart = (length - ZIP_TAIL_BYTES).coerceAtLeast(0)
            val tail = requestRange(tailStart, length - 1)
            val (centralOffset, centralSize) = parseEndOfCentralDirectory(tail)
            val central = requestRange(centralOffset, centralOffset + centralSize - 1)
            val entry = parseCentralDirectory(central, TARGET_ENTRY)
            require(entry.compression == DEFLATE) { "MiSans 压缩格式不受支持" }
            require(entry.compressedSize == EXPECTED_COMPRESSED_SIZE) { "MiSans 官方字体包已更新，请更新 HyperShell" }
            require(entry.uncompressedSize == EXPECTED_FONT_SIZE) { "MiSans 官方字体大小不匹配" }

            val localHeader = requestRange(entry.localHeaderOffset, entry.localHeaderOffset + LOCAL_HEADER_PROBE - 1)
            require(localHeader.intLe(0) == LOCAL_HEADER_SIGNATURE) { "MiSans ZIP 本地头无效" }
            val nameLength = localHeader.ushortLe(26)
            val extraLength = localHeader.ushortLe(28)
            val dataOffset = entry.localHeaderOffset + 30L + nameLength + extraLength
            val compressed = requestRange(
                dataOffset,
                dataOffset + entry.compressedSize - 1,
            ) { read ->
                _state.value = FontInstallState.Downloading(read.toFloat() / entry.compressedSize)
            }
            inflateAndVerify(compressed, entry)
            Typeface.createFromFile(temporary)
            moveAtomically(temporary, target)
            _state.value = FontInstallState.Installed(target.absolutePath)
            Result.success(target)
        } catch (cancelled: CancellationException) {
            temporary.delete()
            throw cancelled
        } catch (error: Throwable) {
            temporary.delete()
            val message = error.message ?: "MiSans 下载失败"
            _state.value = FontInstallState.Failed(message)
            Result.failure(error)
        }
    }

    suspend fun removeMiSans() = withContext(ioDispatcher) {
        target.delete()
        temporary.delete()
        _state.value = FontInstallState.NotInstalled
    }

    private fun contentLength(): Long {
        val connection = openConnection().apply { requestMethod = "HEAD" }
        return connection.useConnection {
            require(responseCode in 200..299) { "MiSans 官方服务器返回 $responseCode" }
            contentLengthLong.takeIf { it > 0 } ?: error("MiSans 官方包未提供大小")
        }
    }

    private suspend fun requestRange(
        start: Long,
        endInclusive: Long,
        onProgress: (Int) -> Unit = {},
    ): ByteArray {
        require(start >= 0 && endInclusive >= start)
        val expected = (endInclusive - start + 1).toInt()
        val connection = openConnection().apply { setRequestProperty("Range", "bytes=$start-$endInclusive") }
        return connection.useConnection {
            require(responseCode == HttpURLConnection.HTTP_PARTIAL) { "MiSans 官方服务器不支持分段下载" }
            val contentRange = getHeaderField("Content-Range").orEmpty()
            require(contentRange.startsWith("bytes $start-$endInclusive/")) { "MiSans 分段响应无效" }
            val output = ByteArrayOutputStream(expected)
            inputStream.buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    total += count
                    onProgress(total)
                    require(total <= expected) { "MiSans 分段响应过长" }
                }
                require(total == expected) { "MiSans 下载不完整" }
            }
            output.toByteArray()
        }
    }

    private suspend fun inflateAndVerify(compressed: ByteArray, entry: RemoteZipEntry) {
        val crc = CRC32()
        val digest = MessageDigest.getInstance("SHA-256")
        val inflater = Inflater(true)
        try {
            InflaterInputStream(compressed.inputStream(), inflater).use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= EXPECTED_FONT_SIZE) { "MiSans 解压数据过长" }
                        crc.update(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    require(total == entry.uncompressedSize) { "MiSans 解压数据不完整" }
                }
            }
        } finally {
            inflater.end()
        }
        require(crc.value == entry.crc32 && crc.value == EXPECTED_CRC32) { "MiSans CRC32 校验失败" }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        require(sha256 == EXPECTED_SHA256) { "MiSans SHA-256 校验失败" }
    }

    private fun openConnection() = (URL(DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("Accept-Encoding", "identity")
    }

    private inline fun <T> HttpURLConnection.useConnection(block: HttpURLConnection.() -> T): T =
        try { block() } finally { disconnect() }

    private fun moveAtomically(source: File, destination: File) {
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
            throw IllegalStateException("无法安装 MiSans 字体", error)
        }
    }

    internal companion object {
        const val DOWNLOAD_URL = "https://hyperos.mi.com/font-download/MiSans.zip"
        const val LICENSE_URL = "https://hyperos.mi.com/font/zh/download/"
        const val TARGET_ENTRY = "MiSans/ttf/MiSans-Regular.ttf"
        const val EXPECTED_COMPRESSED_SIZE = 5_441_042
        const val EXPECTED_FONT_SIZE = 8_122_324
        const val EXPECTED_CRC32 = 0x729d48cbL
        const val EXPECTED_SHA256 = "9c120f0a849bc0aa5048daae2a3c0f6eecd828b5b33fce682a9622833f5feea6"
        private const val ZIP_TAIL_BYTES = 65_557L
        private const val LOCAL_HEADER_PROBE = 4_096L
        private const val EOCD_SIGNATURE = 0x06054b50
        private const val CENTRAL_SIGNATURE = 0x02014b50
        private const val LOCAL_HEADER_SIGNATURE = 0x04034b50
        private const val DEFLATE = 8

        internal fun parseEndOfCentralDirectory(tail: ByteArray): Pair<Long, Long> {
            val position = (tail.size - 22 downTo 0).firstOrNull { tail.intLe(it) == EOCD_SIGNATURE }
                ?: error("MiSans ZIP 目录无效")
            val size = tail.uintLe(position + 12)
            val offset = tail.uintLe(position + 16)
            require(size > 0) { "MiSans ZIP 目录为空" }
            return offset to size
        }

        internal fun parseCentralDirectory(bytes: ByteArray, target: String): RemoteZipEntry {
            var offset = 0
            while (offset + 46 <= bytes.size) {
                require(bytes.intLe(offset) == CENTRAL_SIGNATURE) { "MiSans ZIP 中央目录损坏" }
                val flags = bytes.ushortLe(offset + 8)
                val compression = bytes.ushortLe(offset + 10)
                val crc = bytes.uintLe(offset + 16)
                val compressed = bytes.uintLe(offset + 20).toInt()
                val uncompressed = bytes.uintLe(offset + 24).toInt()
                val nameLength = bytes.ushortLe(offset + 28)
                val extraLength = bytes.ushortLe(offset + 30)
                val commentLength = bytes.ushortLe(offset + 32)
                val localHeader = bytes.uintLe(offset + 42)
                val nameStart = offset + 46
                val nameEnd = nameStart + nameLength
                require(nameEnd <= bytes.size) { "MiSans ZIP 文件名越界" }
                val charset = if (flags and 0x800 != 0) Charsets.UTF_8 else Charsets.ISO_8859_1
                val name = bytes.copyOfRange(nameStart, nameEnd).toString(charset)
                if (name == target) {
                    return RemoteZipEntry(compression, crc, compressed, uncompressed, localHeader)
                }
                offset = nameEnd + extraLength + commentLength
            }
            error("MiSans ZIP 缺少目标字体")
        }

        private fun ByteArray.ushortLe(offset: Int): Int =
            ByteBuffer.wrap(this, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff

        private fun ByteArray.intLe(offset: Int): Int =
            ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

        private fun ByteArray.uintLe(offset: Int): Long = intLe(offset).toLong() and 0xffffffffL
    }
}
