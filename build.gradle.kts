plugins {
    // Must match :app's kotlin("android") version in app/build.gradle.kts exactly: a
    // single Gradle build shares one plugin classpath, so kotlin("jvm") here and
    // kotlin("android") there resolving to different Kotlin Gradle Plugin versions fails
    // configuration with "the plugin is already on the classpath with an unknown version"
    // (hit for real on CI). :core has no AGP dependency, so it is otherwise indifferent to
    // which Kotlin version it runs — see app/build.gradle.kts for why 1.9.24 was chosen.
    kotlin("jvm") version "1.9.24" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
