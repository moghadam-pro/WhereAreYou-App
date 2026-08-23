// NoClassDefFoundError: com/android/build/gradle/api/BaseVariant reproduced identically
// across 9 straight CI runs trying the plugins{} DSL: AGP 8.5.2/8.4.2, Gradle
// 8.14.3/8.6/8.7, Kotlin 2.0.21/1.9.24, and with/without KSP (see docs/DEVIATIONS.md and
// this branch's commit history) — ruling out every version combination as the cause and
// pointing at the plugins{} DSL's plugin-classloader isolation itself. This switches
// com.android.application and kotlin-android to the legacy buildscript{} + apply(plugin=)
// mechanism, a different Gradle code path that does not go through the same
// plugin-marker/classloader isolation as the plugins{} block.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.5.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
    }
}

apply(plugin = "com.android.application")
apply(plugin = "kotlin-android")

plugins {
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

configure<com.android.build.gradle.AppExtension> {
    namespace = "com.whereareyou.app"
    compileSdkVersion(34)

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

    buildFeatures.compose = true

    composeOptions {
        // Must match Kotlin 1.9.24 per Google's Compose-Kotlin compatibility map.
        kotlinCompilerExtensionVersion = "1.5.14"
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

// Uses add("implementation", ...) instead of the typed implementation(...) accessor:
// those accessor extension functions are only pre-compiled for plugins declared via
// the plugins{} block (resolved before script compilation). com.android.application and
// kotlin-android are applied imperatively above via apply(plugin = "..."), so their
// configurations (implementation, debugImplementation) don't get generated accessors and
// the typed form fails script compilation with "Unresolved reference". add(...) is part
// of the core DependencyHandler interface and works regardless of how a plugin was applied.
dependencies {
    add("implementation", project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    add("implementation", composeBom)

    add("implementation", "androidx.core:core-ktx:1.13.1")
    add("implementation", "androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    add("implementation", "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    add("implementation", "androidx.activity:activity-compose:1.9.2")
    add("implementation", "androidx.navigation:navigation-compose:2.8.1")

    add("implementation", "androidx.compose.ui:ui")
    add("implementation", "androidx.compose.ui:ui-graphics")
    add("implementation", "androidx.compose.ui:ui-tooling-preview")
    add("implementation", "androidx.compose.material3:material3")

    add("implementation", "androidx.datastore:datastore-preferences:1.1.1")

    add("implementation", "androidx.room:room-runtime:2.6.1")
    add("implementation", "androidx.room:room-ktx:2.6.1")
    add("ksp", "androidx.room:room-compiler:2.6.1")

    add("debugImplementation", "androidx.compose.ui:ui-tooling")
}
