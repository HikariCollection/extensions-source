package app.hikari.extension.all.webtoons

import app.hikari.source.model.SChapter
import kotlinx.serialization.Serializable

@Serializable
class EpisodesResponseDto(
    val result: EpisodesResultDto? = null,
)

@Serializable
class EpisodesResultDto(
    val episodeList: List<EpisodeDto> = emptyList(),
    val nextCursor: Long? = null,
)

@Serializable
class EpisodeDto(
    private val episodeNo: Int = 0,
    private val episodeTitle: String = "",
    private val viewerLink: String = "",
    private val exposureDateMillis: Long = 0L,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = viewerLink
        name = episodeTitle.trim()
        date_upload = exposureDateMillis
        chapter_number = episodeNo.toFloat()
    }
}
