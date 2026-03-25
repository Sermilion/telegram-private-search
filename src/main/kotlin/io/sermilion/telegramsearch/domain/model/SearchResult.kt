package io.sermilion.telegramsearch.domain.model

import java.time.Instant

data class SearchResult(
  val chunkKey: String,
  val chatId: Long,
  val anchorMessageIds: List<Long>,
  val anchorSenderName: String,
  val anchorSentAt: Instant,
  val chatTitle: String,
  val text: String,
  val anchorText: String,
  val messages: List<IndexedMessage>,
  val contextExpanded: Boolean,
  val lexicalScore: Double,
  val semanticScore: Double,
  val combinedScore: Double,
)
