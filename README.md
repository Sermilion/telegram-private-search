# telegram-private-search

![Kotlin/JVM](https://img.shields.io/badge/Kotlin-JVM-7F52FF?logo=kotlin&logoColor=white)
![MCP Server](https://img.shields.io/badge/MCP-server-1f6feb)
![Local First](https://img.shields.io/badge/local-first-2da44e)
![SQLite](https://img.shields.io/badge/storage-SQLite-003B57?logo=sqlite&logoColor=white)

**Turn your Telegram history into searchable memory for you and your AI tools.**

`telegram-private-search` is a local-first Kotlin/JVM tool and MCP server that turns years of private Telegram chats into a structured, searchable knowledge source.

It is built for a very specific feeling: you know a conversation happened, you remember what it meant, but you do not remember the exact wording well enough to find it with normal search.

With this project, your assistant can stop guessing and start querying your local Telegram archive directly.

## Quick start

```bash
cp .env.example .env
./gradlew test
./gradlew run --console=plain --args='index --limit-per-chat 500'
./gradlew run --args='mcp'
```

Then connect an MCP-compatible client to the server and query your Telegram history in natural language.

## Why this is interesting

Your Telegram history often contains:

- decisions that were never written down anywhere else
- informal project updates
- agreements, promises, and follow-ups
- travel plans, addresses, links, and recommendations
- context that only exists inside chat threads

This project turns that messy, memory-shaped information into something an MCP-compatible client can actually use.

Instead of scrolling through years of chats and trying random keywords, you can ask higher-level questions such as:

- "Find the last time we discussed Readian progress"
- "When did she mention sending the invoice?"
- "Show messages where he said he was blocked on the backend"
- "Find the conversation where we compared apartment options"

It indexes your private Telegram chats into a local SQLite database, then combines keyword search with semantic search so vague, memory-shaped queries still have a good chance of finding the right message.

## What this MCP server is

This project exposes your Telegram search index through an MCP server over `stdio`.

That means an AI tool that supports MCP can call into this server as a capability instead of you manually copying text around. In practice, this lets your assistant search your local Telegram history as a structured tool, returning relevant messages and context when you ask questions in natural language.

Think of it as a bridge between:

- your local Telegram archive
- a search engine tuned for conversational recall
- an MCP-compatible assistant or client

## Why MCP matters here

Without MCP, this project is already useful as a CLI search tool.

With MCP, it becomes part of a larger assistant workflow. Your client can treat Telegram search as a real tool call instead of a manual side task. That makes it possible to:

- ask questions in natural language and get grounded results
- combine Telegram search with other tools in the same assistant session
- reduce hallucinated memory reconstruction
- keep the message index local while still making it usable by AI tooling

## Architecture at a glance

```text
Telegram account
      |
      v
   TDLight client
      |
      v
Message ingestion
      |
      v
Local SQLite index (Room)
      |
      +--> keyword search
      |
      +--> optional embeddings / semantic retrieval
      |
      v
Ranking and result shaping
      |
      +--> CLI search
      |
      +--> MCP server over stdio
              |
              v
      MCP-compatible assistant or client
```

In short: Telegram messages come in, a local index is built, hybrid retrieval finds relevant results, and the MCP server exposes that capability to tools that can speak MCP.

## Why it exists

People rarely remember the exact words used in a chat. They remember intent, context, and fragments:

- "the message where he finally agreed"
- "that time we planned the trip"
- "the last update about the feature rollout"

Traditional search is great when you remember exact text. It is much less helpful when you remember meaning.

`telegram-private-search` is built for that second case. It helps recover messages from fuzzy memory, long-running conversations, and topic shifts spread across multiple chats.

## What it can do

- Authenticate with your own Telegram account
- Index private chat messages into a local SQLite database
- Search with hybrid full-text and semantic retrieval
- Rank results with recency-aware heuristics
- Expose search through an MCP-compatible server
- Run as a local CLI for indexing and direct querying

## How it works

1. The app authenticates against Telegram using your own API credentials.
2. It reads private chat messages from your account.
3. Messages are stored locally in SQLite via Room.
4. Search uses keyword matching plus optional embeddings from an OpenAI-compatible provider.
5. The MCP server exposes that search capability to external AI clients.

The current ingestion path focuses on text messages from main and archived private chats.

## Privacy and data flow

This project is designed to keep retrieval local and only expose the conversation slices you actually ask for.

- Telegram data is indexed into a local database under `data/`
- Your `.env` stays local and should never be committed
- The MCP server runs locally over `stdio`
- `search_messages` reconstructs expanded thread context locally before returning results
- Query analysis falls back to local heuristics unless you explicitly enable remote query analysis
- Archive-wide embeddings are disabled by default
- Semantic features use an OpenAI-compatible API for query understanding, and for archive embeddings only when you explicitly enable them

If query analysis is enabled, your search query text is sent to the configured model provider. If embeddings are enabled, indexing sends chunk text to the provider to build semantic vectors. If you want the strictest setup, leave both disabled and rely on local heuristics plus thread expansion.

## Example use cases

This project is useful when you want to:

- recover the last concrete progress update from a teammate or friend
- find a decision that was made informally in chat
- trace when someone promised, postponed, confirmed, or declined something
- search conversations by meaning rather than exact phrasing
- give an MCP-enabled assistant access to your Telegram memory as a tool

## Stack

- Kotlin/JVM
- Clean architecture with MVVM-style presentation state
- Room 3 with bundled SQLite
- TDLight for Telegram user-account access
- OpenAI-compatible API for query understanding and embeddings
- MCP server over `stdio`

## Setup

1. Copy `.env.example` to `.env`.
2. Fill in your Telegram API credentials from `https://my.telegram.org`.
3. Install OpenSSL 3 on macOS if it is not already installed.
4. Run `./gradlew test`.
5. Optionally set `TELEGRAM_PHONE_NUMBER` in `.env` to skip the first login-mode prompt.

Example local configuration:

```env
OPENAI_API_KEY=
TELEGRAM_API_ID=
TELEGRAM_API_HASH=
TELEGRAM_PHONE_NUMBER=
TELEGRAM_USE_CONSOLE_LOGIN=true
TELEGRAM_SESSION_DIR=data/telegram-session
DATABASE_PATH=data/telegram-search.db
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_CHAT_MODEL=gpt-4.1-mini
QUERY_ANALYSIS_ENABLED=false
OPENAI_EMBEDDING_MODEL=text-embedding-3-small
EMBEDDINGS_ENABLED=false
```

## CLI usage

Build the local index:

```bash
./gradlew run --console=plain --args='index --limit-per-chat 500'
```

Install a runnable distribution:

```bash
./gradlew installDist
./build/install/telegram-private-search/bin/telegram-private-search index --limit-per-chat 500
```

Run a direct search:

```bash
./gradlew run --args='search "find last message where he reported progress on Readian" --context-before-messages 12 --context-after-messages 12'
```

## MCP usage

Start the MCP server:

```bash
./gradlew run --args='mcp'
```

Once running, an MCP-compatible client can use the server's search tools to query your indexed Telegram history.

`search_messages` accepts these optional context controls:

- `context_before_messages`: how many earlier messages to include around each matched anchor
- `context_after_messages`: how many later messages to include around each matched anchor

Both default to `12`, so the tool returns a local conversation slice rather than an isolated chunk. Set them to `0` if you want anchor-only results.

## Notes

- The first `index` run is interactive if `TELEGRAM_USE_CONSOLE_LOGIN=true`.
- TDLight logs are reduced to keep login prompts visible.
- Search combines local keyword filtering, optional embeddings, recency-aware ranking, and local thread expansion.
