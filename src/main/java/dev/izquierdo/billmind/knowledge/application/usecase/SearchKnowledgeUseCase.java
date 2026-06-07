package dev.izquierdo.billmind.knowledge.application.usecase;

import dev.izquierdo.billmind.knowledge.application.query.SearchKnowledgeQuery;
import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeSearchResult;
import dev.izquierdo.billmind.knowledge.domain.port.KnowledgeSearchRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class SearchKnowledgeUseCase {

    private final KnowledgeSearchRepository searchRepository;

    public SearchKnowledgeUseCase(KnowledgeSearchRepository searchRepository) {
        this.searchRepository = Objects.requireNonNull(searchRepository, "KnowledgeSearchRepository cannot be null");
    }

    public List<KnowledgeSearchResult> execute(SearchKnowledgeQuery query) {
        return searchRepository.search(query.query(), query.maxResults());
    }
}