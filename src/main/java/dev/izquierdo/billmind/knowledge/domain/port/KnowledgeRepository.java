package dev.izquierdo.billmind.knowledge.domain.port;

import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeChunk;
import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeDocument;

import java.util.List;

public interface KnowledgeRepository {

    void save(KnowledgeDocument document, List<KnowledgeChunk> chunks);

    boolean isEmpty();

    void deleteAll();

    long rebuildIndex();
}