package io.sermilion.telegramsearch.domain.usecase

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.sermilion.telegramsearch.domain.model.IndexedMessage
import io.sermilion.telegramsearch.domain.model.MessageChunk
import io.sermilion.telegramsearch.domain.model.SearchIntent
import io.sermilion.telegramsearch.domain.model.SpeakerHint
import io.sermilion.telegramsearch.domain.model.StoredChunk
import io.sermilion.telegramsearch.domain.model.TelegramAccount
import io.sermilion.telegramsearch.domain.repository.MessageRepository
import io.sermilion.telegramsearch.domain.repository.SearchIntelligence
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SearchMessagesUseCaseTest : FunSpec({
  test("prefers the other participant for progress searches") {
    val repository = FakeMessageRepository(
      storedAccount = TelegramAccount(userId = 11, displayName = "Me"),
      chunks = listOf(
        StoredChunk(
          chunkKey = "1",
          chatId = 100,
          messageIds = listOf(1),
          senderId = 22,
          senderName = "Alex",
          sentAt = Instant.parse("2026-03-20T10:00:00Z"),
          chatTitle = "Alex",
          text = "Readian progress is on track",
          embedding = listOf(1.0, 0.0),
        ),
        StoredChunk(
          chunkKey = "2",
          chatId = 100,
          messageIds = listOf(2),
          senderId = 11,
          senderName = "Me",
          sentAt = Instant.parse("2026-03-21T10:00:00Z"),
          chatTitle = "Alex",
          text = "We should discuss lunch",
          embedding = listOf(0.0, 1.0),
        ),
      ),
    )
    val intelligence = FakeSearchIntelligence()
    val useCase = SearchMessagesUseCase(
      messageRepository = repository,
      searchIntelligence = intelligence,
      clock = Clock.fixed(Instant.parse("2026-03-25T00:00:00Z"), ZoneOffset.UTC),
    )

    val response = useCase(
      query = "find last time he reported progress on Readian",
      limit = 5,
      selfUserId = 11,
    )

    response.results shouldHaveSize 1
    response.results.first().chunkKey shouldBe "1"
  }
})

private class FakeMessageRepository(
  private val storedAccount: TelegramAccount?,
  private val chunks: List<StoredChunk>,
) : MessageRepository {
  override suspend fun upsertMessages(messages: List<IndexedMessage>): Int = messages.size

  override suspend fun replaceChunks(chatId: Long, chunks: List<MessageChunk>): Int = chunks.size

  override suspend fun searchLexical(query: String, limit: Int): List<StoredChunk> = chunks.take(limit)

  override suspend fun recentChunks(limit: Int): List<StoredChunk> = chunks.take(limit)

  override suspend fun storeAccount(account: TelegramAccount) = Unit

  override suspend fun getStoredAccount(): TelegramAccount? = storedAccount
}

private class FakeSearchIntelligence : SearchIntelligence {
  override suspend fun analyzeQuery(query: String): SearchIntent = SearchIntent(
    originalQuery = query,
    topic = "readian",
    activity = "progress update",
    speakerHint = SpeakerHint.OTHER,
    wantsLatest = true,
    keywords = listOf("readian", "progress"),
  )

  override suspend fun embedTexts(texts: List<String>): List<List<Double>> {
    return texts.map { text ->
      when (text.lowercase()) {
        "find last time he reported progress on readian" -> listOf(1.0, 0.0)
        "readian progress is on track" -> listOf(1.0, 0.0)
        "we should discuss lunch" -> listOf(0.0, 1.0)
        else -> emptyList()
      }
    }
  }
}
