import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import tasks.GenerateManifestTask
import tasks.GenerateSourceInfoTask
import java.security.MessageDigest

@Serializable
internal data class HikariExtensionMetadata(
    val name: String,
    val packageName: String,
    val extensionLib: String,
    val versionCode: Int,
    val versionName: String,
    val contentWarning: String,
    val apkFileName: String,
    val iconFileName: String,
    val theme: String? = null,
    val sources: List<HikariSourceMetadata>,
)

@Serializable
internal data class HikariSourceMetadata(
    val id: Long,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val mirrorUrls: List<String> = emptyList(),
)

private val json = Json { prettyPrint = true }

class HikariExtensionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        val hikari = extensions.create<HikariExtension>("hikari")
        val lang = project.parent?.name ?: "all"
        val slug = project.name
        val dirSuffix = "$lang.$slug"
        val fullPackageName = "app.hikari.extension.$dirSuffix"

        val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")

        android {
            namespace = fullPackageName
            compileSdk = 37

            defaultConfig {
                applicationId = fullPackageName
                minSdk = 26
                targetSdk = 35
            }

            sourceSets.named("main") {
                manifest.srcFile(rootProject.file("common/AndroidManifest.xml"))
            }

            val signingKeyFile = rootProject.file("signingkey.jks")
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
                named("release") {
                    isMinifyEnabled = false
                    signingConfig = if (signingKeyFile.exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
                }
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
            add("compileOnly", libs.findLibrary("androidx-preference").get())
            add("compileOnly", libs.findLibrary("coroutines-core").get())
            add("compileOnly", libs.findLibrary("coroutines-android").get())
        }

        val themeExtension = hikari.theme.map { themeName ->
            evaluationDependsOn(":lib-multisrc:$themeName").extensions.findByType<HikariThemeExtension>()
                ?: error("Theme project :lib-multisrc:$themeName must apply hikari.plugins.theme")
        }

        afterEvaluate {
            if (hikari.theme.isPresent) {
                dependencies {
                    add("implementation", project(":lib-multisrc:${hikari.theme.get()}"))
                }
            }
        }

        val extNameProvider = hikari.name.orElse(slug.replaceFirstChar { it.uppercase() })
        val libVersionProvider = themeExtension.flatMap { it.libVersion }.orElse(hikari.libVersion.orElse("1.1"))
        val versionCodeProvider = themeExtension.flatMap { theme ->
            theme.baseVersionCode.zip(hikari.versionCode.orElse(1)) { base, ext -> base + ext }
        }.orElse(hikari.versionCode.orElse(1))
        val cwProvider = hikari.contentWarning.orElse(ContentWarning.SAFE)

        val classNameProvider = hikari.extensionClass.orElse(
            providers.provider {
                val srcDir = project.file("src/main/kotlin/app/hikari/extension/$lang/$slug")
                val ktFiles = srcDir.listFiles { _, name -> name.endsWith(".kt") } ?: emptyArray()
                val normalizedSlug = slug.replace("-", "").replace("_", "")

                // Priority 1: File matching slug case-insensitively
                val slugMatch = ktFiles.firstOrNull {
                    it.nameWithoutExtension.replace("-", "").replace("_", "").equals(normalizedSlug, ignoreCase = true)
                }

                // Priority 2: File containing a class extending a known Source base class
                val sourceClassRegex = Regex("""(?:class|object)\s+([A-Za-z0-9_]+)\s*(?:\([^)]*\))?\s*:\s*(?:[A-Za-z0-9_.]*Source|MangaThemesia|Madara|NatsuId|MangaTaro)""")
                val classMatch = if (slugMatch == null) {
                    ktFiles.firstNotNullOfOrNull { file ->
                        sourceClassRegex.find(file.readText())?.groupValues?.get(1)
                    }
                } else null

                // Priority 3: First file not matching known utility file names
                val fallbackFile = ktFiles.firstOrNull {
                    it.nameWithoutExtension !in setOf("Dto", "Filters", "Models", "Helper", "Utils")
                }

                val simpleName = slugMatch?.nameWithoutExtension
                    ?: classMatch
                    ?: fallbackFile?.nameWithoutExtension
                    ?: slug.replaceFirstChar { it.uppercase() }

                "$fullPackageName.$simpleName"
            },
        ).map {
            if (it.contains(".")) it else "$fullPackageName.$it"
        }

        val manifestTask = tasks.register<GenerateManifestTask>("generateExtensionManifest") {
            extensionName.set(extNameProvider)
            extensionClass.set(classNameProvider)
            contentWarning.set(cwProvider)
            extensionLib.set(libVersionProvider)
            outputFile.set(layout.buildDirectory.file("generated/manifests/AndroidManifest.xml"))
        }

        val sourceInfoTask = tasks.register<GenerateSourceInfoTask>("generateSourceInfo") {
            outputFile.set(layout.buildDirectory.file("hikari-source-info.json"))
        }

        androidComponents {
            onVariants { variant ->
                val vCode = versionCodeProvider.get()
                val vName = "${libVersionProvider.get()}.$vCode"
                val apkName = "hikari-$lang-$slug-release.apk"

                variant.outputs.forEach { output ->
                    output.versionCode.set(vCode)
                    output.versionName.set(vName)
                    @Suppress("UnstableApiUsage")
                    output.outputFileName.set(apkName)
                }

                variant.sources.manifests.addGeneratedManifestFile(manifestTask) { it.outputFile }

                if (variant.buildType == "release") {
                    afterEvaluate {
                        val sourcesList = hikari.sources.getOrElse(emptyList()).map { s ->
                            val sName = s.name.getOrElse(extNameProvider.get())
                            val sLang = s.lang.getOrElse(lang)
                            val sVersionId = s.versionId.getOrElse(1)
                            val sId = s.id.getOrElse(computeSourceId(sName, sLang, sVersionId))
                            HikariSourceMetadata(
                                id = sId,
                                name = sName,
                                lang = sLang,
                                baseUrl = s.baseUrlProperty.getOrElse(""),
                            )
                        }

                        val cwString = when (cwProvider.get()) {
                            ContentWarning.SAFE -> "SAFE"
                            ContentWarning.MIXED -> "MIXED"
                            ContentWarning.NSFW -> "NSFW"
                        }

                        val metadata = HikariExtensionMetadata(
                            name = extNameProvider.get(),
                            packageName = fullPackageName,
                            extensionLib = libVersionProvider.get(),
                            versionCode = vCode,
                            versionName = vName,
                            contentWarning = cwString,
                            apkFileName = apkName,
                            iconFileName = "hikari-$lang-$slug-release.png",
                            theme = hikari.theme.orNull,
                            sources = sourcesList,
                        )

                        sourceInfoTask.configure {
                            content.set(json.encodeToString(HikariExtensionMetadata.serializer(), metadata))
                        }
                    }

                    tasks.matching { it.name == "assembleRelease" }.configureEach {
                        dependsOn(sourceInfoTask)
                    }
                }
            }
        }
    }

    private fun Project.android(block: ApplicationExtension.() -> Unit) {
        extensions.configure(block)
    }

    private fun Project.androidComponents(block: ApplicationAndroidComponentsExtension.() -> Unit) {
        extensions.configure(block)
    }

    private fun computeSourceId(name: String, lang: String, versionId: Int = 1): Long {
        val key = "${name.lowercase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff }
            .reduce { acc, l -> (acc shl 8) or l } and Long.MAX_VALUE
    }
}
