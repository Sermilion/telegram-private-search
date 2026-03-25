package io.sermilion.telegramsearch.presentation.search

import io.sermilion.telegramsearch.domain.model.SearchIntent
import io.sermilion.telegramsearch.domain.model.SearchResult

data class SearchUiState(
  val query: String = "",
  val isLoading: Boolean = false,
  val intent: SearchIntent? = null,
  val results: List<SearchResult> = emptyList(),
)
