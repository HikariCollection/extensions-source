plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.hikari.extension.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }
}

dependencies {
    compileOnly(libs.hikari.source.api)
    compileOnly("org.jspecify:jspecify:1.0.0")

    compileOnly(libs.okhttp)
    compileOnly(libs.okhttp.brotli)
    compileOnly(libs.jsoup)
    compileOnly(libs.kotlin.json)
    compileOnly(libs.injekt.core)
    compileOnly(libs.coroutines.core)
    compileOnly(libs.coroutines.android)
}

