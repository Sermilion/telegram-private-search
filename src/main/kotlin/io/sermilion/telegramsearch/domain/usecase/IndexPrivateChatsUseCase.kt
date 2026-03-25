package io.sermilion.telegramsearch.domain.usecase

import io.sermilion.telegramsearch.domain.model.IndexSummary
import io.sermilion.telegramsearch.domain.model.MessageChunk
import io.sermilion.telegramsearch.domain.repository.MessageRepository
import io.sermilion.telegramsearch.domain.repository.SearchIntelligence
import io.sermilion.telegramsearch.domain.repository.TelegramGateway
import io.sermilion.telegramsearch.domain.service.MessageChunker

class IndexPrivateChatsUseCase(
  private val telegramGateway: TelegramGateway,
  private val messageRepository: MessageRepository,
  private val searchIntelligence: SearchIntelligence,
  private val messageChunker: MessageChunker,
) {
  suspend operator fun invoke(limitPerChat: Int): IndexSummary {
    val account = telegramGateway.connect()
    try {
      val messages = telegramGateway.loadPrivateMessages(limitPerChat)
      messageRepository.storeAccount(account)
      val storedMessages = messageRepository.upsertMessages(messages)
      var storedChunks = 0
      val chatGroups = messages.groupBy { it.chatId }
      chatGroups.forEach { (chatId, chatMessages) ->
        val chunks = messageChunker.build(chatMessages)
        val embeddings = searchIntelligence.embedTexts(chunks.map { it.text })
        val enrichedChunks = chunks.mapIndexed { index, chunk ->
          chunk.copy(embedding = embeddings.getOrElse(index) { emptyList() })
        }
        storedChunks += messageRepository.replaceChunks(chatId, enrichedChunks)
      }
      return IndexSummary(
        account = account,
        messageCount = storedMessages,
        chunkCount = storedChunks,
        chatCount = chatGroups.size,
      )
    } finally {
      telegramGateway.disconnect()
    }
  }
}
