package io.sermilion.telegramsearch.data.local.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "metadata")
data class MetadataEntity(
  @PrimaryKey
  val key: String,
  val value: String,
)
