package io.sermilion.telegramsearch.data.remote.telegram

import io.github.oshai.kotlinlogging.KotlinLogging
import io.sermilion.telegramsearch.app.config.TelegramConfig
import io.sermilion.telegramsearch.domain.model.IndexedMessage
import io.sermilion.telegramsearch.domain.model.TelegramAccount
import io.sermilion.telegramsearch.domain.repository.TelegramGateway
import it.tdlight.Log
import it.tdlight.client.APIToken
import it.tdlight.client.AuthenticationSupplier
import it.tdlight.client.SimpleTelegramClient
import it.tdlight.client.SimpleTelegramClientFactory
import it.tdlight.client.TDLibSettings
import it.tdlight.client.TelegramError
import it.tdlight.jni.TdApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant

class TdLightTelegramGateway(
  private val config: TelegramConfig,
) : TelegramGateway {
  private val logger = KotlinLogging.logger {}
  private var clientFactory: SimpleTelegramClientFactory? = null
  private var client: SimpleTelegramClient? = null
  private val userNameCache = mutableMapOf<Long, String>()

  override suspend fun connect(): TelegramAccount {
    client?.let { activeClient ->
      val me = activeClient.meAsync.await()
      return me.toAccount()
    }
    System.setProperty("org.slf4j.simpleLogger.log.it.tdlight", "warn")
    System.setProperty("org.slf4j.simpleLogger.log.it.tdlight.TDLight", "warn")
    Log.setVerbosityLevel(0)
    val sessionRoot = Paths.get(config.sessionDirectory)
    Files.createDirectories(sessionRoot)
    val settings = TDLibSettings.create(APIToken(config.apiId, config.apiHash)).apply {
      databaseDirectoryPath = sessionRoot.resolve("tdlight-data")
        downloadedFilesDirectoryPath = sessionRoot.resolve("downloads")
        isMessageDatabaseEnabled = true
        isChatInfoDatabaseEnabled = true
        isFileDatabaseEnabled = true
    }
    val factory = SimpleTelegramClientFactory()
    val builder = factory.builder(settings)
    val authenticationSupplier = when {
      config.phoneNumber.isNotBlank() -> AuthenticationSupplier.user(config.phoneNumber)
      config.useConsoleLogin -> AuthenticationSupplier.consoleLogin()
      else -> error("Set TELEGRAM_PHONE_NUMBER or enable TELEGRAM_USE_CONSOLE_LOGIN")
    }
    if (config.phoneNumber.isBlank() && config.useConsoleLogin) {
      println("Telegram login is interactive. Choose phone login when prompted below.")
    }
    val createdClient = builder.build(authenticationSupplier)
    clientFactory = factory
    client = createdClient
    val me = createdClient.meAsync.await()
    userNameCache[me.id] = me.displayName()
    return me.toAccount()
  }

  override suspend fun loadPrivateMessages(limitPerChat: Int): List<IndexedMessage> {
    val activeClient = client ?: error("Telegram client is not connected")
    val me = activeClient.meAsync.await()
    userNameCache[me.id] = me.displayName()
    val messages = mutableListOf<IndexedMessage>()
    loadPrivateChatIds(activeClient, me.id).forEach { chatId ->
      val chat: TdApi.Chat = activeClient.send(TdApi.GetChat(chatId)).await()
      val privateType = chat.type as? TdApi.ChatTypePrivate ?: return@forEach
      val chatTitle = normalizePrivateChatTitle(chat.title, privateType.userId, me.id)
      val history = loadChatHistory(
        activeClient = activeClient,
        chatId = chat.id,
        latestMessageId = chat.lastMessage?.id ?: 0L,
        limitPerChat = limitPerChat,
      )
      history.forEach { message ->
        val text = extractText(message.content)
        if (text.isBlank()) {
          return@forEach
        }
        val sender = resolveSender(activeClient, message, me.id, chatTitle)
        messages += IndexedMessage(
          chatId = chat.id,
          messageId = message.id,
          senderId = sender.first,
          senderName = sender.second,
          sentAt = Instant.ofEpochSecond(message.date.toLong()),
          text = text,
          chatTitle = chatTitle,
          replyToMessageId = replyToMessageId(message),
          isOutgoing = message.isOutgoing,
        )
      }
    }
    logger.info { "Loaded ${messages.size} private messages from Telegram" }
    return messages
  }

  private suspend fun loadPrivateChatIds(activeClient: SimpleTelegramClient, selfUserId: Long): List<Long> {
    val chatIds = linkedSetOf<Long>()
    listOf(TdApi.ChatListMain(), TdApi.ChatListArchive()).forEach { chatList ->
      loadChatList(activeClient, chatList)
      chatIds += loadChatIds(activeClient, chatList)
    }
    chatIds += loadPrivateChatIdsFromContacts(activeClient)
    chatIds += loadSelfChatId(activeClient, selfUserId)
    return chatIds.toList()
  }

  private suspend fun loadPrivateChatIdsFromContacts(activeClient: SimpleTelegramClient): List<Long> {
    val contactUsers = activeClient.send(TdApi.GetContacts()).await()
    val chatIds = linkedSetOf<Long>()
    contactUsers.userIds?.forEach { userId ->
      val privateChat = activeClient.send(TdApi.CreatePrivateChat(userId, false)).await()
      val privateType = privateChat.type as? TdApi.ChatTypePrivate ?: return@forEach
      if (privateType.userId == userId) {
        chatIds += privateChat.id
      }
    }
    return chatIds.toList()
  }

  private suspend fun loadSelfChatId(activeClient: SimpleTelegramClient, selfUserId: Long): List<Long> {
    val privateChat = activeClient.send(TdApi.CreatePrivateChat(selfUserId, false)).await()
    val privateType = privateChat.type as? TdApi.ChatTypePrivate ?: return emptyList()
    return if (privateType.userId == selfUserId) listOf(privateChat.id) else emptyList()
  }

  private suspend fun loadChatIds(
    activeClient: SimpleTelegramClient,
    chatList: TdApi.ChatList,
  ): List<Long> {
    val initialChats: TdApi.Chats = activeClient.send(TdApi.GetChats(chatList, 100)).await()
    val initialChatIds = initialChats.chatIds?.toList().orEmpty()
    val totalCount = maxOf(initialChats.totalCount, initialChatIds.size)
    if (totalCount <= initialChatIds.size) {
      return initialChatIds
    }
    val allChats: TdApi.Chats = activeClient.send(TdApi.GetChats(chatList, totalCount)).await()
    return allChats.chatIds?.toList().orEmpty()
  }

  private suspend fun loadChatList(
    activeClient: SimpleTelegramClient,
    chatList: TdApi.ChatList,
  ) {
    var knownCount = loadChatIds(activeClient, chatList).size
    while (true) {
      try {
        activeClient.send(TdApi.LoadChats(chatList, 100)).await()
        knownCount = awaitLoadedChatCount(activeClient, chatList, knownCount)
      } catch (error: TelegramError) {
        if (error.errorCode == 404) {
          awaitLoadedChatCount(activeClient, chatList, knownCount)
          return
        }
        val chatListName = chatList.javaClass.simpleName.ifBlank { "chat list" }
        throw IllegalStateException(
          "Failed to load $chatListName: ${error.errorCode} ${error.errorMessage}",
          error,
        )
      }
    }
  }

  private suspend fun awaitLoadedChatCount(
    activeClient: SimpleTelegramClient,
    chatList: TdApi.ChatList,
    currentCount: Int,
  ): Int {
    var observedCount = currentCount
    repeat(20) {
      delay(100)
      val updatedCount = loadChatIds(activeClient, chatList).size
      if (updatedCount > observedCount) {
        observedCount = updatedCount
      } else if (observedCount > currentCount) {
        return observedCount
      }
    }
    return observedCount
  }

  override suspend fun disconnect() {
    client?.close()
    client = null
    clientFactory?.close()
    clientFactory = null
    userNameCache.clear()
  }

  private suspend fun loadChatHistory(
    activeClient: SimpleTelegramClient,
    chatId: Long,
    latestMessageId: Long,
    limitPerChat: Int,
  ): List<TdApi.Message> {
    val messages = mutableListOf<TdApi.Message>()
    val seenIds = mutableSetOf<Long>()
    var fromMessageId = latestMessageId
    while (messages.size < limitPerChat) {
      val pageLimit = minOf(100, limitPerChat - messages.size)
      val page = activeClient.send(TdApi.GetChatHistory(chatId, fromMessageId, 0, pageLimit, false)).await()
      val pageMessages = page.messages.orEmpty().toList()
      if (pageMessages.isEmpty()) {
        break
      }
      pageMessages.forEach { message ->
        if (seenIds.add(message.id)) {
          messages += message
        }
      }
      val oldestMessage = pageMessages.last().id
      if (oldestMessage == 0L || oldestMessage == fromMessageId) {
        break
      }
      fromMessageId = oldestMessage
    }
    return messages.sortedBy { it.date }
  }

  private suspend fun resolveSender(
    activeClient: SimpleTelegramClient,
    message: TdApi.Message,
    selfUserId: Long,
    chatTitle: String,
  ): Pair<Long?, String> {
    val sender = message.senderId
    val senderUser = sender as? TdApi.MessageSenderUser
    if (senderUser != null) {
      val senderName = userNameCache.getOrPut(senderUser.userId) {
        val user = runCatching { activeClient.send(TdApi.GetUser(senderUser.userId)).get() }.getOrNull()
        user?.displayName().orEmpty().ifBlank { chatTitle }
      }
      return senderUser.userId to senderName
    }
    if (message.isOutgoing) {
      return selfUserId to userNameCache[selfUserId].orEmpty().ifBlank { "Me" }
    }
    val authorSignature = message.authorSignature.takeIf { it.isNotBlank() }.orEmpty()
    return null to authorSignature.ifBlank { chatTitle }
  }

  private fun extractText(content: TdApi.MessageContent): String {
    val messageText = content as? TdApi.MessageText ?: return ""
    return messageText.text.text.trim()
  }

  private fun replyToMessageId(message: TdApi.Message): Long? {
    val replyTo = message.replyTo as? TdApi.MessageReplyToMessage ?: return null
    return replyTo.messageId.takeIf { it != 0L }
  }

  private fun TdApi.User.toAccount(): TelegramAccount = TelegramAccount(
    userId = id,
    displayName = displayName(),
  )

  private fun TdApi.User.displayName(): String {
    return listOf(firstName.orEmpty(), lastName.orEmpty())
      .filter { it.isNotBlank() }
      .joinToString(separator = " ")
      .ifBlank { phoneNumber.orEmpty().ifBlank { id.toString() } }
  }
}

internal fun normalizePrivateChatTitle(chatTitle: String, participantUserId: Long, selfUserId: Long): String {
  return if (participantUserId == selfUserId) {
    SAVED_MESSAGES_TITLE
  } else {
    chatTitle
  }
}

internal const val SAVED_MESSAGES_TITLE = "Saved Messages"
