package io.sermilion.telegramsearch.domain.repository

import io.sermilion.telegramsearch.domain.model.IndexedMessage
import io.sermilion.telegramsearch.domain.model.TelegramAccount

interface TelegramGateway {
  suspend fun connect(): TelegramAccount
  suspend fun loadPrivateMessages(limitPerChat: Int): List<IndexedMessage>
  suspend fun disconnect()
}
