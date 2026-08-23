plugins {
    // kotlin("android") 2.0.21's Android target reflects on
    // com.android.build.gradle.api.BaseVariant at configuration time and fails with
    // NoClassDefFoundError on that class when paired with com.android.application, no
    // matter which AGP 8.4/8.5 point release or Gradle 8.6-8.14 was tried (all reproduced
    // for real on CI; see docs/DEVIATIONS.md) — a K2/new-target-rewrite regression in
    // Kotlin 2.0.x's Android plugin, not an AGP version issue. Kotlin 1.9.24's
    // (pre-rewrite) android plugin uses the same BaseVariant-based API directly and does
    // not hit it.
    //
    // This version must match the root build.gradle.kts kotlin("jvm") version exactly:
    // a single Gradle build shares one plugin classpath across all subprojects, so :core
    // and :app resolving different Kotlin Gradle Plugin versions fails configuration with
    // "the plugin is already on the classpath with an unknown version" (also hit for real
    // on CI) — there is no way to give :app an older Kotlin than :core here without
    // splitting them into separate Gradle builds, which isn't worth it for this.
    id("com.android.application") version "8.4.2"
    kotlin("android") version "1.9.24"
    id("com.google.devtools.ksp") version "1.9.24-1.0.20"
}

// NOTE ON BUILD VERIFICATION (see /docs/DEVIATIONS.md):
// This module could not be compiled in the development sandbox that produced it: the
// Android Gradle Plugin and the Android SDK components it needs are only published to
// Google's Maven repository (dl.google.com), and that host is blocked by this
// environment's outbound network policy (confirmed via the agent proxy status endpoint —
// 403 on CONNECT to dl.google.com). :core (plain Kotlin/JVM, Maven Central only) builds
// and its 120+ unit tests run cleanly in this same sandbox; this module has only been
// reviewed by hand against current AGP/Compose/Room APIs and must be built/run in a
// normal Android Studio environment (or any CI runner with SDK + Google Maven access)
// before it can be trusted.

android {
    namespace = "com.whereareyou.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.whereareyou.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        // Must match Kotlin 1.9.24 per Google's Compose-Kotlin compatibility map.
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
