plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("Asura Scans")
    versionCode.set(1)
    libVersion.set("1.1")
    contentWarning.set(ContentWarning.SAFE)

    source {
        lang.set("en")
        baseUrl = "https://asurascans.com"
        versionId.set(1)
    }
}
