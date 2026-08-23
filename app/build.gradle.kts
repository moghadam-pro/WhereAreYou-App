// See build.gradle.kts (root) for the full explanation: 23 straight CI failures with
// NoClassDefFoundError: com/android/build/gradle/api/BaseVariant traced to Kotlin's
// KotlinAndroidTarget being instantiated from a shared root-level classloader scope that
// never had AGP in it, because every prior attempt declared AGP only inside :app's own
// local scope (via plugins{} here directly, or via buildscript{}/apply(plugin=)/class-based
// apply — the application mechanism was never the issue). Root now declares both AGP and
// kotlin-android with apply false, so they resolve into the same shared scope Kotlin's
// classes already live in; this block goes back to applying them normally, without
// repeating a version.
plugins {
    id("com.android.application")
    kotlin("android")
    id("com.google.devtools.ksp") version "1.8.22-1.0.11"
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

configure<com.android.build.gradle.AppExtension> {
    namespace = "com.whereareyou.app"
    compileSdkVersion(33)

    defaultConfig {
        applicationId = "com.whereareyou.app"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures.compose = true

    composeOptions {
        // Must match Kotlin 1.8.22 per Google's Compose-Kotlin compatibility map.
        kotlinCompilerExtensionVersion = "1.4.8"
    }

    packagingOptions {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2023.06.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
    implementation("androidx.activity:activity-compose:1.7.2")
    implementation("androidx.navigation:navigation-compose:2.6.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.datastore:datastore-preferences:1.0.0")

    implementation("androidx.room:room-runtime:2.5.2")
    implementation("androidx.room:room-ktx:2.5.2")
    ksp("androidx.room:room-compiler:2.5.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
