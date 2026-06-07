package dev.izquierdo.billmind.knowledge.application.usecase;

import dev.izquierdo.billmind.knowledge.application.query.SearchKnowledgeQuery;
import dev.izquierdo.billmind.knowledge.domain.model.DocType;
import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeSearchResult;
import dev.izquierdo.billmind.knowledge.domain.port.KnowledgeSearchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchKnowledgeUseCaseTest {

    @Mock KnowledgeSearchRepository searchRepository;

    @InjectMocks SearchKnowledgeUseCase useCase;

    @Test
    void shouldDelegateToSearchRepository() {
        KnowledgeSearchResult result = new KnowledgeSearchResult(
                UUID.randomUUID(), UUID.randomUUID(), DocType.GLOSSARY,
                "Glosario", "REE", null, "contenido", 0.95);
        when(searchRepository.search("PVPC", 5)).thenReturn(List.of(result));

        List<KnowledgeSearchResult> results = useCase.execute(new SearchKnowledgeQuery("PVPC", 5));

        assertEquals(1, results.size());
        assertEquals("Glosario", results.get(0).title());
        verify(searchRepository).search("PVPC", 5);
    }

    @Test
    void shouldReturnEmptyListWhenNoResults() {
        when(searchRepository.search(anyString(), anyInt())).thenReturn(List.of());

        List<KnowledgeSearchResult> results = useCase.execute(new SearchKnowledgeQuery("desconocido", 3));

        assertTrue(results.isEmpty());
    }

    @Test
    void shouldRejectNullRepository() {
        assertThrows(NullPointerException.class, () -> new SearchKnowledgeUseCase(null));
    }
}