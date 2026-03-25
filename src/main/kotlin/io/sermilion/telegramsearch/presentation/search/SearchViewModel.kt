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

  suspend fun search(
    query: String,
    limit: Int,
    selfUserId: Long?,
    contextBeforeMessages: Int = 12,
    contextAfterMessages: Int = 12,
  ) {
    mutableState.value = SearchUiState(query = query, isLoading = true)
    val response = searchMessagesUseCase(
      query = query,
      limit = limit,
      selfUserId = selfUserId,
      contextBeforeMessages = contextBeforeMessages,
      contextAfterMessages = contextAfterMessages,
    )
    mutableState.value = SearchUiState(
      query = query,
      isLoading = false,
      intent = response.intent,
      results = response.results,
      contextBeforeMessages = response.contextBeforeMessages,
      contextAfterMessages = response.contextAfterMessages,
    )
  }
}
