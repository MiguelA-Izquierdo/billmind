package dev.izquierdo.billmind.assistant.infrastructure.adapter.cache;

import dev.izquierdo.billmind.assistant.domain.model.RegulatorySnippet;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineToolResultCacheTest {

    private final CaffeineToolResultCache cache =
            new CaffeineToolResultCache(500, Duration.ofHours(2));

    @Test
    void shouldReturnEmptyWhenKeyIsAbsent() {
        assertThat(cache.get("search_regulation|{\"query\":\"x\"}")).isEmpty();
    }

    @Test
    void shouldReturnStoredValueWithCitationsAfterPut() {
        RegulatorySnippet snippet = new RegulatorySnippet("Guía 2.0TD", "REE", "GUIDE", "contenido");
        CachedToolResult value = new CachedToolResult("texto", List.of(snippet));

        cache.put("k", value);

        assertThat(cache.get("k")).contains(value);
        assertThat(cache.get("k")).get().extracting(CachedToolResult::citations)
                .isEqualTo(List.of(snippet));
    }

    @Test
    void shouldDefensivelyCopyCitationsSoCallerMutationDoesNotLeak() {
        List<RegulatorySnippet> mutable = new java.util.ArrayList<>();
        mutable.add(new RegulatorySnippet("A", "REE", "GUIDE", "c"));
        CachedToolResult value = new CachedToolResult("texto", mutable);

        mutable.clear(); // must not affect the stored snapshot

        assertThat(value.citations()).hasSize(1);
    }
}