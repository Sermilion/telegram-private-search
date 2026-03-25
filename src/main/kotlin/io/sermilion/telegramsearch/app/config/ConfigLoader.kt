package io.sermilion.telegramsearch.app.config

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object ConfigLoader {
  fun load(rootDirectory: Path = Paths.get(System.getProperty("user.dir"))): AppConfig {
    val fileValues = loadDotEnv(rootDirectory.resolve(".env"))
    val env = fileValues + System.getenv()
    return AppConfig(
      telegram = TelegramConfig(
        apiId = env.required("TELEGRAM_API_ID").toInt(),
        apiHash = env.required("TELEGRAM_API_HASH"),
        phoneNumber = env["TELEGRAM_PHONE_NUMBER"].orEmpty(),
        useConsoleLogin = env["TELEGRAM_USE_CONSOLE_LOGIN"].orEmpty().ifBlank { "true" }.toBoolean(),
        sessionDirectory = resolvePath(rootDirectory, env["TELEGRAM_SESSION_DIR"].orEmpty().ifBlank { "data/telegram-session" }),
      ),
      openAi = OpenAiConfig(
        baseUrl = env["OPENAI_BASE_URL"].orEmpty().ifBlank { "https://api.openai.com/v1" }.removeSuffix("/"),
        apiKey = env["OPENAI_API_KEY"].orEmpty(),
        chatModel = env["OPENAI_CHAT_MODEL"].orEmpty().ifBlank { "gpt-4.1-mini" },
        queryAnalysisEnabled = env["QUERY_ANALYSIS_ENABLED"].orEmpty().ifBlank { "false" }.toBoolean(),
        embeddingModel = env["OPENAI_EMBEDDING_MODEL"].orEmpty().ifBlank { "text-embedding-3-small" },
        embeddingsEnabled = env["EMBEDDINGS_ENABLED"].orEmpty().ifBlank { "false" }.toBoolean(),
      ),
      databasePath = resolvePath(rootDirectory, env["DATABASE_PATH"].orEmpty().ifBlank { "data/telegram-search.db" }),
    )
  }

  private fun loadDotEnv(path: Path): Map<String, String> {
    if (!Files.exists(path)) {
      return emptyMap()
    }
    return Files.readAllLines(path)
      .asSequence()
      .map { it.trim() }
      .filter { it.isNotBlank() && !it.startsWith("#") }
      .mapNotNull { line ->
        val separator = line.indexOf('=')
        if (separator == -1) {
          null
        } else {
          val key = line.substring(0, separator).trim()
          val value = line.substring(separator + 1).trim().removeSurrounding("\"")
          key to value
        }
      }
      .toMap()
  }

  private fun resolvePath(rootDirectory: Path, rawPath: String): String {
    val candidate = Paths.get(rawPath)
    return if (candidate.isAbsolute) candidate.normalize().toString() else rootDirectory.resolve(candidate).normalize().toString()
  }

  private fun Map<String, String>.required(key: String): String =
    get(key)?.takeIf { it.isNotBlank() } ?: error("Missing required configuration: $key")
}
