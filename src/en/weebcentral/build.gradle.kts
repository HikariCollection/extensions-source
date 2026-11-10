plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("Weeb Central")
    versionCode.set(1)
    libVersion.set("1.1")
    contentWarning.set(ContentWarning.MIXED)

    source {
        lang.set("en")
        baseUrl = "https://weebcentral.com"
        versionId.set(1)
    }
}
