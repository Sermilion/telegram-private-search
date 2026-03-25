package io.sermilion.telegramsearch.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import io.sermilion.telegramsearch.data.local.entity.MetadataEntity

@Dao
interface MetadataDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entries: List<MetadataEntity>)

  @Query("SELECT value FROM metadata WHERE `key` = :key LIMIT 1")
  suspend fun valueForKey(key: String): String?
}
