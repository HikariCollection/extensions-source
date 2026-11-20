plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("Shinigami")
    versionCode.set(3)
    libVersion.set("1.1")
    contentWarning.set(ContentWarning.SAFE)

    source {
        lang.set("id")
        baseUrl = "https://s1.shinigami.mx"
        mirrorUrl("https://11.shinigami.asia")
        id.set(3411809758861089969L)
        versionId.set(1)
    }
}
