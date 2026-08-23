plugins {
    // :core's only plugin. :app now applies com.android.application and kotlin-android
    // via the legacy buildscript{}/apply(plugin=) mechanism directly in
    // app/build.gradle.kts (see that file for why), so it no longer needs — and must not
    // repeat — a plugins{} DSL declaration for either here.
    kotlin("jvm") version "1.9.24" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
