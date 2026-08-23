// NoClassDefFoundError: com/android/build/gradle/api/BaseVariant reproduced identically
// across 13 straight CI runs under AGP 8.4.2/8.5.2/8.7.3, Gradle 8.14.3/8.6/8.7/8.9, Kotlin
// 1.9.24/2.0.21, with/without KSP, and both plugin-application mechanisms (see
// docs/DEVIATIONS.md and this branch's commit history). A diagnostic that directly
// inspected the resolved buildscript classpath jars with java.util.zip.ZipFile proved
// BaseVariant.class DOES physically exist in gradle-8.7.3.jar (the full AGP jar) — this was
// never a missing-class problem, it's a classloader-visibility one. The same diagnostic run
// showed kotlin-gradle-plugin:2.0.21 resolving its "-gradle85" variant jar (Kotlin Gradle
// Plugin publishes Gradle-version-targeted jars via Gradle Module Metadata) while the
// wrapper runs Gradle 8.9 — a 4-minor-version gap between the jar's target Gradle
// internal-API generation and the actual Gradle version running it, which is exactly the
// kind of mismatch that could break Gradle's internal reflection-based ClassInspector/
// AbstractClassGenerator machinery in ways that never show up when the two stay close
// together (the overwhelmingly common case). Kotlin 2.1.0 is picked here specifically to
// get a kotlin-gradle-plugin release published against a newer Gradle-API generation,
// closer to Gradle 8.9. Still applied imperatively via buildscript{}/apply(plugin=) so AGP
// resolution stays out of root build.gradle.kts's always-evaluated plugins{} block,
// preserving local :core:test runnability without reaching dl.google.com.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.1.0")
    }
}

apply(plugin = "com.android.application")
apply(plugin = "kotlin-android")
apply(plugin = "org.jetbrains.kotlin.plugin.compose")

plugins {
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
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

    // No composeOptions.kotlinCompilerExtensionVersion: Kotlin 2.0+ uses the
    // org.jetbrains.kotlin.plugin.compose Gradle plugin (applied above) instead, which
    // picks the matching Compose compiler automatically from the Kotlin version.

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
