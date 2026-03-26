# Feature: private-telegram-search
Created: 2026-03-25
Status: In Progress
Sources: interactive user conversation

## Acceptance Criteria
1. A local Kotlin/JVM project exists under /Users/sermilion/Development/telegram-private-search.
2. The project can authenticate to Telegram using the user's own account.
3. The project can index private conversation messages and metadata into a local SQLite database.
4. The project supports description-based search through local retrieval and conversation expansion.
5. The project exposes search through an MCP-compatible server interface.
6. The project includes tests covering core search and indexing logic.

---
The user wants to search private Telegram conversations with prompts such as: find the last message where we actively discussed working on Readian and determine when the other person last reported progress. The implementation should be full Kotlin on the JVM for simpler Telegram integration.
