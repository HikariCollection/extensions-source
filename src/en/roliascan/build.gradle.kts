plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("Rolia Scan")
    versionCode.set(1)
    libVersion.set("1.1")
    contentWarning.set(ContentWarning.SAFE)

    source {
        lang.set("en")
        baseUrl = "https://roliascan.com"
        versionId.set(1)
    }
}
