package io.github.hypershell.terminal

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.system.Os
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

sealed interface TermuxEnvironmentStatus {
    data object Checking : TermuxEnvironmentStatus
    data object Installing : TermuxEnvironmentStatus
    data class Ready(val prefix: String) : TermuxEnvironmentStatus
    data class Failed(val message: String) : TermuxEnvironmentStatus
}

class TermuxEnvironmentManager(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val debianMutex = Mutex()
    private val _status = MutableStateFlow<TermuxEnvironmentStatus>(TermuxEnvironmentStatus.Checking)
    val status = _status.asStateFlow()

    val prefix = File(appContext.filesDir, "usr")
    val home = File(appContext.filesDir, "home")
    val bash = File(prefix, "bin/bash")
    val offlineRepository = File(appContext.filesDir, "repository")
    val debianRoot = File(appContext.filesDir, "debian-rootfs")
    private val legacyUbuntuRoot = File(appContext.filesDir, "ubuntu-rootfs")

    suspend fun ensureInstalled(): Result<Unit> = mutex.withLock {
        withContext(ioDispatcher) {
            if (isReady()) {
                ensureOfflineRepository()
                _status.value = TermuxEnvironmentStatus.Ready(prefix.absolutePath)
                return@withContext Result.success(Unit)
            }
            _status.value = TermuxEnvironmentStatus.Installing
            runCatching { install() }
                .onSuccess { _status.value = TermuxEnvironmentStatus.Ready(prefix.absolutePath) }
                .onFailure { error ->
                    _status.value = TermuxEnvironmentStatus.Failed(
                        error.message ?: "Termux bootstrap 安装失败",
                    )
                }
        }
    }

    fun environment(): Array<String> {
        val prefixPath = prefix.absolutePath
        return arrayOf(
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "LANG=C.UTF-8",
            "HOME=${home.absolutePath}",
            "PREFIX=$prefixPath",
            "TMPDIR=$prefixPath/tmp",
            "SHELL=$prefixPath/bin/bash",
            "PATH=$prefixPath/bin:$prefixPath/bin/applets:/system/bin:/system/xbin:/vendor/bin:/product/bin",
        )
    }

    fun hasLegacyUbuntu(): Boolean = legacyUbuntuRoot.isDirectory

    suspend fun deleteLegacyUbuntu() = withContext(ioDispatcher) {
        legacyUbuntuRoot.deleteRecursively()
        File(appContext.filesDir, "ubuntu-rootfs-staging").deleteRecursively()
    }

    suspend fun ensureDebianInstalled(requireProot: Boolean = false): Result<Unit> {
        ensureInstalled().getOrElse { return Result.failure(it) }
        return debianMutex.withLock {
            withContext(ioDispatcher) {
                runCatching {
                    val asset = debianAssetName()
                    val expected = expectedDigest(asset)
                    val marker = File(debianRoot, DEBIAN_MARKER)
                    if (requireProot) ensureProot()
                    // A valid user environment survives app updates even when the bundled base
                    // digest changes. This prevents an APK upgrade from erasing installed data.
                    if (File(debianRoot, "bin/bash").isFile && marker.isFile) {
                        configureDebianRoot(debianRoot)
                        return@runCatching
                    }

                    val staging = File(appContext.filesDir, "debian-rootfs-staging")
                    val archive = File(appContext.cacheDir, "hypershell-debian-13.tar.gz")
                    staging.deleteRecursively()
                    archive.delete()
                    check(staging.mkdirs()) { "无法创建 Debian 暂存目录" }
                    try {
                        val digest = MessageDigest.getInstance("SHA-256")
                        appContext.assets.open(asset).use { input ->
                            archive.outputStream().buffered().use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    digest.update(buffer, 0, count)
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        val actual = digest.digest().joinToString("") { "%02x".format(it) }
                        require(actual == expected) { "Debian 13 SHA-256 校验失败" }
                        val tar = File(prefix, "bin/tar")
                        validateTarArchive(tar, archive)
                        val child = ProcessBuilder(tar.absolutePath, "-xzf", archive.absolutePath, "-C", staging.absolutePath)
                            .redirectErrorStream(true)
                            .apply {
                                environment().apply {
                                    clear()
                                    this@TermuxEnvironmentManager.environment().forEach { assignment ->
                                        val separator = assignment.indexOf('=')
                                        put(assignment.substring(0, separator), assignment.substring(separator + 1))
                                    }
                                }
                            }
                            .start()
                        val output = child.inputStream.bufferedReader().use { it.readText() }
                        check(child.waitFor() == 0) { "Debian 13 解压失败：${output.takeLast(1200)}" }
                        configureDebianRoot(staging)
                        File(staging, DEBIAN_MARKER).writeText("$actual\n")
                        debianRoot.deleteRecursively()
                        check(staging.renameTo(debianRoot)) { "无法启用 Debian 环境" }
                    } finally {
                        archive.delete()
                        staging.deleteRecursively()
                    }
                }
            }
        }
    }

    fun debianCommand(backend: LinuxBackend): List<String> = when (backend) {
        LinuxBackend.Chroot -> chrootInteractiveCommand()
        LinuxBackend.Proot -> debianProotCommand()
    }

    suspend fun checkChrootSupport(): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val command = chrootProcessCommand("probe")
            val child = ProcessBuilder(command).redirectErrorStream(true).start()
            if (!child.waitFor(CHROOT_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                child.destroyForcibly()
                error("Root chroot 能力检查超时；KernelSU 可能尚未授予权限")
            }
            val output = child.inputStream.bufferedReader().use { it.readText().trim() }
            check(child.exitValue() == 0) {
                if (output.isNotBlank()) output.takeLast(600)
                else "Root chroot 不可用：请检查 KernelSU 的 UID 0、CAP_SYS_ADMIN、CAP_SYS_CHROOT 与 SELinux 配置"
            }
        }
    }

    private fun debianProotCommand(): List<String> {
        val proot = File(prefix, "bin/proot")
        check(proot.isFile && File(debianRoot, "bin/bash").isFile) { "Debian 环境尚未安装" }
        return listOf(
            proot.absolutePath,
            "--link2symlink",
            "--kill-on-exit",
            "-0",
            "-r", debianRoot.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "${home.absolutePath}:/host-home",
            "-w", "/root",
            "/usr/bin/env", "-i",
            "HOME=/root", "USER=root", "LOGNAME=root",
            "TERM=xterm-256color", "LANG=C.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "/bin/bash", "-lc", debianFirstRunCommand(),
        )
    }

    private fun chrootProcessCommand(action: String): List<String> {
        val sourceApk = appContext.applicationInfo.sourceDir
        val nativeLibrary = File(appContext.applicationInfo.nativeLibraryDir, "libhypershell_chroot.so")
        check(nativeLibrary.isFile) { "APK 缺少 chroot Native 组件" }
        val className = ChrootProcessMain::class.java.name
        val command = "exec env CLASSPATH=${shellQuote(sourceApk)} /system/bin/app_process /system/bin " +
            "${shellQuote(className)} ${shellQuote(nativeLibrary.absolutePath)} ${shellQuote(action)} " +
            "${shellQuote(debianRoot.absolutePath)} ${shellQuote(home.absolutePath)}"
        return listOf("su", "-c", command)
    }

    /**
     * Starts chroot without app_process/JNI in the PTY child chain. app_process closes or
     * reconfigures the controlling terminal on some KernelSU builds, making an otherwise
     * successful interactive bash receive EOF immediately. System unshare/chroot preserve the
     * PTY descriptors created by Termux's TerminalSession.
     */
    private fun chrootInteractiveCommand(): List<String> {
        val root = shellQuote(debianRoot.absolutePath)
        val hostHome = shellQuote(home.absolutePath)
        val login = shellQuote(debianFirstRunCommand())
        val inner = """
            set -e
            busybox=''
            for candidate in /data/adb/ksu/bin/busybox /data/adb/magisk/busybox; do
                if [ -x "${'$'}candidate" ]; then busybox="${'$'}candidate"; break; fi
            done
            [ -n "${'$'}busybox" ] || { echo 'HyperShell chroot: root manager BusyBox not found' >&2; exit 127; }
            "${'$'}busybox" mount --make-rprivate /
            "${'$'}busybox" mount --rbind /dev $root/dev
            "${'$'}busybox" mount -t proc proc $root/proc
            "${'$'}busybox" mount --rbind /sys $root/sys
            "${'$'}busybox" mount --bind $hostHome $root/root
            exec /system/bin/chroot $root /usr/bin/env -i \
                HOME=/root USER=root LOGNAME=root SHELL=/bin/bash TERM=xterm-256color \
                LANG=C.UTF-8 PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
                /bin/bash -lc $login
        """.trimIndent()
        val command = "exec /system/bin/unshare -m /system/bin/sh -c ${shellQuote(inner)}"
        return listOf("su", "-c", command)
    }

    private fun isReady(): Boolean {
        val marker = File(prefix, MARKER)
        val expected = runCatching { expectedDigest(bootstrapAssetName()) }.getOrNull() ?: return false
        return bash.isFile &&
            bash.canExecute() &&
            marker.isFile &&
            marker.readText().trim().equals(expected, ignoreCase = true)
    }

    private fun install() {
        val asset = bootstrapAssetName()
        val staging = File(appContext.filesDir, "usr-staging")
        val cachedZip = File(appContext.cacheDir, "hypershell-bootstrap.zip")
        staging.deleteRecursively()
        cachedZip.delete()
        check(staging.mkdirs()) { "无法创建 bootstrap 暂存目录" }
        home.mkdirs()

        val symlinks = mutableListOf<Pair<String, String>>()
        try {
            val source = try {
                appContext.assets.open(asset)
            } catch (_: Exception) {
                error("APK 未包含 ${asset.substringAfterLast('/')}，请先运行 termux-build/scripts/build-bootstrap.sh")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            source.use { input ->
                cachedZip.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            val expectedDigest = expectedDigest(asset)
            val actualDigest = digest.digest().joinToString("") { "%02x".format(it) }
            require(actualDigest == expectedDigest) { "bootstrap SHA-256 校验失败" }

            cachedZip.inputStream().buffered().use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.name == "SYMLINKS.txt") {
                            val reader = BufferedReader(InputStreamReader(zip))
                            while (true) {
                                val line = reader.readLine() ?: break
                                val parts = line.split('←', limit = 2)
                                require(parts.size == 2) { "bootstrap 符号链接记录无效" }
                                symlinks += parts[0] to parts[1]
                            }
                        }
                        else {
                            val target = safeTarget(staging, entry.name)
                            if (entry.isDirectory) {
                                check(target.mkdirs() || target.isDirectory) { "无法创建 ${entry.name}" }
                            } else {
                                check(target.parentFile?.mkdirs() != false || target.parentFile?.isDirectory == true) {
                                    "无法创建 ${entry.name} 的父目录"
                                }
                                target.outputStream().buffered().use { output -> zip.copyTo(output) }
                                if (needsExecutePermission(entry.name)) Os.chmod(target.absolutePath, 0b111_000_000)
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
            require(symlinks.isNotEmpty()) { "bootstrap 缺少 SYMLINKS.txt" }
            symlinks.forEach { (oldPath, relativeNewPath) ->
                val link = safeTarget(staging, relativeNewPath)
                check(link.parentFile?.mkdirs() != false || link.parentFile?.isDirectory == true)
                Os.symlink(oldPath, link.absolutePath)
            }

            prefix.deleteRecursively()
            check(staging.renameTo(prefix)) { "无法启用 bootstrap 目录" }
            runSecondStage()
            ensureOfflineRepository()
            File(prefix, MARKER).writeText("$actualDigest\n")
        } catch (error: Throwable) {
            staging.deleteRecursively()
            if (!isReady()) prefix.deleteRecursively()
            throw error
        } finally {
            cachedZip.delete()
        }
    }

    private fun runSecondStage() {
        val script = File(prefix, "etc/termux/bootstrap/termux-bootstrap-second-stage.sh")
        if (!script.isFile) return
        val process = ProcessBuilder(bash.absolutePath, script.absolutePath)
            .directory(home)
            .redirectErrorStream(true)
        process.environment().apply {
            clear()
            environment().forEach { assignment ->
                val separator = assignment.indexOf('=')
                put(assignment.substring(0, separator), assignment.substring(separator + 1))
            }
        }
        val child = process.start()
        val output = child.inputStream.bufferedReader().use { it.readText() }
        val exitCode = child.waitFor()
        check(exitCode == 0) { "bootstrap 第二阶段失败 ($exitCode)：${output.takeLast(1200)}" }
    }

    private fun ensureOfflineRepository() {
        val packagedVersion = appContext.assets.open("repository/VERSION").bufferedReader().use { it.readText().trim() }
        val expected = "$packagedVersion-layout-$REPOSITORY_LAYOUT_VERSION"
        val marker = File(offlineRepository, "VERSION")
        val installedVersion = marker.readTextOrNull()
        if (installedVersion == expected) {
            configureAptSources()
            return
        }
        if (offlineRepository.isDirectory && installedVersion == packagedVersion) {
            migrateRepositoryFileNames(offlineRepository)
            marker.writeText("$expected\n")
            configureAptSources()
            updateLocalAptIndexes()
            return
        }
        val staging = File(appContext.filesDir, "repository-staging")
        staging.deleteRecursively()
        check(staging.mkdirs()) { "无法创建离线软件源目录" }
        copyAssetTree("repository", staging)
        offlineRepository.deleteRecursively()
        check(staging.renameTo(offlineRepository)) { "无法启用离线软件源" }
        File(offlineRepository, "VERSION").writeText("$expected\n")
        configureAptSources()
        updateLocalAptIndexes()
    }

    private fun migrateRepositoryFileNames(repository: File) {
        repository.walkBottomUp().forEach { current ->
            val androidName = androidRepositoryFileName(current.name)
            if (androidName != current.name) {
                val target = File(current.parentFile, androidName)
                check(!target.exists() && current.renameTo(target)) {
                    "无法迁移软件源文件：${current.name}"
                }
            }
        }
    }

    private fun copyAssetTree(assetPath: String, target: File) {
        val children = appContext.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            check(target.parentFile?.mkdirs() != false || target.parentFile?.isDirectory == true)
            appContext.assets.open(assetPath).use { input -> target.outputStream().use(input::copyTo) }
            return
        }
        check(target.mkdirs() || target.isDirectory) { "无法创建 ${target.name}" }
        children.forEach { child ->
            // Windows cannot store ':' in filenames. The repository staging scripts encode it
            // as U+F03A, while the Debian Packages index correctly retains ':'. Restore the real
            // filename when copying into Android's private filesystem, which supports colons.
            val androidName = androidRepositoryFileName(child)
            copyAssetTree("$assetPath/$child", File(target, androidName))
        }
    }

    private fun configureAptSources() {
        val sources = File(prefix, "etc/apt/sources.list")
        sources.parentFile?.mkdirs()
        val keyring = installRepositoryKey()
        sources.writeText("# HyperShell sources are managed in sources.list.d.\n")
        val sourceParts = File(prefix, "etc/apt/sources.list.d")
        sourceParts.mkdirs()
        sourceParts.listFiles()?.forEach { file -> if (file.isFile) file.delete() }
        localSourceList().writeText(
            "deb [trusted=yes] file:${offlineRepository.absolutePath} stable main\n",
        )
        File(sourceParts, "hypershell-remote.list").writeText(
            "deb [signed-by=${keyring.absolutePath}] $REMOTE_REPOSITORY stable main\n",
        )
        disablePkgMirrorSelection()
        val pacman = File(prefix, "etc/pacman.conf")
        if (pacman.isFile) {
            val optionsOnly = pacman.readLines().takeWhile { !it.trim().equals("[main]", ignoreCase = true) }
            pacman.writeText(
                optionsOnly.joinToString("\n").trimEnd() +
                    "\n\n# HyperShell packages are maintained through the bundled apt/pkg repository.\n",
            )
        }
        repairPacmanDatabase()
        repairPacmanLauncher()
    }

    private fun installRepositoryKey(): File {
        val keyring = File(prefix, "etc/apt/keyrings/hypershell-repository.asc")
        keyring.parentFile?.mkdirs()
        appContext.assets.open("hypershell-repository.asc").use { input ->
            keyring.outputStream().use(input::copyTo)
        }
        return keyring
    }

    private fun disablePkgMirrorSelection() {
        val pkg = File(prefix, "bin/pkg")
        if (!pkg.isFile) return
        val marker = "# HYPERSHELL_OFFLINE_REPOSITORY"
        val script = pkg.readText()
        if (marker in script) return
        val functionStart = "select_mirror() {"
        if (functionStart !in script) return
        pkg.writeText(
            script.replaceFirst(
                functionStart,
                "$functionStart\n\t$marker\n\treturn 0",
            ),
        )
        Os.chmod(pkg.absolutePath, 0b111_000_000)
    }

    private fun repairPacmanDatabase() {
        val local = File(prefix, "var/lib/pacman/local")
        if (!local.isDirectory) local.mkdirs()
        val version = File(local, "ALPM_DB_VERSION")
        if (version.readTextOrNull() == PACMAN_DB_VERSION) return
        val packageEntries = local.listFiles().orEmpty().filterNot { file ->
            file.name == ".placeholder" || file.name == "ALPM_DB_VERSION"
        }
        // The bundled runtime has never been managed by pacman, so its local
        // database is empty. Do not rewrite a non-empty user database.
        if (packageEntries.isEmpty()) version.writeText("$PACMAN_DB_VERSION\n")
    }

    private fun repairPacmanLauncher() {
        val pacman = File(prefix, "bin/pacman")
        val realPacman = File(prefix, "libexec/hypershell/pacman-real")
        val marker = "# HYPERSHELL_PACMAN_LAUNCHER"
        if (pacman.isFile && pacman.readPrefix(marker.length + 96).contains(marker)) {
            check(realPacman.isFile) { "pacman 真实程序缺失，请重新安装应用数据" }
            val restored = File(prefix, "bin/.pacman-restored")
            realPacman.copyTo(restored, overwrite = true)
            Os.chmod(restored.absolutePath, 0b111_000_000)
            check(pacman.delete() && restored.renameTo(pacman)) { "无法恢复 pacman 程序" }
        }
        if (pacman.isFile) Os.chmod(pacman.absolutePath, 0b111_000_000)
        // Keep the old name as a compatibility alias for users who followed
        // the previous broken launcher's message.
        val alias = File(prefix, "bin/pacman-real")
        if (pacman.isFile && !alias.exists()) runCatching { Os.symlink("pacman", alias.absolutePath) }
    }

    private fun configureDebianRoot(root: File) {
        File(root, "etc/resolv.conf").apply {
            parentFile?.mkdirs()
            val dns = currentDnsServers().ifEmpty { listOf("1.1.1.1", "8.8.8.8") }
            writeText(dns.distinct().joinToString(separator = "\n", postfix = "\n") { "nameserver $it" })
        }
        File(root, "etc/apt/sources.list").apply {
            parentFile?.mkdirs()
            writeText("# Debian sources are defined in sources.list.d/debian.sources\n")
        }
        File(root, "etc/apt/sources.list.d").apply {
            mkdirs()
            listFiles()?.forEach { if (it.isFile) it.delete() }
        }
        File(root, "etc/apt/sources.list.d/debian.sources").writeText(debianAptSources())
        check(File(root, "usr/share/keyrings/debian-archive-keyring.pgp").isFile) {
            "Debian archive keyring missing"
        }
        File(root, "etc/apt/apt.conf.d/99hypershell-network").apply {
            parentFile?.mkdirs()
            writeText(
                "Acquire::Retries \"3\";\n" +
                    "Acquire::ForceIPv4 \"true\";\n" +
                    "Acquire::http::Timeout \"25\";\n" +
                    "Acquire::https::Timeout \"25\";\n",
            )
        }
        File(root, "usr/sbin/policy-rc.d").apply {
            parentFile?.mkdirs()
            writeText("#!/bin/sh\nexit 101\n")
            Os.chmod(absolutePath, 0b111_101_101)
        }
    }

    private fun currentDnsServers(): List<String> = runCatching {
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
        val network = connectivity?.activeNetwork ?: return@runCatching emptyList()
        connectivity.getLinkProperties(network)?.dnsServers.orEmpty().map { it.hostAddress }
    }.getOrDefault(emptyList())

    private fun File.readPrefix(limit: Int): String = runCatching {
        inputStream().buffered().use { input ->
            val bytes = ByteArray(limit)
            val count = input.read(bytes)
            if (count > 0) bytes.decodeToString(0, count) else ""
        }
    }.getOrDefault("")

    private fun localSourceList() = File(prefix, "etc/apt/sources.list.d/hypershell-local.list")

    private fun updateLocalAptIndexes() {
        val aptGet = File(prefix, "bin/apt-get")
        if (!aptGet.isFile) return
        val process = ProcessBuilder(localAptUpdateCommand(aptGet, localSourceList()))
            .directory(home)
            .redirectErrorStream(true)
        process.environment().apply {
            clear()
            environment().forEach { assignment ->
                val separator = assignment.indexOf('=')
                put(assignment.substring(0, separator), assignment.substring(separator + 1))
            }
        }
        val child = process.start()
        val output = child.inputStream.bufferedReader().use { it.readText() }
        val exitCode = child.waitFor()
        check(exitCode == 0) { "本地软件源索引失败 ($exitCode)：${output.takeLast(1200)}" }
    }

    private fun ensureProot() {
        if (File(prefix, "bin/proot").isFile) return
        updateLocalAptIndexes()
        val aptGet = File(prefix, "bin/apt-get")
        val process = ProcessBuilder(aptGet.absolutePath, "install", "-y", "proot")
            .directory(home)
            .redirectErrorStream(true)
        process.environment().apply {
            clear()
            environment().forEach { assignment ->
                val separator = assignment.indexOf('=')
                put(assignment.substring(0, separator), assignment.substring(separator + 1))
            }
        }
        val child = process.start()
        val output = child.inputStream.bufferedReader().use { it.readText() }
        check(child.waitFor() == 0 && File(prefix, "bin/proot").isFile) {
            "proot 安装失败：${output.takeLast(1200)}"
        }
    }

    private fun bootstrapAssetName(): String {
        val arch = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi ->
            when (abi) {
                "arm64-v8a" -> "aarch64"
                "armeabi-v7a" -> "arm"
                "x86_64" -> "x86_64"
                else -> null
            }
        } ?: error("不支持的 CPU 架构：${Build.SUPPORTED_ABIS.joinToString()}")
        return "bootstrap/bootstrap-$arch.zip"
    }

    private fun debianAssetName(): String {
        return debianAssetPathForAbis(Build.SUPPORTED_ABIS.toList())
        // The .bin suffix prevents AAPT from transparently expanding .gz and
        // dropping its suffix. Bytes remain the verified OCI-derived archive.
    }

    private fun expectedDigest(asset: String): String =
        appContext.assets.open("$asset.sha256").bufferedReader().use { reader ->
            reader.readLine().substringBefore(' ').trim().lowercase().also { digest ->
                require(digest.matches(Regex("[0-9a-f]{64}"))) { "bootstrap SHA-256 清单无效" }
            }
        }

    private fun safeTarget(root: File, relativePath: String): File {
        require(relativePath.isNotBlank() && !relativePath.startsWith('/')) { "bootstrap 路径无效" }
        val rootPath = root.canonicalPath + File.separator
        val target = File(root, relativePath).canonicalFile
        require(target.path.startsWith(rootPath)) { "bootstrap 路径越界：$relativePath" }
        return target
    }

    private fun needsExecutePermission(path: String): Boolean =
        path.startsWith("bin/") ||
            path.startsWith("libexec/") ||
            path.startsWith("lib/apt/apt-helper") ||
            path.startsWith("lib/apt/methods/") ||
            path == "etc/termux/bootstrap/termux-bootstrap-second-stage.sh"

    private fun validateTarArchive(tar: File, archive: File) {
        val child = ProcessBuilder(tar.absolutePath, "-tzf", archive.absolutePath).redirectErrorStream(true).start()
        val listing = child.inputStream.bufferedReader().use { it.readLines() }
        check(child.waitFor() == 0 && listing.isNotEmpty()) { "Debian 资产目录清单无效" }
        listing.forEach { entry -> require(isSafeArchivePath(entry)) { "Debian 资产路径越界：$entry" } }
    }

    private companion object {
        const val MARKER = ".hypershell-bootstrap-version"
        const val DEBIAN_MARKER = ".hypershell-debian-version"
        const val PACMAN_DB_VERSION = "9"
        const val REPOSITORY_LAYOUT_VERSION = 2
        const val CHROOT_PROBE_TIMEOUT_SECONDS = 20L
        const val REMOTE_REPOSITORY =
            "https://raw.githubusercontent.com/Github1145142882/HyperShell/gh-pages/"
    }
}

private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

private fun File.readTextOrNull(): String? = runCatching { readText().trim() }.getOrNull()

internal fun localAptUpdateCommand(aptGet: File, localSource: File): List<String> = listOf(
    aptGet.absolutePath,
    "-o", "Dir::Etc::sourcelist=${localSource.absolutePath}",
    "-o", "Dir::Etc::sourceparts=-",
    "-o", "APT::Get::List-Cleanup=0",
    "update",
)

internal fun androidRepositoryFileName(assetName: String): String = assetName.replace('\uF03A', ':')

internal fun debianAssetPathForAbis(abis: List<String>): String {
    require("arm64-v8a" in abis) { "预置 Debian 13 当前仅支持 arm64-v8a" }
    return "debian/debian-13-slim-arm64-android.tar.gz.bin"
}

internal fun debianAptSources(): String {
    val components = "main contrib non-free non-free-firmware"
    val keyring = "/usr/share/keyrings/debian-archive-keyring.pgp"
    return """
        Types: deb
        URIs: http://deb.debian.org/debian
        Suites: trixie trixie-updates
        Components: $components
        Signed-By: $keyring

        Types: deb
        URIs: http://deb.debian.org/debian-security
        Suites: trixie-security
        Components: $components
        Signed-By: $keyring
    """.trimIndent() + "\n"
}

internal fun debianFirstRunCommand(): String = """
    if ! find /var/lib/apt/lists -maxdepth 1 -type f -name '*_Packages*' -print -quit 2>/dev/null | grep -q .; then
        printf '\\nHyperShell: 正在首次同步 Debian 13 官方软件索引…\\n'
        if ! apt-get update; then
            printf '\\nHyperShell: APT 索引同步失败，请检查网络后运行 apt-get update。\\n'
        fi
    fi
    exec /bin/bash --login -i
""".trimIndent()

internal fun isSafeArchivePath(path: String): Boolean {
    val normalized = path.removePrefix("./").trimEnd('/')
    if (normalized.isEmpty()) return true
    return !normalized.startsWith('/') && normalized.split('/').none { it == ".." || it.isEmpty() }
}
