plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    compileOnly(libs.android.gradle)
    compileOnly(libs.kotlin.gradle)
    implementation(libs.kotlin.json)

    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        register("extension") {
            id = "hikari.plugins.extension"
            implementationClass = "HikariExtensionPlugin"
        }
        register("theme") {
            id = "hikari.plugins.theme"
            implementationClass = "HikariThemePlugin"
        }
    }
}
