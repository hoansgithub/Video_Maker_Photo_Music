pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // ============================================
        // JITPACK - ACCCore Library
        // ============================================
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "VideoMaker"
include(":app")

// ============================================
// LOCAL DEVELOPMENT
// Using composite build for local ACCCore library
// To use remote JitPack: make repo public, then comment out this block
// ============================================
includeBuild("/Users/hoanl/Documents/alcheclub-android/ACCCoreAndroid") {
    dependencySubstitution {
        substitute(module("com.github.hoansgithub.ACCCoreAndroid:ACCCore"))
            .using(project(":ACCCore"))
        substitute(module("com.github.hoansgithub.ACCCoreAndroid:ACCCore-Firebase"))
            .using(project(":ACCCore-Firebase"))
        substitute(module("com.github.hoansgithub.ACCCoreAndroid:ACCCore-RevenueCat"))
            .using(project(":ACCCore-RevenueCat"))
        substitute(module("com.github.hoansgithub.ACCCoreAndroid:ACCCore-Ads"))
            .using(project(":ACCCore-Ads"))
        substitute(module("com.github.hoansgithub.ACCCoreAndroid:ACCCore-AppsFlyer"))
            .using(project(":ACCCore-AppsFlyer"))
    }
}
