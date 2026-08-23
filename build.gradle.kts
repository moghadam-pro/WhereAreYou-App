// A diagnostic that directly inspected the runtime classloader Gradle uses to instantiate
// Kotlin's KotlinAndroidTarget (see app/build.gradle.kts commit history and
// docs/DEVIATIONS.md section 1) found the actual root cause of 23 straight failed CI
// builds: KotlinAndroidTarget gets loaded from a shared "root-project export" classloader
// scope — the scope Gradle promotes ALL plugin classes into once any plugin/buildscript
// dependency is applied anywhere in the build — while AGP was only ever declared inside
// :app's own local buildscript scope. Gradle's classloader scopes are child-sees-parent,
// not the reverse, so the shared root scope that Kotlin's classes live in structurally
// could never see AGP's classes added only to a descendant scope, regardless of which
// AGP/Kotlin/Gradle version was requested or how the plugin was applied — every one of
// those 23 attempts was varying the wrong thing.
//
// The fix is the standard multi-module pattern: declare every plugin :app needs — AGP
// included — here at root with apply false, so Gradle resolves them all into the SAME
// shared scope; :app's own plugins{} block then applies them without repeating a version.
// This DOES mean root's plugins{} block (always evaluated, unlike a subproject's
// lazily-configured block under org.gradle.configureondemand=true) now needs to resolve
// AGP too, which needs Google's Maven repository — :core:test can no longer be verified
// in this sandbox, which blocks dl.google.com (see docs/DEVIATIONS.md section 1). That
// tradeoff is accepted: GitHub Actions CI (where this actually needs to build) has full
// internet access, and getting :app to compile at all took priority once local-only
// verification was no longer an option without it.
plugins {
    kotlin("jvm") version "1.8.22" apply false
    id("com.android.application") version "7.4.2" apply false
    kotlin("android") version "1.8.22" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
