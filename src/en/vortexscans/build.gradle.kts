plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("Vortex Scans")
    versionCode.set(1)
    libVersion.set("1.1")
    contentWarning.set(ContentWarning.SAFE)

    source {
        lang.set("en")
        baseUrl = "https://vortexscans.org"
        versionId.set(1)
    }
}
