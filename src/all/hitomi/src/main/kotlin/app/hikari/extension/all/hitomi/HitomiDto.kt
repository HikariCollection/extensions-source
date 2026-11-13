package app.hikari.extension.all.hitomi

import kotlinx.serialization.Serializable

@Serializable
class HitomiGalleryDto(
    val id: String,
    val title: String,
    val japanese_title: String? = null,
    val type: String? = null,
    val language: String? = null,
    val date: String? = null,
    val artists: List<HitomiArtistDto>? = null,
    val groups: List<HitomiGroupDto>? = null,
    val characters: List<HitomiCharacterDto>? = null,
    val parodys: List<HitomiParodyDto>? = null,
    val tags: List<HitomiTagDto>? = null,
    val files: List<HitomiFileDto>? = null,
)

@Serializable
class HitomiArtistDto(
    val artist: String? = null,
    val url: String? = null,
)

@Serializable
class HitomiGroupDto(
    val group: String? = null,
    val url: String? = null,
)

@Serializable
class HitomiCharacterDto(
    val character: String? = null,
    val url: String? = null,
)

@Serializable
class HitomiParodyDto(
    val parody: String? = null,
    val url: String? = null,
)

@Serializable
class HitomiTagDto(
    val tag: String? = null,
    val male: String? = null,
    val female: String? = null,
    val url: String? = null,
)

@Serializable
class HitomiFileDto(
    val hash: String,
    val name: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val haswebp: Int? = null,
    val hasavif: Int? = null,
)
