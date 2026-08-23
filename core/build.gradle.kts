import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

// Pure Kotlin/JVM module: domain model, trigger rules, protocol codec and
// security primitives live here with zero Android framework dependency so
// they can be unit tested on a plain JVM (see AGENTS.md "Architecture rules").

// JVM target 17 — must match :app (Android's supported target), and is set as a plain
// compiler/javac target rather than a `jvmToolchain(17)`: a toolchain forces Gradle to
// locate a JDK of that exact version, which fails on any machine (or CI runner) whose
// only installed JDK is a newer LTS. Any JDK >= 17 can *emit* 17 bytecode.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
