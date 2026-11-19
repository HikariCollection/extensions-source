plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("West Manga")
    versionCode.set(1)
    libVersion.set("1.1")
    contentWarning.set(ContentWarning.SAFE)

    source {
        lang.set("id")
        baseUrl = "https://v1.westmanga.my"
        mirrorUrl("https://v1.westmanga.top")
        id.set(8883916630998758688L)
        versionId.set(1)
    }
}
