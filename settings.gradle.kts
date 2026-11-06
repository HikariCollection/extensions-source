pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
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
        maven(url = "https://www.jitpack.io")
    }
}

rootProject.name = "hikari-extensions"

include(":core")
include(":src:en:roliascan")
include(":src:en:arenascans")
include(":src:id:mgkomik")
include(":src:id:shinigami")
include(":src:id:wurmz")
