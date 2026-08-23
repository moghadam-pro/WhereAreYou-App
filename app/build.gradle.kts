// NoClassDefFoundError: com/android/build/gradle/api/BaseVariant reproduced identically
// across 11 straight CI runs under AGP 8.4.2/8.5.2/8.7.3, Gradle 8.14.3/8.6/8.7/8.9,
// Kotlin 1.9.24 paired with every one of those, with/without KSP, and both plugin-
// application mechanisms — ruling out AGP version, Gradle version, and application
// mechanism as the sole cause (see docs/DEVIATIONS.md and this branch's commit history).
// kotlin-gradle-plugin 1.9.24's KotlinAndroidTarget references the old BaseVariant API
// directly in its public surface, which is what triggers Gradle's reflection-based
// decorated-class generation to need that class at all; 2.0.21's K2-based Android target
// was never actually retested under a clean, current AGP/Gradle pairing (the original
// "downgrade to 1.9.24" theory was itself based on a stale environment). This moves :app
// to Kotlin 2.0.21 to match :core exactly (avoiding two different Kotlin Gradle Plugin
// versions in one build entirely) plus AGP's own paired Compose Compiler Gradle plugin
// (org.jetbrains.kotlin.plugin.compose), which Kotlin 2.0+ requires in place of the old
// composeOptions.kotlinCompilerExtensionVersion mechanism. Still applied imperatively via
// buildscript{}/apply(plugin=) — that was never the BaseVariant cause, but it's what keeps
// AGP resolution out of the root project's always-evaluated plugins{} block, letting
// :core:test keep running in the local sandbox without reaching dl.google.com (see root
// build.gradle.kts).
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.0.21")
    }
}

// DIAGNOSTIC (temporary): 12 straight CI runs hit the identical
// NoClassDefFoundError: com/android/build/gradle/api/BaseVariant across every AGP
// (8.4.2/8.5.2/8.7.3) x Gradle (8.6/8.7/8.9/8.14.3) x Kotlin (1.9.24/2.0.21) combination
// and both plugin-application mechanisms tried — ruling out every version knob as the
// cause. Before guessing another version, inspect the actual resolved buildscript
// classpath jars directly to settle whether BaseVariant.class physically exists in the
// AGP jar Gradle resolved here at all.
buildscript.configurations.getByName("classpath").files.filter { f ->
    f.name.startsWith("gradle-8") || f.name.startsWith("gradle-api-8") ||
        f.name.startsWith("kotlin-gradle-plugin-2") || f.name == "builder-model-8.7.3.jar" ||
        f.name.startsWith("builder-8")
}.forEach { f ->
    val hasBaseVariant = try {
        java.util.zip.ZipFile(f).use { zip ->
            zip.getEntry("com/android/build/gradle/api/BaseVariant.class") != null
        }
    } catch (e: Exception) {
        "ERROR: ${e.message}"
    }
    println("DIAGNOSTIC classpath jar: ${f.name} -> BaseVariant.class present = $hasBaseVariant")
}

apply(plugin = "com.android.application")
apply(plugin = "kotlin-android")
apply(plugin = "org.jetbrains.kotlin.plugin.compose")

plugins {
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
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
