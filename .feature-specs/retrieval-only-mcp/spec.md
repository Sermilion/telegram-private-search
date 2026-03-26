# Feature: retrieval-only-mcp
Created: 2026-03-26
Status: Complete
Sources: conversational design in this session

## Acceptance Criteria
1. The MCP server and CLI search path must work without calling any external LLM or embedding provider.
2. Retrieval remains local-first: query interpretation uses local heuristics, indexing stores no new remote embeddings, and thread expansion still works.
3. Runtime configuration and docs no longer require any external model-provider configuration for normal MCP usage.
4. Tests cover the new local search intelligence behavior and the retrieval-only runtime path.

---

The best architecture for this project is to keep the server retrieval-only and let the current Copilot agent do the reasoning after calling MCP tools. The server should index Telegram locally, search locally, expand local thread context, and return structured results. It should not call any external model provider internally.

The implementation should:

- replace the runtime provider-backed search intelligence with a local-only implementation
- keep the existing retrieval and context-expansion behavior intact
- simplify config and docs so Telegram credentials and the local database are the only required runtime inputs
- remove unused remote-client wiring from the app container and build dependencies
