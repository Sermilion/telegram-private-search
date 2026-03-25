package io.sermilion.telegramsearch.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Fts4

@Fts4
@Entity(tableName = "message_chunk_fts")
data class MessageChunkFtsEntity(
  @ColumnInfo(name = "chunk_key")
  val chunkKey: String,
  @ColumnInfo(name = "sender_name")
  val senderName: String,
  @ColumnInfo(name = "chat_title")
  val chatTitle: String,
  @ColumnInfo(name = "text")
  val text: String,
)
