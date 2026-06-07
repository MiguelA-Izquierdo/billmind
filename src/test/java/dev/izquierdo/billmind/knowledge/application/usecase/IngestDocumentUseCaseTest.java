package dev.izquierdo.billmind.knowledge.application.usecase;

import dev.izquierdo.billmind.knowledge.application.command.IngestDocumentCommand;
import dev.izquierdo.billmind.knowledge.domain.model.DocType;
import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeChunk;
import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeDocument;
import dev.izquierdo.billmind.knowledge.domain.port.DocumentChunker;
import dev.izquierdo.billmind.knowledge.domain.port.KnowledgeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestDocumentUseCaseTest {

    @Mock KnowledgeRepository repository;
    @Mock DocumentChunker chunker;

    @InjectMocks IngestDocumentUseCase useCase;

    @Test
    void shouldChunkAndSaveDocument() {
        IngestDocumentCommand cmd = new IngestDocumentCommand(
                DocType.GLOSSARY, "Glosario", "REE", "word1 word2 word3", null, null);
        when(chunker.chunk(anyString())).thenReturn(List.of("word1 word2", "word2 word3"));

        useCase.execute(cmd);

        ArgumentCaptor<KnowledgeDocument> docCaptor   = ArgumentCaptor.forClass(KnowledgeDocument.class);
        ArgumentCaptor<List<KnowledgeChunk>> chunkCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).save(docCaptor.capture(), chunkCaptor.capture());

        assertEquals("Glosario",     docCaptor.getValue().getTitle());
        assertEquals(DocType.GLOSSARY, docCaptor.getValue().getDocType());
        assertEquals(2, chunkCaptor.getValue().size());
        assertEquals(0, chunkCaptor.getValue().get(0).getChunkIndex());
        assertEquals(1, chunkCaptor.getValue().get(1).getChunkIndex());
    }

    @Test
    void shouldAssignUniqueIdsToChunks() {
        IngestDocumentCommand cmd = new IngestDocumentCommand(
                DocType.GENERAL, "T", "S", "content", null, null);
        when(chunker.chunk(anyString())).thenReturn(List.of("chunk A", "chunk B"));

        useCase.execute(cmd);

        ArgumentCaptor<List<KnowledgeChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).save(any(), captor.capture());

        List<KnowledgeChunk> chunks = captor.getValue();
        assertNotEquals(chunks.get(0).getId(), chunks.get(1).getId());
    }

    @Test
    void shouldRejectNullRepository() {
        assertThrows(NullPointerException.class, () -> new IngestDocumentUseCase(null, chunker));
    }
}