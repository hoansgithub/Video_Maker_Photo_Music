import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    id("kotlin-parcelize")

    // ============================================
    // FIREBASE
    // ============================================
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")
}

// ============================================
// LOCAL PROPERTIES (Supabase credentials)
// ============================================
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

fun getPropertyOrEmpty(name: String): String {
    return localProperties.getProperty(name) ?: ""
}

android {
    namespace = "com.videomaker.aimusic"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.videomaker.aimusic"
        minSdk = 28
        targetSdk = 36
        versionCode = 4
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ============================================
        // SUPABASE CONFIGURATION (from local.properties)
        // ============================================
        buildConfigField("String", "SUPABASE_URL", "\"${getPropertyOrEmpty("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${getPropertyOrEmpty("SUPABASE_ANON_KEY")}\"")

        // ============================================
        // APP THINNING OPTIMIZATION
        // ============================================

        // Only include ARM architectures (99% of Android devices)
        // This reduces APK size by ~15-25MB
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        // Use vector drawable support library
        vectorDrawables.useSupportLibrary = true
    }

    // Only include supported languages
    // This reduces APK size by ~5-10MB
    androidResources {
        localeFilters += listOf("en", "zh-rCN", "de", "in", "ja", "pt-rBR", "es", "hi", "ms", "ar", "my", "vi")
    }

    // ============================================
    // ANDROID APP BUNDLE (AAB) CONFIGURATION
    // ============================================
    @Suppress("UnstableApiUsage")
    bundle {
        language {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }

    buildTypes {
        debug {
            // Debug-specific configurations
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false  // Allow build with lint warnings
    }

    // Packaging options to exclude unnecessary files
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/*.kotlin_module",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        }
    }
}

// Kotlin JVM target configuration for Kotlin 2.3.20+
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // ============================================
    // ANDROIDX CORE
    // ============================================
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)

    // ============================================
    // COMPOSE
    // ============================================
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // ============================================
    // NAVIGATION 3
    // ============================================
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // ============================================
    // MEDIA3 (Video Editing & Playback)
    // ============================================
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.media3.common)
    // implementation(libs.media3.session)  // Uncomment if media session needed

    // ============================================
    // ROOM DATABASE
    // ============================================
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ============================================
    // DATASTORE
    // ============================================
    implementation(libs.androidx.datastore.preferences)

    // ============================================
    // WORKMANAGER (Background Export)
    // ============================================
    implementation(libs.androidx.work.runtime.ktx)

    // ============================================
    // SERIALIZATION
    // ============================================
    implementation(libs.kotlinx.serialization.json)

    // ============================================
    // KTOR (HTTP Client for Supabase)
    // ============================================
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    // ============================================
    // SUPABASE
    // ============================================
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    // implementation(libs.supabase.auth)  // Uncomment when auth needed

    // ============================================
    // DEPENDENCY INJECTION (Koin) - Matches ACCCoreAndroid
    // ============================================
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // ============================================
    // ACCCORE - AlcheClub DI & Services (GitHub Packages)
    // ============================================
    implementation(libs.acccore)
    implementation(libs.acccore.firebase)    // Firebase Analytics, Crashlytics, RemoteConfig, Performance
    // implementation(libs.acccore.revenuecat)  // Uncomment when RevenueCat needed
    // implementation(libs.acccore.ads)         // Uncomment when Ads needed
    // implementation(libs.acccore.appsflyer)   // Uncomment when AppsFlyer needed

    // ============================================
    // UI UTILITIES
    // ============================================
    implementation(libs.shimmer.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.video)

    // ============================================
    // PLAY SERVICES (Optional)
    // ============================================
    // implementation(libs.play.review)
    // implementation(libs.play.review.ktx)

    // ============================================
    // TESTING
    // ============================================
    testImplementation(libs.junit)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
