package io.sermilion.telegramsearch.adapter.mcp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.utils.io.streams.asInput
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.sermilion.telegramsearch.domain.model.IndexedMessage
import io.sermilion.telegramsearch.domain.model.MessageChunk
import io.sermilion.telegramsearch.domain.model.SearchIntent
import io.sermilion.telegramsearch.domain.model.SpeakerHint
import io.sermilion.telegramsearch.domain.model.StoredChunk
import io.sermilion.telegramsearch.domain.model.TelegramAccount
import io.sermilion.telegramsearch.domain.repository.MessageRepository
import io.sermilion.telegramsearch.domain.repository.SearchIntelligence
import io.sermilion.telegramsearch.domain.repository.TelegramGateway
import io.sermilion.telegramsearch.domain.service.MessageChunker
import io.sermilion.telegramsearch.domain.usecase.IndexPrivateChatsUseCase
import io.sermilion.telegramsearch.domain.usecase.SearchMessagesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

class TelegramSearchMcpServerTest : FunSpec({
  test("handles newline-delimited MCP requests over stdio") {
    val repository = FakeMessageRepository(
      storedAccount = TelegramAccount(userId = 11, displayName = "Me"),
      chunks = listOf(
        StoredChunk(
          chunkKey = "100:1:1",
          chatId = 100,
          messageIds = listOf(1),
          senderId = 22,
          senderName = "Ruslan Batukaev",
          sentAt = Instant.parse("2026-03-05T10:00:00Z"),
          chatTitle = "Ruslan Batukaev",
          text = "Readian progress is on track",
          embedding = emptyList(),
        ),
      ),
      conversationSlices = mapOf(
        "100:1" to listOf(
          IndexedMessage(
            chatId = 100,
            messageId = 1,
            senderId = 22,
            senderName = "Ruslan Batukaev",
            sentAt = Instant.parse("2026-03-05T10:00:00Z"),
            text = "Readian progress is on track",
            chatTitle = "Ruslan Batukaev",
            replyToMessageId = null,
            isOutgoing = false,
          ),
          IndexedMessage(
            chatId = 100,
            messageId = 2,
            senderId = 22,
            senderName = "Ruslan Batukaev",
            sentAt = Instant.parse("2026-03-05T10:01:00Z"),
            text = "I finished the API fix yesterday",
            chatTitle = "Ruslan Batukaev",
            replyToMessageId = 1,
            isOutgoing = false,
          ),
        )
      ),
    )
    val searchIntelligence = FakeSearchIntelligence()
    val mcpServer = TelegramSearchMcpServer(
      indexPrivateChatsUseCase = IndexPrivateChatsUseCase(
        telegramGateway = FakeTelegramGateway(),
        messageRepository = repository,
        searchIntelligence = searchIntelligence,
        messageChunker = MessageChunker(),
      ),
      searchMessagesUseCase = SearchMessagesUseCase(
        messageRepository = repository,
        searchIntelligence = searchIntelligence,
      ),
      searchIntelligence = searchIntelligence,
    )
    val server = mcpServer.createServer()
    val clientOutput = PipedOutputStream()
    val serverInput = PipedInputStream(clientOutput)
    val serverOutput = PipedOutputStream()
    val clientInput = PipedInputStream(serverOutput)
    val writer = BufferedWriter(OutputStreamWriter(clientOutput, StandardCharsets.UTF_8))
    val reader = BufferedReader(InputStreamReader(clientInput, StandardCharsets.UTF_8))

    try {
      server.createSession(
        StdioServerTransport(
          serverInput.asInput(),
          serverOutput.asSink().buffered(),
        ),
      )

      writer.writeJsonLine(
        """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0"}}}"""
      )
      val initializeResponse = reader.readJsonObject()
      initializeResponse["id"]?.jsonPrimitive?.content shouldBe "1"
      initializeResponse["result"]?.jsonObject?.get("protocolVersion")?.jsonPrimitive?.content shouldBe "2024-11-05"

      writer.writeJsonLine("""{"jsonrpc":"2.0","method":"notifications/initialized","params":{}}""")
      writer.writeJsonLine("""{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}""")
      val toolsResponse = reader.readJsonObject()
      val toolNames = toolsResponse["result"]
        ?.jsonObject
        ?.get("tools")
        ?.jsonArray
        ?.map { tool -> tool.jsonObject["name"]?.jsonPrimitive?.content.orEmpty() }
        ?.sorted()
        .orEmpty()
      toolNames shouldBe listOf("index_private_messages", "search_messages")

      writer.writeJsonLine(
        """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"search_messages","arguments":{"query":"find Readian progress","limit":1}}}"""
      )
      val toolCallResponse = reader.readJsonObject()
      val contentText = toolCallResponse["result"]
        ?.jsonObject
        ?.get("content")
        ?.jsonArray
        ?.first()
        ?.jsonObject
        ?.get("text")
        ?.jsonPrimitive
        ?.content
        .orEmpty()
      contentText shouldContain "Readian progress is on track"
      val payload = Json.parseToJsonElement(contentText).jsonObject
      val results = payload["results"]?.jsonArray.orEmpty()
      results shouldHaveSize 1
      val firstResult = results.first().jsonObject
      firstResult["chatTitle"]?.jsonPrimitive?.content shouldBe "Ruslan Batukaev"
      firstResult["contextExpanded"]?.jsonPrimitive?.content shouldBe "true"
      firstResult["messages"]?.jsonArray?.map { message ->
        message.jsonObject["messageId"]?.jsonPrimitive?.content.orEmpty()
      } shouldBe listOf("1", "2")
    } finally {
      withTimeout(5_000) {
        server.close()
      }
      withContext(Dispatchers.IO) {
        reader.close()
        writer.close()
      }
    }
  }

  test("reindexes before search when the query asks for latest messages") {
    val gateway = FakeTelegramGateway()
    val repository = FakeMessageRepository(
      storedAccount = TelegramAccount(userId = 11, displayName = "Me"),
      chunks = listOf(
        StoredChunk(
          chunkKey = "100:1:1",
          chatId = 100,
          messageIds = listOf(1),
          senderId = 22,
          senderName = "Ruslan Batukaev",
          sentAt = Instant.parse("2026-03-05T10:00:00Z"),
          chatTitle = "Ruslan Batukaev",
          text = "Latest progress update",
          embedding = emptyList(),
        ),
      ),
      conversationSlices = mapOf(
        "100:1" to listOf(
          IndexedMessage(
            chatId = 100,
            messageId = 1,
            senderId = 22,
            senderName = "Ruslan Batukaev",
            sentAt = Instant.parse("2026-03-05T10:00:00Z"),
            text = "Latest progress update",
            chatTitle = "Ruslan Batukaev",
            replyToMessageId = null,
            isOutgoing = false,
          ),
        ),
      ),
    )
    val searchIntelligence = FakeSearchIntelligence(wantsLatest = true)
    val mcpServer = TelegramSearchMcpServer(
      indexPrivateChatsUseCase = IndexPrivateChatsUseCase(
        telegramGateway = gateway,
        messageRepository = repository,
        searchIntelligence = searchIntelligence,
        messageChunker = MessageChunker(),
      ),
      searchMessagesUseCase = SearchMessagesUseCase(
        messageRepository = repository,
        searchIntelligence = searchIntelligence,
      ),
      searchIntelligence = searchIntelligence,
    )
    val server = mcpServer.createServer()
    val clientOutput = PipedOutputStream()
    val serverInput = PipedInputStream(clientOutput)
    val serverOutput = PipedOutputStream()
    val clientInput = PipedInputStream(serverOutput)
    val writer = BufferedWriter(OutputStreamWriter(clientOutput, StandardCharsets.UTF_8))
    val reader = BufferedReader(InputStreamReader(clientInput, StandardCharsets.UTF_8))

    try {
      server.createSession(
        StdioServerTransport(
          serverInput.asInput(),
          serverOutput.asSink().buffered(),
        ),
      )

      writer.writeJsonLine(
        """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0"}}}"""
      )
      reader.readJsonObject()
      writer.writeJsonLine("""{"jsonrpc":"2.0","method":"notifications/initialized","params":{}}""")
      writer.writeJsonLine(
        """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"search_messages","arguments":{"query":"latest message from Ruslan","limit":1}}}"""
      )
      val response = reader.readJsonObject()
      response["id"]?.jsonPrimitive?.content shouldBe "2"
      gateway.loadPrivateMessagesCallCount shouldBe 1
      repository.storeAccountCallCount shouldBe 1
    } finally {
      withTimeout(5_000) {
        server.close()
      }
      withContext(Dispatchers.IO) {
        reader.close()
        writer.close()
      }
    }
  }

  test("coalesces overlapping latest refresh requests") {
    val clock = MutableClock(Instant.parse("2026-03-05T10:00:00Z"))
    val gate = LatestMessageRefreshGate(
      clock = clock,
      freshnessWindow = Duration.ofSeconds(30),
    )
    val refreshCallCount = AtomicInteger(0)

    coroutineScope {
      launch {
        gate.refreshIfStale {
          refreshCallCount.incrementAndGet()
          delay(100)
        }
      }
      launch {
        gate.refreshIfStale {
          refreshCallCount.incrementAndGet()
          delay(100)
        }
      }
    }

    refreshCallCount.get() shouldBe 1
  }

  test("refreshes again after the freshness window expires") {
    val clock = MutableClock(Instant.parse("2026-03-05T10:00:00Z"))
    val gate = LatestMessageRefreshGate(
      clock = clock,
      freshnessWindow = Duration.ofSeconds(30),
    )
    var refreshCallCount = 0

    gate.refreshIfStale {
      refreshCallCount += 1
    }
    gate.refreshIfStale {
      refreshCallCount += 1
    }

    refreshCallCount shouldBe 1

    clock.advanceBy(Duration.ofSeconds(31))
    gate.refreshIfStale {
      refreshCallCount += 1
    }

    refreshCallCount shouldBe 2
  }
})

