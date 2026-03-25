package io.sermilion.telegramsearch.domain.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.sermilion.telegramsearch.domain.model.IndexedMessage
import java.time.Instant

class MessageChunkerTest : FunSpec({
  test("groups consecutive messages from the same sender") {
    val messages = listOf(
      IndexedMessage(1, 1, 10, "Alex", Instant.parse("2026-03-20T10:00:00Z"), "Started Readian work", "Alex", null, false),
      IndexedMessage(1, 2, 10, "Alex", Instant.parse("2026-03-20T10:01:00Z"), "Progress is on track", "Alex", null, false),
      IndexedMessage(1, 3, 20, "Me", Instant.parse("2026-03-20T10:02:00Z"), "Nice", "Alex", null, true),
    )

    val chunks = MessageChunker(maxMessagesPerChunk = 5).build(messages)

    chunks shouldHaveSize 2
    chunks.first().messageIds shouldBe listOf(1L, 2L)
    chunks.first().text shouldBe "Started Readian work\nProgress is on track"
  }
})
