package io.sermilion.telegramsearch.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
  tableName = "message_chunks",
  indices = [Index(value = ["chat_id", "sent_at_epoch_seconds"])],
)
data class MessageChunkEntity(
  @PrimaryKey
  @ColumnInfo(name = "chunk_key")
  val chunkKey: String,
  @ColumnInfo(name = "chat_id")
  val chatId: Long,
  @ColumnInfo(name = "message_ids_json")
  val messageIdsJson: String,
  @ColumnInfo(name = "sender_id")
  val senderId: Long?,
  @ColumnInfo(name = "sender_name")
  val senderName: String,
  @ColumnInfo(name = "sent_at_epoch_seconds")
  val sentAtEpochSeconds: Long,
  @ColumnInfo(name = "chat_title")
  val chatTitle: String,
  @ColumnInfo(name = "text")
  val text: String,
  @ColumnInfo(name = "embedding_json")
  val embeddingJson: String,
)
