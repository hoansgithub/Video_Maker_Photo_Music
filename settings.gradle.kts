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
        // Requires credentials in ~/.gradle/gradle.properties:
        //   gpr.user=YOUR_GITHUB_USERNAME
        //   gpr.key=YOUR_GITHUB_PAT_TOKEN
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
// REMOVED: Local composite build
// Now using GitHub Packages for ACCCore library
// ============================================
