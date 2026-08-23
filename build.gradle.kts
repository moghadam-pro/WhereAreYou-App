plugins {
    // :core only ever needs kotlin("jvm") — it has no Android/AGP dependency at all.
    // :app pins its own kotlin("android")/AGP/KSP versions directly in app/build.gradle.kts
    // (see the comment there for why they deliberately don't match :core's Kotlin version).
    kotlin("jvm") version "2.0.21" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
