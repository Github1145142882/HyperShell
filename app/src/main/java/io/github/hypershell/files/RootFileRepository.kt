package io.github.hypershell.files

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant

enum class RootAccess { Unknown, Granted, Denied, Unavailable }

enum class FileKind { Directory, Regular, SymbolicLink, Other }

data class RootFileEntry(
    val path: String,
    val name: String,
    val kind: FileKind,
    val size: Long,
    val modifiedAt: Instant,
    val mode: String,
    val owner: String,
    val group: String,
)

data class TextDocument(
    val path: String,
    val text: String,
    internal val originalBytes: ByteArray,
)

data class FileProbe(
    val path: String,
    val kind: FileKind,
    val size: Long,
    val shellScript: Boolean,
    val validUtf8Text: Boolean,
)

data class TextPage(
    val path: String,
    val offset: Long,
    val text: String,
    val nextOffset: Long?,
    val previousOffset: Long?,
)

sealed interface FileOperationResult<out T> {
    data class Success<T>(val value: T) : FileOperationResult<T>
    data class Failure(val message: String, val denied: Boolean = false) : FileOperationResult<Nothing>
}

class RootFileRepository(
    private val runner: ShellCommandRunner = ShellCommandRunner(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun checkRoot(): RootAccess {
        val result = runner.runRoot("id -u", timeoutMillis = 20_000)
        if (result.denied) return RootAccess.Denied
        if (!result.successful) return RootAccess.Unavailable
        return if (result.stdout.toString(Charsets.UTF_8).trim() == "0") RootAccess.Granted else RootAccess.Denied
    }

    suspend fun list(directory: String): FileOperationResult<List<RootFileEntry>> {
        val normalized = normalizeAbsolutePath(directory)
            ?: return FileOperationResult.Failure("路径必须是绝对路径")
        val quoted = shellQuote(normalized)
        val command = """
            export LC_ALL=C
            dir=$quoted
            cd -- "${'$'}dir" || exit 2
            for wanted in d n; do
              for name in ./* ./.[!.]* ./..?*; do
                [ -e "${'$'}name" ] || [ -L "${'$'}name" ] || continue
                if [ -L "${'$'}name" ]; then kind=l
                elif [ -d "${'$'}name" ]; then kind=d
                elif [ -f "${'$'}name" ]; then kind=f
                else kind=o
                fi
                if [ "${'$'}wanted" = d ] && [ "${'$'}kind" != d ]; then continue; fi
                if [ "${'$'}wanted" = n ] && [ "${'$'}kind" = d ]; then continue; fi
                if [ "${'$'}dir" = "/" ]; then path="/${'$'}{name#./}"; else path="${'$'}{dir%/}/${'$'}{name#./}"; fi
                printf "%s\0%s\0" "${'$'}kind" "${'$'}path"
              done
            done
        """.trimIndent()
        val result = runner.runRoot(command)
        if (!result.successful) return result.failure("无法读取目录")
        return try {
            FileOperationResult.Success(parseFastDirectoryOutput(result.stdout))
        } catch (error: IllegalArgumentException) {
            FileOperationResult.Failure("目录数据格式无效：${error.message}")
        }
    }

    suspend fun listStreaming(
        directory: String,
        onEntry: suspend (RootFileEntry) -> Unit,
    ): FileOperationResult<Unit> {
        val normalized = normalizeAbsolutePath(directory)
            ?: return FileOperationResult.Failure("路径必须是绝对路径")
        val quoted = shellQuote(normalized)
        val command = """
            export LC_ALL=C
            dir=$quoted
            cd -- "${'$'}dir" || exit 2
            for wanted in d n; do
              for name in ./* ./.[!.]* ./..?*; do
                [ -e "${'$'}name" ] || [ -L "${'$'}name" ] || continue
                if [ -L "${'$'}name" ]; then kind=l
                elif [ -d "${'$'}name" ]; then kind=d
                elif [ -f "${'$'}name" ]; then kind=f
                else kind=o
                fi
                if [ "${'$'}wanted" = d ] && [ "${'$'}kind" != d ]; then continue; fi
                if [ "${'$'}wanted" = n ] && [ "${'$'}kind" = d ]; then continue; fi
                if [ "${'$'}dir" = "/" ]; then path="/${'$'}{name#./}"; else path="${'$'}{dir%/}/${'$'}{name#./}"; fi
                printf "%s\0%s\0" "${'$'}kind" "${'$'}path"
              done
            done
        """.trimIndent()
        val result = runner.runRootStreaming(command) { input ->
            while (true) {
                val kindCode = input.readNullTerminated() ?: break
                val pathBytes = input.readNullTerminated()
                    ?: throw IllegalArgumentException("missing path terminator")
                val path = pathBytes.toString(Charsets.UTF_8)
                onEntry(
                    RootFileEntry(
                        path = path,
                        name = path.substringAfterLast('/').ifEmpty { "/" },
                        kind = when (kindCode.toString(Charsets.US_ASCII)) {
                            "d" -> FileKind.Directory
                            "f" -> FileKind.Regular
                            "l" -> FileKind.SymbolicLink
                            else -> FileKind.Other
                        },
                        size = -1,
                        modifiedAt = Instant.EPOCH,
                        mode = "",
                        owner = "",
                        group = "",
                    ),
                )
            }
        }
        return if (result.successful) FileOperationResult.Success(Unit) else result.failure("无法读取目录")
    }

    suspend fun listDetailed(directory: String): FileOperationResult<List<RootFileEntry>> {
        val normalized = normalizeAbsolutePath(directory)
            ?: return FileOperationResult.Failure("路径必须是绝对路径")
        val quoted = shellQuote(normalized)
        val command = """
            export LC_ALL=C
            dir=$quoted
            cd -- "${'$'}dir" || exit 2
            for name in ./* ./.[!.]* ./..?*; do
              [ -e "${'$'}name" ] || [ -L "${'$'}name" ] || continue
              if [ "${'$'}dir" = "/" ]; then path="/${'$'}{name#./}"; else path="${'$'}{dir%/}/${'$'}{name#./}"; fi
              printf '%s\0' "${'$'}path"
              stat -c '%F|%s|%Y|%a|%U|%G' -- "${'$'}name" || exit 3
            done
        """.trimIndent()
        val result = runner.runRoot(command)
        if (!result.successful) return result.failure("无法补充目录信息")
        return try {
            FileOperationResult.Success(parseDirectoryOutput(result.stdout))
        } catch (error: IllegalArgumentException) {
            FileOperationResult.Failure("目录详细数据格式无效：${error.message}")
        }
    }

    suspend fun readText(path: String, maxTextBytes: Int = 4 * 1_048_576): FileOperationResult<TextDocument> {
        val normalized = normalizeAbsolutePath(path) ?: return FileOperationResult.Failure("路径必须是绝对路径")
        val quoted = shellQuote(normalized)
        val metadata = runner.runRoot("export LC_ALL=C; stat -c '%F|%s' -- $quoted")
        if (!metadata.successful) return metadata.failure("无法读取文件信息")
        val fields = metadata.stdout.toString(Charsets.UTF_8).trim().split('|')
        if (fields.size != 2 || !fields[0].contains("regular file")) {
            return FileOperationResult.Failure("只允许编辑普通文本文件")
        }
        val size = fields[1].toLongOrNull() ?: return FileOperationResult.Failure("文件大小无效")
        if (size > maxTextBytes) return FileOperationResult.Failure("文件超过编辑上限，可使用分页只读查看")

        val content = runner.runRoot("cat -- $quoted")
        if (!content.successful) return content.failure("无法读取文件")
        if (content.stdout.size > maxTextBytes) return FileOperationResult.Failure("文件读取结果超过限制")
        if (content.stdout.any { it == 0.toByte() }) return FileOperationResult.Failure("检测到二进制内容，禁止编辑")
        val text = decodeUtf8(content.stdout) ?: return FileOperationResult.Failure("文件不是有效 UTF-8 文本")
        return FileOperationResult.Success(TextDocument(normalized, text, content.stdout))
    }

    suspend fun saveText(
        document: TextDocument,
        newText: String,
        maxTextBytes: Int = 4 * 1_048_576,
    ): FileOperationResult<Unit> {
        val bytes = newText.toByteArray(Charsets.UTF_8)
        if (bytes.size > maxTextBytes) return FileOperationResult.Failure("保存内容超过编辑上限")
        val quoted = shellQuote(document.path)
        val writeResult = runner.runRoot("cat > $quoted", stdin = bytes)
        if (!writeResult.successful) return writeResult.failure("写入失败")

        val verification = runner.runRoot("cat -- $quoted")
        if (verification.successful && verification.stdout.contentEquals(bytes)) {
            return FileOperationResult.Success(Unit)
        }

        val restore = runner.runRoot("cat > $quoted", stdin = document.originalBytes)
        return if (restore.successful) {
            FileOperationResult.Failure("写入校验失败，已恢复原内容")
        } else {
            FileOperationResult.Failure("写入校验失败，且自动恢复失败；请立即检查文件")
        }
    }

    suspend fun runScript(path: String): ShellResult {
        val normalized = requireNotNull(normalizeAbsolutePath(path)) { "Script path must be absolute" }
        return runner.runRoot("exec /system/bin/sh -- ${shellQuote(normalized)}", timeoutMillis = 120_000)
    }

    suspend fun runDirect(path: String): ShellResult {
        val normalized = requireNotNull(normalizeAbsolutePath(path)) { "Executable path must be absolute" }
        return runner.runRoot("exec ${shellQuote(normalized)}", timeoutMillis = 120_000)
    }

    suspend fun chmodOwnerExecutable(path: String): FileOperationResult<Unit> {
        val normalized = normalizeAbsolutePath(path) ?: return FileOperationResult.Failure("路径必须是绝对路径")
        val result = runner.runRoot("chmod 0700 -- ${shellQuote(normalized)}")
        return if (result.successful) FileOperationResult.Success(Unit) else result.failure("chmod 0700 失败")
    }

    suspend fun probe(path: String, editorLimitBytes: Int): FileOperationResult<FileProbe> {
        val normalized = normalizeAbsolutePath(path) ?: return FileOperationResult.Failure("路径必须是绝对路径")
        val quoted = shellQuote(normalized)
        val metadata = runner.runRoot("export LC_ALL=C; stat -c '%F|%s' -- $quoted")
        if (!metadata.successful) return metadata.failure("无法探测文件")
        val fields = metadata.stdout.toString(Charsets.UTF_8).trim().split('|')
        if (fields.size != 2) return FileOperationResult.Failure("文件探测结果无效")
        val kind = when {
            fields[0].contains("regular file") -> FileKind.Regular
            fields[0].contains("directory") -> FileKind.Directory
            fields[0].contains("symbolic link") -> FileKind.SymbolicLink
            else -> FileKind.Other
        }
        val size = fields[1].toLongOrNull() ?: 0L
        if (kind != FileKind.Regular) {
            return FileOperationResult.Success(FileProbe(normalized, kind, size, false, false))
        }
        val content = runner.runRoot("head -c 256 -- $quoted")
        if (!content.successful) return content.failure("无法读取文件头")
        val prefix = content.stdout
        val prefixText = decodeUtf8(prefix)
        val shellScript = isShellScript(normalized, prefixText)
        val validText = kind == FileKind.Regular && prefix.none { it == 0.toByte() } && prefixText != null
        return FileOperationResult.Success(FileProbe(normalized, kind, size, shellScript, validText))
    }

    suspend fun readTextPage(
        path: String,
        offset: Long,
        fileSize: Long,
        pageBytes: Int = 256 * 1024,
    ): FileOperationResult<TextPage> {
        val normalized = normalizeAbsolutePath(path) ?: return FileOperationResult.Failure("路径必须是绝对路径")
        val safeOffset = offset.coerceIn(0, fileSize)
        val result = runner.runRoot(
            "dd if=${shellQuote(normalized)} bs=1 skip=$safeOffset count=${pageBytes + 4} 2>/dev/null",
        )
        if (!result.successful) return result.failure("无法读取分页内容")
        if (result.stdout.any { it == 0.toByte() }) return FileOperationResult.Failure("检测到二进制内容")
        val usable = validUtf8Prefix(result.stdout, pageBytes)
            ?: return FileOperationResult.Failure("文件不是有效 UTF-8 文本")
        val next = (safeOffset + usable.size).takeIf { it < fileSize }
        val previous = (safeOffset - pageBytes).coerceAtLeast(0).takeIf { safeOffset > 0 }
        return FileOperationResult.Success(
            TextPage(normalized, safeOffset, usable.toString(Charsets.UTF_8), next, previous),
        )
    }

    suspend fun readMode(path: String): FileOperationResult<String> {
        val normalized = normalizeAbsolutePath(path) ?: return FileOperationResult.Failure("路径必须是绝对路径")
        val result = runner.runRoot("stat -c '%a' -- ${shellQuote(normalized)}")
        return if (result.successful) {
            FileOperationResult.Success(result.stdout.toString(Charsets.UTF_8).trim())
        } else result.failure("无法读取权限")
    }

    suspend fun chmod(path: String, mode: String): FileOperationResult<Unit> {
        if (!mode.matches(Regex("[0-7]{3,4}"))) return FileOperationResult.Failure("权限模式无效")
        val normalized = normalizeAbsolutePath(path) ?: return FileOperationResult.Failure("路径必须是绝对路径")
        val result = runner.runRoot("chmod $mode -- ${shellQuote(normalized)}")
        return if (result.successful) FileOperationResult.Success(Unit) else result.failure("chmod 失败")
    }

    suspend fun create(path: String, directory: Boolean): FileOperationResult<Unit> {
        val normalized = normalizeAbsolutePath(path) ?: return FileOperationResult.Failure("路径必须是绝对路径")
        val command = if (directory) "mkdir -- ${shellQuote(normalized)}" else "(umask 077; : > ${shellQuote(normalized)})"
        return runner.runRoot(command).unitResult(if (directory) "新建文件夹失败" else "新建文件失败")
    }

    suspend fun rename(source: String, destination: String): FileOperationResult<Unit> =
        when (val result = move(source, destination, ConflictPolicy.Ask)) {
            is FileOperationResult.Success -> FileOperationResult.Success(Unit)
            is FileOperationResult.Failure -> result
        }

    suspend fun copy(source: String, destination: String, policy: ConflictPolicy): FileOperationResult<String> =
        transfer(source, destination, policy, move = false)

    suspend fun move(source: String, destination: String, policy: ConflictPolicy): FileOperationResult<String> =
        transfer(source, destination, policy, move = true)

    private suspend fun transfer(
        source: String,
        destination: String,
        policy: ConflictPolicy,
        move: Boolean,
    ): FileOperationResult<String> {
        val from = normalizeAbsolutePath(source) ?: return FileOperationResult.Failure("源路径无效")
        val requested = normalizeAbsolutePath(destination) ?: return FileOperationResult.Failure("目标路径无效")
        val target = when (policy) {
            ConflictPolicy.AutoRename -> uniqueDestination(requested)
            else -> requested
        }
        val exists = runner.runRoot("[ -e ${shellQuote(target)} ] || [ -L ${shellQuote(target)} ]")
        if (exists.successful) {
            when (policy) {
                ConflictPolicy.Ask -> return FileOperationResult.Failure("目标已存在", denied = false)
                ConflictPolicy.Skip -> return FileOperationResult.Success(target)
                ConflictPolicy.Overwrite -> Unit
                ConflictPolicy.AutoRename -> Unit
            }
        }
        val temp = "${target}.hypershell-${System.nanoTime()}"
        val qFrom = shellQuote(from)
        val qTarget = shellQuote(target)
        val qTemp = shellQuote(temp)
        val cleanup = "rm -rf -- $qTemp"
        val replace = if (policy == ConflictPolicy.Overwrite) "rm -rf -- $qTarget; " else ""
        val copyCommand =
            "set -e; $cleanup; cp -a -- $qFrom $qTemp; " +
                "[ -e $qTemp ] || [ -L $qTemp ]; $replace mv -- $qTemp $qTarget"
        val copied = runner.runRoot(copyCommand, timeoutMillis = 10 * 60_000)
        if (!copied.successful) {
            runner.runRoot(cleanup)
            return copied.failure(if (move) "移动复制阶段失败" else "复制失败")
        }
        if (move) {
            val deleted = runner.runRoot("rm -rf -- $qFrom", timeoutMillis = 120_000)
            if (!deleted.successful) return deleted.failure("目标已写入，但源路径删除失败")
        }
        return FileOperationResult.Success(target)
    }

    private suspend fun uniqueDestination(requested: String): String {
        val parent = requested.substringBeforeLast('/', "/").ifEmpty { "/" }
        val name = requested.substringAfterLast('/')
        val stem = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
        for (index in 1..9_999) {
            val candidate = "$parent/$stem ($index)$extension".replace("//", "/")
            val exists = runner.runRoot("[ -e ${shellQuote(candidate)} ] || [ -L ${shellQuote(candidate)} ]")
            if (!exists.successful) return candidate
        }
        error("无法生成不冲突的文件名")
    }

    suspend fun delete(paths: List<String>): FileOperationResult<Unit> {
        if (paths.isEmpty()) return FileOperationResult.Success(Unit)
        val normalized = paths.map { normalizeAbsolutePath(it) ?: return FileOperationResult.Failure("路径无效：$it") }
        if (normalized.any { it == "/" }) return FileOperationResult.Failure("禁止删除根目录")
        // rm without a trailing slash removes a symbolic link itself and never follows its target.
        val command = "rm -rf -- " + normalized.joinToString(" ") { shellQuote(it.trimEnd('/')) }
        return runner.runRoot(command, timeoutMillis = 10 * 60_000).unitResult("永久删除失败")
    }

    suspend fun chown(path: String, owner: String, group: String, recursive: Boolean = false): FileOperationResult<Unit> {
        if (!owner.matches(Regex("[A-Za-z0-9_.-]+")) || !group.matches(Regex("[A-Za-z0-9_.-]+"))) {
            return FileOperationResult.Failure("所有者或组名称无效")
        }
        val normalized = normalizeAbsolutePath(path) ?: return FileOperationResult.Failure("路径无效")
        val option = if (recursive) "-R " else ""
        return runner.runRoot("chown ${option}${shellQuote("$owner:$group")} -- ${shellQuote(normalized)}")
            .unitResult("chown 失败")
    }

    suspend fun archive(paths: List<String>, destination: String, format: String): FileOperationResult<Unit> {
        if (paths.isEmpty()) return FileOperationResult.Failure("未选择文件")
        val target = normalizeAbsolutePath(destination) ?: return FileOperationResult.Failure("归档目标无效")
        val normalized = paths.map { normalizeAbsolutePath(it) ?: return FileOperationResult.Failure("归档源路径无效") }
        val command = when (format.lowercase()) {
            "zip" -> "zip -qry -- ${shellQuote(target)} " + normalized.joinToString(" ") { shellQuote(it) }
            "tar" -> "tar -cf ${shellQuote(target)} -- " + normalized.joinToString(" ") { shellQuote(it) }
            "tar.gz", "tgz" -> "tar -czf ${shellQuote(target)} -- " + normalized.joinToString(" ") { shellQuote(it) }
            else -> return FileOperationResult.Failure("不支持的归档格式")
        }
        return runner.runRoot(command, timeoutMillis = 10 * 60_000).unitResult("创建归档失败")
    }

    suspend fun extractArchive(path: String, destination: String): FileOperationResult<Unit> {
        val source = normalizeAbsolutePath(path) ?: return FileOperationResult.Failure("压缩包路径无效")
        val target = normalizeAbsolutePath(destination) ?: return FileOperationResult.Failure("解压目标无效")
        val qSource = shellQuote(source)
        val listCommand = when {
            source.endsWith(".zip", true) -> "unzip -Z1 -- $qSource"
            source.endsWith(".tar", true) || source.endsWith(".tar.gz", true) || source.endsWith(".tgz", true) -> "tar -tf $qSource"
            else -> return FileOperationResult.Failure("仅支持 ZIP、TAR 和 TAR.GZ")
        }
        val listing = runner.runRoot(listCommand, timeoutMillis = 120_000)
        if (!listing.successful) return listing.failure("无法读取压缩包目录")
        val unsafe = listing.stdout.toString(Charsets.UTF_8).lineSequence().firstOrNull { !isSafeArchiveEntry(it) }
        if (unsafe != null) return FileOperationResult.Failure("压缩包包含越界路径：${unsafe.take(120)}")
        // Shell extractors do not consistently protect against an archive-created symlink that is
        // followed by another entry below that link. Reject links before writing anything so a
        // malicious archive cannot escape the staging directory through link traversal.
        val typeCommand = if (source.endsWith(".zip", true)) {
            "unzip -Z -l -- $qSource"
        } else {
            "tar -tvf $qSource"
        }
        val types = runner.runRoot(typeCommand, timeoutMillis = 120_000)
        if (!types.successful) return types.failure("无法验证压缩包条目类型")
        val linkedEntry = types.stdout.toString(Charsets.UTF_8).lineSequence()
            .map(String::trimStart)
            .firstOrNull { it.startsWith('l') || it.startsWith('h') }
        if (linkedEntry != null) return FileOperationResult.Failure("为防止路径逃逸，不解压包含链接的归档")
        val staging = "$target/.hypershell-extract-${System.nanoTime()}"
        val qStaging = shellQuote(staging)
        val qTarget = shellQuote(target)
        val extraction = if (source.endsWith(".zip", true)) {
            "unzip -q -- $qSource -d $qStaging"
        } else {
            "tar -xf $qSource -C $qStaging"
        }
        val command = "set -e; rm -rf -- $qStaging; mkdir -p -- $qStaging $qTarget; " +
            "$extraction; cp -a -- $qStaging/. $qTarget/; rm -rf -- $qStaging"
        val result = runner.runRoot(command, timeoutMillis = 10 * 60_000)
        if (!result.successful) runner.runRoot("rm -rf -- $qStaging")
        return result.unitResult("安全解压失败")
    }

    suspend fun export(path: String, destination: File): FileOperationResult<Unit> {
        val normalized = normalizeAbsolutePath(path) ?: return FileOperationResult.Failure("路径无效")
        val result = runner.runRootToFile("cat -- ${shellQuote(normalized)}", destination)
        return if (result.successful) FileOperationResult.Success(Unit) else result.failure("导出失败")
    }

    private suspend fun decodeUtf8(bytes: ByteArray): String? = withContext(ioDispatcher) {
        try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun validUtf8Prefix(bytes: ByteArray, preferredSize: Int): ByteArray? =
        withContext(ioDispatcher) {
            val end = minOf(bytes.size, preferredSize)
            for (trim in 0..3) {
                val candidateEnd = end - trim
                if (candidateEnd < 0) break
                val candidate = bytes.copyOfRange(0, candidateEnd)
                if (decodeUtf8(candidate) != null) return@withContext candidate
            }
            null
        }

    internal fun parseDirectoryOutput(bytes: ByteArray): List<RootFileEntry> {
        val entries = mutableListOf<RootFileEntry>()
        var cursor = 0
        while (cursor < bytes.size) {
            val nul = bytes.indexOf(0, cursor)
            require(nul >= cursor) { "missing path terminator" }
            val newline = bytes.indexOf('\n'.code.toByte(), nul + 1)
            require(newline > nul) { "missing metadata terminator" }
            val path = bytes.copyOfRange(cursor, nul).toString(Charsets.UTF_8)
            val metadata = bytes.copyOfRange(nul + 1, newline).toString(Charsets.UTF_8).split('|')
            require(metadata.size == 6) { "expected 6 metadata fields" }
            val kind = when {
                metadata[0].contains("directory") -> FileKind.Directory
                metadata[0].contains("regular file") -> FileKind.Regular
                metadata[0].contains("symbolic link") -> FileKind.SymbolicLink
                else -> FileKind.Other
            }
            entries += RootFileEntry(
                path = path,
                name = path.substringAfterLast('/').ifEmpty { "/" },
                kind = kind,
                size = metadata[1].toLongOrNull() ?: 0,
                modifiedAt = Instant.ofEpochSecond(metadata[2].toLongOrNull() ?: 0),
                mode = metadata[3],
                owner = metadata[4],
                group = metadata[5],
            )
            cursor = newline + 1
        }
        return entries.sortedWith(compareBy<RootFileEntry> { it.kind != FileKind.Directory }.thenBy { it.name.lowercase() })
    }

    internal fun parseFastDirectoryOutput(bytes: ByteArray): List<RootFileEntry> {
        val entries = mutableListOf<RootFileEntry>()
        var cursor = 0
        while (cursor < bytes.size) {
            val kindEnd = bytes.indexOf(0, cursor)
            require(kindEnd > cursor) { "missing kind terminator" }
            val pathEnd = bytes.indexOf(0, kindEnd + 1)
            require(pathEnd > kindEnd) { "missing path terminator" }
            val kindCode = bytes.copyOfRange(cursor, kindEnd).toString(Charsets.US_ASCII)
            val path = bytes.copyOfRange(kindEnd + 1, pathEnd).toString(Charsets.UTF_8)
            entries += RootFileEntry(
                path = path,
                name = path.substringAfterLast('/').ifEmpty { "/" },
                kind = when (kindCode) {
                    "d" -> FileKind.Directory
                    "f" -> FileKind.Regular
                    "l" -> FileKind.SymbolicLink
                    else -> FileKind.Other
                },
                size = -1,
                modifiedAt = Instant.EPOCH,
                mode = "",
                owner = "",
                group = "",
            )
            cursor = pathEnd + 1
        }
        return entries
    }

    private fun ShellResult.failure(prefix: String) = FileOperationResult.Failure(
        message = buildString {
            append(prefix)
            stderr.trim().takeIf { it.isNotEmpty() }?.let { append("：").append(it.take(300)) }
            if (timedOut) append("：操作超时")
        },
        denied = denied,
    )

    private fun ShellResult.unitResult(prefix: String): FileOperationResult<Unit> =
        if (successful) FileOperationResult.Success(Unit) else failure(prefix)

    companion object {
        fun isShellScript(path: String, prefixText: String?): Boolean {
            if (path.endsWith(".sh", ignoreCase = true)) return true
            val interpreter = prefixText?.lineSequence()?.firstOrNull()?.takeIf { it.startsWith("#!") }
                ?.substring(2)?.trim()?.substringBefore(' ')?.substringAfterLast('/')?.lowercase()
            return interpreter in setOf("sh", "bash", "ash", "zsh")
        }

        fun normalizeAbsolutePath(path: String): String? {
            if (!path.startsWith('/')) return null
            val stack = ArrayDeque<String>()
            path.split('/').forEach { part ->
                when (part) {
                    "", "." -> Unit
                    ".." -> if (stack.isNotEmpty()) stack.removeLast()
                    else -> stack.addLast(part)
                }
            }
            return "/" + stack.joinToString("/")
        }

        fun isSafeArchiveEntry(path: String): Boolean {
            val normalized = path.replace('\\', '/').trimEnd('/')
            if (normalized.isBlank() || normalized.startsWith('/')) return false
            return normalized.split('/').none { it.isEmpty() || it == ".." }
        }
    }
}

private fun InputStream.readNullTerminated(): ByteArray? {
    val output = ByteArrayOutputStream(128)
    while (true) {
        val value = read()
        if (value < 0) return output.takeIf { it.size() > 0 }?.toByteArray()
        if (value == 0) return output.toByteArray()
        output.write(value)
    }
}

private fun ByteArray.indexOf(value: Byte, startIndex: Int): Int {
    for (index in startIndex until size) if (this[index] == value) return index
    return -1
}
