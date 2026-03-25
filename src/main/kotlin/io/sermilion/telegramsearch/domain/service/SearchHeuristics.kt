package io.sermilion.telegramsearch.domain.service

import io.sermilion.telegramsearch.domain.model.SearchIntent
import io.sermilion.telegramsearch.domain.model.SpeakerHint

object SearchHeuristics {
  fun analyze(query: String): SearchIntent {
    val keywords = keywordize(query)
    return SearchIntent(
      originalQuery = query,
      topic = keywords.firstOrNull().orEmpty(),
      activity = guessActivity(query),
      speakerHint = guessSpeaker(query),
      wantsLatest = wantsLatest(query),
      keywords = keywords,
    )
  }

  fun keywordize(query: String): List<String> {
    val normalized = query
      .lowercase()
      .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
      .trim()
    return normalized
      .split(Regex("\\s+"))
      .filter { it.length > 2 }
      .distinct()
  }

  private fun guessActivity(query: String): String {
    val lowered = query.lowercase()
    return when {
      lowered.contains("progress") || lowered.contains("reported") -> "progress update"
      lowered.contains("working on") || lowered.contains("discuss") -> "work discussion"
      else -> ""
    }
  }

  private fun guessSpeaker(query: String): SpeakerHint {
    val lowered = query.lowercase()
    return when {
      listOf(" he ", " she ", " they ", " reported ").any { lowered.contains(it) } -> SpeakerHint.OTHER
      listOf(" i ", " my ", " me ").any { lowered.contains(it) } -> SpeakerHint.SELF
      else -> SpeakerHint.UNKNOWN
    }
  }

  private fun wantsLatest(query: String): Boolean {
    val lowered = query.lowercase()
    return listOf("last", "latest", "most recent", "when").any { lowered.contains(it) }
  }
}
