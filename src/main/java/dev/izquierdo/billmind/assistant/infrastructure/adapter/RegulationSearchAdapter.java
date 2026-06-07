package dev.izquierdo.billmind.assistant.infrastructure.adapter;

import dev.izquierdo.billmind.assistant.domain.model.RegulatorySnippet;
import dev.izquierdo.billmind.assistant.domain.port.RegulationSearchPort;
import dev.izquierdo.billmind.knowledge.domain.port.KnowledgeSearchRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class RegulationSearchAdapter implements RegulationSearchPort {

    private final KnowledgeSearchRepository searchRepository;

    public RegulationSearchAdapter(KnowledgeSearchRepository searchRepository) {
        this.searchRepository = Objects.requireNonNull(searchRepository);
    }

    @Override
    public List<RegulatorySnippet> search(String query, int maxResults) {
        return searchRepository.search(query, maxResults).stream()
                .map(r -> new RegulatorySnippet(r.title(), r.source(), r.docType().name(), r.content()))
                .toList();
    }
}