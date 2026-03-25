package io.sermilion.telegramsearch.domain.model

data class SearchResponse(
  val intent: SearchIntent,
  val results: List<SearchResult>,
  val selfUserId: Long?,
  val contextBeforeMessages: Int,
  val contextAfterMessages: Int,
)
