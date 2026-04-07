package io.sermilion.telegramsearch.app.di

import io.sermilion.telegramsearch.adapter.mcp.TelegramSearchMcpServer
import io.sermilion.telegramsearch.app.config.ConfigLoader
import io.sermilion.telegramsearch.data.local.database.TelegramSearchDatabase
import io.sermilion.telegramsearch.data.local.database.TelegramSearchDatabaseFactory
import io.sermilion.telegramsearch.data.remote.telegram.TdLightTelegramGateway
import io.sermilion.telegramsearch.data.repository.RoomMessageRepository
import io.sermilion.telegramsearch.domain.service.LocalSearchIntelligence
import io.sermilion.telegramsearch.domain.service.MessageChunker
import io.sermilion.telegramsearch.domain.usecase.IndexPrivateChatsUseCase
import io.sermilion.telegramsearch.domain.usecase.SearchMessagesUseCase
import kotlinx.coroutines.runBlocking

class AppContainer private constructor(
  private val database: TelegramSearchDatabase,
  private val telegramGateway: TdLightTelegramGateway,
  val indexPrivateChatsUseCase: IndexPrivateChatsUseCase,
  val searchMessagesUseCase: SearchMessagesUseCase,
  val mcpServer: TelegramSearchMcpServer,
) : AutoCloseable {
  override fun close() {
    runBlocking {
      telegramGateway.disconnect()
    }
    database.close()
  }

  companion object {
    fun create(): AppContainer {
      val config = ConfigLoader.load()
      val database = TelegramSearchDatabaseFactory.create(config)
      val repository = RoomMessageRepository(
        messageDao = database.messageDao(),
        messageChunkDao = database.messageChunkDao(),
        metadataDao = database.metadataDao(),
      )
      val searchIntelligence = LocalSearchIntelligence()
      val telegramGateway = TdLightTelegramGateway(config.telegram)
      val indexPrivateChatsUseCase = IndexPrivateChatsUseCase(
        telegramGateway = telegramGateway,
        messageRepository = repository,
        searchIntelligence = searchIntelligence,
        messageChunker = MessageChunker(),
      )
      val searchMessagesUseCase = SearchMessagesUseCase(
        messageRepository = repository,
        searchIntelligence = searchIntelligence,
      )
      val mcpServer = TelegramSearchMcpServer(
        indexPrivateChatsUseCase = indexPrivateChatsUseCase,
        searchMessagesUseCase = searchMessagesUseCase,
        searchIntelligence = searchIntelligence,
      )
      return AppContainer(
        database = database,
        telegramGateway = telegramGateway,
        indexPrivateChatsUseCase = indexPrivateChatsUseCase,
        searchMessagesUseCase = searchMessagesUseCase,
        mcpServer = mcpServer,
      )
    }
  }
}
