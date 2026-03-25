package io.sermilion.telegramsearch.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import io.sermilion.telegramsearch.data.local.entity.MessageEntity

@Dao
interface MessageDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(messages: List<MessageEntity>): List<Long>

  @Query(
    """
    SELECT *
    FROM messages
    WHERE chat_id = :chatId
      AND message_id IN (:messageIds)
    ORDER BY sent_at_epoch_seconds ASC, message_id ASC
    """
  )
  suspend fun messagesByIds(chatId: Long, messageIds: List<Long>): List<MessageEntity>

  @Query(
    """
    SELECT *
    FROM messages
    WHERE chat_id = :chatId
      AND message_id < :beforeMessageId
    ORDER BY message_id DESC
    LIMIT :limit
    """
  )
  suspend fun messagesBefore(chatId: Long, beforeMessageId: Long, limit: Int): List<MessageEntity>

  @Query(
    """
    SELECT *
    FROM messages
    WHERE chat_id = :chatId
      AND message_id > :afterMessageId
    ORDER BY message_id ASC
    LIMIT :limit
    """
  )
  suspend fun messagesAfter(chatId: Long, afterMessageId: Long, limit: Int): List<MessageEntity>
}
