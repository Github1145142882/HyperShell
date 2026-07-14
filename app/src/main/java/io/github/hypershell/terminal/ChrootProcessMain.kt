package io.github.hypershell.terminal

import android.system.Os
import kotlin.system.exitProcess

internal object ChrootNative {
    external fun nativeProbe(root: String, home: String): Int
    external fun nativeEnter(root: String, home: String): Int
}

/** Entrypoint loaded by Android's app_process after KernelSU has elevated it. */
object ChrootProcessMain {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size != 4) exitProcess(64)
        System.load(args[0])
        val result = when (args[1]) {
            "probe" -> ChrootNative.nativeProbe(args[2], args[3])
            "enter" -> ChrootNative.nativeEnter(args[2], args[3])
            else -> 64
        }
        if (result != 0) {
            val detail = runCatching { Os.strerror(result) }.getOrDefault("unknown error")
            System.err.println("HyperShell chroot failed: $detail (errno=$result)")
        }
        exitProcess(if (result == 0) 0 else result.coerceIn(1, 125))
    }
}
