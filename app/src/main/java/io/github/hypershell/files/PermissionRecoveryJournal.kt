package io.github.hypershell.files

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileOutputStream

data class PermissionRecovery(val path: String, val originalMode: String)

class PermissionRecoveryJournal(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val file = context.cacheDir.resolve("hypershell-permission-recovery")

    suspend fun write(value: PermissionRecovery) = withContext(ioDispatcher) {
        val output = FileOutputStream(file)
        DataOutputStream(output).use {
            it.writeUTF(value.path)
            it.writeUTF(value.originalMode)
            it.flush()
            output.fd.sync()
        }
    }

    suspend fun read(): PermissionRecovery? = withContext(ioDispatcher) {
        if (!file.isFile) return@withContext null
        runCatching {
            DataInputStream(file.inputStream()).use { PermissionRecovery(it.readUTF(), it.readUTF()) }
        }.getOrNull()
    }

    suspend fun clear() = withContext(ioDispatcher) { file.delete() }
}
