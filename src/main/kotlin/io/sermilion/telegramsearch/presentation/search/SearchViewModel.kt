package io.sermilion.telegramsearch.presentation.search

import io.sermilion.telegramsearch.domain.usecase.SearchMessagesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SearchViewModel(
  private val searchMessagesUseCase: SearchMessagesUseCase,
) {
  private val mutableState = MutableStateFlow(SearchUiState())
  val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

  suspend fun search(query: String, limit: Int, selfUserId: Long?) {
    mutableState.value = SearchUiState(query = query, isLoading = true)
    val response = searchMessagesUseCase(query = query, limit = limit, selfUserId = selfUserId)
    mutableState.value = SearchUiState(
      query = query,
      isLoading = false,
      intent = response.intent,
      results = response.results,
    )
  }
}
