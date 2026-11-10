plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("Rolia Scan")
    versionCode.set(1)
    theme.set("mangataro")
    contentWarning.set(ContentWarning.SAFE)

    source {
        lang.set("en")
        baseUrl = "https://roliascan.com"
        versionId.set(1)
    }
}
