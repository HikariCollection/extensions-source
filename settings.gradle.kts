pluginManagement {
    includeBuild("gradle/build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
        maven(url = "https://raw.githubusercontent.com/HikariCollection/extensions/maven")
        maven(url = "https://www.jitpack.io")
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven(url = "https://raw.githubusercontent.com/HikariCollection/extensions/maven")
        maven(url = "https://www.jitpack.io")
    }
}

rootProject.name = "hikari-extensions"

include(":core")
File(rootDir, "lib-multisrc").eachDir { include("lib-multisrc:${it.name}") }
loadAllIndividualExtensions()

fun loadAllIndividualExtensions() {
    File(rootDir, "src").eachDir { dir ->
        dir.eachDir { subdir ->
            include("src:${dir.name}:${subdir.name}")
        }
    }
}

fun File.eachDir(block: (File) -> Unit) {
    val files = listFiles() ?: return
    for (file in files) {
        if (file.isDirectory && file.name != ".gradle" && file.name != "build") {
            block(file)
        }
    }
}
