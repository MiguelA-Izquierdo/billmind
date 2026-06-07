package dev.izquierdo.billmind.knowledge.application.query;

import dev.izquierdo.billmind._shared.application.query.QueryHandler;
import dev.izquierdo.billmind.knowledge.application.usecase.SearchKnowledgeUseCase;
import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeSearchResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class SearchKnowledgeQueryHandler implements QueryHandler<SearchKnowledgeQuery, List<KnowledgeSearchResult>> {

    private final SearchKnowledgeUseCase useCase;

    public SearchKnowledgeQueryHandler(SearchKnowledgeUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "SearchKnowledgeUseCase cannot be null");
    }

    @Override
    public List<KnowledgeSearchResult> handle(SearchKnowledgeQuery query) {
        return useCase.execute(query);
    }

    @Override
    public Class<SearchKnowledgeQuery> queryType() {
        return SearchKnowledgeQuery.class;
    }
}