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

# Optional HyperOS framework classes referenced by the HyperCeiler provision UI.
# They are supplied by supported Xiaomi devices and have guarded fallbacks elsewhere.
-dontwarn com.android.internal.view.menu.MenuBuilder
-dontwarn miui.util.HapticFeedbackUtil

# fan.miuix 1.0.12.5 contains precompiled call sites whose method references are not all
# rewritten when R8 renames Folme members. Keeping the original API surface prevents release
# builds from calling FolmeEase.spring(...) after that method has been renamed.
-keep class fan.** { *; }
-keep interface fan.** { *; }
-keep enum fan.** { *; }

# PreferenceInflater derives constructor package prefixes from Class.getPackage(). R8 moving
# androidx.preference classes into the default package makes getPackage() return null at runtime.
-keep class androidx.preference.** { *; }

# The original provision flow also resolves fragments, services and preference classes by name.
-keep class com.sevtinge.hyperceiler.provision.** { *; }
-keep class com.sevtinge.hyperceiler.common.** { *; }
