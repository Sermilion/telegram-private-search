package io.sermilion.telegramsearch.data.repository

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.sermilion.telegramsearch.data.local.database.TelegramSearchDatabase
import io.sermilion.telegramsearch.domain.model.IndexedMessage
import kotlinx.coroutines.Dispatchers
import java.nio.file.Files
import java.time.Instant

class RoomMessageRepositoryTest : FunSpec({
  test("conversationSlice keeps only exact anchor messages before adding context") {
    val tempDirectory = Files.createTempDirectory("telegram-search-test").toFile()
    val databaseFile = tempDirectory.resolve("telegram-search.db")
    val database = Room.databaseBuilder<TelegramSearchDatabase>(name = databaseFile.absolutePath)
      .setDriver(BundledSQLiteDriver())
      .setQueryCoroutineContext(Dispatchers.IO)
      .build()
    try {
      val repository = RoomMessageRepository(
        messageDao = database.messageDao(),
        messageChunkDao = database.messageChunkDao(),
        metadataDao = database.metadataDao(),
      )
      repository.upsertMessages(
        listOf(
          storedMessage(messageId = 9, sentAt = "2026-03-20T09:59:00Z", text = "Before"),
          storedMessage(messageId = 10, sentAt = "2026-03-20T10:00:00Z", text = "Anchor A"),
          storedMessage(messageId = 11, sentAt = "2026-03-20T10:01:00Z", text = "Not an anchor"),
          storedMessage(messageId = 15, sentAt = "2026-03-20T10:02:00Z", text = "Anchor B"),
          storedMessage(messageId = 20, sentAt = "2026-03-20T10:03:00Z", text = "Still not an anchor"),
          storedMessage(messageId = 50, sentAt = "2026-03-20T10:04:00Z", text = "Anchor C"),
          storedMessage(messageId = 51, sentAt = "2026-03-20T10:05:00Z", text = "After"),
        )
      )

      val slice = repository.conversationSlice(
        chatId = 100,
        anchorMessageIds = listOf(10, 15, 50),
        beforeCount = 1,
        afterCount = 1,
      )

      slice.map { it.messageId } shouldBe listOf(9L, 10L, 15L, 50L, 51L)
    } finally {
      database.close()
      tempDirectory.deleteRecursively()
    }
  }
})

private fun storedMessage(messageId: Long, sentAt: String, text: String): IndexedMessage = IndexedMessage(
  chatId = 100,
  messageId = messageId,
  senderId = 22,
  senderName = "Alex",
  sentAt = Instant.parse(sentAt),
  text = text,
  chatTitle = "Alex",
  replyToMessageId = null,
  isOutgoing = false,
)
