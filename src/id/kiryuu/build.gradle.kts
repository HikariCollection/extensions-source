plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("Kiryuu")
    versionCode.set(1)
    contentWarning.set(ContentWarning.MIXED)
    theme.set("natsuid")

    source {
        lang.set("id")
        baseUrl = "https://v7.kiryuu.to"
        id = 3639673976007021338L
    }
}