private suspend fun BufferedWriter.writeJsonLine(line: String) {
  withContext(Dispatchers.IO) {
    write(line)
    newLine()
    flush()
  }
}

private suspend fun BufferedReader.readJsonObject(): JsonObject {
  val line = withTimeout(5_000) {
    withContext(Dispatchers.IO) {
      readLine()
    }
  }.orEmpty()
  return Json.parseToJsonElement(line).jsonObject
}

private class FakeMessageRepository(
  private val storedAccount: TelegramAccount,
  private val chunks: List<StoredChunk>,
  private val conversationSlices: Map<String, List<IndexedMessage>>,
) : MessageRepository {
  var storeAccountCallCount = 0

  override suspend fun upsertMessages(messages: List<IndexedMessage>): Int = messages.size

  override suspend fun replaceChunks(chatId: Long, chunks: List<MessageChunk>): Int = chunks.size

  override suspend fun searchLexical(query: String, limit: Int): List<StoredChunk> = chunks.take(limit)

  override suspend fun recentChunks(limit: Int): List<StoredChunk> = emptyList()

  override suspend fun conversationSlice(
    chatId: Long,
    anchorMessageIds: List<Long>,
    beforeCount: Int,
    afterCount: Int,
  ): List<IndexedMessage> = conversationSlices["$chatId:${anchorMessageIds.joinToString(",")}"].orEmpty()

  override suspend fun storeAccount(account: TelegramAccount) {
    storeAccountCallCount += 1
  }

  override suspend fun getStoredAccount(): TelegramAccount = storedAccount
}

