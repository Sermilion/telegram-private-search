package io.sermilion.telegramsearch.domain.repository

import io.sermilion.telegramsearch.domain.model.SearchIntent

interface SearchIntelligence {
  suspend fun analyzeQuery(query: String): SearchIntent
  suspend fun embedTexts(texts: List<String>): List<List<Double>>
}
