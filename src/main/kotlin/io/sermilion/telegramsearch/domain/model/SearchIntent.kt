package io.sermilion.telegramsearch.domain.model

data class SearchIntent(
  val originalQuery: String,
  val topic: String,
  val activity: String,
  val speakerHint: SpeakerHint,
  val wantsLatest: Boolean,
  val keywords: List<String>,
)

enum class SpeakerHint {
  SELF,
  OTHER,
  UNKNOWN,
}
