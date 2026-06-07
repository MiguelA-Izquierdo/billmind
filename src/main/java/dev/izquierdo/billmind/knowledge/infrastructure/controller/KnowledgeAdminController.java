package dev.izquierdo.billmind.knowledge.infrastructure.controller;

import dev.izquierdo.billmind._shared.application.command.CommandBus;
import dev.izquierdo.billmind._shared.application.query.QueryBus;
import dev.izquierdo.billmind._shared.infrastructure.dto.SuccessResponseDTO;
import dev.izquierdo.billmind.knowledge.application.command.IngestDocumentCommand;
import dev.izquierdo.billmind.knowledge.application.query.SearchKnowledgeQuery;
import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeSearchResult;
import dev.izquierdo.billmind.knowledge.domain.port.KnowledgeRepository;
import dev.izquierdo.billmind.knowledge.infrastructure.config.KnowledgeSeedData;
import dev.izquierdo.billmind.knowledge.infrastructure.controller.dto.IngestRequest;
import dev.izquierdo.billmind.knowledge.infrastructure.controller.dto.KnowledgeSearchResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/knowledge")
public class KnowledgeAdminController {

    private final CommandBus commandBus;
    private final QueryBus queryBus;
    private final KnowledgeRepository knowledgeRepository;

    public KnowledgeAdminController(CommandBus commandBus, QueryBus queryBus,
                                     KnowledgeRepository knowledgeRepository) {
        this.commandBus          = commandBus;
        this.queryBus            = queryBus;
        this.knowledgeRepository = knowledgeRepository;
    }

    @PostMapping("/ingest")
    public ResponseEntity<SuccessResponseDTO> ingest(@RequestBody IngestRequest request) {
        commandBus.dispatch(new IngestDocumentCommand(
                request.docType(), request.title(), request.source(),
                request.content(), request.validFrom(), request.validTo()));
        return ResponseEntity.ok(SuccessResponseDTO.of(200, "Documento ingestado correctamente"));
    }

    @PostMapping("/ingest/seed")
    public ResponseEntity<SuccessResponseDTO> seed() {
        KnowledgeSeedData.exampleDocuments()
                .forEach(commandBus::dispatch);
        return ResponseEntity.ok(SuccessResponseDTO.of(200, "Documentos de ejemplo ingestados correctamente"));
    }

    @GetMapping("/search")
    public ResponseEntity<SuccessResponseDTO> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int maxResults) {
        List<KnowledgeSearchResult> results = queryBus.dispatch(new SearchKnowledgeQuery(q, maxResults));
        List<KnowledgeSearchResponse> data = results.stream().map(KnowledgeSearchResponse::from).toList();
        return ResponseEntity.ok(SuccessResponseDTO.of(200, "Búsqueda completada", data));
    }

    @DeleteMapping
    public ResponseEntity<SuccessResponseDTO> deleteAll() {
        knowledgeRepository.deleteAll();
        return ResponseEntity.ok(SuccessResponseDTO.of(200, "Base de conocimiento eliminada"));
    }
}