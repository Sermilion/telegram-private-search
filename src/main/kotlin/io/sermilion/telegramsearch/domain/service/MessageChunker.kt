package io.sermilion.telegramsearch.domain.service

import io.sermilion.telegramsearch.domain.model.IndexedMessage
import io.sermilion.telegramsearch.domain.model.MessageChunk

class MessageChunker(
  private val maxMessagesPerChunk: Int = 5,
) {
  fun build(messages: List<IndexedMessage>): List<MessageChunk> {
    if (messages.isEmpty()) {
      return emptyList()
    }
    val sortedMessages = messages.sortedBy { it.sentAt }
    val chunks = mutableListOf<MessageChunk>()
    var current = mutableListOf<IndexedMessage>()
    sortedMessages.forEach { message ->
      if (current.isEmpty()) {
        current.add(message)
      } else {
        val previous = current.last()
        val sameSender = previous.senderId == message.senderId
        if (sameSender && current.size < maxMessagesPerChunk) {
          current.add(message)
        } else {
          chunks += current.toChunk()
          current = mutableListOf(message)
        }
      }
    }
    if (current.isNotEmpty()) {
      chunks += current.toChunk()
    }
    return chunks
  }

  private fun List<IndexedMessage>.toChunk(): MessageChunk {
    val first = first()
    val last = last()
    val messageIds = map { it.messageId }
    return MessageChunk(
      chunkKey = "${first.chatId}:${messageIds.first()}:${messageIds.last()}",
      chatId = first.chatId,
      messageIds = messageIds,
      senderId = first.senderId,
      senderName = first.senderName,
      sentAt = last.sentAt,
      chatTitle = first.chatTitle,
      text = joinToString(separator = "\n") { it.text },
    )
  }
}
