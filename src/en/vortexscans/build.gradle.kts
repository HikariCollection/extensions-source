plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("Vortex Scans")
    versionCode.set(2)
    libVersion.set("1.1")
    contentWarning.set(ContentWarning.SAFE)
    theme.set("iken")

    source {
        lang.set("en")
        baseUrl = "https://vortexscans.org"
        versionId.set(1)
    }
}
