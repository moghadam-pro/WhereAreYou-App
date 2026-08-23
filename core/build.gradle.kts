plugins {
    kotlin("jvm")
}

// Pure Kotlin/JVM module: domain model, trigger rules, protocol codec and
// security primitives live here with zero Android framework dependency so
// they can be unit tested on a plain JVM (see AGENTS.md "Architecture rules").

kotlin {
    // Must match :app's JVM target (17, see app/build.gradle.kts) and stay a value the
    // Kotlin 1.8.22 compiler recognizes: it maps a resolved toolchain JavaVersion straight to
    // its JvmTarget enum, which in 1.8.22 tops out below 21 — CI resolves a real JDK 21 (also
    // installed) when this said 21, and compileKotlin died with "Unknown Kotlin JVM target: 21".
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
