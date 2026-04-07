package io.sermilion.telegramsearch.data.remote.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

class TdLightTelegramGatewayTest {
  @Test
  fun `normalizePrivateChatTitle maps self chat to saved messages`() {
    assertEquals(
      SAVED_MESSAGES_TITLE,
      normalizePrivateChatTitle(chatTitle = "Sermilion", participantUserId = 42L, selfUserId = 42L),
    )
  }

  @Test
  fun `normalizePrivateChatTitle keeps normal private chat titles`() {
    assertEquals(
      "Anzor",
      normalizePrivateChatTitle(chatTitle = "Anzor", participantUserId = 7L, selfUserId = 42L),
    )
  }
}
