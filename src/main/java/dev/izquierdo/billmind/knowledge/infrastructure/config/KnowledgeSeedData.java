package dev.izquierdo.billmind.knowledge.infrastructure.config;

import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind.knowledge.application.command.IngestDocumentCommand;
import dev.izquierdo.billmind.knowledge.domain.model.DocType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class KnowledgeSeedData {

    private record SeedEntry(UUID docId, DocType docType, SupplyDomain supplyDomain, String title, String source) {
        String resourcePath() {
            return "knowledge/" + supplyDomain.name().toLowerCase() + "/" + docType.name() + ".md";
        }
    }

    private static final List<SeedEntry> ENTRIES = List.of(
            new SeedEntry(
                    UUID.fromString("a1b2c3d4-0001-0000-0000-000000000000"),
                    DocType.GLOSSARY,
                    SupplyDomain.ELECTRICITY,
                    "Glosario de Términos de la Factura Eléctrica",
                    "BillMind / Elaboración propia"
            ),
            new SeedEntry(
                    UUID.fromString("a1b2c3d4-0002-0000-0000-000000000000"),
                    DocType.REE_GUIDE,
                    SupplyDomain.ELECTRICITY,
                    "Guía REE — PVPC, Mercado Eléctrico y Perfil de Consumo",
                    "REE / ESIOS"
            ),
            new SeedEntry(
                    UUID.fromString("a1b2c3d4-0003-0000-0000-000000000000"),
                    DocType.CNMC_CIRCULAR,
                    SupplyDomain.ELECTRICITY,
                    "Circulares y Resoluciones CNMC sobre Peajes y Tarifas Eléctricas",
                    "CNMC — Circular 3/2020 y desarrollos posteriores"
            ),
            new SeedEntry(
                    UUID.fromString("a1b2c3d4-0004-0000-0000-000000000000"),
                    DocType.BOE_REGULATION,
                    SupplyDomain.ELECTRICITY,
                    "Marco Normativo y Regulatorio del Sector Eléctrico Español",
                    "BOE — Legislación vigente"
            ),
            new SeedEntry(
                    UUID.fromString("a1b2c3d4-0005-0000-0000-000000000000"),
                    DocType.GENERAL,
                    SupplyDomain.ELECTRICITY,
                    "Guía General de Facturación Eléctrica — FAQ, Lectura y Optimización",
                    "BillMind / Elaboración propia"
            )
    );

    public static List<IngestDocumentCommand> exampleDocuments() {
        return ENTRIES.stream()
                .map(entry -> new IngestDocumentCommand(
                        entry.docId(),
                        entry.docType(),
                        entry.supplyDomain(),
                        entry.title(),
                        entry.source(),
                        loadResource(entry.resourcePath()),
                        null,
                        null
                ))
                .toList();
    }

    private static String loadResource(String path) {
        try (InputStream is = KnowledgeSeedData.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Knowledge resource not found on classpath: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load knowledge resource: " + path, e);
        }
    }
}