package io.sermilion.telegramsearch.data.repository

import io.sermilion.telegramsearch.data.local.dao.MessageChunkDao
import io.sermilion.telegramsearch.data.local.dao.MessageDao
import io.sermilion.telegramsearch.data.local.dao.MetadataDao
import io.sermilion.telegramsearch.data.local.entity.MessageChunkEntity
import io.sermilion.telegramsearch.data.local.entity.MessageChunkFtsEntity
import io.sermilion.telegramsearch.data.local.entity.MessageEntity
import io.sermilion.telegramsearch.data.local.entity.MetadataEntity
import io.sermilion.telegramsearch.data.local.model.StoredChunkDataModel
import io.sermilion.telegramsearch.domain.model.IndexedMessage
import io.sermilion.telegramsearch.domain.model.MessageChunk
import io.sermilion.telegramsearch.domain.model.StoredChunk
import io.sermilion.telegramsearch.domain.model.TelegramAccount
import io.sermilion.telegramsearch.domain.repository.MessageRepository
import io.sermilion.telegramsearch.domain.service.JsonSupport
import java.time.Instant

class RoomMessageRepository(
  private val messageDao: MessageDao,
  private val messageChunkDao: MessageChunkDao,
  private val metadataDao: MetadataDao,
) : MessageRepository {
  override suspend fun upsertMessages(messages: List<IndexedMessage>): Int {
    if (messages.isEmpty()) {
      return 0
    }
    messageDao.upsert(messages.map { message ->
      MessageEntity(
        messageKey = "${message.chatId}:${message.messageId}",
        chatId = message.chatId,
        messageId = message.messageId,
        senderId = message.senderId,
        senderName = message.senderName,
        sentAtEpochSeconds = message.sentAt.epochSecond,
        text = message.text,
        chatTitle = message.chatTitle,
        replyToMessageId = message.replyToMessageId,
        isOutgoing = message.isOutgoing,
      )
    })
    return messages.size
  }

  override suspend fun replaceChunks(chatId: Long, chunks: List<MessageChunk>): Int {
    return messageChunkDao.replaceChunksForChat(
      chatId = chatId,
      chunks = chunks.map { chunk ->
        MessageChunkEntity(
          chunkKey = chunk.chunkKey,
          chatId = chunk.chatId,
          messageIdsJson = JsonSupport.encodeLongList(chunk.messageIds),
          senderId = chunk.senderId,
          senderName = chunk.senderName,
          sentAtEpochSeconds = chunk.sentAt.epochSecond,
          chatTitle = chunk.chatTitle,
          text = chunk.text,
          embeddingJson = JsonSupport.encodeDoubleList(chunk.embedding),
        )
      },
      ftsRows = chunks.map { chunk ->
        MessageChunkFtsEntity(
          chunkKey = chunk.chunkKey,
          senderName = chunk.senderName,
          chatTitle = chunk.chatTitle,
          text = chunk.text,
        )
      },
    )
  }

  override suspend fun searchLexical(query: String, limit: Int): List<StoredChunk> =
    messageChunkDao.searchLexical(query, limit).map { it.toDomain() }

  override suspend fun recentChunks(limit: Int): List<StoredChunk> =
    messageChunkDao.recentChunks(limit).map { it.toDomain() }

  override suspend fun conversationSlice(
    chatId: Long,
    anchorMessageIds: List<Long>,
    beforeCount: Int,
    afterCount: Int,
  ): List<IndexedMessage> {
    val firstMessageId = anchorMessageIds.minOrNull() ?: return emptyList()
    val lastMessageId = anchorMessageIds.maxOrNull() ?: return emptyList()
    val beforeMessages = if (beforeCount > 0) {
      messageDao.messagesBefore(chatId, firstMessageId, beforeCount).asReversed()
    } else {
      emptyList()
    }
    val anchorMessages = messageDao.messagesByIds(chatId, anchorMessageIds)
    val afterMessages = if (afterCount > 0) {
      messageDao.messagesAfter(chatId, lastMessageId, afterCount)
    } else {
      emptyList()
    }
    return (beforeMessages + anchorMessages + afterMessages)
      .distinctBy { it.messageId }
      .map { it.toDomain() }
  }

  override suspend fun storeAccount(account: TelegramAccount) {
    metadataDao.upsert(
      listOf(
        MetadataEntity(key = "account_user_id", value = account.userId.toString()),
        MetadataEntity(key = "account_display_name", value = account.displayName),
      )
    )
  }

  override suspend fun getStoredAccount(): TelegramAccount? {
    val userId = metadataDao.valueForKey("account_user_id")?.toLongOrNull() ?: return null
    val displayName = metadataDao.valueForKey("account_display_name").orEmpty()
    return TelegramAccount(userId = userId, displayName = displayName)
  }

  private fun StoredChunkDataModel.toDomain(): StoredChunk =
    StoredChunk(
      chunkKey = chunkKey,
      chatId = chatId,
      messageIds = JsonSupport.decodeLongList(messageIdsJson),
      senderId = senderId,
      senderName = senderName,
      sentAt = Instant.ofEpochSecond(sentAtEpochSeconds),
      chatTitle = chatTitle,
      text = text,
      embedding = JsonSupport.decodeDoubleList(embeddingJson),
    )

  private fun MessageEntity.toDomain(): IndexedMessage =
    IndexedMessage(
      chatId = chatId,
      messageId = messageId,
      senderId = senderId,
      senderName = senderName,
      sentAt = Instant.ofEpochSecond(sentAtEpochSeconds),
      text = text,
      chatTitle = chatTitle,
      replyToMessageId = replyToMessageId,
      isOutgoing = isOutgoing,
    )
}
