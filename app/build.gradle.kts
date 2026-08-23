plugins {
    id("com.android.application") version "8.5.2"
    kotlin("android") version "2.0.21"
}

android {
    namespace = "com.whereareyou.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.whereareyou.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))
}
