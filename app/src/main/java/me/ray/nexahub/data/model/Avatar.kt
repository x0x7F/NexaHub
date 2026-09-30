package me.ray.nexahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The serial names keep the class names Avatar was persisted under before the package rename,
// so settings and backups written by older versions still decode.
@Serializable
sealed class Avatar {
    @Serializable
    @SerialName("me.rerere.rikkahub.data.model.Avatar.Dummy")
    data object Dummy : Avatar()

    @Serializable
    @SerialName("me.rerere.rikkahub.data.model.Avatar.Emoji")
    data class Emoji(val content: String) : Avatar()

    @Serializable
    @SerialName("me.rerere.rikkahub.data.model.Avatar.Image")
    data class Image(val url: String) : Avatar()
}
