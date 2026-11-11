plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("Toonily")
    theme.set("madara")
    versionCode.set(1)
    libVersion.set("1.1")
    contentWarning.set(ContentWarning.NSFW)

    source {
        lang.set("en")
        baseUrl = "https://toonily.com"
        versionId.set(1)
    }
}
