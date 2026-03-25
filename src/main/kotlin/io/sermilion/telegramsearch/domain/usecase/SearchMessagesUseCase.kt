package io.sermilion.telegramsearch.domain.usecase

import io.sermilion.telegramsearch.domain.model.IndexedMessage
import io.sermilion.telegramsearch.domain.model.SearchIntent
import io.sermilion.telegramsearch.domain.model.SearchResponse
import io.sermilion.telegramsearch.domain.model.SearchResult
import io.sermilion.telegramsearch.domain.model.SpeakerHint
import io.sermilion.telegramsearch.domain.model.StoredChunk
import io.sermilion.telegramsearch.domain.repository.MessageRepository
import io.sermilion.telegramsearch.domain.repository.SearchIntelligence
import java.time.Clock
import java.time.Duration

class SearchMessagesUseCase(
  private val messageRepository: MessageRepository,
  private val searchIntelligence: SearchIntelligence,
  private val clock: Clock = Clock.systemUTC(),
) {
  suspend operator fun invoke(
    query: String,
    limit: Int,
    selfUserId: Long? = null,
    contextBeforeMessages: Int = DEFAULT_CONTEXT_MESSAGES,
    contextAfterMessages: Int = DEFAULT_CONTEXT_MESSAGES,
  ): SearchResponse {
    val safeContextBeforeMessages = contextBeforeMessages.coerceAtLeast(0)
    val safeContextAfterMessages = contextAfterMessages.coerceAtLeast(0)
    val intent = searchIntelligence.analyzeQuery(query)
    val lexicalQuery = buildLexicalQuery(intent)
    val candidates = linkedMapOf<String, StoredChunk>()
    if (lexicalQuery.isNotBlank()) {
      messageRepository.searchLexical(lexicalQuery, maxOf(limit * 6, 24)).forEach { candidate ->
        candidates.putIfAbsent(candidate.chunkKey, candidate)
      }
    }
    messageRepository.recentChunks(maxOf(limit * 10, 50)).forEach { candidate ->
      candidates.putIfAbsent(candidate.chunkKey, candidate)
    }
    val queryEmbedding = searchIntelligence.embedTexts(listOf(query)).firstOrNull().orEmpty()
    val effectiveSelfUserId = selfUserId ?: messageRepository.getStoredAccount()?.userId
    val rankedCandidates = candidates.values
      .asSequence()
      .filter { candidate -> matchesSpeaker(intent, candidate.senderId, effectiveSelfUserId) }
      .map { candidate -> toResult(candidate, intent, queryEmbedding) }
      .sortedWith(
        if (intent.wantsLatest) {
          compareByDescending<SearchResult> { it.anchorSentAt }.thenByDescending { it.combinedScore }
        } else {
          compareByDescending<SearchResult> { it.combinedScore }.thenByDescending { it.anchorSentAt }
        }
      )
      .toList()
    val ranked = mutableListOf<SearchResult>()
    rankedCandidates.forEach { candidate ->
      if (ranked.size >= limit) {
        return@forEach
      }
      val messages = messageRepository.conversationSlice(
        chatId = candidate.chatId,
        anchorMessageIds = candidate.anchorMessageIds,
        beforeCount = safeContextBeforeMessages,
        afterCount = safeContextAfterMessages,
      )
      if (messages.isEmpty()) {
        return@forEach
      }
      if (overlapsExistingSlice(ranked, candidate.chatId, messages)) {
        return@forEach
      }
      ranked += candidate.copy(
        text = messages.formatResultText().ifBlank { candidate.anchorText },
        messages = messages,
        contextExpanded = messages.any { message -> candidate.anchorMessageIds.none { it == message.messageId } },
      )
    }
    return SearchResponse(
      intent = intent,
      results = ranked,
      selfUserId = effectiveSelfUserId,
      contextBeforeMessages = safeContextBeforeMessages,
      contextAfterMessages = safeContextAfterMessages,
    )
  }

  private fun overlapsExistingSlice(
    existingResults: List<SearchResult>,
    chatId: Long,
    messages: List<IndexedMessage>,
  ): Boolean {
    if (messages.isEmpty()) {
      return false
    }
    val messageIds = messages.map { it.messageId }.toSet()
    return existingResults.any { result ->
      result.chatId == chatId && result.messages.any { message -> messageIds.contains(message.messageId) }
    }
  }

  private fun buildLexicalQuery(intent: SearchIntent): String {
    return buildList {
      addAll(intent.keywords)
      addAll(intent.topic.lowercase().split(Regex("\\s+")))
      addAll(intent.activity.lowercase().split(Regex("\\s+")))
    }
      .map { it.trim() }
      .filter { it.length > 2 }
      .distinct()
      .joinToString(separator = " OR ")
  }

  private fun matchesSpeaker(intent: SearchIntent, senderId: Long?, selfUserId: Long?): Boolean {
    if (intent.speakerHint == SpeakerHint.UNKNOWN || senderId == null || selfUserId == null) {
      return true
    }
    return when (intent.speakerHint) {
      SpeakerHint.SELF -> senderId == selfUserId
      SpeakerHint.OTHER -> senderId != selfUserId
      SpeakerHint.UNKNOWN -> true
    }
  }

  private fun toResult(candidate: StoredChunk, intent: SearchIntent, queryEmbedding: List<Double>): SearchResult {
    val lexicalScore = keywordOverlap(candidate, intent)
    val semanticScore = cosineSimilarity(queryEmbedding, candidate.embedding)
    val recencyScore = recencyScore(candidate)
    val combinedScore = (lexicalScore * 0.45) + (semanticScore * 0.45) + (recencyScore * 0.10)
    return SearchResult(
      chunkKey = candidate.chunkKey,
      chatId = candidate.chatId,
      anchorMessageIds = candidate.messageIds,
      anchorSenderName = candidate.senderName,
      anchorSentAt = candidate.sentAt,
      chatTitle = candidate.chatTitle,
      text = candidate.text,
      anchorText = candidate.text,
      messages = emptyList(),
      contextExpanded = false,
      lexicalScore = lexicalScore,
      semanticScore = semanticScore,
      combinedScore = combinedScore,
    )
  }

  private fun keywordOverlap(candidate: StoredChunk, intent: SearchIntent): Double {
    val terms = buildList {
      addAll(intent.keywords)
      addAll(intent.topic.split(Regex("\\s+")))
      addAll(intent.activity.split(Regex("\\s+")))
    }
      .map { it.lowercase() }
      .filter { it.length > 2 }
      .distinct()
    if (terms.isEmpty()) {
      return 0.0
    }
    val haystack = buildString {
      append(candidate.text.lowercase())
      append(' ')
      append(candidate.chatTitle.lowercase())
      append(' ')
      append(candidate.senderName.lowercase())
    }
    val hits = terms.count { haystack.contains(it) }
    return hits.toDouble() / terms.size.toDouble()
  }

  private fun cosineSimilarity(left: List<Double>, right: List<Double>): Double {
    if (left.isEmpty() || right.isEmpty() || left.size != right.size) {
      return 0.0
    }
    val numerator = left.zip(right).sumOf { (a, b) -> a * b }
    val leftNorm = kotlin.math.sqrt(left.sumOf { it * it })
    val rightNorm = kotlin.math.sqrt(right.sumOf { it * it })
    if (leftNorm == 0.0 || rightNorm == 0.0) {
      return 0.0
    }
    return numerator / (leftNorm * rightNorm)
  }

  private fun recencyScore(candidate: StoredChunk): Double {
    val age = Duration.between(candidate.sentAt, clock.instant()).toDays().toDouble()
    return (1.0 - (age / 180.0)).coerceIn(0.0, 1.0)
  }

  private fun List<IndexedMessage>.formatResultText(): String {
    return joinToString(separator = "\n\n") { message ->
      "${message.sentAt} | ${message.senderName}\n${message.text}"
    }
  }

  private companion object {
    const val DEFAULT_CONTEXT_MESSAGES = 12
  }
}
