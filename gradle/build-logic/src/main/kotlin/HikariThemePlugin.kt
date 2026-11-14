import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class HikariThemePlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        val hikariTheme = extensions.create<HikariThemeExtension>("hikariTheme")
        hikariTheme.baseVersionCode.convention(0)
        hikariTheme.libVersion.convention("1.1")

        val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")

        android {
            namespace = "app.hikari.multisrc.${project.name}"
            compileSdk = 37

            defaultConfig {
                minSdk = 26
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }
        }

        extensions.configure<KotlinAndroidProjectExtension> {
            jvmToolchain(21)
        }

        dependencies {
            add("compileOnly", libs.findLibrary("hikari-source-api").get())
            add("compileOnly", libs.findLibrary("jspecify").get())
            add("implementation", project(":core"))
            add("compileOnly", libs.findLibrary("okhttp").get())
            add("compileOnly", libs.findLibrary("okhttp-brotli").get())
            add("compileOnly", libs.findLibrary("jsoup").get())
            add("compileOnly", libs.findLibrary("kotlin-json").get())
            add("compileOnly", libs.findLibrary("injekt-core").get())
            add("compileOnly", libs.findLibrary("coroutines-core").get())
            add("compileOnly", libs.findLibrary("coroutines-android").get())
        }
    }

    private fun Project.android(block: LibraryExtension.() -> Unit) {
        extensions.configure(block)
    }
}
