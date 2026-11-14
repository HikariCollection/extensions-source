import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import tasks.escapeXml
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HikariBuildLogicTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `resolvePackageSuffix uses dirSuffix when pkgName is null or blank`() {
        assertEquals("en.sample", resolvePackageSuffix(null, "en.sample"))
        assertEquals("en.sample", resolvePackageSuffix("   ", "en.sample"))
    }

    @Test
    fun `resolvePackageSuffix accepts valid custom suffix`() {
        assertEquals("en.custom_name", resolvePackageSuffix("en.custom_name", "en.sample"))
    }

    @Test
    fun `resolvePackageSuffix rejects full package prefix and invalid segments`() {
        assertFailsWith<IllegalArgumentException> {
            resolvePackageSuffix("app.hikari.extension.en.sample", "en.sample")
        }
        assertFailsWith<IllegalArgumentException> {
            resolvePackageSuffix("single", "en.sample")
        }
        assertFailsWith<IllegalArgumentException> {
            resolvePackageSuffix("en.bad-slug", "en.sample")
        }
    }

    @Test
    fun `resolveExtensionClassName detects slug match, base classes, and skips auxiliary files`() {
        val slugDir = File(tempDir, "slug").apply { mkdirs() }
        File(slugDir, "Dto.kt").writeText("class Dto")
        File(slugDir, "MySource.kt").writeText("class MySource : KeiSource()")
        assertEquals("app.hikari.extension.en.mysource.MySource", resolveExtensionClassName(slugDir, "app.hikari.extension.en.mysource", "mysource"))

        val baseClasses = listOf(
            "CustomIken" to "Iken(\"a\", \"b\", \"c\")",
            "CustomHean" to "HeanCms(\"a\", \"b\", \"c\")",
            "CustomMadaraBase" to "MadaraBase(\"a\", \"b\", \"c\")",
            "CustomFactory" to "SourceFactory",
        )
        baseClasses.forEachIndexed { index, (name, base) ->
            val dir = File(tempDir, "base_$index").apply { mkdirs() }
            File(dir, "Filters.kt").writeText("class Filters")
            File(dir, "Entry.kt").writeText("class $name : $base")
            assertEquals("pkg.$name", resolveExtensionClassName(dir, "pkg", "unmatched"))
        }
    }

    @Test
    fun `escapeXml escapes all five XML special characters`() {
        assertEquals(
            "A &amp; B &lt;tag&gt; &quot;quoted&quot; &apos;single&apos;",
            "A & B <tag> \"quoted\" 'single'".escapeXml(),
        )
    }

    @Test
    fun `Gradle plugin wires pkgName, mirrorUrls, theme conventions, and manifest escaping`() {
        val projectDir = File(tempDir, "sample-project").apply { mkdirs() }
        val repoRoot = File(System.getProperty("user.dir")).parentFile.parentFile

        File(projectDir, "gradle/libs.versions.toml").apply {
            parentFile.mkdirs()
            writeText(File(repoRoot, "gradle/libs.versions.toml").readText())
        }
        val localProps = File(repoRoot, "local.properties")
        if (localProps.exists()) {
            localProps.copyTo(File(projectDir, "local.properties"), overwrite = true)
        }
        File(projectDir, "common").mkdirs()
        File(projectDir, "common/AndroidManifest.xml").writeText("<manifest />")

        val buildLogicPath = repoRoot.resolve("gradle/build-logic").invariantSeparatorsPath

        File(projectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                includeBuild("$buildLogicPath")
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositories {
                    google()
                    mavenCentral()
                    maven(url = "https://raw.githubusercontent.com/HikariCollection/extensions/maven")
                    maven(url = "https://www.jitpack.io")
                }
            }
            rootProject.name = "test-extensions"
            include(":core")
            include(":lib-multisrc:mytheme")
            include(":src:en:sample")
            """.trimIndent(),
        )

        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                alias(libs.plugins.android.application) apply false
                alias(libs.plugins.android.library) apply false
                alias(libs.plugins.kotlin.serialization) apply false
            }
            """.trimIndent(),
        )

        File(projectDir, "core/build.gradle.kts").apply {
            parentFile.mkdirs()
            writeText(
                """
                plugins {
                    id("com.android.library")
                }
                android {
                    namespace = "app.hikari.extension.core"
                    compileSdk = 37
                    defaultConfig { minSdk = 26 }
                }
                """.trimIndent(),
            )
        }

        File(projectDir, "lib-multisrc/mytheme/build.gradle.kts").apply {
            parentFile.mkdirs()
            writeText(
                """
                plugins {
                    id("hikari.plugins.theme")
                }
                """.trimIndent(),
            )
        }

        File(projectDir, "src/en/sample/src/main/kotlin/app/hikari/extension/en/sample").mkdirs()
        File(projectDir, "src/en/sample/src/main/kotlin/app/hikari/extension/en/sample/Entry.kt").writeText(
            "package app.hikari.extension.en.sample\nclass SampleExt : Iken()",
        )
        File(projectDir, "src/en/sample/build.gradle.kts").writeText(
            """
            plugins {
                id("hikari.plugins.extension")
            }
            hikari {
                name.set("A & B's <Sample>")
                pkgName.set("en.customsample")
                theme.set("mytheme")
                versionCode.set(2)
                contentWarning.set(ContentWarning.MIXED)

                source {
                    lang.set("en")
                    baseUrl = "https://example.com"
                    mirrorUrl("https://mirror1.example.com", "https://mirror2.example.com")
                    versionId.set(1)
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(":src:en:sample:generateSourceInfo", ":src:en:sample:generateExtensionManifest")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))

        val sourceInfo = File(projectDir, "src/en/sample/build/hikari-source-info.json").readText()
        assertTrue(sourceInfo.contains("\"packageName\": \"app.hikari.extension.en.customsample\""), sourceInfo)
        assertTrue(sourceInfo.contains("\"versionName\": \"1.1.2\""), sourceInfo)
        assertTrue(sourceInfo.contains("\"https://mirror1.example.com\""), sourceInfo)
        assertTrue(sourceInfo.contains("\"https://mirror2.example.com\""), sourceInfo)

        val manifest = File(projectDir, "src/en/sample/build/generated")
            .walkTopDown()
            .first { it.isFile && it.name == "AndroidManifest.xml" }
            .readText()
        assertTrue(manifest.contains("app.hikari.extension.en.sample.SampleExt"), manifest)
        assertTrue(manifest.contains("A &amp; B&apos;s &lt;Sample&gt;"), manifest)
    }
}
