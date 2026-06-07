package dev.izquierdo.billmind.assistant.domain.port;

import dev.izquierdo.billmind.assistant.domain.model.RegulatorySnippet;

import java.util.List;

public interface RegulationSearchPort {
    List<RegulatorySnippet> search(String query, int maxResults);
}