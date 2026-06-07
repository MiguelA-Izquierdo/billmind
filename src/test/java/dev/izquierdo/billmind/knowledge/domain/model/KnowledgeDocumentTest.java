package dev.izquierdo.billmind.knowledge.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeDocumentTest {

    @Test
    void shouldCreateDocumentWithValidData() {
        UUID id = UUID.randomUUID();
        KnowledgeDocument doc = KnowledgeDocument.create(id, DocType.GLOSSARY, "Glosario", "REE", null, null);

        assertEquals(id,             doc.getId());
        assertEquals(DocType.GLOSSARY, doc.getDocType());
        assertEquals("Glosario",     doc.getTitle());
        assertEquals("REE",          doc.getSource());
        assertNotNull(doc.getCreatedAt());
    }

    @Test
    void shouldRejectNullId() {
        assertThrows(NullPointerException.class, () ->
                KnowledgeDocument.create(null, DocType.GLOSSARY, "T", "S", null, null));
    }

    @Test
    void shouldRejectBlankTitle() {
        assertThrows(IllegalArgumentException.class, () ->
                KnowledgeDocument.create(UUID.randomUUID(), DocType.GLOSSARY, "  ", "S", null, null));
    }

    @Test
    void shouldRejectValidToBeforeValidFrom() {
        assertThrows(IllegalArgumentException.class, () ->
                KnowledgeDocument.create(UUID.randomUUID(), DocType.REE_GUIDE, "T", "S",
                        LocalDate.of(2025, 6, 1), LocalDate.of(2025, 1, 1)));
    }
}