// NoClassDefFoundError: com/android/build/gradle/api/BaseVariant reproduced identically
// across 14 straight CI runs under AGP 8.4.2/8.5.2/8.7.3, Gradle 8.14.3/8.6/8.7/8.9, Kotlin
// 1.9.24/2.0.21/2.1.0, with/without KSP, and both plugin-application mechanisms (see
// docs/DEVIATIONS.md and this branch's commit history). A diagnostic that directly
// inspected the resolved buildscript classpath jars with java.util.zip.ZipFile proved
// BaseVariant.class DOES physically exist in gradle-8.7.3.jar — this was never a
// missing-class problem, it's a classloader-visibility one, and it survived a jump to
// Kotlin 2.1.0 (a newer kotlin-gradle-plugin release, tested specifically to rule out a
// Gradle-API-generation mismatch in the jar Kotlin resolves) with the byte-identical
// failure. Every version knob within the Gradle 8.x line has now been exhausted without
// changing the outcome, which points at something in Gradle 8.x's own plugin/buildscript
// classloading behavior rather than any AGP/Kotlin version choice. This drops to a
// Gradle 7.x + AGP 7.x + Kotlin 1.8.x triad that predates whatever changed — a combination
// that was standard and extremely well-tested for years before Gradle 8's plugins{}-first
// era, as a test of whether the whole approach works on this runner at all. Compose
// BOM/AndroidX versions below are rolled back to match this era (AGP 7.4.2 requires
// compileSdk <= 33). Still applied imperatively via buildscript{}/apply(plugin=) so AGP
// resolution stays out of root build.gradle.kts's always-evaluated plugins{} block,
// preserving local :core:test runnability without reaching dl.google.com.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.4.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.22")
    }
}

apply(plugin = "com.android.application")
apply(plugin = "kotlin-android")

plugins {
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

// Uses add("implementation", ...) instead of the typed implementation(...) accessor.
// Both plugins are back on the plugins{} DSL above so the typed accessors would resolve
// too, but add(...) is part of the core DependencyHandler interface rather than a
// generated accessor, so it's left as-is: one less thing to break if the plugin
// application mechanism ever needs to change again.
dependencies {
    add("implementation", project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2023.06.01")
    add("implementation", composeBom)

    add("implementation", "androidx.core:core-ktx:1.10.1")
    add("implementation", "androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    add("implementation", "androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
    add("implementation", "androidx.activity:activity-compose:1.7.2")
    add("implementation", "androidx.navigation:navigation-compose:2.6.0")

    add("implementation", "androidx.compose.ui:ui")
    add("implementation", "androidx.compose.ui:ui-graphics")
    add("implementation", "androidx.compose.ui:ui-tooling-preview")
    add("implementation", "androidx.compose.material3:material3")

    add("implementation", "androidx.datastore:datastore-preferences:1.0.0")

    add("implementation", "androidx.room:room-runtime:2.5.2")
    add("implementation", "androidx.room:room-ktx:2.5.2")
    add("ksp", "androidx.room:room-compiler:2.5.2")

    add("debugImplementation", "androidx.compose.ui:ui-tooling")
}
