// NoClassDefFoundError: com/android/build/gradle/api/BaseVariant reproduced identically
// across 15 straight CI runs spanning every AGP version 7.4.2-8.7.3, every Gradle version
// 7.6.4-8.14.3, and every Kotlin version 1.8.22-2.1.0 (see docs/DEVIATIONS.md and this
// branch's commit history) — including a diagnostic that proved BaseVariant.class
// physically exists in the resolved AGP jar (never a missing-class problem) and a full
// drop to a pre-Gradle-8 generation of tooling that changed nothing. Every failure has the
// identical shape: Gradle's ClassInspector, generating a decorated proxy for Kotlin's
// KotlinAndroidTarget via objects.newInstance(), can't resolve BaseVariant from whatever
// classloader loaded KotlinAndroidTarget. Every attempt so far applied kotlin-android by
// STRING PLUGIN ID — either plugins{ id(...) } or apply(plugin = "kotlin-android") — both
// of which route through Gradle's PluginRegistry, which appears to hand the resolved
// plugin its own isolated classloader scope regardless of version or of whether that ID
// was declared via the plugins{} block or applied imperatively (both mechanisms were
// tried and both failed identically). This tries a genuinely different code path never
// exercised before: applying kotlin-android by its concrete Plugin class
// (KotlinAndroidPlugin, visible directly in every failing run's stack trace) via
// pluginManager.apply(Class), which does not go through PluginRegistry's ID-based lookup
// at all — the class is resolved via normal Kotlin type reference in this script, so it
// loads through this script's own (buildscript-classpath-merged, AGP-visible) classloader
// instead of a plugin-specific isolated one.
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
project.pluginManager.apply(org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPlugin::class.java)

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
