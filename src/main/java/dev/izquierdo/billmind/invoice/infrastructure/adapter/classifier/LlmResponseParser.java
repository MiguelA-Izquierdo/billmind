package dev.izquierdo.billmind.invoice.infrastructure.adapter.classifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.izquierdo.billmind._shared.infrastructure.llm.LlmResponseJsonSanitizer;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceClassification;
import dev.izquierdo.billmind._shared.domain.model.InvoiceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LlmResponseParser {

    private static final Logger log = LoggerFactory.getLogger(LlmResponseParser.class);

    private final ObjectMapper objectMapper;
    private final LlmResponseJsonSanitizer jsonSanitizer;

    public LlmResponseParser(ObjectMapper objectMapper, LlmResponseJsonSanitizer jsonSanitizer) {
        this.objectMapper  = objectMapper;
        this.jsonSanitizer = jsonSanitizer;
    }

    public InvoiceClassification parse(String response) {
        try {
            JsonNode node    = objectMapper.readTree(jsonSanitizer.sanitize(response));
            InvoiceType type = parseType(node.path("tipo").asText("OTRO"));
            String company   = node.path("compania").asText("").trim();
            return new InvoiceClassification(type, company);
        } catch (Exception e) {
            log.warn("Failed to parse LLM classification response, defaulting to OTRO. Response: {}", response);
            return new InvoiceClassification(InvoiceType.OTRO, "");
        }
    }

    private InvoiceType parseType(String rawType) {
        try {
            return InvoiceType.valueOf(rawType.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return InvoiceType.OTRO;
        }
    }
}