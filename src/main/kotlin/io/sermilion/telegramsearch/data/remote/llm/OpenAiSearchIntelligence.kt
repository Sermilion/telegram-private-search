package io.sermilion.telegramsearch.data.remote.llm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.sermilion.telegramsearch.app.config.OpenAiConfig
import io.sermilion.telegramsearch.domain.model.SearchIntent
import io.sermilion.telegramsearch.domain.model.SpeakerHint
import io.sermilion.telegramsearch.domain.repository.SearchIntelligence
import io.sermilion.telegramsearch.domain.service.JsonSupport
import io.sermilion.telegramsearch.domain.service.SearchHeuristics
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class OpenAiSearchIntelligence(
  private val config: OpenAiConfig,
) : SearchIntelligence, AutoCloseable {
  private val client = HttpClient(CIO) {
    install(ContentNegotiation) {
      json(JsonSupport.json)
    }
  }

  override suspend fun analyzeQuery(query: String): SearchIntent {
    if (config.apiKey.isBlank() || !config.queryAnalysisEnabled) {
      return SearchHeuristics.analyze(query)
    }
    val response = client.post("${config.baseUrl}/chat/completions") {
      header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
      contentType(ContentType.Application.Json)
      setBody(
        ChatCompletionsRequestDataModel(
          model = config.chatModel,
          temperature = 0.0,
          responseFormat = ResponseFormatDataModel(type = "json_object"),
          messages = listOf(
            ChatMessageDataModel(
              role = "system",
              content = "Extract message search intent. Return JSON with topic, activity, speaker_hint, wants_latest, keywords. speaker_hint must be self, other, or unknown.",
            ),
            ChatMessageDataModel(role = "user", content = query),
          ),
        )
      )
    }
    val payload = response.body<ChatCompletionsResponseDataModel>()
    val content = payload.choices.firstOrNull()?.message?.content?.trim().orEmpty()
    if (content.isBlank()) {
      error("OpenAI returned an empty query analysis")
    }
    val parsed = JsonSupport.json.decodeFromString<QueryAnalysisDataModel>(content)
    return SearchIntent(
      originalQuery = query,
      topic = parsed.topic.trim(),
      activity = parsed.activity.trim(),
      speakerHint = parsed.speakerHint.toSpeakerHint(),
      wantsLatest = parsed.wantsLatest,
      keywords = parsed.keywords.map { it.trim().lowercase() }.filter { it.isNotBlank() },
    )
  }

  override suspend fun embedTexts(texts: List<String>): List<List<Double>> {
    if (texts.isEmpty()) {
      return emptyList()
    }
    if (config.apiKey.isBlank() || !config.embeddingsEnabled) {
      return texts.map { emptyList() }
    }
    val response = client.post("${config.baseUrl}/embeddings") {
      header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
      contentType(ContentType.Application.Json)
      setBody(
        EmbeddingsRequestDataModel(
          model = config.embeddingModel,
          input = texts,
        )
      )
    }
    val payload = response.body<EmbeddingsResponseDataModel>()
    return payload.data.sortedBy { it.index }.map { it.embedding }
  }

  override fun close() {
    client.close()
  }

  private fun String.toSpeakerHint(): SpeakerHint = when (lowercase()) {
    "self" -> SpeakerHint.SELF
    "other" -> SpeakerHint.OTHER
    else -> SpeakerHint.UNKNOWN
  }
}

@Serializable
private data class ChatCompletionsRequestDataModel(
  val model: String,
  val temperature: Double,
  @SerialName("response_format")
  val responseFormat: ResponseFormatDataModel,
  val messages: List<ChatMessageDataModel>,
)

@Serializable
private data class ResponseFormatDataModel(
  val type: String,
)

@Serializable
private data class ChatMessageDataModel(
  val role: String,
  val content: String,
)

@Serializable
private data class ChatCompletionsResponseDataModel(
  val choices: List<ChatChoiceDataModel>,
)

@Serializable
private data class ChatChoiceDataModel(
  val message: ChatMessageDataModel,
)

@Serializable
private data class QueryAnalysisDataModel(
  val topic: String = "",
  val activity: String = "",
  @SerialName("speaker_hint")
  val speakerHint: String = "unknown",
  @SerialName("wants_latest")
  val wantsLatest: Boolean = false,
  val keywords: List<String> = emptyList(),
)

@Serializable
private data class EmbeddingsRequestDataModel(
  val model: String,
  val input: List<String>,
)

@Serializable
private data class EmbeddingsResponseDataModel(
  val data: List<EmbeddingDataModel>,
)

@Serializable
private data class EmbeddingDataModel(
  val index: Int,
  val embedding: List<Double>,
)
