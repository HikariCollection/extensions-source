plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("Wurmz")
    versionCode.set(1)
    libVersion.set("1.1")
    contentWarning.set(ContentWarning.MIXED)

    source {
        lang.set("id")
        baseUrl = "https://wurmz.net"
        versionId.set(1)
    }
}
