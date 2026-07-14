package io.github.hypershell.files

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class ZipItem(
    val path: String,
    val name: String,
    val directory: Boolean,
    val size: Long,
    val compressedSize: Long,
    val modifiedAt: Instant,
) {
    val shellScript: Boolean get() = !directory && name.endsWith(".sh", ignoreCase = true)
}

data class ZipArchive(
    val sourcePath: String,
    val localPath: String,
    val name: String,
)

/** Process-local ZIP access. Root is used only to stage/copy files at the archive boundary. */
class ZipRepository(
    context: Context,
    private val runner: ShellCommandRunner = ShellCommandRunner(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val cacheDir = File(context.cacheDir, "hypershell-zip").apply { mkdirs() }

    suspend fun open(sourcePath: String): FileOperationResult<ZipArchive> = withContext(ioDispatcher) {
        val normalized = RootFileRepository.normalizeAbsolutePath(sourcePath)
            ?: return@withContext FileOperationResult.Failure("ZIP 路径必须是绝对路径")
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            return@withContext FileOperationResult.Failure("无法创建 ZIP 临时目录")
        }
        val local = File(cacheDir, "archive-${normalized.hashCode().toUInt().toString(16)}.zip")
        val result = runner.runRootToFile(
            "cat -- ${shellQuote(normalized)}",
            local,
            timeoutMillis = 120_000,
        )
        if (!result.successful) {
            local.delete()
            return@withContext FileOperationResult.Failure("无法读取 ZIP：${result.stderr.take(240)}", result.denied)
        }
        try {
            ZipFile(local).use { zip ->
                if (zip.size() > MAX_ENTRIES) throw IllegalArgumentException("条目超过 $MAX_ENTRIES 个")
                zip.entries().asSequence().forEach { requireSafePath(it.name) }
            }
            FileOperationResult.Success(ZipArchive(normalized, local.absolutePath, normalized.substringAfterLast('/')))
        } catch (error: Throwable) {
            local.delete()
            FileOperationResult.Failure("ZIP 格式无效：${error.message ?: "无法解析"}")
        }
    }

    suspend fun list(archive: ZipArchive, directory: String): FileOperationResult<List<ZipItem>> = withContext(ioDispatcher) {
        try {
            val prefix = normalizeDirectory(directory)
            val children = linkedMapOf<String, ZipItem>()
            ZipFile(archive.localPath).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    val safe = requireSafePath(entry.name)
                    if (!safe.startsWith(prefix) || safe == prefix) return@forEach
                    val remainder = safe.removePrefix(prefix)
                    val first = remainder.substringBefore('/')
                    if (first.isEmpty()) return@forEach
                    val childPath = prefix + first
                    val syntheticDirectory = '/' in remainder || entry.isDirectory
                    val item = if (syntheticDirectory) {
                        ZipItem(childPath, first, true, -1, -1, Instant.EPOCH)
                    } else entry.toItem(childPath, first)
                    children.putIfAbsent(childPath, item)
                }
            }
            FileOperationResult.Success(children.values.sortedWith(compareBy<ZipItem> { !it.directory }.thenBy { it.name.lowercase() }))
        } catch (error: Throwable) {
            FileOperationResult.Failure("无法列出 ZIP：${error.message ?: "读取失败"}")
        }
    }

    suspend fun extractScript(archive: ZipArchive, item: ZipItem): FileOperationResult<String> = withContext(ioDispatcher) {
        if (item.directory) return@withContext FileOperationResult.Failure("目录不能执行")
        try {
            val target = File(cacheDir, "script-${item.path.hashCode().toUInt().toString(16)}.sh")
            ZipFile(archive.localPath).use { zip ->
                val entry = zip.getEntry(item.path) ?: return@withContext FileOperationResult.Failure("ZIP 条目不存在")
                extractEntry(zip, entry, target)
            }
            target.setReadable(true, true)
            target.setExecutable(true, true)
            FileOperationResult.Success(target.absolutePath)
        } catch (error: Throwable) {
            FileOperationResult.Failure("无法准备脚本：${error.message ?: "解压失败"}")
        }
    }

    suspend fun extractToRoot(
        archive: ZipArchive,
        item: ZipItem,
        destinationDirectory: String,
    ): FileOperationResult<String> = withContext(ioDispatcher) {
        val destination = RootFileRepository.normalizeAbsolutePath(destinationDirectory)
            ?: return@withContext FileOperationResult.Failure("目标必须是绝对路径")
        val staging = File(cacheDir, "extract-${System.nanoTime()}").apply { mkdirs() }
        try {
            var total = 0L
            val extracted = mutableListOf<Pair<File, String>>()
            ZipFile(archive.localPath).use { zip ->
                val selectedPrefix = item.path.trimEnd('/') + "/"
                zip.entries().asSequence().forEach { entry ->
                    val safe = requireSafePath(entry.name)
                    if (safe != item.path && !(item.directory && safe.startsWith(selectedPrefix))) return@forEach
                    if (entry.isDirectory) return@forEach
                    total += entry.size.coerceAtLeast(0)
                    require(total <= MAX_EXTRACTED_BYTES) { "解压内容超过 512 MiB 安全上限" }
                    val relative = if (item.directory) safe.removePrefix(item.path.substringBeforeLast('/', "") + "/") else item.name
                    val target = File(staging, relative)
                    require(target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) { "条目越过目标目录" }
                    extractEntry(zip, entry, target)
                    extracted += target to relative
                }
            }
            if (extracted.isEmpty() && !item.directory) return@withContext FileOperationResult.Failure("ZIP 条目不存在")
            extracted.forEach { (source, relative) ->
                val target = "$destination/${relative.replace(File.separatorChar, '/')}"
                val result = runner.runRootFromFile(
                    "mkdir -p -- ${shellQuote(target.substringBeforeLast('/'))} && " +
                        "cat > ${shellQuote(target)} && chmod 0600 -- ${shellQuote(target)}",
                    source,
                    timeoutMillis = 120_000,
                )
                if (!result.successful) return@withContext FileOperationResult.Failure("解压写入失败：${result.stderr.take(240)}", result.denied)
            }
            FileOperationResult.Success("$destination/${item.name}")
        } catch (error: Throwable) {
            FileOperationResult.Failure("解压失败：${error.message ?: "未知错误"}")
        } finally {
            staging.deleteRecursively()
        }
    }

    fun close(archive: ZipArchive?) {
        archive?.localPath?.let(::File)?.delete()
    }

    private fun extractEntry(zip: ZipFile, entry: ZipEntry, target: File) {
        require(entry.size in 0..MAX_SINGLE_ENTRY_BYTES) { "单个条目超过 256 MiB 安全上限" }
        target.parentFile?.mkdirs()
        zip.getInputStream(entry).use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var written = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    written += count
                    require(written <= MAX_SINGLE_ENTRY_BYTES) { "条目展开数据超过安全上限" }
                    output.write(buffer, 0, count)
                }
            }
        }
    }

    private fun ZipEntry.toItem(path: String, name: String) = ZipItem(
        path = path,
        name = name,
        directory = isDirectory,
        size = size,
        compressedSize = compressedSize,
        modifiedAt = lastModifiedTime?.toInstant() ?: Instant.EPOCH,
    )

    companion object {
        private const val MAX_ENTRIES = 10_000
        private const val MAX_SINGLE_ENTRY_BYTES = 256L * 1024 * 1024
        private const val MAX_EXTRACTED_BYTES = 512L * 1024 * 1024

        internal fun requireSafePath(raw: String): String {
            require(raw.isNotBlank() && '\u0000' !in raw) { "空条目名" }
            require(!raw.startsWith('/') && !raw.startsWith('\\') && !Regex("^[A-Za-z]:").containsMatchIn(raw)) { "绝对路径条目" }
            val parts = raw.replace('\\', '/').split('/').filter { it.isNotEmpty() }
            require(parts.none { it == "." || it == ".." }) { "路径穿越条目" }
            return parts.joinToString("/") + if (raw.endsWith('/') && parts.isNotEmpty()) "/" else ""
        }

        internal fun normalizeDirectory(path: String): String = path.trim('/').let { if (it.isEmpty()) "" else "$it/" }
    }
}
