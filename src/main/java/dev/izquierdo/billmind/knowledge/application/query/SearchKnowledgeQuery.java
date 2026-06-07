package dev.izquierdo.billmind.knowledge.application.query;

import dev.izquierdo.billmind._shared.application.query.Query;
import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeSearchResult;

import java.util.List;

public record SearchKnowledgeQuery(String query, int maxResults) implements Query<List<KnowledgeSearchResult>> {

    public SearchKnowledgeQuery {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query cannot be blank");
        if (maxResults <= 0)                  throw new IllegalArgumentException("maxResults must be > 0");
    }
}