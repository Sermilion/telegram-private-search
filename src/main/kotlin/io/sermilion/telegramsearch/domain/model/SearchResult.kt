package io.sermilion.telegramsearch.domain.model

import java.time.Instant

data class SearchResult(
  val chunkKey: String,
  val chatId: Long,
  val messageIds: List<Long>,
  val senderName: String,
  val sentAt: Instant,
  val chatTitle: String,
  val text: String,
  val lexicalScore: Double,
  val semanticScore: Double,
  val combinedScore: Double,
)
