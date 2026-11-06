plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    compileOnly(libs.android.gradle)
    compileOnly(libs.kotlin.gradle)
    implementation(libs.kotlin.json)
}

gradlePlugin {
    plugins {
        register("extension") {
            id = "hikari.plugins.extension"
            implementationClass = "HikariExtensionPlugin"
        }
    }
}
