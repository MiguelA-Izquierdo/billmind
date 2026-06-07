package dev.izquierdo.billmind.knowledge.domain.port;

import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeSearchResult;

import java.util.List;

public interface KnowledgeSearchRepository {

    List<KnowledgeSearchResult> search(String query, int maxResults);
}