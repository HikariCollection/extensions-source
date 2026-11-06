plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("MG Komik")
    versionCode.set(2)
    libVersion.set("1.1")
    contentWarning.set(ContentWarning.SAFE)

    source {
        lang.set("id")
        baseUrl = "https://web1.mgkomik.cc"
        versionId.set(2)
    }
}
