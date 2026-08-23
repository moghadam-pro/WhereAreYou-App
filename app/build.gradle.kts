plugins {
    // NoClassDefFoundError: com/android/build/gradle/api/BaseVariant reproduced
    // identically across AGP 8.5.2/8.4.2, Gradle 8.14.3/8.6, Kotlin 2.0.21/1.9.24, and
    // with/without KSP (8 straight CI runs — see docs/DEVIATIONS.md and this branch's
    // commit history). A CI run with --info logging showed the real AGP jar (with
    // BaseVariant) DOES resolve onto the classpath alongside a separate, lean
    // com.android.tools.build:gradle-api jar (the New Variant API surface, which does not
    // ship BaseVariant) — pointing at a plugin-classloader interaction, not a missing
    // artifact. AGP 8.5.2 + Kotlin 1.9.24 + Gradle 8.7 is the specific trio actually
    // shipped together in Android Studio's own Compose project template for this Kotlin
    // line, so it's the next attempt instead of another blind version guess.
    //
    // No version here for kotlin("android"): pinned once via the root build.gradle.kts
    // apply-false declaration (kept in sync with :core's kotlin("jvm") version there) —
    // repeating a version here alongside the root's apply-false entry produced "the plugin
    // is already on the classpath with an unknown version" on a real CI run.
    id("com.android.application") version "8.5.2"
    kotlin("android")
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
