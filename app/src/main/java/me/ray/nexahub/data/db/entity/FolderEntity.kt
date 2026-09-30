package me.ray.nexahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Conversation folder (grouping within an assistant).
 *
 * A folder belongs to an assistant (assistant_id), and conversations link to it via [ConversationEntity.folderId].
 * No Room foreign key is used; when a folder is deleted, the repository layer manually clears folder_id on its conversations,
 * avoiding accidental cascade deletion of conversations and the complexity of foreign key migrations.
 */
@Entity(
    tableName = "conversation_folder",
    indices = [Index(value = ["assistant_id"])]
)
data class FolderEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("name")
    val name: String,
    @ColumnInfo("sort_index", defaultValue = "0")
    val sortIndex: Int = 0,
    @ColumnInfo("create_at")
    val createAt: Long,
)
