package io.sermilion.telegramsearch.domain.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.sermilion.telegramsearch.domain.model.SpeakerHint

class SearchHeuristicsTest : FunSpec({
  test("detects other speaker and latest intent") {
    val intent = SearchHeuristics.analyze("find last message where he reported progress on Readian")

    intent.wantsLatest shouldBe true
    intent.speakerHint shouldBe SpeakerHint.OTHER
    intent.keywords shouldContain "readian"
  }

  test("does not classify self queries as other just because they mention reported") {
    val intent = SearchHeuristics.analyze("find the last message where i reported the issue")

    intent.speakerHint shouldBe SpeakerHint.SELF
  }
})
