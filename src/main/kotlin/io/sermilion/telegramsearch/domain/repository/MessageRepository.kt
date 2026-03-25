package io.sermilion.telegramsearch.domain.repository

import io.sermilion.telegramsearch.domain.model.IndexedMessage
import io.sermilion.telegramsearch.domain.model.MessageChunk
import io.sermilion.telegramsearch.domain.model.StoredChunk
import io.sermilion.telegramsearch.domain.model.TelegramAccount

interface MessageRepository {
  suspend fun upsertMessages(messages: List<IndexedMessage>): Int
  suspend fun replaceChunks(chatId: Long, chunks: List<MessageChunk>): Int
  suspend fun searchLexical(query: String, limit: Int): List<StoredChunk>
  suspend fun recentChunks(limit: Int): List<StoredChunk>
  suspend fun storeAccount(account: TelegramAccount)
  suspend fun getStoredAccount(): TelegramAccount?
}
