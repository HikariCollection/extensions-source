plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("Arena Scans")
    versionCode.set(1)
    theme.set("mangathemesia")
    contentWarning.set(ContentWarning.MIXED)

    source {
        lang.set("en")
        baseUrl = "https://arenascan.com"
        versionId.set(1)
    }
}
