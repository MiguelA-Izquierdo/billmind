package dev.izquierdo.billmind.assistant.infrastructure.adapter.cache;

import dev.izquierdo.billmind.assistant.domain.model.RegulatorySnippet;

import java.util.List;

/**
 * A memoized tool execution: the text the model reads plus the regulatory snippets that must be
 * re-added to the turn's citation sink on a cache hit — otherwise a cached answer would lose its
 * citations. Only tools whose result depends solely on their arguments (currently
 * {@code search_regulation}) are cacheable; see {@link ToolResultCache}.
 */
public record CachedToolResult(String text, List<RegulatorySnippet> citations) {

    public CachedToolResult {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
