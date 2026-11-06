plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("Shinigami")
    versionCode.set(1)
    libVersion.set("1.1")
    contentWarning.set(ContentWarning.SAFE)

    source {
        lang.set("id")
        baseUrl = "https://11.shinigami.asia"
        versionId.set(1)
    }
}
