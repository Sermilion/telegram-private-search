package io.sermilion.telegramsearch.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import io.sermilion.telegramsearch.data.local.entity.MessageChunkEntity
import io.sermilion.telegramsearch.data.local.entity.MessageChunkFtsEntity
import io.sermilion.telegramsearch.data.local.model.StoredChunkDataModel

@Dao
abstract class MessageChunkDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  protected abstract suspend fun insertChunks(chunks: List<MessageChunkEntity>): List<Long>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  protected abstract suspend fun insertFtsRows(rows: List<MessageChunkFtsEntity>)

  @Query("SELECT chunk_key FROM message_chunks WHERE chat_id = :chatId")
  protected abstract suspend fun chunkKeysForChat(chatId: Long): List<String>

  @Query("DELETE FROM message_chunk_fts WHERE chunk_key IN (:chunkKeys)")
  protected abstract suspend fun deleteFtsRows(chunkKeys: List<String>)

  @Query("DELETE FROM message_chunks WHERE chat_id = :chatId")
  protected abstract suspend fun deleteChunksForChat(chatId: Long)

  @Transaction
  open suspend fun replaceChunksForChat(
    chatId: Long,
    chunks: List<MessageChunkEntity>,
    ftsRows: List<MessageChunkFtsEntity>,
  ): Int {
    val existingKeys = chunkKeysForChat(chatId)
    if (existingKeys.isNotEmpty()) {
      deleteFtsRows(existingKeys)
    }
    deleteChunksForChat(chatId)
    if (chunks.isNotEmpty()) {
      insertChunks(chunks)
      insertFtsRows(ftsRows)
    }
    return chunks.size
  }

  @Query(
    """
    SELECT 
      message_chunks.chunk_key,
      message_chunks.chat_id,
      message_chunks.message_ids_json,
      message_chunks.sender_id,
      message_chunks.sender_name,
      message_chunks.sent_at_epoch_seconds,
      message_chunks.chat_title,
      message_chunks.text,
      message_chunks.embedding_json
    FROM message_chunk_fts
    JOIN message_chunks ON message_chunk_fts.chunk_key = message_chunks.chunk_key
    WHERE message_chunk_fts MATCH :query
    ORDER BY message_chunks.sent_at_epoch_seconds DESC
    LIMIT :limit
    """
  )
  abstract suspend fun searchLexical(query: String, limit: Int): List<StoredChunkDataModel>

  @Query(
    """
    SELECT 
      chunk_key,
      chat_id,
      message_ids_json,
      sender_id,
      sender_name,
      sent_at_epoch_seconds,
      chat_title,
      text,
      embedding_json
    FROM message_chunks
    ORDER BY sent_at_epoch_seconds DESC
    LIMIT :limit
    """
  )
  abstract suspend fun recentChunks(limit: Int): List<StoredChunkDataModel>
}
