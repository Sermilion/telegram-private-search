package io.sermilion.telegramsearch.app.config

data class AppConfig(
  val telegram: TelegramConfig,
  val openAi: OpenAiConfig,
  val databasePath: String,
)

data class TelegramConfig(
  val apiId: Int,
  val apiHash: String,
  val phoneNumber: String,
  val useConsoleLogin: Boolean,
  val sessionDirectory: String,
)

data class OpenAiConfig(
  val baseUrl: String,
  val apiKey: String,
  val chatModel: String,
  val queryAnalysisEnabled: Boolean,
  val embeddingModel: String,
  val embeddingsEnabled: Boolean,
)
