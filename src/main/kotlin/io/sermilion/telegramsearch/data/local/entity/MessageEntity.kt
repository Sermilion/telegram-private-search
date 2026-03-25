package io.sermilion.telegramsearch.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
  tableName = "messages",
  indices = [Index(value = ["chat_id", "sent_at_epoch_seconds"])],
)
data class MessageEntity(
  @PrimaryKey
  @ColumnInfo(name = "message_key")
  val messageKey: String,
  @ColumnInfo(name = "chat_id")
  val chatId: Long,
  @ColumnInfo(name = "message_id")
  val messageId: Long,
  @ColumnInfo(name = "sender_id")
  val senderId: Long?,
  @ColumnInfo(name = "sender_name")
  val senderName: String,
  @ColumnInfo(name = "sent_at_epoch_seconds")
  val sentAtEpochSeconds: Long,
  @ColumnInfo(name = "text")
  val text: String,
  @ColumnInfo(name = "chat_title")
  val chatTitle: String,
  @ColumnInfo(name = "reply_to_message_id")
  val replyToMessageId: Long?,
  @ColumnInfo(name = "is_outgoing")
  val isOutgoing: Boolean,
)
