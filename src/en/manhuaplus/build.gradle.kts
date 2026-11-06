plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("Manhua Plus")
    versionCode.set(1)
    contentWarning.set(ContentWarning.SAFE)
    theme.set("madara")

    source {
        lang.set("en")
        baseUrl = "https://manhuaplus.com"
    }
}
