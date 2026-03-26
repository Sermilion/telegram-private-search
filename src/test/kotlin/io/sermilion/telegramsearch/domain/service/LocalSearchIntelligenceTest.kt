package io.sermilion.telegramsearch.domain.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.sermilion.telegramsearch.domain.model.SpeakerHint

class LocalSearchIntelligenceTest : FunSpec({
  test("analyzeQuery uses local heuristics") {
    val intelligence = LocalSearchIntelligence()

    val intent = intelligence.analyzeQuery("find last message where he reported progress on Readian")

    intent.wantsLatest shouldBe true
    intent.speakerHint shouldBe SpeakerHint.OTHER
    intent.keywords shouldContain "readian"
  }

  test("embedTexts returns empty embeddings for retrieval-only mode") {
    val intelligence = LocalSearchIntelligence()

    val embeddings = intelligence.embedTexts(listOf("hello", "world"))

    embeddings shouldHaveSize 2
    embeddings.forEach { it shouldBe emptyList() }
  }
})
