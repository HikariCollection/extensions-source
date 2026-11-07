plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("Flame Comics")
    versionCode.set(1)
    libVersion.set("1.1")
    contentWarning.set(ContentWarning.SAFE)

    source {
        lang.set("en")
        baseUrl = "https://flamecomics.xyz"
        id.set(8531542650987673943L)
        versionId.set(1)
    }
}
