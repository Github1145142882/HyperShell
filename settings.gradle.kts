pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://api.xposed.info")
        maven("https://maven.pkg.github.com/ReChronoRain/HyperCeiler") {
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GIT_ACTOR")
                    ?: "github"
                password = providers.gradleProperty("gpr.key").orNull
                    ?: System.getenv("GIT_TOKEN")
                    ?: ""
            }
        }
    }
}

rootProject.name = "HyperShell"
include(":app")
include(":onboarding")
include(":terminal-view-hdr")
include(":hyperceiler-common")
include(":provision-baseline")