private class FakeSearchIntelligence(
  private val wantsLatest: Boolean = false,
) : SearchIntelligence {
  override suspend fun analyzeQuery(query: String): SearchIntent = SearchIntent(
    originalQuery = query,
    topic = "readian progress",
    activity = "progress",
    speakerHint = SpeakerHint.UNKNOWN,
    wantsLatest = wantsLatest,
    keywords = listOf("readian", "progress"),
  )

  override suspend fun embedTexts(texts: List<String>): List<List<Double>> = List(texts.size) { emptyList() }
}

private class FakeTelegramGateway : TelegramGateway {
  var loadPrivateMessagesCallCount = 0

  override suspend fun connect(): TelegramAccount = TelegramAccount(
    userId = 11,
    displayName = "Me",
  )

  override suspend fun loadPrivateMessages(limitPerChat: Int): List<IndexedMessage> {
    loadPrivateMessagesCallCount += 1
    return emptyList()
  }

  override suspend fun disconnect() = Unit
}

private class MutableClock(
  private var currentInstant: Instant,
  private val zoneId: ZoneId = ZoneOffset.UTC,
) : Clock() {
  override fun getZone(): ZoneId = zoneId

  override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)

  override fun instant(): Instant = currentInstant

  fun advanceBy(duration: Duration) {
    currentInstant = currentInstant.plus(duration)
  }
}
