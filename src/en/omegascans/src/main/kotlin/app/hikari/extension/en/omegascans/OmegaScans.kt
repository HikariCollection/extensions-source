package app.hikari.extension.en.omegascans

import app.hikari.multisrc.heancms.HeanCms

class OmegaScans : HeanCms(
    name = "Omega Scans",
    baseUrl = "https://omegascans.org",
    lang = "en",
) {
    override val apiUrl: String = "https://api.omegascans.org"
}
