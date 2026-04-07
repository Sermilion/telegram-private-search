package io.sermilion.telegramsearch.adapter.mcp

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant

internal class LatestMessageRefreshGate(
  private val clock: Clock = Clock.systemUTC(),
  private val freshnessWindow: Duration = DEFAULT_FRESHNESS_WINDOW,
) {
  private val mutex = Mutex()
  private var lastRefreshAt: Instant? = null

  suspend fun refreshIfStale(refresh: suspend () -> Unit) {
    mutex.withLock {
      if (isFresh()) {
        return
      }
      refresh()
      lastRefreshAt = clock.instant()
    }
  }

  private fun isFresh(): Boolean {
    val previousRefreshAt = lastRefreshAt ?: return false
    return Duration.between(previousRefreshAt, clock.instant()) < freshnessWindow
  }

  companion object {
    val DEFAULT_FRESHNESS_WINDOW: Duration = Duration.ofSeconds(30)
  }
}
