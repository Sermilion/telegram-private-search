package io.sermilion.telegramsearch.adapter.mcp

import io.ktor.utils.io.streams.asInput
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.sermilion.telegramsearch.domain.model.IndexSummary
import io.sermilion.telegramsearch.domain.model.SearchResponse
import io.sermilion.telegramsearch.domain.service.JsonSupport
import io.sermilion.telegramsearch.domain.usecase.IndexPrivateChatsUseCase
import io.sermilion.telegramsearch.domain.usecase.SearchMessagesUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class TelegramSearchMcpServer(
  private val indexPrivateChatsUseCase: IndexPrivateChatsUseCase,
  private val searchMessagesUseCase: SearchMessagesUseCase,
) {
  fun run() {
    val server = createServer()
    val transport = StdioServerTransport(
      System.`in`.asInput(),
      System.out.asSink().buffered(),
    )
    runBlocking {
      val session = server.createSession(transport)
      val done = CompletableDeferred<Unit>()
      session.onClose {
        done.complete(Unit)
      }
      done.await()
    }
  }

  fun createServer(): Server {
    val server = Server(
      Implementation(name = "telegram-private-search", version = "0.1.0"),
      ServerOptions(
        capabilities = ServerCapabilities(
          tools = ServerCapabilities.Tools(listChanged = false),
          logging = ServerCapabilities.Logging,
        ),
      ),
    )
    server.addTool(
      name = "search_messages",
      description = "Search indexed private Telegram messages using keyword and semantic ranking.",
      inputSchema = ToolSchema(
        properties = buildJsonObject {
          putJsonObject("query") {
            put("type", "string")
            put("description", "Natural-language search query")
          }
          putJsonObject("limit") {
            put("type", "integer")
            put("default", 5)
          }
          putJsonObject("self_user_id") {
            put("type", "integer")
            put("description", "Optional Telegram user id used to resolve self/other speaker hints")
          }
        },
        required = listOf("query"),
      ),
      toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false),
    ) { request ->
      runBlocking {
        val arguments = request.arguments
        val query = arguments.requiredString("query")
        val limit = arguments.intValue("limit") ?: 5
        val selfUserId = arguments.longValue("self_user_id")
        val response = searchMessagesUseCase(query = query, limit = limit, selfUserId = selfUserId)
        CallToolResult(content = listOf(TextContent(JsonSupport.json.encodeToString(response.toDataModel()))))
      }
    }
    server.addTool(
      name = "index_private_messages",
      description = "Authenticate with Telegram if needed and index private text messages into the local Room database.",
      inputSchema = ToolSchema(
        properties = buildJsonObject {
          putJsonObject("limit_per_chat") {
            put("type", "integer")
            put("default", 1000)
          }
        },
      ),
      toolAnnotations = ToolAnnotations(readOnlyHint = false, openWorldHint = false),
    ) { request ->
      runBlocking {
        val limitPerChat = request.arguments.intValue("limit_per_chat") ?: 1000
        val summary = indexPrivateChatsUseCase(limitPerChat)
        CallToolResult(content = listOf(TextContent(JsonSupport.json.encodeToString(summary.toDataModel()))))
      }
    }
    return server
  }

  private fun Map<String, JsonElement>?.requiredString(key: String): String {
    return this?.get(key)?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
      ?: error("Missing required argument: $key")
  }

  private fun Map<String, JsonElement>?.intValue(key: String): Int? =
    this?.get(key)?.jsonPrimitive?.content?.toIntOrNull()

  private fun Map<String, JsonElement>?.longValue(key: String): Long? =
    this?.get(key)?.jsonPrimitive?.content?.toLongOrNull()
}

@Serializable
private data class SearchResponseDataModel(
  val intent: SearchIntentDataModel,
  val selfUserId: Long?,
  val results: List<SearchResultDataModel>,
)

@Serializable
private data class SearchIntentDataModel(
  val topic: String,
  val activity: String,
  val speakerHint: String,
  val wantsLatest: Boolean,
  val keywords: List<String>,
)

@Serializable
private data class SearchResultDataModel(
  val chunkKey: String,
  val chatId: Long,
  val messageIds: List<Long>,
  val senderName: String,
  val sentAt: String,
  val chatTitle: String,
  val text: String,
  val lexicalScore: Double,
  val semanticScore: Double,
  val combinedScore: Double,
)

@Serializable
private data class IndexSummaryDataModel(
  val accountUserId: Long,
  val accountDisplayName: String,
  val messageCount: Int,
  val chunkCount: Int,
  val chatCount: Int,
)

private fun SearchResponse.toDataModel(): SearchResponseDataModel = SearchResponseDataModel(
  intent = SearchIntentDataModel(
    topic = intent.topic,
    activity = intent.activity,
    speakerHint = intent.speakerHint.name.lowercase(),
    wantsLatest = intent.wantsLatest,
    keywords = intent.keywords,
  ),
  selfUserId = selfUserId,
  results = results.map { result ->
    SearchResultDataModel(
      chunkKey = result.chunkKey,
      chatId = result.chatId,
      messageIds = result.messageIds,
      senderName = result.senderName,
      sentAt = result.sentAt.toString(),
      chatTitle = result.chatTitle,
      text = result.text,
      lexicalScore = result.lexicalScore,
      semanticScore = result.semanticScore,
      combinedScore = result.combinedScore,
    )
  },
)

private fun IndexSummary.toDataModel(): IndexSummaryDataModel = IndexSummaryDataModel(
  accountUserId = account.userId,
  accountDisplayName = account.displayName,
  messageCount = messageCount,
  chunkCount = chunkCount,
  chatCount = chatCount,
)
