package io.sermilion.telegramsearch.app.config

data class AppConfig(
  val telegram: TelegramConfig,
  val databasePath: String,
)

data class TelegramConfig(
  val apiId: Int,
  val apiHash: String,
  val phoneNumber: String,
  val useConsoleLogin: Boolean,
  val sessionDirectory: String,
)
