package dev.izquierdo.billmind.knowledge.infrastructure.config;

import dev.izquierdo.billmind.knowledge.application.usecase.IngestDocumentUseCase;
import dev.izquierdo.billmind.knowledge.domain.port.KnowledgeRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KnowledgeSeedConfig {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSeedConfig.class);

    private final KnowledgeRepository knowledgeRepository;
    private final IngestDocumentUseCase ingestUseCase;
    private final boolean seedEnabled;

    public KnowledgeSeedConfig(KnowledgeRepository knowledgeRepository,
                                IngestDocumentUseCase ingestUseCase,
                                @Value("${knowledge.seed.enabled:true}") boolean seedEnabled) {
        this.knowledgeRepository = knowledgeRepository;
        this.ingestUseCase       = ingestUseCase;
        this.seedEnabled         = seedEnabled;
    }

    @PostConstruct
    public void initialize() {
        if (seedEnabled && knowledgeRepository.isEmpty()) {
            log.info("Seeding knowledge base with example documents…");
            KnowledgeSeedData.exampleDocuments().forEach(cmd -> {
                try {
                    ingestUseCase.execute(cmd);
                    log.info("Seeded: [{}] {}", cmd.docType(), cmd.title());
                } catch (Exception ex) {
                    log.error("Failed to seed document '{}': {}", cmd.title(), ex.getMessage(), ex);
                }
            });
            log.info("Knowledge base seeding complete");
        }
        knowledgeRepository.rebuildIndex();
    }
}