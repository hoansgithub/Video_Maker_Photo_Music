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
        // GITHUB PACKAGES - ACCCore Library (Private)
        // ============================================
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/hoansgithub/ACCCoreAndroid")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: providers.gradleProperty("gpr.user").orNull
                password = System.getenv("GITHUB_TOKEN")
                    ?: providers.gradleProperty("gpr.key").orNull
            }
        }
    }
}

rootProject.name = "VideoMaker"
include(":app")

// ============================================
// LOCAL COMPOSITE BUILD - ACCCoreAndroid
// COMMENTED OUT - Using GitHub Packages instead
// Uncomment for local testing of ACCCore updates
// ============================================
// includeBuild("../ACCCoreAndroid") {
//     dependencySubstitution {
//         substitute(module("co.alcheclub.lib:acccore")).using(project(":ACCCore"))
//         substitute(module("co.alcheclub.lib:acccore-firebase")).using(project(":ACCCore-Firebase"))
//         substitute(module("co.alcheclub.lib:acccore-revenuecat")).using(project(":ACCCore-RevenueCat"))
//         substitute(module("co.alcheclub.lib:acccore-ads")).using(project(":ACCCore-Ads"))
//         substitute(module("co.alcheclub.lib:acccore-appsflyer")).using(project(":ACCCore-AppsFlyer"))
//     }
// }
