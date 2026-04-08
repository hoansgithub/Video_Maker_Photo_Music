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
                // Load credentials from local.properties or environment variables
                val localProperties = java.util.Properties()
                val localPropertiesFile = File(rootDir, "local.properties")
                if (localPropertiesFile.exists()) {
                    localPropertiesFile.inputStream().use { localProperties.load(it) }
                }

                username = System.getenv("GITHUB_ACTOR")
                    ?: localProperties.getProperty("gpr.user")
                password = System.getenv("GITHUB_TOKEN")
                    ?: localProperties.getProperty("gpr.key")
            }
        }
    }
}

rootProject.name = "VideoMaker"
include(":app")

// ============================================
// LOCAL COMPOSITE BUILD - ACCCoreAndroid
// DISABLED - Using GitHub Packages (v0.0.26)
// ============================================
// includeBuild("../ACCCoreAndroid") {
//     dependencySubstitution {
//         substitute(module("co.alcheclub.lib:acccore")).using(project(":ACCCore"))
//         substitute(module("co.alcheclub.lib:acccore-firebase")).using(project(":ACCCore-Firebase"))
//         substitute(module("co.alcheclub.lib:acccore-facebook")).using(project(":ACCCore-Facebook"))
//         substitute(module("co.alcheclub.lib:acccore-revenuecat")).using(project(":ACCCore-RevenueCat"))
//         substitute(module("co.alcheclub.lib:acccore-ads")).using(project(":ACCCore-Ads"))
//         substitute(module("co.alcheclub.lib:acccore-appsflyer")).using(project(":ACCCore-AppsFlyer"))
//     }
// }
