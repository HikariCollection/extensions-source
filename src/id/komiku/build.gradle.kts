plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("Komiku")
    versionCode.set(1)
    libVersion.set("1.1")
    contentWarning.set(ContentWarning.MIXED)

    source {
        lang.set("id")
        baseUrl = "https://komiku.org"
        versionId.set(1)
    }
}
