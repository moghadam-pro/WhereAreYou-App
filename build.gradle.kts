plugins {
    // Both Kotlin sub-plugins declared here with apply false, matching versions, and
    // NOT repeated with a version in core/app's own plugins{} blocks (they just apply
    // the bare id). Declaring kotlin("android") only in app/build.gradle.kts (without
    // this root apply-false entry) failed CI configuration with "the plugin is already
    // on the classpath with an unknown version, so compatibility cannot be checked" —
    // apparently both plugin IDs resolve from the same underlying kotlin-gradle-plugin
    // jar closely enough that Gradle's plugin resolution needs both declared here to
    // reconcile them. :core has no AGP dependency, so it's otherwise indifferent to which
    // Kotlin version it runs — see app/build.gradle.kts for why 1.9.24 was chosen.
    kotlin("jvm") version "1.9.24" apply false
    kotlin("android") version "1.9.24" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
