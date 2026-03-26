package io.sermilion.telegramsearch.domain.service

import io.sermilion.telegramsearch.domain.model.SearchIntent
import io.sermilion.telegramsearch.domain.repository.SearchIntelligence

class LocalSearchIntelligence : SearchIntelligence {
  override suspend fun analyzeQuery(query: String): SearchIntent = SearchHeuristics.analyze(query)

  override suspend fun embedTexts(texts: List<String>): List<List<Double>> = texts.map { emptyList() }
}
