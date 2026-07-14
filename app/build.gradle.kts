plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "io.github.hypershell"
    // Termux executes packages from the writable app prefix. Android blocks this for targetSdk 29+.
    // Keep compileSdk current for Miuix while matching the official Termux app's execution contract.
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "io.github.hypershell"
        minSdk = 26
        targetSdk = 28
        versionCode = 25
        versionName = "1.4.19"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++20", "-Wall", "-Wextra", "-Werror")
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildFeatures {
        compose = true
    }

    sourceSets["main"].assets.srcDir(rootProject.layout.projectDirectory.dir("termux-build/generated/assets"))

    androidResources {
        // Keep Ubuntu Base byte-for-byte identical to the official SHA-256 target.
        // AAPT otherwise transparently expands .gz assets and drops the suffix.
        noCompress += "gz"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // Intentional: target 29+ forbids executing the package-managed ELF files in app data.
        // compileSdk remains current; this exception is specific to the embedded Termux runtime.
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    val termuxVersion = libs.versions.termux.get()
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur)
    implementation(libs.datastore.preferences)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.coroutines.android)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.navigationevent.compose)
    implementation("com.github.termux.termux-app:terminal-emulator:$termuxVersion:release@aar")
    implementation(project(":terminal-view-hdr"))

    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test)
}
