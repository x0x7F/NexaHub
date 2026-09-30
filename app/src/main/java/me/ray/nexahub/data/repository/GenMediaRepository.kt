package me.ray.nexahub.data.repository

import androidx.paging.PagingSource
import me.ray.nexahub.data.db.dao.GenMediaDAO
import me.ray.nexahub.data.db.entity.GenMediaEntity

class GenMediaRepository(private val dao: GenMediaDAO) {
    fun getAllMedia(): PagingSource<Int, GenMediaEntity> = dao.getAll()

    suspend fun insertMedia(media: GenMediaEntity) = dao.insert(media)

    suspend fun deleteMedia(id: Int) = dao.delete(id)
}
