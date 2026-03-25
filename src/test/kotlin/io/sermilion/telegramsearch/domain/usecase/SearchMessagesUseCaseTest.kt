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
  test("expands local context around the matched anchor by default") {
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
      conversationSlices = mapOf(
        "100:1" to listOf(
          IndexedMessage(
            chatId = 100,
            messageId = 0,
            senderId = 11,
            senderName = "Me",
            sentAt = Instant.parse("2026-03-20T09:59:00Z"),
            text = "Can you share the latest Readian status?",
            chatTitle = "Alex",
            replyToMessageId = null,
            isOutgoing = true,
          ),
          IndexedMessage(
            chatId = 100,
            messageId = 1,
            senderId = 22,
            senderName = "Alex",
            sentAt = Instant.parse("2026-03-20T10:00:00Z"),
            text = "Readian progress is on track",
            chatTitle = "Alex",
            replyToMessageId = null,
            isOutgoing = false,
          ),
          IndexedMessage(
            chatId = 100,
            messageId = 2,
            senderId = 22,
            senderName = "Alex",
            sentAt = Instant.parse("2026-03-20T10:01:00Z"),
            text = "I finished the API fix yesterday",
            chatTitle = "Alex",
            replyToMessageId = 1,
            isOutgoing = false,
          ),
        )
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
    response.results.first().contextExpanded shouldBe true
    response.results.first().messages.map { it.messageId } shouldBe listOf(0L, 1L, 2L)
    response.results.first().text shouldBe """
      2026-03-20T09:59:00Z | Me
      Can you share the latest Readian status?
      
      2026-03-20T10:00:00Z | Alex
      Readian progress is on track
      
      2026-03-20T10:01:00Z | Alex
      I finished the API fix yesterday
    """.trimIndent()
    repository.sliceRequests shouldBe listOf(
      SliceRequest(chatId = 100, anchorMessageIds = listOf(1), beforeCount = 12, afterCount = 12)
    )
  }

  test("skips overlapping expanded slices from the same thread") {
    val repository = FakeMessageRepository(
      storedAccount = TelegramAccount(userId = 11, displayName = "Me"),
      chunks = listOf(
        StoredChunk(
          chunkKey = "1",
          chatId = 100,
          messageIds = listOf(10),
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
          messageIds = listOf(11),
          senderId = 22,
          senderName = "Alex",
          sentAt = Instant.parse("2026-03-20T10:01:00Z"),
          chatTitle = "Alex",
          text = "I finished the API fix yesterday",
          embedding = listOf(1.0, 0.0),
        ),
      ),
      conversationSlices = mapOf(
        "100:10" to listOf(
          storedMessage(messageId = 9, sentAt = "2026-03-20T09:59:00Z", text = "Can you share the latest Readian status?", senderId = 11, senderName = "Me", isOutgoing = true),
          storedMessage(messageId = 10, sentAt = "2026-03-20T10:00:00Z", text = "Readian progress is on track"),
          storedMessage(messageId = 11, sentAt = "2026-03-20T10:01:00Z", text = "I finished the API fix yesterday"),
        ),
        "100:11" to listOf(
          storedMessage(messageId = 10, sentAt = "2026-03-20T10:00:00Z", text = "Readian progress is on track"),
          storedMessage(messageId = 11, sentAt = "2026-03-20T10:01:00Z", text = "I finished the API fix yesterday"),
          storedMessage(messageId = 12, sentAt = "2026-03-20T10:02:00Z", text = "Deploying it now"),
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
    response.results.first().chunkKey shouldBe "2"
    response.results.first().contextExpanded shouldBe true
    response.results.first().messages.map { it.messageId } shouldBe listOf(10L, 11L, 12L)
    response.results.first().text shouldBe """
      2026-03-20T10:00:00Z | Alex
      Readian progress is on track
      
      2026-03-20T10:01:00Z | Alex
      I finished the API fix yesterday
      
      2026-03-20T10:02:00Z | Alex
      Deploying it now
    """.trimIndent()
  }

  test("keeps anchor-only behavior when context expansion is disabled") {
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
      ),
      conversationSlices = mapOf(
        "100:1" to listOf(
          storedMessage(messageId = 1, sentAt = "2026-03-20T10:00:00Z", text = "Readian progress is on track")
        )
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
      contextBeforeMessages = 0,
      contextAfterMessages = 0,
    )

    response.results shouldHaveSize 1
    response.results.first().contextExpanded shouldBe false
    response.results.first().messages.map { it.messageId } shouldBe listOf(1L)
    repository.sliceRequests shouldBe listOf(
      SliceRequest(chatId = 100, anchorMessageIds = listOf(1), beforeCount = 0, afterCount = 0)
    )
  }

  test("skips candidates when the anchor messages can no longer be reconstructed") {
    val repository = FakeMessageRepository(
      storedAccount = TelegramAccount(userId = 11, displayName = "Me"),
      chunks = listOf(
        StoredChunk(
          chunkKey = "1",
          chatId = 100,
          messageIds = listOf(999),
          senderId = 22,
          senderName = "Alex",
          sentAt = Instant.parse("2026-03-20T10:00:00Z"),
          chatTitle = "Alex",
          text = "Readian progress is on track",
          embedding = listOf(1.0, 0.0),
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

    response.results shouldHaveSize 0
  }

  test("does not collapse overlapping message ids from different chats") {
    val repository = FakeMessageRepository(
      storedAccount = TelegramAccount(userId = 11, displayName = "Me"),
      chunks = listOf(
        StoredChunk(
          chunkKey = "1",
          chatId = 100,
          messageIds = listOf(10),
          senderId = 22,
          senderName = "Alex",
          sentAt = Instant.parse("2026-03-20T10:01:00Z"),
          chatTitle = "Alex",
          text = "Readian progress is on track",
          embedding = listOf(1.0, 0.0),
        ),
        StoredChunk(
          chunkKey = "2",
          chatId = 200,
          messageIds = listOf(10),
          senderId = 33,
          senderName = "Taylor",
          sentAt = Instant.parse("2026-03-20T10:02:00Z"),
          chatTitle = "Taylor",
          text = "Readian fix is ready",
          embedding = listOf(1.0, 0.0),
        ),
      ),
      conversationSlices = mapOf(
        "100:10" to listOf(
          storedMessage(messageId = 10, sentAt = "2026-03-20T10:01:00Z", text = "Readian progress is on track")
        ),
        "200:10" to listOf(
          storedMessage(
            chatId = 200,
            chatTitle = "Taylor",
            messageId = 10,
            sentAt = "2026-03-20T10:02:00Z",
            text = "Readian fix is ready",
            senderId = 33,
            senderName = "Taylor",
          )
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

    response.results shouldHaveSize 2
    response.results.map { it.chatId } shouldBe listOf(200L, 100L)
  }
})

private class FakeMessageRepository(
  private val storedAccount: TelegramAccount?,
  private val chunks: List<StoredChunk>,
  private val conversationSlices: Map<String, List<IndexedMessage>> = emptyMap(),
) : MessageRepository {
  val sliceRequests = mutableListOf<SliceRequest>()

  override suspend fun upsertMessages(messages: List<IndexedMessage>): Int = messages.size

  override suspend fun replaceChunks(chatId: Long, chunks: List<MessageChunk>): Int = chunks.size

  override suspend fun searchLexical(query: String, limit: Int): List<StoredChunk> = chunks.take(limit)

  override suspend fun recentChunks(limit: Int): List<StoredChunk> = chunks.take(limit)

  override suspend fun conversationSlice(
    chatId: Long,
    anchorMessageIds: List<Long>,
    beforeCount: Int,
    afterCount: Int,
  ): List<IndexedMessage> {
    sliceRequests += SliceRequest(chatId = chatId, anchorMessageIds = anchorMessageIds, beforeCount = beforeCount, afterCount = afterCount)
    return conversationSlices["$chatId:${anchorMessageIds.joinToString(",")}"].orEmpty()
  }

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
        "i finished the api fix yesterday" -> listOf(1.0, 0.0)
        "we should discuss lunch" -> listOf(0.0, 1.0)
        else -> emptyList()
      }
    }
  }
}

private data class SliceRequest(
  val chatId: Long,
  val anchorMessageIds: List<Long>,
  val beforeCount: Int,
  val afterCount: Int,
)

private fun storedMessage(
  chatId: Long = 100,
  chatTitle: String = "Alex",
  messageId: Long,
  sentAt: String,
  text: String,
  senderId: Long? = 22,
  senderName: String = "Alex",
  isOutgoing: Boolean = false,
): IndexedMessage = IndexedMessage(
  chatId = chatId,
  messageId = messageId,
  senderId = senderId,
  senderName = senderName,
  sentAt = Instant.parse(sentAt),
  text = text,
  chatTitle = chatTitle,
  replyToMessageId = null,
  isOutgoing = isOutgoing,
)
