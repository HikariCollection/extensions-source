plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("WEBTOON")
    versionCode.set(1)
    libVersion.set("1.1")
    contentWarning.set(ContentWarning.SAFE)

    source {
        lang.set("en")
        baseUrl = "https://www.webtoons.com"
        versionId.set(1)
    }

    source {
        lang.set("id")
        baseUrl = "https://www.webtoons.com"
        versionId.set(1)
    }
}
