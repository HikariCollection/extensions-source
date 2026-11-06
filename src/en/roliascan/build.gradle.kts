plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

val signingKeyFile = rootProject.file("signingkey.jks")

android {
    namespace = "app.hikari.extension.en.roliascan"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.hikari.extension.en.roliascan"
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
    archivesName.set("hikari-en-roliascan")
}

dependencies {
    compileOnly(libs.hikari.source.api)
    implementation(project(":core"))

    implementation(libs.okhttp)
    implementation(libs.okhttp.brotli)
    implementation(libs.jsoup)
    implementation(libs.kotlin.json)
    implementation(libs.injekt.core)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
}
