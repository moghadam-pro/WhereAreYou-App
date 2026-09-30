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
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
