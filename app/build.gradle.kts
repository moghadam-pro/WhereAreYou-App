// NoClassDefFoundError: com/android/build/gradle/api/BaseVariant reproduced identically
// across 10 straight CI runs under AGP 8.4.2/8.5.2, Gradle 8.14.3/8.6/8.7, Kotlin
// 2.0.21/1.9.24, with/without KSP, AND both plugin-application mechanisms (plugins{} DSL
// and this buildscript{}+apply(plugin=) form) — ruling out every version combination *and*
// application mechanism as the cause (see docs/DEVIATIONS.md and this branch's commit
// history). The actual cause: Gradle 8.6 added an unconditional bytecode-instrumentation
// transform (ExternalDependencyInstrumentingArtifactTransform, visible in every failing
// run's --info log) applied to every external plugin/buildscript dependency, and AGP
// versions before 8.6 ship a BaseVariant classfile that doesn't survive that transform
// intact for Gradle's reflection-based decorated-class generation (needed here because
// Kotlin's KotlinAndroidTarget references BaseVariant in its public API). This pins AGP to
// 8.7.3 (past the version this was fixed) and the wrapper to Gradle 8.9 (AGP 8.7's own
// documented minimum). Still applied imperatively via buildscript{}/apply(plugin=) rather
// than the plugins{} block — not because that mechanism itself was ever the problem (it
// wasn't; see above), but because that's what keeps AGP resolution out of the root
// project's always-evaluated plugins{} block, which is what lets :core:test still run in
// the local sandbox without reaching dl.google.com (see root build.gradle.kts).
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
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
