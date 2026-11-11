plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("Komik Indo")
    versionCode.set(1)
    libVersion.set("1.1")
    contentWarning.set(ContentWarning.MIXED)

    source {
        lang.set("id")
        baseUrl = "https://komikindo.ch"
        versionId.set(1)
    }
}
