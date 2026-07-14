-keepclasseswithmembernames class io.github.hypershell.terminal.PtyBridge {
    native <methods>;
}

# Loaded by /system/bin/app_process after KernelSU elevates the child process.
-keep class io.github.hypershell.terminal.ChrootProcessMain {
    public static void main(java.lang.String[]);
}

# JNI symbol names in chroot_bridge.cpp depend on this exact class and method name.
-keep class io.github.hypershell.terminal.ChrootNative {
    native <methods>;
}
