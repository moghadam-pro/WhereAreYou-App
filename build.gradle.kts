// Root build script.
//
// Every plugin the subprojects need is declared here with `apply false` so Gradle
// resolves them all into the same shared classloader scope. This is the standard
// multi-module pattern, and it is also what fixed a long run of CI failures with
// `NoClassDefFoundError: com/android/build/gradle/api/BaseVariant`: Kotlin's
// KotlinAndroidTarget is instantiated from the shared root-project scope, while AGP had
// only ever been declared inside :app's own local buildscript scope. Gradle scopes are
// child-sees-parent, never the reverse, so that shared scope structurally could not see
// AGP. Declaring both at root puts them in the same scope; :app then applies them
// without repeating a version.
plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.android.application") version "8.9.1" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
