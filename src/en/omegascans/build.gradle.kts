plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("Omega Scans")
    versionCode.set(1)
    contentWarning.set(ContentWarning.NSFW)
    theme.set("heancms")

    source {
        lang.set("en")
        baseUrl = "https://omegascans.org"
    }
}
