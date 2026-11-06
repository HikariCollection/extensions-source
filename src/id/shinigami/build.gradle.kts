plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

val signingKeyFile = rootProject.file("signingkey.jks")

android {
    namespace = "app.hikari.extension.id.shinigami"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.hikari.extension.id.shinigami"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.1.1"
    }

    signingConfigs {
        create("release") {
            if (signingKeyFile.exists()) {
                storeFile = signingKeyFile
                storePassword = System.getenv("KEY_STORE_PASSWORD") ?: "hikari-extensions"
                keyAlias = System.getenv("ALIAS") ?: "hikari-extensions"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "hikari-extensions"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (signingKeyFile.exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }
}

base {
    archivesName.set("hikari-id-shinigami")
}

dependencies {
    compileOnly(libs.hikari.source.api)
    implementation(project(":core"))

    compileOnly(libs.okhttp)
    compileOnly(libs.okhttp.brotli)
    compileOnly(libs.jsoup)
    compileOnly(libs.kotlin.json)
    compileOnly(libs.injekt.core)
    compileOnly(libs.coroutines.core)
    compileOnly(libs.coroutines.android)
}

