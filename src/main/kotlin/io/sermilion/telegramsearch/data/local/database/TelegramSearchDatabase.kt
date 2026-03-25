package io.sermilion.telegramsearch.data.local.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import io.sermilion.telegramsearch.data.local.dao.MessageChunkDao
import io.sermilion.telegramsearch.data.local.dao.MessageDao
import io.sermilion.telegramsearch.data.local.dao.MetadataDao
import io.sermilion.telegramsearch.data.local.entity.MessageChunkEntity
import io.sermilion.telegramsearch.data.local.entity.MessageChunkFtsEntity
import io.sermilion.telegramsearch.data.local.entity.MessageEntity
import io.sermilion.telegramsearch.data.local.entity.MetadataEntity

@Database(
  entities = [
    MessageEntity::class,
    MessageChunkEntity::class,
    MessageChunkFtsEntity::class,
    MetadataEntity::class,
  ],
  version = 1,
  exportSchema = true,
)
abstract class TelegramSearchDatabase : RoomDatabase() {
  abstract fun messageDao(): MessageDao
  abstract fun messageChunkDao(): MessageChunkDao
  abstract fun metadataDao(): MetadataDao
}
