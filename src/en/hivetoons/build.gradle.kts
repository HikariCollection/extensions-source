plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("Hive Toons")
    versionCode.set(1)
    libVersion.set("1.1")
    contentWarning.set(ContentWarning.SAFE)
    theme.set("iken")

    source {
        lang.set("en")
        baseUrl = "https://hivetoons.org"
        versionId.set(1)
    }
}
