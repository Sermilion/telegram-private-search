# Feature: targeted-thread-expansion
Created: 2026-03-25
Status: Complete
Sources: conversational design in this session

## Acceptance Criteria
1. Keep retrieval local-first and avoid relying on bulk remote archive embedding for thread analysis.
2. Expand search hits into larger local conversation slices around anchor messages so long arguments can be analyzed.
3. Preserve message order and metadata in expanded results so a remote LLM receives the relevant thread, not isolated chunks.
4. Expose context expansion through `search_messages` without breaking existing basic search behavior.
5. Add tests for expansion, ordering, overlap handling, and no-context behavior.

---

The current search flow indexes chunks and can send message chunk text to an OpenAI-compatible embedding provider during indexing when embeddings are enabled. Retrieval is local, but the archive-wide embedding step expands the privacy boundary more than necessary.

This feature changes the search path to reconstruct relevant thread slices locally from indexed messages. The MCP search tool should return expanded conversation context by default so a remote LLM can analyze the actual thread that matters instead of isolated message chunks. Context expansion should still support opt-out behavior for smaller results.

The implementation should:

- add local repository support for loading ordered messages around an anchor range
- expand ranked chunk hits into conversation slices with preserved message metadata
- avoid duplicate overlapping slices when multiple anchors land in the same local thread window
- expose context controls in the MCP tool schema and response
- update docs to explain the privacy model clearly, including the fact that archive-wide embeddings are disabled by default and expanded thread retrieval happens locally
