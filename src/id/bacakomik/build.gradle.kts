plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("BacaKomik")
    versionCode.set(1)
    libVersion.set("1.1")
    contentWarning.set(ContentWarning.SAFE)

    source {
        lang.set("id")
        baseUrl = "https://bacakomik.my"
        versionId.set(1)
    }
}
