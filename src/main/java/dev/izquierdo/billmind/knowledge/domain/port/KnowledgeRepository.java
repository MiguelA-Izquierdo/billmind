package dev.izquierdo.billmind.knowledge.domain.port;

import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeChunk;
import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeDocument;

import java.util.List;
import java.util.UUID;

public interface KnowledgeRepository {

    void upsert(KnowledgeDocument document, List<KnowledgeChunk> chunks);

    boolean isEmpty();

    void deleteAll();

    long rebuildIndex();
}