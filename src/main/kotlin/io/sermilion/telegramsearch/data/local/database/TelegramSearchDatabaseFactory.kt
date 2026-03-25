package io.sermilion.telegramsearch.data.local.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.sermilion.telegramsearch.app.config.AppConfig
import kotlinx.coroutines.Dispatchers
import java.nio.file.Files
import java.nio.file.Paths

object TelegramSearchDatabaseFactory {
  fun create(config: AppConfig): TelegramSearchDatabase {
    val databasePath = Paths.get(config.databasePath)
    databasePath.parent?.let { Files.createDirectories(it) }
    return Room.databaseBuilder<TelegramSearchDatabase>(name = databasePath.toString())
      .setDriver(BundledSQLiteDriver())
      .setQueryCoroutineContext(Dispatchers.IO)
      .build()
  }
}
