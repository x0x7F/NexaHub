package me.ray.nexahub.data.favorite

import me.ray.nexahub.data.db.entity.FavoriteEntity
import me.ray.nexahub.data.model.FavoriteType

interface FavoriteAdapter<T> {
    val type: FavoriteType

    fun buildRefKey(target: T): String

    fun buildFavoriteEntity(
        target: T,
        existing: FavoriteEntity? = null,
        now: Long = System.currentTimeMillis()
    ): FavoriteEntity
}
