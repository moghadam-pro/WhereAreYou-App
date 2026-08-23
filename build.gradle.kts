plugins {
    // :core's only plugin. Deliberately NOT declaring com.android.application or
    // kotlin("android") here even with apply false: root's own plugins{} block is always
    // evaluated (unlike a subproject's, which org.gradle.configureondemand=true can skip),
    // so declaring AGP at root would force plugin-portal resolution — reaching
    // dl.google.com — on every single Gradle invocation, including plain :core:test. This
    // sandbox blocks that host (see docs/DEVIATIONS.md section 1), so :app applies AGP and
    // kotlin-android imperatively instead, directly in app/build.gradle.kts, keeping
    // :core:test runnable here without ever touching Google's Maven repo.
    kotlin("jvm") version "1.9.24" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
