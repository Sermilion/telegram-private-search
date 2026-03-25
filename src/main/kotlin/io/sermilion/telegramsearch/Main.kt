package io.sermilion.telegramsearch

import io.sermilion.telegramsearch.app.di.AppContainer
import io.sermilion.telegramsearch.presentation.search.SearchViewModel
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
  if (args.isEmpty()) {
    printUsage()
    return@runBlocking
  }

  val container = AppContainer.create()
  try {
    when (args.first()) {
      "index" -> runIndex(container, args.drop(1))
      "search" -> runSearch(container, args.drop(1))
      "mcp" -> container.mcpServer.run()
      else -> printUsage()
    }
  } finally {
    container.close()
  }
}

private suspend fun runIndex(container: AppContainer, args: List<String>) {
  val limitPerChat = args.flagValue("--limit-per-chat")?.toInt() ?: 1000
  val summary = container.indexPrivateChatsUseCase(limitPerChat)
  println("Indexed account: ${summary.account.displayName} (${summary.account.userId})")
  println("Chats indexed: ${summary.chatCount}")
  println("Messages stored: ${summary.messageCount}")
  println("Chunks stored: ${summary.chunkCount}")
}

private suspend fun runSearch(container: AppContainer, args: List<String>) {
  val query = args.firstOrNull()?.takeIf { it.isNotBlank() }
    ?: error("Search query is required")
  val limit = args.drop(1).flagValue("--limit")?.toInt() ?: 5
  val selfUserId = args.drop(1).flagValue("--self-user-id")?.toLong()
  val viewModel = SearchViewModel(container.searchMessagesUseCase)
  viewModel.search(query = query, limit = limit, selfUserId = selfUserId)
  val state = viewModel.state.value
  val intent = requireNotNull(state.intent)
  println("Topic: ${intent.topic.ifBlank { "-" }}")
  println("Activity: ${intent.activity.ifBlank { "-" }}")
  println("Speaker hint: ${intent.speakerHint}")
  println("Wants latest: ${intent.wantsLatest}")
  println()
  if (state.results.isEmpty()) {
    println("No results found")
    return
  }
  state.results.forEach { result ->
    println("${result.sentAt} | ${result.chatTitle} | ${result.senderName}")
    println("score=${"%.3f".format(result.combinedScore)} lexical=${"%.3f".format(result.lexicalScore)} semantic=${"%.3f".format(result.semanticScore)}")
    println(result.text)
    println()
  }
}

private fun List<String>.flagValue(flag: String): String? {
  val index = indexOf(flag)
  if (index == -1 || index == lastIndex) {
    return null
  }
  return get(index + 1)
}

private fun printUsage() {
  println("Usage:")
  println("  ./gradlew run --args='index --limit-per-chat 500'")
  println("  ./gradlew run --args='search \"find last message where he reported progress on Readian\" --limit 5'")
  println("  ./gradlew run --args='mcp'")
}
