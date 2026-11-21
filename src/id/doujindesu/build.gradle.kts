plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("Doujindesu")
    versionCode.set(1)
    libVersion.set("1.1")
    contentWarning.set(ContentWarning.NSFW)

    source {
        lang.set("id")
        baseUrl = "https://doujin.desu.xxx"
        mirrorUrl("https://doujindesu.tv")
        mirrorUrl("https://doujindesu.xxx")
        versionId.set(1)
    }
}
