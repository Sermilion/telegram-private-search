package io.sermilion.telegramsearch.domain.model

data class IndexSummary(
  val account: TelegramAccount,
  val messageCount: Int,
  val chunkCount: Int,
  val chatCount: Int,
)
