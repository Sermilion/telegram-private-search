# telegram-private-search

A Kotlin/JVM project for indexing your private Telegram conversations and searching them with hybrid full-text plus semantic retrieval.

## Stack

- Kotlin/JVM
- Clean architecture with MVVM-style presentation state
- Room 3 with bundled SQLite
- TDLight for Telegram user-account access
- OpenAI-compatible API for query understanding and embeddings
- MCP server over stdio

## Setup

1. Copy `.env.example` to `.env` and fill in your Telegram API credentials from `https://my.telegram.org`.
2. Install OpenSSL 3 on macOS if it is not already installed.
3. Run `./gradlew test`.
4. Optionally set `TELEGRAM_PHONE_NUMBER` in `.env` to skip the first login-mode prompt.
5. Run `./gradlew run --console=plain --args='index --limit-per-chat 500'` to authenticate and build the local index.
6. If you prefer an installed binary, run `./gradlew installDist` and then `./build/install/telegram-private-search/bin/telegram-private-search index --limit-per-chat 500`.
7. Run `./gradlew run --args='search "find last message where he reported progress on Readian"'` to search.
8. Run `./gradlew run --args='mcp'` to start the MCP server on stdio.

## Notes

- The first `index` run is interactive if `TELEGRAM_USE_CONSOLE_LOGIN=true`.
- TDLight logs are reduced to keep the login prompts visible.
- The current ingestion path indexes text messages from main and archived private chats. Media captions and richer content can be added next.
- Search combines keyword filtering, optional embeddings, and recency-aware ranking.
