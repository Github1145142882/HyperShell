plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.termux.view"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    val termuxVersion = libs.versions.termux.get()
    api("com.github.termux.termux-app:terminal-emulator:$termuxVersion:release@aar")
    implementation("androidx.annotation:annotation:1.9.1")
}
