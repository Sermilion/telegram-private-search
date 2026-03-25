package io.sermilion.telegramsearch.domain.service

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object JsonSupport {
  val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    prettyPrint = true
  }

  fun encodeLongList(values: List<Long>): String = json.encodeToString(ListSerializer(Long.serializer()), values)

  fun decodeLongList(value: String): List<Long> = json.decodeFromString(ListSerializer(Long.serializer()), value)

  fun encodeDoubleList(values: List<Double>): String = json.encodeToString(ListSerializer(Double.serializer()), values)

  fun decodeDoubleList(value: String): List<Double> = json.decodeFromString(ListSerializer(Double.serializer()), value)
}
