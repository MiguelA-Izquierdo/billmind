package dev.izquierdo.billmind.assistant.infrastructure.adapter.cache;

import java.util.Optional;

/**
 * Cache for agentic tool results, keyed by a caller-computed signature. Lets the agentic adapter
 * skip re-running expensive, argument-deterministic tool calls (currently {@code search_regulation}:
 * embedding + hybrid vector/BM25 retrieval) both within a turn and across turns.
 *
 * <p>Only tools whose result is a pure function of their arguments — independent of the user's
 * invoice — may be cached through this port; invoice-specific tools would leak one session's data
 * into another. The Caffeine implementation is in-process; a Redis-backed implementation will
 * later share the cache across instances behind this same interface.
 */
public interface ToolResultCache {

    Optional<CachedToolResult> get(String key);

    void put(String key, CachedToolResult value);
}
