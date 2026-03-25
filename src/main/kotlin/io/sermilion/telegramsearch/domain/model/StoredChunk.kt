package io.sermilion.telegramsearch.domain.model

import java.time.Instant

data class StoredChunk(
  val chunkKey: String,
  val chatId: Long,
  val messageIds: List<Long>,
  val senderId: Long?,
  val senderName: String,
  val sentAt: Instant,
  val chatTitle: String,
  val text: String,
  val embedding: List<Double>,
)
