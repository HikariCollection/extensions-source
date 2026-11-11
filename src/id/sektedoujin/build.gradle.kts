plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("Sektedoujin")
    theme.set("mangathemesia")
    versionCode.set(1)
    libVersion.set("1.1")
    contentWarning.set(ContentWarning.NSFW)

    source {
        lang.set("id")
        baseUrl = "https://sektedoujin.cc"
        versionId.set(1)
    }
}
