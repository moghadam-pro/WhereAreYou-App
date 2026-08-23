plugins {
    kotlin("jvm")
}

// Pure Kotlin/JVM module: domain model, trigger rules, protocol codec and
// security primitives live here with zero Android framework dependency so
// they can be unit tested on a plain JVM (see AGENTS.md "Architecture rules").

kotlin {
    // Android's Kotlin/JVM target for this project is 17 (see app/build.gradle.kts).
    // This sandbox only has a JDK 21 installed, so the toolchain here targets 21;
    // both are >= the language level core actually uses (Java 8/11-compatible stdlib calls).
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
