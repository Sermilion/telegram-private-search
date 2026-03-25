package io.sermilion.telegramsearch.domain.model

import java.time.Instant

data class IndexedMessage(
  val chatId: Long,
  val messageId: Long,
  val senderId: Long?,
  val senderName: String,
  val sentAt: Instant,
  val text: String,
  val chatTitle: String,
  val replyToMessageId: Long?,
  val isOutgoing: Boolean,
)
